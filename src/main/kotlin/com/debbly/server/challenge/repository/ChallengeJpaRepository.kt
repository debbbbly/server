package com.debbly.server.challenge.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChallengeJpaRepository : JpaRepository<ChallengeEntity, String>
