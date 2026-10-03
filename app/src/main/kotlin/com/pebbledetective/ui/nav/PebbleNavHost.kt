package com.pebbledetective.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pebbledetective.R
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.screens.CreditsScreen
import com.pebbledetective.ui.screens.PlaceholderScreen

@Composable
fun PebbleNavHost(session: SessionViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CAPTURE) {
        composable(Routes.CAPTURE) {
            PlaceholderScreen(
                session = session,
                titleRes = R.string.screen_capture,
                bodyRes = R.string.capture_hint,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.RESEARCH) {
            PlaceholderScreen(session, R.string.screen_research, null, null, navController::popBackStack)
        }
        composable(Routes.RESULT) {
            PlaceholderScreen(session, R.string.screen_result, null, null, navController::popBackStack)
        }
        composable(Routes.JOURNEY) {
            PlaceholderScreen(session, R.string.screen_journey, null, null, navController::popBackStack)
        }
        composable(Routes.HISTORY) {
            PlaceholderScreen(
                session = session,
                titleRes = R.string.screen_history,
                bodyRes = R.string.history_empty,
                onOpenHistory = null,
                onBack = navController::popBackStack,
                onOpenCredits = { navController.navigate(Routes.CREDITS) },
            )
        }
        composable(Routes.CREDITS) {
            CreditsScreen(session, onBack = navController::popBackStack)
        }
    }
}
