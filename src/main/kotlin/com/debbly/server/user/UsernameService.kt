package com.debbly.server.user

import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class UsernameService(
    private val userCachedRepository: UserCachedRepository
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
}
