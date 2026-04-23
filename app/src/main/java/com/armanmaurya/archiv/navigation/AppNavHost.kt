package com.armanmaurya.archiv.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import com.armanmaurya.archiv.ui.scanner.ScannerScreen
import com.armanmaurya.archiv.ui.scanner.ScannerViewModel
import com.armanmaurya.archiv.ui.scanner.EditorScreen
import com.armanmaurya.archiv.ui.document.DocumentListScreen
import com.armanmaurya.archiv.ui.document.DocumentViewModel
import com.armanmaurya.archiv.ui.settings.AboutScreen
import com.armanmaurya.archiv.ui.settings.SettingsScreen
import com.armanmaurya.archiv.ui.settings.SettingsViewModel

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
    val context = LocalContext.current
    var scrollToIndexHint by remember { mutableStateOf<Int?>(null) }

    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = Screen.DOCUMENTS) {

            composable(
                route = Screen.DOCUMENTS,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                val documentViewModel: DocumentViewModel = viewModel(
                    factory = DocumentViewModel.factory(context)
                )

                DocumentListScreen(
                    viewModel = documentViewModel,
                    onOpenScanner = {
                        navController.navigate(Screen.SCANNER_FLOW) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.SETTINGS) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            navigation(
                startDestination = Screen.CAMERA,
                route = Screen.SCANNER_FLOW
            ) {
                composable(
                    route = Screen.CAMERA,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                ) { backStackEntry ->

                    val viewModel: ScannerViewModel = backStackEntry.sharedViewModel(navController)

                    ScannerScreen(
                        viewModel = viewModel,
                        onOpenEditor = { startIndex ->
                            navController.navigate(Screen.editorRoute(startIndex))
                        },
                        onExitScanner = { navController.popBackStack() },
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
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                ) { backStackEntry ->

                    val viewModel: ScannerViewModel = backStackEntry.sharedViewModel(navController)

                    val startIndex = backStackEntry.arguments?.getInt(Screen.EDITOR_START_ARG) ?: 0

                    EditorScreen(
                        viewModel = viewModel,
                        initialPage = startIndex,
                        onBack = { navController.popBackStack() },
                        onOpenDocumentList = {
                            navController.navigate(Screen.DOCUMENTS) {
                                popUpTo(Screen.DOCUMENTS) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        sharedElementKeyForUri = { uri -> "page-$uri" }
                    )
                }
            }

            composable(
                route = Screen.SETTINGS,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(context)
                )

                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAboutClick = {
                        navController.navigate(Screen.ABOUT)
                    }
                )
            }

            composable(
                route = Screen.ABOUT,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                AboutScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
