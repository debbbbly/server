package com.debbly.server.claim.topic.top

import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.top.TopClaimRedisRepository
import com.debbly.server.claim.topic.repository.TopicRepository
import com.debbly.server.claim.topic.model.toModel
import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Service
import kotlin.math.abs
import kotlin.math.ln1p
import kotlin.math.max

@Service
class TopTopicsService(
    private val topClaimRedisRepository: TopClaimRedisRepository,
    private val topicRepository: TopicRepository,
    private val topTopicRedisRepository: TopTopicRedisRepository,
    private val categoryCachedRepository: CategoryCachedRepository
) {
    private val logger = getLogger(javaClass)

    fun getTopTopicsFromCache(): List<TopTopicResponse> {
        return topTopicRedisRepository.findAllByOrderByRankAsc().map { stats ->
            TopTopicResponse(
                topicId = stats.topicId,
                categoryId = stats.categoryId,
                title = stats.title,
                rank = stats.rank,
                claimCount = stats.claimCount,
                recentDebates = stats.recentDebates
            )
        }
    }

    /**
     * Calculate and update top topics based on already calculated top claims.
     */
    fun calculateAndUpdateTopTopics() {
        val activeCategoryIds = categoryCachedRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        // Get top claims from cache and filter those with topicId
        val topClaims = topClaimRedisRepository.findAllByOrderByRankAsc()
            .filter { it.categoryId in activeCategoryIds && it.topicId != null }

        if (topClaims.isEmpty()) {
            logger.warn("No top claims found in cache, top topics will not be updated")
            return
        }

        // Build claim scores with topicId directly from cache
        val claimScores = topClaims.map { claim ->
            ClaimScoreForTopic(
                claimId = claim.claimId,
                topicId = claim.topicId!!,
                score = claim.score,
                debatesIn48h = claim.recentDebates,
                forCount = claim.forCount,
                againstCount = claim.againstCount
            )
        }

        // Group by topic
        val claimsByTopic = claimScores.groupBy { it.topicId }

        // Calculate topic scores
        val topicScores = claimsByTopic.map { (topicId, claims) ->
            // Cap contribution of a single claim (anti-dominance) - top 3 claims max
            val cappedScore = claims
                .sortedByDescending { it.score }
                .take(3)
                .sumOf { it.score }

            val uniqueClaims = claims.size
            val debateCount = claims.sumOf { it.debatesIn48h }

            // Topic balance = average claim balance
            val avgBalance = claims
                .map {
                    max(0.3, 1.0 - abs(it.forCount - it.againstCount).toDouble() /
                            max(it.forCount + it.againstCount, 1))
                }
                .average()

            // Final topic score
            val topicScore = (cappedScore * 1.5 + ln1p(uniqueClaims.toDouble()) * 2.0) * avgBalance

            TopicScore(
                topicId = topicId,
                score = topicScore,
                claimCount = uniqueClaims,
                recentDebates = debateCount
            )
        }

        val topTopics = topicScores
            .sortedByDescending { it.score }
            .take(50)

        topTopicRedisRepository.deleteAll()

        // Get all topic details in one query
        val topicIds = topTopics.map { it.topicId }
        val topicsMap = topicRepository.findAllById(topicIds)
            .associate { it.topicId to it.toModel() }

        topTopics.forEachIndexed { index, topicScore ->
            val topic = topicsMap[topicScore.topicId]
            if (topic != null && topic.categoryId in activeCategoryIds) {
                topTopicRedisRepository.save(
                    TopTopicWithStats(
                        topicId = topicScore.topicId,
                        categoryId = topic.categoryId,
                        title = topic.title,
                        rank = index + 1,
                        score = topicScore.score,
                        claimCount = topicScore.claimCount,
                        recentDebates = topicScore.recentDebates
                    )
                )
            }
        }
    }

    private data class ClaimScoreForTopic(
        val claimId: String,
        val topicId: String,
        val score: Double,
        val debatesIn48h: Int,
        val forCount: Int,
        val againstCount: Int
    )

    private data class TopicScore(
        val topicId: String,
        val score: Double,
        val claimCount: Int,
        val recentDebates: Int
    )
}
