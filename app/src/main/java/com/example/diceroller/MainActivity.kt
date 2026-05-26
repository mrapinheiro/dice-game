package com.example.diceroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.diceroller.audio.SoundManager
import com.example.diceroller.navigation.Screen
import com.example.diceroller.ui.game.GameScreen
import com.example.diceroller.ui.menu.MenuScreen
import com.example.diceroller.ui.rules.RulesScreen
import com.example.diceroller.ui.theme.DiceRollerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiceRollerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PadmUalApp()
                }
            }
        }
    }
}

@Composable
fun PadmUalApp() {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context).also { it.load() } }
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> soundManager.pauseAll()
                Lifecycle.Event.ON_RESUME -> {
                    val currentRoute = navController.currentDestination?.route
                    soundManager.resumeMusic(isInGame = currentRoute?.startsWith("game") == true)
                }
                Lifecycle.Event.ON_DESTROY -> soundManager.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            soundManager.release()
        }
    }

    NavHost(navController = navController, startDestination = Screen.Menu.route) {
        composable(Screen.Menu.route) {
            MenuScreen(
                soundManager = soundManager,
                onPlayClick = { difficulty ->
                    soundManager.playButtonClick()
                    navController.navigate(Screen.Game.createRoute(difficulty.name))
                },
                onRulesClick = {
                    soundManager.playButtonClick()
                    navController.navigate(Screen.Rules.route)
                }
            )
        }
        composable(Screen.Rules.route) {
            RulesScreen(
                onBackClick = {
                    soundManager.playButtonClick()
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Screen.Game.route,
            arguments = listOf(navArgument("difficulty") { type = NavType.StringType })
        ) { backStackEntry ->
            val difficultyName = backStackEntry.arguments?.getString("difficulty") ?: "MEDIUM"
            GameScreen(
                difficultyName = difficultyName,
                soundManager = soundManager,
                onNavigateToMenu = {
                    navController.popBackStack(Screen.Menu.route, inclusive = false)
                }
            )
        }
    }
}
