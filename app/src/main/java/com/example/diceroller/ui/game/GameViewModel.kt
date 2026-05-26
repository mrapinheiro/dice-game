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

class GameViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val difficultyName: String = savedStateHandle["difficulty"] ?: "MEDIUM"
    private val difficulty = Difficulty.valueOf(difficultyName)

    private val _uiState = MutableStateFlow(GameUiState(difficulty = difficulty))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

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

    fun roll() {
        val state = _uiState.value
        if (state.isRolling || state.gamePhase == GamePhase.GAME_OVER) return
        if (state.currentPlayer != Player.HUMAN) return

        val scope = animationScope ?: return

        val rolledValue = (1..20).random()
        _uiState.update { it.copy(isRolling = true, gamePhase = GamePhase.ROLLING) }

        scope.launch {
            onDiceRollSound?.invoke()
            diceAnimation.animateRoll(rolledValue)
            onDiceLandSound?.invoke()
            onHapticLand?.invoke()
            applyRollResult(rolledValue)
        }
    }

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

    private fun applyRollResult(rolledValue: Int) {
        val scope = animationScope ?: return

        if (rolledValue == 1) {
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

    private fun startCpuTurn() {
        val scope = animationScope ?: return
        cpuTurnJob?.cancel()
        cpuTurnJob = scope.launch {
            delay(1000)
            cpuTurnLoop()
        }
    }

    private suspend fun cpuTurnLoop() {
        while (true) {
            val state = _uiState.value
            if (state.gamePhase == GamePhase.GAME_OVER) return

            onCpuThinkingSound?.invoke()
            delay(1000 + (Math.random() * 1000).toLong())

            val rolledValue = (1..20).random()
            _uiState.update { it.copy(isRolling = true, gamePhase = GamePhase.ROLLING) }

            onDiceRollSound?.invoke()
            diceAnimation.animateRoll(rolledValue)
            onDiceLandSound?.invoke()
            onHapticLand?.invoke()

            if (rolledValue == 1) {
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

    fun resetGame() {
        cpuTurnJob?.cancel()
        cpuTurnJob = null
        _uiState.update {
            GameUiState(difficulty = difficulty)
        }
        diceAnimation.displayNumber = 0
    }

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
