package com.pebbledetective.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.capture.CaptureScreen
import com.pebbledetective.ui.history.HistoryDetailScreen
import com.pebbledetective.ui.history.HistoryScreen
import com.pebbledetective.ui.journey.JourneyScreen
import com.pebbledetective.ui.radar.RadarScreen
import com.pebbledetective.ui.research.ResearchScreen
import com.pebbledetective.ui.result.ResultScreen
import com.pebbledetective.ui.screens.CreditsScreen
import com.pebbledetective.ui.sky.SkyScreen
import com.pebbledetective.ui.splash.SplashScreen

@Composable
fun PebbleNavHost(session: SessionViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                session = session,
                // Popped inclusively, so back from the camera leaves the app
                // rather than replaying the titles.
                onFinished = {
                    navController.navigate(Routes.CAPTURE) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureScreen(
                session = session,
                onCaptured = { bitmap, tap ->
                    session.onCaptured(bitmap, tap)
                    navController.navigate(Routes.RESEARCH)
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onRadar = { navController.navigate(Routes.RADAR) },
                onSky = { navController.navigate(Routes.SKY) },
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
                // Plain pop, not a pop back to RESULT: the journey is also
                // reachable from a logbook entry, and that stack has no
                // RESULT on it, so popping to it did nothing at all and the
                // Done button appeared dead.
                onFinished = {
                    session.resetJourney()
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                session = session,
                onOpen = { id -> navController.navigate(Routes.historyDetail(id)) },
                onBack = navController::popBackStack,
                onOpenCredits = { navController.navigate(Routes.CREDITS) },
            )
        }
        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            HistoryDetailScreen(
                session = session,
                entryId = backStackEntry.arguments?.getString("id").orEmpty(),
                onBack = navController::popBackStack,
                onReplay = { navController.navigate(Routes.JOURNEY) },
            )
        }
        composable(Routes.RADAR) {
            RadarScreen(
                session = session,
                onSwitchToDetection = {
                    session.stopRadar()
                    navController.popBackStack(Routes.CAPTURE, inclusive = false)
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.SKY) {
            SkyScreen(
                session = session,
                onSwitchToDetection = {
                    session.stopSky()
                    navController.popBackStack(Routes.CAPTURE, inclusive = false)
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.CREDITS) {
            CreditsScreen(session, onBack = navController::popBackStack)
        }
    }
}
