package com.example.diceroller.model

object CpuStrategy {

    fun shouldHold(
        difficulty: Difficulty,
        turnTotal: Int,
        cpuScore: Int,
        playerScore: Int
    ): Boolean {
        val threshold = when (difficulty) {
            Difficulty.EASY -> 10
            Difficulty.MEDIUM -> 20
            Difficulty.HARD -> {
                val scoreDiff = cpuScore - playerScore
                (20 + scoreDiff / 5).coerceIn(12, 30)
            }
        }
        return turnTotal >= threshold
    }
}
