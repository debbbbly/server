package com.debbly.server.user.repository

import com.debbly.server.user.UserReportEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserReportJpaRepository : JpaRepository<UserReportEntity, String> {
    fun existsByReporterUserIdAndReportedUserId(reporterUserId: String, reportedUserId: String): Boolean
}
