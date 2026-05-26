package com.example.diceroller.model

enum class Player { HUMAN, CPU }

enum class GamePhase {
    WAITING_TO_ROLL,
    ROLLING,
    ROLLED,
    TURN_LOST,
    GAME_OVER
}

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD
}

data class GameUiState(
    val playerScore: Int = 0,
    val cpuScore: Int = 0,
    val currentTurnTotal: Int = 0,
    val currentPlayer: Player = Player.HUMAN,
    val dieValue: Int = 1,
    val isRolling: Boolean = false,
    val gamePhase: GamePhase = GamePhase.WAITING_TO_ROLL,
    val winner: Player? = null,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val soundEnabled: Boolean = true
)
