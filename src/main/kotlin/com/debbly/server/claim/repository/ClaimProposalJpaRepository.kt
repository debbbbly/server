package com.debbly.server.claim.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClaimProposalJpaRepository : JpaRepository<ClaimProposalEntity, String>