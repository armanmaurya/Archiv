package com.armanmaurya.archiv.navigation

import android.net.Uri
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
import com.armanmaurya.archiv.SharedIntent
import com.armanmaurya.archiv.ui.scanner.ScannerScreen
import com.armanmaurya.archiv.ui.scanner.ScannerViewModel
import com.armanmaurya.archiv.ui.scanner.EditorScreen
import com.armanmaurya.archiv.ui.document.DocumentsScreen
import com.armanmaurya.archiv.ui.document.DocumentViewModel
import com.armanmaurya.archiv.ui.settings.AboutScreen
import com.armanmaurya.archiv.ui.settings.SettingsScreen
import com.armanmaurya.archiv.ui.settings.SettingsViewModel
import com.armanmaurya.archiv.ui.viewer.PdfViewerScreen

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
fun ArchivNavHost(
    sharedIntent: SharedIntent,
    onSharedIntentProcessed: () -> Unit,
    onExit: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var scrollToIndexHint by remember { mutableStateOf<Int?>(null) }
    var sharedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

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

                DocumentsScreen(
                    viewModel = documentViewModel,
                    sharedIntent = sharedIntent,
                    onSharedIntentProcessed = onSharedIntentProcessed,
                    onOpenScanner = { initialImages ->
                        if (initialImages.isNotEmpty()) {
                            sharedImages = initialImages
                        }
                        navController.navigate(Screen.SCANNER_FLOW) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenDocument = { documentId ->
                        navController.navigate(Screen.pdfViewerRoute(documentId)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenExternalDocument = { uri ->
                        navController.navigate(Screen.pdfExternalViewerRoute(uri)) {
                            popUpTo(Screen.DOCUMENTS) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this
                )
            }

            navigation(
                startDestination = Screen.CAMERA,
                route = Screen.SCANNER_FLOW
            ) {
                composable(
                    route = Screen.CAMERA,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                    exitTransition = {
                        val targetRoute = targetState?.destination?.route
                        if (targetRoute == Screen.EDITOR_ROUTE) {
                            null
                        } else {
                            slideOutHorizontally(targetOffsetX = { -it })
                        }
                    },
                    popEnterTransition = {
                        val fromRoute = initialState?.destination?.route
                        if (fromRoute == Screen.EDITOR_ROUTE) {
                            null
                        } else {
                            slideInHorizontally(initialOffsetX = { -it })
                        }
                    },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
                ) { backStackEntry ->

                    val viewModel: ScannerViewModel = backStackEntry.sharedViewModel(navController)

                    ScannerScreen(
                        viewModel = viewModel,
                        sharedImages = sharedImages,
                        onSharedImagesProcessed = { sharedImages = emptyList() },
                        onOpenEditor = { startIndex ->
                            navController.navigate(Screen.editorRoute(startIndex))
                        },
                        onExitScanner = { navController.popBackStack() },
                        onOpenDocumentList = {
                            navController.navigate(Screen.DOCUMENTS) {
                                popUpTo(Screen.DOCUMENTS) { inclusive = false }
                                launchSingleTop = true
                            }
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
                    )
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

            composable(
                route = Screen.PDF_VIEWER_ROUTE,
                arguments = listOf(
                    navArgument(Screen.PDF_VIEWER_DOCUMENT_ID) {
                        type = NavType.StringType
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) { backStackEntry ->
                val documentViewModel: DocumentViewModel = viewModel(
                    factory = DocumentViewModel.factory(context)
                )
                val rawDocumentId = backStackEntry.arguments
                    ?.getString(Screen.PDF_VIEWER_DOCUMENT_ID)
                val documentId = rawDocumentId?.let { Uri.decode(it) } ?: return@composable

                val uri = documentViewModel.getDocumentFileUri(documentId) ?: return@composable
                documentViewModel.onDocumentOpened(documentId)

                PdfViewerScreen(
                    uri = uri,
                    title = documentId,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.PDF_EXTERNAL_VIEWER_ROUTE,
                arguments = listOf(
                    navArgument(Screen.PDF_EXTERNAL_URI) {
                        type = NavType.StringType
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) { backStackEntry ->
                val rawUri = backStackEntry.arguments
                    ?.getString(Screen.PDF_EXTERNAL_URI)
                val uri = rawUri?.let { Uri.parse(Uri.decode(it)) } ?: return@composable

                val title = com.armanmaurya.archiv.ui.viewer.getFileName(context, uri) ?: "PDF Document"

                PdfViewerScreen(
                    uri = uri,
                    title = title,
                    onBackClick = {
                        if (!navController.popBackStack()) {
                            onExit()
                        }
                    }
                )
            }
        }
    }
}
