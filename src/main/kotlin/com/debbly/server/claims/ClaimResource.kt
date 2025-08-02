package com.debbly.server.claims

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/claims")
class ClaimResource(private val service: ClaimService) {

    @GetMapping
    fun getAllClaims(): List<Claim> = service.findAll()

    @PostMapping
    fun createClaim(@RequestBody claim: Claim): Claim = service.save(claim)
}