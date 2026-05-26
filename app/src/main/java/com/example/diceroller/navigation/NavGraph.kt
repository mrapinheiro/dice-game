package com.example.diceroller.navigation

sealed class Screen(val route: String) {
    data object Menu : Screen("menu")
    data object Rules : Screen("rules")
    data object Game : Screen("game/{difficulty}") {
        fun createRoute(difficulty: String) = "game/$difficulty"
    }
}
