package com.debbly.server.claim.exception

class ClaimValidationException(
    val violations: List<String>,
    val reasoning: String?
) : RuntimeException("Claim violates platform rules: ${violations.joinToString(", ")}")