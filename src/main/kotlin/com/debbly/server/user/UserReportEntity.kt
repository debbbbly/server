package com.debbly.server.user

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.Instant

@Entity(name = "user_reports")
data class UserReportEntity(
    @Id
    val reportId: String,
    val reporterUserId: String?,
    val reportedUserId: String,
    @Enumerated(EnumType.STRING)
    val reason: ReportReason,
    val createdAt: Instant
)

enum class ReportReason {
    HARASSMENT,
    HATE_SPEECH,
    HARMFUL_CONTENT,
    SPAM
}
