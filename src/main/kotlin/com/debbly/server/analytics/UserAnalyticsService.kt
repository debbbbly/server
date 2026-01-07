package com.debbly.server.analytics

import com.debbly.server.user.repository.UserJpaRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@Service
class UserAnalyticsService(
    private val userJpaRepository: UserJpaRepository
) {

    /**
     * Daily Active Users - users active today
     */
    fun getDailyActiveUsers(): Long {
        val startOfToday = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        return userJpaRepository.countActiveUsersSince(startOfToday)
    }

    /**
     * Weekly Active Users - users active in last 7 days
     */
    fun getWeeklyActiveUsers(): Long {
        val weekAgo = Instant.now().minus(Duration.ofDays(7))
        return userJpaRepository.countActiveUsersSince(weekAgo)
    }

    /**
     * Monthly Active Users - users active in last 30 days
     */
    fun getMonthlyActiveUsers(): Long {
        val monthAgo = Instant.now().minus(Duration.ofDays(30))
        return userJpaRepository.countActiveUsersSince(monthAgo)
    }

    /**
     * Stickiness - DAU/MAU ratio (engagement metric)
     * Higher is better, 20%+ is considered good
     */
    fun getStickiness(): Double {
        val dau = getDailyActiveUsers()
        val mau = getMonthlyActiveUsers()
        return if (mau > 0) (dau.toDouble() / mau.toDouble()) * 100 else 0.0
    }

    /**
     * User retention - % of users from a cohort still active
     *
     * @param cohortDaysAgo How many days ago the cohort signed up (e.g., 30 for users who joined 30 days ago)
     * @param activeDaysThreshold Users active within this many days are considered retained (e.g., 7 for active in last week)
     */
    fun getCohortRetention(cohortDaysAgo: Int, activeDaysThreshold: Int = 7): Double {
        val now = Instant.now()
        val cohortStart = now.minus(Duration.ofDays(cohortDaysAgo.toLong() + 1))
            .atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        val cohortEnd = now.minus(Duration.ofDays(cohortDaysAgo.toLong()))
            .atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        val activeThreshold = now.minus(Duration.ofDays(activeDaysThreshold.toLong()))

        val cohortSize = userJpaRepository.countUsersCreatedBetween(cohortStart, cohortEnd)
        if (cohortSize == 0L) return 0.0

        val retainedUsers = userJpaRepository.countRetainedUsers(cohortStart, cohortEnd, activeThreshold)

        return (retainedUsers.toDouble() / cohortSize.toDouble()) * 100
    }

    /**
     * Find users who haven't been active in N days
     */
    fun getInactiveUsers(inactiveDays: Int = 30): List<String> {
        val threshold = Instant.now().minus(Duration.ofDays(inactiveDays.toLong()))
        return userJpaRepository.findInactiveUsers(threshold)
            .map { it.username }
    }

    /**
     * User growth - new signups today
     */
    fun getNewSignupsToday(): Long {
        val now = Instant.now()
        val startOfToday = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        val startOfTomorrow = startOfToday.plus(Duration.ofDays(1))
        return userJpaRepository.countUsersCreatedBetween(startOfToday, startOfTomorrow)
    }

    /**
     * Comprehensive analytics summary
     */
    fun getAnalyticsSummary(): AnalyticsSummary {
        return AnalyticsSummary(
            dau = getDailyActiveUsers(),
            wau = getWeeklyActiveUsers(),
            mau = getMonthlyActiveUsers(),
            stickiness = getStickiness(),
            newSignupsToday = getNewSignupsToday(),
            retention7Day = getCohortRetention(7, 7),
            retention30Day = getCohortRetention(30, 7)
        )
    }
}

data class AnalyticsSummary(
    val dau: Long,
    val wau: Long,
    val mau: Long,
    val stickiness: Double,
    val newSignupsToday: Long,
    val retention7Day: Double,
    val retention30Day: Double
)
