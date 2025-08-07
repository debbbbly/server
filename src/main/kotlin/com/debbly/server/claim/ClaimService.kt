package com.debbly.server.claim

import org.springframework.stereotype.Service

@Service
class ClaimService(private val repository: ClaimRepository) {

    fun findAll(): List<Claim> = repository.findAll()

    fun save(claim: Claim): Claim = repository.save(claim)
}