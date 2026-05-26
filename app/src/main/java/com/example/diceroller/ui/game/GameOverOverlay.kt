package com.example.diceroller.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.diceroller.R
import com.example.diceroller.model.Player
import com.example.diceroller.ui.theme.CasinoBlack
import com.example.diceroller.ui.theme.DeepRed
import com.example.diceroller.ui.theme.FeltGreen
import com.example.diceroller.ui.theme.Gold
import com.example.diceroller.ui.theme.GoldLight

@Composable
fun GameOverOverlay(
    winner: Player,
    playerScore: Int,
    cpuScore: Int,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit
) {
    val isPlayerWin = winner == Player.HUMAN

    AnimatedVisibility(
        visible = true,
        enter = fadeIn()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isPlayerWin) Gold.copy(alpha = 0.9f)
                    else CasinoBlack.copy(alpha = 0.92f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = if (isPlayerWin) stringResource(R.string.you_win)
                    else stringResource(R.string.cpu_wins),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isPlayerWin) CasinoBlack else DeepRed,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.final_score, playerScore, cpuScore),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isPlayerWin) CasinoBlack.copy(alpha = 0.7f) else GoldLight,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onPlayAgain,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayerWin) FeltGreen else Gold,
                            contentColor = if (isPlayerWin) Gold else CasinoBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.play_again),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedButton(
                        onClick = onMainMenu,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPlayerWin) CasinoBlack else GoldLight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.main_menu),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}
