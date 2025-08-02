package com.debbly.server.claims

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClaimRepository : JpaRepository<Claim, java.util.UUID>
