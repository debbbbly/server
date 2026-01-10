package com.debbly.server.claim.exception

import com.debbly.server.claim.SimilarClaim

class DuplicateClaimException(
    val duplicateClaim: SimilarClaim
) : RuntimeException("This claim already exists: '${duplicateClaim.title}' (${(duplicateClaim.similarity * 100).toInt()}% similar)")
