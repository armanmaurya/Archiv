package com.example.pdfscanner.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.navArgument
import com.example.pdfscanner.ui.scanner.ScannerScreen
import com.example.pdfscanner.ui.scanner.ScannerViewModel
import com.example.pdfscanner.ui.scanner.EditorScreen

@Composable
inline fun <reified T : ViewModel> NavBackStackEntry.sharedViewModel(
    navController: NavController
): T {
    val navGraphRoute = destination.parent?.route ?: return viewModel()
    val parentEntry = remember(this) {
        navController.getBackStackEntry(navGraphRoute)
    }
    return viewModel(parentEntry)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(
) {
    val navController = rememberNavController()
    var scrollToIndexHint by remember { mutableStateOf<Int?>(null) }

    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = "scanner_flow") {

            navigation(
                startDestination = Screen.CAMERA,
                route = "scanner_flow"
            ) {
                
                composable(
                    route = Screen.CAMERA,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) { backStackEntry ->
                    
                    val viewModel: ScannerViewModel = backStackEntry.sharedViewModel(navController)

                    ScannerScreen(
                        viewModel = viewModel,
                        onOpenEditor = { startIndex ->
                            navController.navigate(Screen.editorRoute(startIndex))
                        },
                        scrollToIndexHint = scrollToIndexHint,
                        onScrollHintConsumed = { scrollToIndexHint = null },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        sharedElementKeyForUri = { uri -> "page-$uri" }
                    )
                }

                composable(
                    route = Screen.EDITOR_ROUTE,
                    arguments = listOf(
                        navArgument(Screen.EDITOR_START_ARG) {
                            type = NavType.IntType
                            defaultValue = 0
                        }
                    ),
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() }
                ) { backStackEntry ->
                    
                    val viewModel: ScannerViewModel = backStackEntry.sharedViewModel(navController)
                    
                    val startIndex = backStackEntry.arguments?.getInt(Screen.EDITOR_START_ARG) ?: 0
                    
                    EditorScreen(
                        viewModel = viewModel,
                        initialPage = startIndex,
                        onBack = { navController.popBackStack() },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        sharedElementKeyForUri = { uri -> "page-$uri" }
                    )
                }
            }
        }
    }
}
