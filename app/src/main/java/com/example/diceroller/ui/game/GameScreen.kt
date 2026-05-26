package com.example.diceroller.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diceroller.R
import com.example.diceroller.audio.SoundManager
import com.example.diceroller.sensor.ShakeDetector
import com.example.diceroller.model.GamePhase
import com.example.diceroller.model.Player
import com.example.diceroller.ui.theme.CasinoBlack
import com.example.diceroller.ui.theme.DeepRed
import com.example.diceroller.ui.theme.FeltGreen
import com.example.diceroller.ui.theme.FeltGreenDark
import com.example.diceroller.ui.theme.Gold
import com.example.diceroller.ui.theme.GoldLight

@Composable
fun GameScreen(
    difficultyName: String,
    soundManager: SoundManager,
    onNavigateToMenu: () -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showExitDialog by remember { mutableStateOf(false) }
    val composeScope = rememberCoroutineScope()

    // Wire up sound/haptic callbacks; clean up when leaving
    DisposableEffect(viewModel) {
        soundManager.startGameMusic()
        viewModel.animationScope = composeScope
        viewModel.onDiceRollSound = { soundManager.playDiceRoll() }
        viewModel.onDiceLandSound = { soundManager.playDiceLand() }
        viewModel.onPointsBankedSound = { soundManager.playPointsBanked() }
        viewModel.onTurnLostSound = { soundManager.playTurnLost() }
        viewModel.onWinSound = { soundManager.playWinFanfare() }
        viewModel.onLoseSound = { soundManager.playLoseSound() }
        viewModel.onCpuThinkingSound = { soundManager.playCpuThinking() }
        viewModel.onHapticLand = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        viewModel.onHapticWin = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onDispose {
            soundManager.stopGameMusic()
            viewModel.clearCallbacks()
        }
    }

    // Shake-to-roll: equivalent to tapping the ROLL button when it's enabled.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestUiState = rememberUpdatedState(uiState)
    DisposableEffect(lifecycleOwner, viewModel) {
        val detector = ShakeDetector(context) {
            val s = latestUiState.value
            val enabled = s.currentPlayer == Player.HUMAN
                && !s.isRolling
                && (s.gamePhase == GamePhase.WAITING_TO_ROLL || s.gamePhase == GamePhase.ROLLED)
            if (!enabled) return@ShakeDetector
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            soundManager.playButtonClick()
            viewModel.roll()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> detector.start()
                Lifecycle.Event.ON_PAUSE -> detector.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            detector.stop()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Back button handler
    BackHandler {
        if (uiState.gamePhase != GamePhase.GAME_OVER) {
            showExitDialog = true
        } else {
            onNavigateToMenu()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CasinoBlack)
                .systemBarsPadding()
        ) {
            // Menu button
            IconButton(
                onClick = {
                    if (uiState.gamePhase != GamePhase.GAME_OVER) {
                        showExitDialog = true
                    } else {
                        soundManager.stopGameMusic()
                        onNavigateToMenu()
                    }
                },
                modifier = Modifier
                    .padding(start = 12.dp, top = 4.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.main_menu),
                    tint = GoldLight.copy(alpha = 0.7f)
                )
            }

            // Scoreboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CasinoBlack)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player score
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.player_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.currentPlayer == Player.HUMAN) Gold else GoldLight.copy(alpha = 0.5f)
                    )
                    Text(
                        text = uiState.playerScore.toString(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.currentPlayer == Player.HUMAN) Gold else GoldLight.copy(alpha = 0.5f)
                    )
                }

                // Target
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.target_score),
                        style = MaterialTheme.typography.labelLarge,
                        color = GoldLight.copy(alpha = 0.4f)
                    )
                }

                // CPU score
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.cpu_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.currentPlayer == Player.CPU) DeepRed else GoldLight.copy(alpha = 0.5f)
                    )
                    Text(
                        text = uiState.cpuScore.toString(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.currentPlayer == Player.CPU) DeepRed else GoldLight.copy(alpha = 0.5f)
                    )
                }
            }

            // Felt table area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(FeltGreen)
                    .border(4.dp, FeltGreenDark, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Turn indicator
                    Text(
                        text = if (uiState.currentPlayer == Player.HUMAN) stringResource(R.string.player_label)
                        else if (uiState.isRolling) stringResource(R.string.cpu_label)
                        else stringResource(R.string.cpu_thinking),
                        style = MaterialTheme.typography.titleLarge,
                        color = Gold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // D20 die
                    D20Canvas(
                        highlightNumber = if (viewModel.diceAnimation.isLanded) viewModel.diceAnimation.displayNumber else 0,
                        rotationAngle = viewModel.diceAnimation.rotation.value,
                        scale = viewModel.diceAnimation.scale.value,
                        yOffset = viewModel.diceAnimation.yOffset.value,
                        shadowBlur = viewModel.diceAnimation.shadowBlur.value,
                        rotationX = viewModel.diceAnimation.rotationX.value,
                        rotationY = viewModel.diceAnimation.rotationY.value
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Turn total
                    Text(
                        text = stringResource(R.string.turn_total),
                        style = MaterialTheme.typography.labelLarge,
                        color = GoldLight.copy(alpha = 0.7f)
                    )
                    Text(
                        text = uiState.currentTurnTotal.toString(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gold
                    )

                    // Busted indicator
                    if (uiState.gamePhase == GamePhase.TURN_LOST) {
                        Text(
                            text = stringResource(R.string.busted),
                            style = MaterialTheme.typography.headlineLarge,
                            color = DeepRed,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val buttonsEnabled = uiState.currentPlayer == Player.HUMAN
                    && !uiState.isRolling
                    && uiState.gamePhase != GamePhase.GAME_OVER
                    && uiState.gamePhase != GamePhase.TURN_LOST

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        soundManager.playButtonClick()
                        viewModel.roll()
                    },
                    enabled = buttonsEnabled && (uiState.gamePhase == GamePhase.WAITING_TO_ROLL || uiState.gamePhase == GamePhase.ROLLED),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = CasinoBlack,
                        disabledContainerColor = Gold.copy(alpha = 0.3f),
                        disabledContentColor = CasinoBlack.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.roll),
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        soundManager.playButtonClick()
                        viewModel.hold()
                    },
                    enabled = buttonsEnabled && uiState.gamePhase == GamePhase.ROLLED,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Gold,
                        disabledContentColor = Gold.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hold),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        // Game over overlay
        if (uiState.gamePhase == GamePhase.GAME_OVER && uiState.winner != null) {
            GameOverOverlay(
                winner = uiState.winner!!,
                playerScore = uiState.playerScore,
                cpuScore = uiState.cpuScore,
                onPlayAgain = {
                    soundManager.playButtonClick()
                    viewModel.resetGame()
                },
                onMainMenu = {
                    soundManager.playButtonClick()
                    soundManager.stopGameMusic()
                    onNavigateToMenu()
                }
            )
        }

        // Exit confirmation dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.leave_game_title),
                        color = Gold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.leave_game_message),
                        color = GoldLight
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        soundManager.stopGameMusic()
                        onNavigateToMenu()
                    }) {
                        Text(stringResource(R.string.leave), color = DeepRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(stringResource(R.string.stay), color = Gold)
                    }
                },
                containerColor = CasinoBlack
            )
        }
    }
}
