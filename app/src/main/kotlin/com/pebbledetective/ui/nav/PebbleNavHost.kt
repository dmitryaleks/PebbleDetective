package com.pebbledetective.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pebbledetective.R
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.capture.CaptureScreen
import com.pebbledetective.ui.journey.JourneyScreen
import com.pebbledetective.ui.research.ResearchScreen
import com.pebbledetective.ui.result.ResultScreen
import com.pebbledetective.ui.screens.CreditsScreen
import com.pebbledetective.ui.screens.PlaceholderScreen

@Composable
fun PebbleNavHost(session: SessionViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CAPTURE) {
        composable(Routes.CAPTURE) {
            CaptureScreen(
                session = session,
                onCaptured = { bitmap, tap ->
                    session.onCaptured(bitmap, tap)
                    navController.navigate(Routes.RESEARCH)
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.RESEARCH) {
            ResearchScreen(
                session = session,
                onFinished = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.CAPTURE)
                    }
                },
                onDeclined = {
                    session.discardCapture()
                    navController.popBackStack(Routes.CAPTURE, inclusive = false)
                },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                session = session,
                onJourney = { navController.navigate(Routes.JOURNEY) },
                onNewPebble = {
                    session.discardCapture()
                    navController.popBackStack(Routes.CAPTURE, inclusive = false)
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.JOURNEY) {
            JourneyScreen(
                session = session,
                onFinished = {
                    session.resetJourney()
                    navController.popBackStack(Routes.RESULT, inclusive = false)
                },
            )
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
