package com.example.diceroller.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.diceroller.R
import com.example.diceroller.audio.SoundManager
import com.example.diceroller.model.Difficulty
import com.example.diceroller.ui.theme.Gold
import com.example.diceroller.ui.theme.GoldLight
import com.example.diceroller.ui.theme.FeltGreen
import com.example.diceroller.ui.theme.CasinoBlack

@Composable
fun MenuScreen(
    soundManager: SoundManager,
    onPlayClick: (Difficulty) -> Unit,
    onRulesClick: () -> Unit
) {
    var selectedDifficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }
    var soundEnabled by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        soundManager.startMenuMusic()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoBlack)
            .systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Title
        Text(
            text = stringResource(R.string.menu_title),
            style = MaterialTheme.typography.displayLarge,
            color = Gold,
            textAlign = TextAlign.Center
        )

        // Buttons group
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Play button
            Button(
                onClick = { onPlayClick(selectedDifficulty) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FeltGreen,
                    contentColor = Gold
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.play_vs_cpu),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rules button
            OutlinedButton(
                onClick = onRulesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.how_to_play),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Difficulty selector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Difficulty",
                style = MaterialTheme.typography.labelLarge,
                color = GoldLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = selectedDifficulty == difficulty,
                        onClick = {
                            selectedDifficulty = difficulty
                            soundManager.playButtonClick()
                        },
                        label = {
                            Text(
                                text = when (difficulty) {
                                    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
                                    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
                                    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Gold,
                            selectedLabelColor = CasinoBlack,
                            containerColor = CasinoBlack,
                            labelColor = GoldLight
                        )
                    )
                }
            }
        }

        // Sound toggle + Credits
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = {
                    soundEnabled = !soundEnabled
                    soundManager.enabled = soundEnabled
                    if (soundEnabled) soundManager.startMenuMusic()
                    else soundManager.pauseAll()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (soundEnabled) stringResource(R.string.sound_on) else stringResource(R.string.sound_off),
                    tint = Gold,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.credits),
                style = MaterialTheme.typography.bodyMedium,
                color = GoldLight.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
