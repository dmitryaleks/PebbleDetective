package com.pebbledetective.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pebbledetective.domain.Planet
import com.pebbledetective.ui.SessionViewModel
import com.pebbledetective.ui.capture.CaptureScreen
import com.pebbledetective.ui.history.HistoryDetailScreen
import com.pebbledetective.ui.history.HistoryScreen
import com.pebbledetective.ui.journey.JourneyScreen
import com.pebbledetective.ui.planetarium.PlanetariumScreen
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
                    navController.navigate(Routes.SKY) {
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
                onPlanetarium = { planet ->
                    navController.navigate(Routes.planetarium(planet))
                },
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
                onPlanetarium = { planet ->
                    navController.navigate(Routes.planetarium(planet))
                },
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
                onPlanetarium = { planet ->
                    navController.navigate(Routes.planetarium(planet))
                },
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
                onPlanetarium = { planet ->
                    navController.navigate(Routes.planetarium(planet))
                },
            )
        }
        composable(Routes.RADAR) {
            RadarScreen(
                session = session,
                // Detection is the end of the chain but no longer its root,
                // so it may or may not be on the stack already. Popping back
                // to a destination that is not there silently does nothing,
                // which is how the Done button was dead for so long.
                onSwitchToDetection = {
                    session.stopRadar()
                    navController.toDetection()
                },
                onSky = { navController.navigate(Routes.SKY) { launchSingleTop = true } },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.SKY) {
            SkyScreen(
                session = session,
                onDetection = {
                    session.stopSky()
                    navController.toDetection()
                },
                // The hunt runs sky, then radar, then camera. Pushed rather
                // than swapped, so going back retraces the way you came.
                onRadar = {
                    session.stopSky()
                    navController.navigate(Routes.RADAR) { launchSingleTop = true }
                },
                onPlanetarium = {
                    session.stopSky()
                    navController.navigate(Routes.planetarium())
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(
            route = Routes.PLANETARIUM,
            arguments = listOf(
                navArgument("planet") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
        ) { backStackEntry ->
            PlanetariumScreen(
                session = session,
                initialFocus = Planet.fromId(backStackEntry.arguments?.getString("planet")),
                onBack = navController::popBackStack,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onSky = { navController.navigate(Routes.SKY) { launchSingleTop = true } },
                onRadar = { navController.navigate(Routes.RADAR) { launchSingleTop = true } },
                onDetection = { navController.toDetection() },
            )
        }
        composable(Routes.CREDITS) {
            CreditsScreen(session, onBack = navController::popBackStack)
        }
    }
}

/**
 * Goes to the camera, reusing the one already behind you if there is one.
 *
 * The three camera modes can be entered in any order, so Detection is
 * sometimes further down the stack and sometimes not on it at all. Popping
 * alone would do nothing in the second case; navigating alone would stack a
 * second copy in the first.
 */
private fun NavHostController.toDetection() {
    if (!popBackStack(Routes.CAPTURE, inclusive = false)) {
        navigate(Routes.CAPTURE) { launchSingleTop = true }
    }
}
