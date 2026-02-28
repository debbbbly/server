package com.debbly.server.challenge

import com.debbly.server.IdService
import com.debbly.server.challenge.repository.ChallengeEntity
import com.debbly.server.challenge.repository.ChallengeJpaRepository
import com.debbly.server.challenge.repository.ChallengeStatus
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.opposite
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.match.MatchService
import com.debbly.server.user.OnlineUsersService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant

@Service
class ChallengeService(
    private val challengeJpaRepository: ChallengeJpaRepository,
    private val claimCachedRepository: ClaimCachedRepository,
    private val userCachedRepository: UserCachedRepository,
    private val matchService: MatchService,
    private val onlineUsersService: OnlineUsersService,
    private val idService: IdService,
    private val clock: Clock,
) {
    companion object {
        private const val CHALLENGE_TTL_HOURS = 24L
    }

    fun create(user: UserModel, claimId: String, stance: ClaimStance): ChallengeResponse {
        if (stance == ClaimStance.EITHER) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Stance must be FOR or AGAINST")
        }

        val claim = claimCachedRepository.getById(claimId)
        val now = Instant.now(clock)

        val challenge = challengeJpaRepository.save(
            ChallengeEntity(
                challengeId = idService.getId(),
                claimId = claim.claimId,
                hostUserId = user.userId,
                hostStance = stance,
                status = ChallengeStatus.PENDING,
                createdAt = now,
                expiresAt = now.plusSeconds(CHALLENGE_TTL_HOURS * 3600),
            )
        )

        return toResponse(challenge)
    }

    fun getById(challengeId: String): ChallengeResponse {
        val challenge = challengeJpaRepository.findById(challengeId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found") }
        return toResponse(challenge)
    }

    fun cancel(user: UserModel, challengeId: String): ChallengeResponse {
        val challenge = challengeJpaRepository.findById(challengeId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found") }

        if (challenge.hostUserId != user.userId) throw ForbiddenException("Only the host can cancel a challenge")

        if (challenge.status != ChallengeStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Challenge is not pending")
        }

        val updated = challengeJpaRepository.save(challenge.copy(status = ChallengeStatus.CANCELLED))
        return toResponse(updated)
    }

    fun accept(user: UserModel, challengeId: String): ChallengeResponse {
        val challenge = challengeJpaRepository.findById(challengeId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found") }

        if (challenge.hostUserId == user.userId) throw ForbiddenException("Host cannot accept their own challenge")

        if (challenge.status != ChallengeStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Challenge is not pending")
        }

        if (Instant.now(clock).isAfter(challenge.expiresAt)) {
            throw ResponseStatusException(HttpStatus.GONE, "Challenge has expired")
        }

        val hostUser = userCachedRepository.getById(challenge.hostUserId)
        val acceptorStance = challenge.hostStance.opposite()

        matchService.joinForChallenge(
            hostUser = hostUser,
            acceptorUser = user,
            claimId = challenge.claimId,
            hostStance = challenge.hostStance,
            acceptorStance = acceptorStance,
            challengeId = challenge.challengeId,
        )

        return toResponse(challenge)
    }

    fun markAccepted(challengeId: String) {
        val challenge = challengeJpaRepository.findById(challengeId).orElse(null) ?: return
        if (challenge.status == ChallengeStatus.PENDING) {
            challengeJpaRepository.save(challenge.copy(status = ChallengeStatus.ACCEPTED))
        }
    }

    private fun toResponse(challenge: ChallengeEntity): ChallengeResponse {
        val claim = claimCachedRepository.getById(challenge.claimId)
        val host = userCachedRepository.getById(challenge.hostUserId)
        return ChallengeResponse(
            challengeId = challenge.challengeId,
            claimId = challenge.claimId,
            claimTitle = claim.title,
            claimSlug = claim.slug,
            hostUserId = challenge.hostUserId,
            hostUsername = host.username,
            hostAvatarUrl = host.avatarUrl,
            hostOnline = onlineUsersService.isUserOnline(challenge.hostUserId),
            hostStance = challenge.hostStance,
            status = challenge.status,
            createdAt = challenge.createdAt,
            expiresAt = challenge.expiresAt,
        )
    }
}

data class ChallengeResponse(
    val challengeId: String,
    val claimId: String,
    val claimTitle: String,
    val claimSlug: String?,
    val hostUserId: String,
    val hostUsername: String,
    val hostAvatarUrl: String?,
    val hostOnline: Boolean,
    val hostStance: ClaimStance,
    val status: ChallengeStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
)
