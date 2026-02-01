package com.debbly.server.match

import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.home.model.QueueBroadcastResponse
import com.debbly.server.home.model.QueueClaimResponse
import com.debbly.server.home.model.QueueTopicResponse
import com.debbly.server.home.model.QueueUserResponse
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.pusher.model.PusherEventName.QUEUE_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.QUEUE_UPDATE
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.user.repository.UserCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class QueueService(
    private val matchQueueRepository: MatchQueueRepository,
    private val userCachedRepository: UserCachedRepository,
    private val claimCachedRepository: ClaimCachedRepository,
    private val pusherService: PusherService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Get queue users for specific topic IDs.
     * Users waiting on claims under these topics are also included with their stance.
     */
    fun getQueueByTopicIds(topicIds: Set<String>): Map<String, List<QueueUserResponse>> {
        if (topicIds.isEmpty()) return emptyMap()

        val allRequests = matchQueueRepository.findAll()
        val userIds = allRequests.map { it.userId }.toSet()
        val usersMap = userCachedRepository.findByIds(userIds.toList())

        // Collect all claim IDs to fetch their topic mappings
        val allClaimIds = allRequests.flatMap { it.claims.map { c -> c.claimId } }.toSet()
        val claimToTopicMap = allClaimIds
            .mapNotNull { claimId -> claimCachedRepository.findById(claimId)?.let { claimId to it.topicId } }
            .toMap()

        val result = mutableMapOf<String, MutableList<QueueUserResponse>>()

        for (request in allRequests) {
            val user = usersMap[request.userId] ?: continue

            // Add from direct topic subscriptions
            for (topic in request.topics) {
                if (topic.topicId in topicIds) {
                    result.getOrPut(topic.topicId) { mutableListOf() }.add(
                        QueueUserResponse(
                            userId = user.userId,
                            username = user.username ?: "unknown",
                            avatarUrl = user.avatarUrl,
                            stance = topic.stance
                        )
                    )
                }
            }

            // Add from claim subscriptions (map claim stance to topic)
            for (claim in request.claims) {
                val topicId = claimToTopicMap[claim.claimId] ?: continue
                if (topicId in topicIds) {
                    // Check if user already added to this topic
                    val existing = result[topicId]?.find { it.userId == user.userId }
                    if (existing == null) {
                        result.getOrPut(topicId) { mutableListOf() }.add(
                            QueueUserResponse(
                                userId = user.userId,
                                username = user.username ?: "unknown",
                                avatarUrl = user.avatarUrl,
                                stance = claim.stance
                            )
                        )
                    }
                }
            }
        }

        return result
    }

    /**
     * Get queue users for specific claim IDs.
     */
    fun getQueueByClaimIds(claimIds: Set<String>): Map<String, List<QueueUserResponse>> {
        if (claimIds.isEmpty()) return emptyMap()

        val allRequests = matchQueueRepository.findAll()
        val userIds = allRequests.map { it.userId }.toSet()
        val usersMap = userCachedRepository.findByIds(userIds.toList())

        val result = mutableMapOf<String, MutableList<QueueUserResponse>>()

        for (request in allRequests) {
            val user = usersMap[request.userId] ?: continue

            for (claim in request.claims) {
                if (claim.claimId in claimIds) {
                    result.getOrPut(claim.claimId) { mutableListOf() }.add(
                        QueueUserResponse(
                            userId = user.userId,
                            username = user.username ?: "unknown",
                            avatarUrl = user.avatarUrl,
                            stance = claim.stance
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Build full queue broadcast data for all topics and claims.
     */
    fun getQueueBroadcast(): QueueBroadcastResponse {
        val allRequests = matchQueueRepository.findAll()

        if (allRequests.isEmpty()) {
            return QueueBroadcastResponse(topics = emptyList(), claims = emptyList())
        }

        val userIds = allRequests.map { it.userId }.toSet()
        val usersMap = userCachedRepository.findByIds(userIds.toList())

        // Collect all claim IDs to fetch their topic mappings
        val allClaimIds = allRequests.flatMap { it.claims.map { c -> c.claimId } }.toSet()
        val claimToTopicMap = allClaimIds
            .mapNotNull { claimId -> claimCachedRepository.findById(claimId)?.let { claimId to it.topicId } }
            .toMap()

        val topicQueues = mutableMapOf<String, MutableList<QueueUserResponse>>()
        val claimQueues = mutableMapOf<String, MutableList<QueueUserResponse>>()

        for (request in allRequests) {
            val user = usersMap[request.userId] ?: continue

            // Add from direct topic subscriptions
            for (topic in request.topics) {
                topicQueues.getOrPut(topic.topicId) { mutableListOf() }.add(
                    QueueUserResponse(
                        userId = user.userId,
                        username = user.username ?: "unknown",
                        avatarUrl = user.avatarUrl,
                        stance = topic.stance
                    )
                )
            }

            // Add from claim subscriptions
            for (claim in request.claims) {
                claimQueues.getOrPut(claim.claimId) { mutableListOf() }.add(
                    QueueUserResponse(
                        userId = user.userId,
                        username = user.username ?: "unknown",
                        avatarUrl = user.avatarUrl,
                        stance = claim.stance
                    )
                )

                // Also add to topic queue
                val topicId = claimToTopicMap[claim.claimId] ?: continue
                val existing = topicQueues[topicId]?.find { it.userId == user.userId }
                if (existing == null) {
                    topicQueues.getOrPut(topicId) { mutableListOf() }.add(
                        QueueUserResponse(
                            userId = user.userId,
                            username = user.username ?: "unknown",
                            avatarUrl = user.avatarUrl,
                            stance = claim.stance
                        )
                    )
                }
            }
        }

        return QueueBroadcastResponse(
            topics = topicQueues.map { (topicId, queue) ->
                QueueTopicResponse(topicId = topicId, queue = queue)
            },
            claims = claimQueues.map { (claimId, queue) ->
                QueueClaimResponse(claimId = claimId, queue = queue)
            }
        )
    }

    /**
     * Broadcast queue update to all users via Pusher.
     * Called at the end of each matching round.
     */
    fun broadcastQueueUpdate() {
        try {
            val queueData = getQueueBroadcast()

            if (queueData.topics.isEmpty() && queueData.claims.isEmpty()) {
                return
            }

            val message = message(QUEUE_UPDATE, queueData)
            pusherService.sendChannelMessage(PusherService.GLOBAL_CHANNEL_ID, QUEUE_EVENT, message)

            logger.debug("Broadcast queue update: {} topics, {} claims",
                queueData.topics.size, queueData.claims.size)
        } catch (e: Exception) {
            logger.error("Failed to broadcast queue update", e)
        }
    }
}
