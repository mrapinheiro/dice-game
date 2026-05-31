package com.example.diceroller.model

/**
 * Decision logic for the CPU opponent.
 *
 * In this D20 Pig variant a turn accumulates points roll by roll, but rolling a 1
 * wipes the turn total. The only real choice is *when to stop rolling and bank* — so
 * the whole CPU "AI" reduces to a hold threshold: keep rolling until the running
 * turn total reaches the threshold, then hold.
 */
object CpuStrategy {

    /**
     * Decide whether the CPU should bank now rather than roll again.
     *
     * A higher threshold means a greedier CPU: more points per turn, but more bust risk.
     *
     * - [Difficulty.EASY]   holds at 10 — banks early, scores slowly, easy to outpace.
     * - [Difficulty.MEDIUM] holds at 20 — the classic near-optimal flat Pig threshold.
     * - [Difficulty.HARD]   adapts to the score gap: when ahead it raises the threshold
     *   (presses the lead to close the game out faster); when behind it lowers it
     *   (banks sooner for steady, low-risk points).
     *
     * @return true when the CPU should hold its current [turnTotal].
     */
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
                // scoreDiff > 0 when the CPU is ahead. Shift the threshold by 1 for every
                // 5 points of lead/deficit, clamped so the CPU never plays absurdly timid
                // (< 12) nor recklessly greedy (> 30).
                val scoreDiff = cpuScore - playerScore
                (20 + scoreDiff / 5).coerceIn(12, 30)
            }
        }
        return turnTotal >= threshold
    }
}
