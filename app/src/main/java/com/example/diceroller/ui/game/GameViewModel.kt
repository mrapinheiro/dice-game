package com.example.diceroller.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diceroller.model.CpuStrategy
import com.example.diceroller.model.Difficulty
import com.example.diceroller.model.GamePhase
import com.example.diceroller.model.GameUiState
import com.example.diceroller.model.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns all game state and turn flow for a single match of D20 Pig.
 *
 * The single source of truth is [uiState]; the UI observes it and renders. Rules of the
 * variant: each roll of a d20 adds to the current turn total, but rolling a 1 busts the
 * turn (the total is lost and play passes over). Holding banks the turn total; first to
 * 100 wins. The CPU plays itself autonomously via [cpuTurnLoop], using [CpuStrategy] to
 * decide when to hold.
 */
class GameViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    // Difficulty arrives as a navigation argument (string); default to MEDIUM if absent.
    private val difficultyName: String = savedStateHandle["difficulty"] ?: "MEDIUM"
    private val difficulty = Difficulty.valueOf(difficultyName)

    private val _uiState = MutableStateFlow(GameUiState(difficulty = difficulty))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Sound/haptic hooks wired up by the UI. Kept as nullable callbacks so the ViewModel
    // stays free of Android UI/audio dependencies and remains unit-testable.
    var onDiceRollSound: (() -> Unit)? = null
    var onDiceLandSound: (() -> Unit)? = null
    var onPointsBankedSound: (() -> Unit)? = null
    var onTurnLostSound: (() -> Unit)? = null
    var onWinSound: (() -> Unit)? = null
    var onLoseSound: (() -> Unit)? = null
    var onCpuThinkingSound: (() -> Unit)? = null
    var onHapticLand: (() -> Unit)? = null
    var onHapticWin: (() -> Unit)? = null

    val diceAnimation = DiceAnimationState()

    // The UI sets this to a Compose-aware coroutine scope that has MonotonicFrameClock
    var animationScope: CoroutineScope? = null

    // Track the CPU turn job so it can be cancelled on cleanup
    private var cpuTurnJob: Job? = null

    /** Human ROLL action. Ignored unless it's the human's turn and no roll is in flight. */
    fun roll() {
        val state = _uiState.value
        if (state.isRolling || state.gamePhase == GamePhase.GAME_OVER) return
        if (state.currentPlayer != Player.HUMAN) return

        // animationScope drives the Compose dice animation; without it there's nothing to roll into.
        val scope = animationScope ?: return

        val rolledValue = (1..20).random()
        _uiState.update { it.copy(isRolling = true, gamePhase = GamePhase.ROLLING) }

        // Play the throw animation first, then apply the result once the die has landed.
        scope.launch {
            onDiceRollSound?.invoke()
            diceAnimation.animateRoll(rolledValue)
            onDiceLandSound?.invoke()
            onHapticLand?.invoke()
            applyRollResult(rolledValue)
        }
    }

    /**
     * Human HOLD action: bank the current turn total into the player's score. Only valid
     * right after a successful roll (ROLLED). Banking to >= 100 wins; otherwise the turn
     * passes to the CPU.
     */
    fun hold() {
        val state = _uiState.value
        if (state.isRolling || state.currentPlayer != Player.HUMAN) return
        if (state.gamePhase != GamePhase.ROLLED) return

        val newScore = state.playerScore + state.currentTurnTotal
        onPointsBankedSound?.invoke()

        if (newScore >= 100) {
            _uiState.update {
                it.copy(
                    playerScore = newScore,
                    currentTurnTotal = 0,
                    gamePhase = GamePhase.GAME_OVER,
                    winner = Player.HUMAN
                )
            }
            onWinSound?.invoke()
            onHapticWin?.invoke()
        } else {
            _uiState.update {
                it.copy(
                    playerScore = newScore,
                    currentTurnTotal = 0,
                    currentPlayer = Player.CPU,
                    gamePhase = GamePhase.WAITING_TO_ROLL
                )
            }
            startCpuTurn()
        }
    }

    /** Resolve a landed human roll: a 1 busts the turn, anything else accrues to the turn total. */
    private fun applyRollResult(rolledValue: Int) {
        val scope = animationScope ?: return

        if (rolledValue == 1) {
            // Bust: lose the turn total, flash the die red, then hand off to the CPU.
            onTurnLostSound?.invoke()
            scope.launch {
                diceAnimation.flashRed()
            }
            _uiState.update {
                it.copy(
                    dieValue = 1,
                    currentTurnTotal = 0,
                    isRolling = false,
                    gamePhase = GamePhase.TURN_LOST
                )
            }
            // The bust pause + handover is just a timed delay (no frame-driven animation),
            // so it runs on viewModelScope rather than the Compose animationScope.
            viewModelScope.launch {
                delay(1500)
                _uiState.update {
                    it.copy(
                        currentPlayer = Player.CPU,
                        gamePhase = GamePhase.WAITING_TO_ROLL
                    )
                }
                startCpuTurn()
            }
        } else {
            _uiState.update {
                it.copy(
                    dieValue = rolledValue,
                    currentTurnTotal = it.currentTurnTotal + rolledValue,
                    isRolling = false,
                    gamePhase = GamePhase.ROLLED
                )
            }
        }
    }

    /** Kick off the CPU's autonomous turn after a short beat. Tracked so it can be cancelled. */
    private fun startCpuTurn() {
        val scope = animationScope ?: return
        cpuTurnJob?.cancel()
        cpuTurnJob = scope.launch {
            delay(1000)
            cpuTurnLoop()
        }
    }

    /**
     * Drives a full CPU turn to completion: roll, animate, then either bust (a 1) or decide
     * via [CpuStrategy] whether to keep rolling or bank. Returns once the turn ends — by bust,
     * by holding, or by reaching 100.
     */
    private suspend fun cpuTurnLoop() {
        while (true) {
            val state = _uiState.value
            if (state.gamePhase == GamePhase.GAME_OVER) return

            // "Thinking" pause with a little jitter so the CPU doesn't feel robotic.
            onCpuThinkingSound?.invoke()
            delay(1000 + (Math.random() * 1000).toLong())

            val rolledValue = (1..20).random()
            _uiState.update { it.copy(isRolling = true, gamePhase = GamePhase.ROLLING) }

            onDiceRollSound?.invoke()
            diceAnimation.animateRoll(rolledValue)
            onDiceLandSound?.invoke()
            onHapticLand?.invoke()

            if (rolledValue == 1) {
                // Bust: drop the turn total and hand control back to the human.
                onTurnLostSound?.invoke()
                diceAnimation.flashRed()
                _uiState.update {
                    it.copy(
                        dieValue = 1,
                        currentTurnTotal = 0,
                        isRolling = false,
                        gamePhase = GamePhase.TURN_LOST
                    )
                }
                delay(1500)
                _uiState.update {
                    it.copy(
                        currentPlayer = Player.HUMAN,
                        gamePhase = GamePhase.WAITING_TO_ROLL
                    )
                }
                return
            }

            val newTurnTotal = _uiState.value.currentTurnTotal + rolledValue
            _uiState.update {
                it.copy(
                    dieValue = rolledValue,
                    currentTurnTotal = newTurnTotal,
                    isRolling = false,
                    gamePhase = GamePhase.ROLLED
                )
            }

            // Ask the strategy whether to bank now; but always take a guaranteed win.
            val currentState = _uiState.value
            val shouldHold = CpuStrategy.shouldHold(
                difficulty = currentState.difficulty,
                turnTotal = newTurnTotal,
                cpuScore = currentState.cpuScore,
                playerScore = currentState.playerScore
            )

            val wouldWin = currentState.cpuScore + newTurnTotal >= 100

            if (shouldHold || wouldWin) {
                delay(800)
                val newScore = currentState.cpuScore + newTurnTotal
                onPointsBankedSound?.invoke()

                if (newScore >= 100) {
                    _uiState.update {
                        it.copy(
                            cpuScore = newScore,
                            currentTurnTotal = 0,
                            gamePhase = GamePhase.GAME_OVER,
                            winner = Player.CPU
                        )
                    }
                    onLoseSound?.invoke()
                    onHapticWin?.invoke()
                } else {
                    _uiState.update {
                        it.copy(
                            cpuScore = newScore,
                            currentTurnTotal = 0,
                            currentPlayer = Player.HUMAN,
                            gamePhase = GamePhase.WAITING_TO_ROLL
                        )
                    }
                }
                return
            }
        }
    }

    /** Start a fresh match. Cancels any in-flight CPU turn so it can't mutate the new state. */
    fun resetGame() {
        cpuTurnJob?.cancel()
        cpuTurnJob = null
        _uiState.update {
            GameUiState(difficulty = difficulty)
        }
        diceAnimation.displayNumber = 0
    }

    /** Drop UI callbacks and stop the CPU job — called when the screen leaves composition. */
    fun clearCallbacks() {
        cpuTurnJob?.cancel()
        cpuTurnJob = null
        onDiceRollSound = null
        onDiceLandSound = null
        onPointsBankedSound = null
        onTurnLostSound = null
        onWinSound = null
        onLoseSound = null
        onCpuThinkingSound = null
        onHapticLand = null
        onHapticWin = null
        animationScope = null
    }

    override fun onCleared() {
        super.onCleared()
        clearCallbacks()
    }
}
