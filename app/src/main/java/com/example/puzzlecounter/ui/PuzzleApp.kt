package com.example.puzzlecounter.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

enum class PuzzleScreen {
    Splash, Start, Selection, Instruction, PhotoFrame, Capture, Result, Debug, Settings, SettingsBackground, SettingsInfo
}

/** Opens [screen]; tapping a button twice in a row does not open it twice. */
private fun NavController.open(screen: PuzzleScreen) = navigate(screen.name) { launchSingleTop = true }

@Composable
fun PuzzleApp(viewModel: PuzzleViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    // By the time the first photo is taken the model is loaded.
    LaunchedEffect(viewModel) { viewModel.preloadModel() }

    NavHost(
        navController = navController,
        startDestination = PuzzleScreen.Splash.name,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
        }
    ) {
        composable(PuzzleScreen.Splash.name) {
            SplashScreen(
                uiState = uiState,
                onTimeout = {
                    navController.navigate(PuzzleScreen.Start.name) {
                        popUpTo(PuzzleScreen.Splash.name) { inclusive = true }
                    }
                }
            )
        }
        composable(PuzzleScreen.Start.name) {
            StartScreen(
                uiState = uiState,
                onStartClicked = { navController.open(PuzzleScreen.Selection) },
                onDebugClicked = { navController.open(PuzzleScreen.Debug) },
                onSettingsClicked = { navController.open(PuzzleScreen.Settings) }
            )
        }
        composable(PuzzleScreen.Selection.name) {
            SelectionScreen(
                uiState = uiState,
                onNextClicked = { count ->
                    viewModel.setTargetPieceCount(count)
                    navController.open(PuzzleScreen.Instruction)
                }
            )
        }
        composable(PuzzleScreen.Instruction.name) {
            InstructionScreen(
                uiState = uiState,
                onStartScan = { navController.open(PuzzleScreen.Capture) },
                onPickPhoto = { uri ->
                    viewModel.loadPickedPhoto(uri)
                    navController.open(PuzzleScreen.PhotoFrame)
                }
            )
        }
        composable(PuzzleScreen.PhotoFrame.name) {
            PhotoFrameScreen(
                uiState = uiState,
                onConfirm = { region ->
                    viewModel.onPickedPhotoFramed(region)
                    // The analysis of the photo is shown by the capture screen, which may already be below this one.
                    navController.navigate(PuzzleScreen.Capture.name) {
                        popUpTo(PuzzleScreen.PhotoFrame.name) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
                onDiscard = { viewModel.discardPickedPhoto() }
            )
        }
        composable(PuzzleScreen.Capture.name) {
            CaptureScreen(
                uiState = uiState,
                onPhotoCaptured = { bitmap -> viewModel.onPhotoCaptured(bitmap) },
                onPickPhoto = { uri ->
                    viewModel.loadPickedPhoto(uri)
                    navController.open(PuzzleScreen.PhotoFrame)
                },
                onRetake = { viewModel.retakePhoto() },
                onConfirm = {
                    viewModel.confirmPhoto()
                    val updatedState = viewModel.uiState.value
                    if (updatedState.currentPileIndex > updatedState.calculatedPiles) {
                        // Back from the result leads to the start screen, not into the finished check.
                        navController.navigate(PuzzleScreen.Result.name) {
                            popUpTo(PuzzleScreen.Start.name)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(PuzzleScreen.Result.name) {
            ResultScreen(
                uiState = uiState,
                onRestart = {
                    viewModel.reset()
                    navController.navigate(PuzzleScreen.Start.name) {
                        popUpTo(PuzzleScreen.Start.name) { inclusive = true }
                    }
                }
            )
        }
        composable(PuzzleScreen.Debug.name) {
            DeveloperDebugScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(PuzzleScreen.Settings.name) {
            SettingsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onNavigateToBackground = { navController.open(PuzzleScreen.SettingsBackground) },
                onNavigateToInfo = { navController.open(PuzzleScreen.SettingsInfo) }
            )
        }
        composable(PuzzleScreen.SettingsBackground.name) {
            BackgroundSettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(PuzzleScreen.SettingsInfo.name) {
            InfoScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
