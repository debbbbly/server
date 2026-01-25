package com.debbly.server.user

import com.debbly.server.ai.OpenAiService
import com.debbly.server.ai.UsernameValidationResult
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class UsernameService(
    private val userCachedRepository: UserCachedRepository,
    private val openAIService: OpenAiService
) {
    private val adjectives = listOf(
        // original kept
        "Bright", "Calm", "Clean", "Clear", "Crisp", "Curious", "Dry", "Easy", "Equal", "Even",
        "Fair", "Firm", "Fresh", "Gentle", "Honest", "Kind", "Light", "Mild", "Natural", "Neat",
        "Neutral", "Patient", "Plain", "Precise", "Quiet", "Raw", "Sharp", "Silent", "Simple",
        "Smooth", "Soft", "Stable", "Steady", "Still", "Sweet", "Warm", "Whole", "Brave",
        "Bold", "Cool", "Chill", "Clear", "Quick", "True", "Wise", "Witty", "Pure", "Calmly",
        "Sunny", "Happy", "Mellow", "Zen", "Nice"
    )

    private val fruitsVegetables = listOf(
        "Onion", "Corn", "Kiwi", "Apple", "Lemon", "Cherry", "Banana", "Carrot", "Olive", "Pear",
        "Kale", "Grape", "Mango", "Fig", "Beet", "Pea", "Tomato", "Celery", "Garlic", "Mint",
        "Peach", "Bean", "Rice", "Plum", "Pepper", "Radish", "Turnip", "Papaya", "Yam", "Grain",
        "Berry", "Lime", "Date", "Guava", "Melon", "Okra", "Chard", "Leek", "Shallot", "Oats",
        "Wheat", "Rye"
    )

    private val reservedUsernames: Set<String> = setOf(
        // Administrative & Authority
        "admin", "administrator", "root", "system", "moderator", "mod", "mods",
        "support", "staff", "team", "official", "service", "security",
        "ceo", "founder", "owner", "verified", "trusted",

        // Platform / Brand
        "debbly", "debblyapp", "debblysupport",

        // Bots & Automation
        "bot", "bots", "automated", "automation", "ai",

        // Auth / Security
        "login", "logout", "signin", "signout", "signup", "register",
        "auth", "authenticate", "password", "reset", "token", "verify", "verification",

        // User states
        "user", "users", "guest", "anonymous", "anon",
        "null", "undefined", "unknown", "deleted", "removed", "banned", "suspended",

        // App routes / pages
        "about", "help", "contact", "privacy", "terms", "tos", "rules", "faq",
        "settings", "profile", "account", "dashboard", "home", "index",

        // Core product concepts
        "debate", "debates", "stage", "stages", "room", "rooms",
        "claim", "claims", "topic", "topics", "match", "matching",
        "feed", "live", "stream", "broadcast",

        // System / routing
        "api", "assets", "static", "cdn", "media", "images",
        "robots", "sitemap", "favicon",

        // Business / monetization
        "billing", "payment", "payments", "wallet",
        "subscription", "premium", "pro", "enterprise",
        "ads", "advertising", "sponsor", "sponsored",

        // Legal
        "legal", "lawyer", "compliance", "dmca", "copyright", "trademark",

        // Generic noise
        "everyone", "someone", "anyone", "nobody", "all", "none",
        "true"
    )

    private val reservedPrefixes = listOf(
        "_", "admin", "moderator", "support", "system", "official", "security"
    )

    private val reservedSuffixes = listOf(
        "_", "admin", "moderator", "support", "official", "bot", "ai"
    )

    val usernameRegex = Regex(
        "^(?!.*__)(?!^(.)\\1+$)(?!.*debbly)[A-Za-z0-9_]{5,15}$"
    )

    fun generateUsername(): String {
        val adjective = adjectives.random().lowercase()
        val fruit = fruitsVegetables.random().lowercase()
        val base = "${adjective}_${fruit}"

        val maxAttempts = 10
        for (attempt in 0 until maxAttempts) {
            val number = Random.nextInt(0, 99)
            val username = "${base}_$number"

            if (userCachedRepository.findByUsername(username) == null) {
                return username
            }
        }

        return "${base}_${Random.nextInt(100, 999)}"
    }

    fun validateUsername(username: String, currentUserId: String? = null): UsernameValidationResult {
        val trimmed = username.trim()

        if (!trimmed.matches(usernameRegex)) {
            return UsernameValidationResult(
                valid = false,
                reason = "Username must be 5–15 characters long and contain only letters, digits, or underscores"
            )
        }

        val normalized = trimmed
            .lowercase()
            .replace('0', 'o')
            .replace('1', 'l')

        val existingUser = userCachedRepository.findByUsername(normalized)
        if (existingUser != null && existingUser.userId != currentUserId) {
            return UsernameValidationResult(
                valid = false,
                reason = "Username is already taken"
            )
        }

        if (reservedUsernames.contains(normalized) ||
            reservedPrefixes.any { normalized.startsWith(it) } ||
            reservedSuffixes.any { normalized.endsWith(it) }
        ) {
            return UsernameValidationResult(
                valid = false,
                reason = "This username is reserved and cannot be used"
            )
        }

        return openAIService.validateUsername(normalized)
    }
}
