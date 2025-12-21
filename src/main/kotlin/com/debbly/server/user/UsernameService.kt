package com.debbly.server.user

import com.debbly.server.ai.OpenAIService
import com.debbly.server.ai.UsernameValidationResult
import com.debbly.server.user.UserValidator.isValidUsername
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class UsernameService(
    private val userCachedRepository: UserCachedRepository,
    private val openAIService: OpenAIService
) {
    private val adjectives = listOf(
        "Bright", "Calm", "Careful", "Clean", "Clear", "Considerate", "Consistent", "Crisp", "Curious",
        "Detached", "Dry", "Easy", "Equal", "Even", "Fair", "Firm", "Focused", "Fresh", "Gentle",
        "Grounded", "Honest", "Insightful", "Kind", "Light", "Mild", "Mindful", "Natural", "Neat",
        "Neutral", "Objective", "Patient", "Plain", "Precise", "Quiet", "Raw", "Reasoned", "Relaxed",
        "Sharp", "Silent", "Simple", "Smooth", "Soft", "Stable", "Steady", "Still", "Sweet",
        "Thoughtful", "Unbiased", "Warm", "Whole"
    )

    private val fruitsVegetables = listOf(
        "Onion", "Corn", "Kiwi", "Apple", "Zucchini", "Lemon", "Cherry", "Avocado", "Banana",
        "Carrot", "Coconut", "Mushroom", "Olive", "Pear", "Kale", "Spinach", "Cucumber", "Blueberry",
        "Grape", "Mango", "Fig", "Beet", "Pea", "Tomato", "Celery", "Cabbage", "Garlic", "Mint",
        "Peach", "Apricot", "Lettuce", "Lentil", "Squash", "Snap-Pea", "Bean", "Potato", "Rice",
        "Plum", "Pepper", "Chickpea", "Radish", "Turnip", "Papaya", "Yam", "Pumpkin", "Parsnip",
        "Broccoli", "Grain"
    )

    // Reserved usernames that cannot be used
    private val reservedUsernames = setOf(
        // Administrative & System
        "admin", "administrator", "root", "system", "moderator", "mod", "mods", "support",
        "staff", "team", "official", "service", "bot", "api", "automated",

        // Platform/Technical
        "debbly", "app", "server", "client", "backend", "frontend", "web", "mobile",
        "debug", "test", "testing", "demo", "example", "sample",

        // Authentication/Security
        "login", "logout", "signin", "signout", "signup", "register", "auth",
        "authenticate", "password", "reset", "security", "verify", "verification",
        "confirmation", "token",

        // User Management
        "user", "users", "guest", "anonymous", "anon", "everyone", "nobody",
        "null", "undefined", "unknown", "deleted", "removed", "banned", "suspended",

        // Common Endpoints/Routes
        "about", "help", "contact", "privacy", "terms", "tos", "rules", "faq",
        "settings", "profile", "account", "dashboard", "home", "index",

        // Reserved Actions
        "create", "delete", "update", "edit", "remove", "add", "new", "post",
        "get", "put", "patch", "upload", "download"
    )

    fun generateUsername(): String {
        val adjective = adjectives.random().lowercase()
        val fruit = fruitsVegetables.random().lowercase()
        val base = "${adjective}_${fruit}"

        val maxAttempts = 100
        for (attempt in 0 until maxAttempts) {
            val number = Random.nextInt(10, 100)
            val username = "${base}_$number"

            if (userCachedRepository.findByUsername(username) == null) {
                return username
            }
        }

        return "${base}_${Random.nextInt(0, 999999)}"
    }

    fun validateUsername(username: String, currentUserId: String? = null): UsernameValidationResult {
        val trimmedUsername = username.trim()

        if (!isValidUsername(trimmedUsername)) {
            return UsernameValidationResult(
                valid = false,
                reason = "Invalid username format. Must be 6-18 characters, alphanumeric and underscores only"
            )
        }

        val existingUser = userCachedRepository.findByUsername(trimmedUsername)
        if (existingUser != null && existingUser.userId != currentUserId) {
            return UsernameValidationResult(
                valid = false,
                reason = "Username is already taken"
            )
        }

        if (reservedUsernames.contains(trimmedUsername.lowercase())) {
            return UsernameValidationResult(
                valid = false,
                reason = "This username is reserved and cannot be used"
            )
        }

        return openAIService.validateUsername(trimmedUsername)
    }
}
