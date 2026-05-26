package com.example.diceroller.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.diceroller.R
import com.example.diceroller.ui.theme.CasinoBlack
import com.example.diceroller.ui.theme.FeltGreen
import com.example.diceroller.ui.theme.Gold
import com.example.diceroller.ui.theme.GoldLight

@Composable
fun RulesScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoBlack)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.rules_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Gold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.rules_body),
            style = MaterialTheme.typography.bodyLarge,
            color = GoldLight,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FeltGreen,
                contentColor = Gold
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.back),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
