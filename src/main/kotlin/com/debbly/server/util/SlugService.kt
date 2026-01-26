package com.debbly.server.util

import org.springframework.stereotype.Service
import java.text.Normalizer

@Service
class SlugService {

    companion object {
        private const val MAX_SLUG_LENGTH = 255
    }

    /**
     * Generate a URL-friendly slug from text.
     * Example: "Lukashenko Should Be Free" -> "lukashenko-should-be-free"
     */
    fun slugify(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .take(MAX_SLUG_LENGTH)
            .trimEnd('-')
    }
}
