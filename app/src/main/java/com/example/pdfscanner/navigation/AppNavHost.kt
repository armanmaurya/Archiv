package com.example.pdfscanner.navigation

import android.graphics.PointF
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pdfscanner.ui.scanner.ScannerScreen
import com.example.pdfscanner.ui.FullScreenImagePager

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(
    hasCameraPermission: Boolean,
    capturedPageUris: List<Uri>,
    pageCropBounds: Map<String, List<PointF>>,
    pageRotationTurns: Map<String, Int>,
    pageFilterModes: Map<String, Int>,
    detectedCorners: List<PointF>?,
    imageAspectRatio: Float,
    isBusy: Boolean,
    errorMessage: String?,
    onCameraPreviewReady: (PreviewView) -> Unit,
    onRequestPermission: () -> Unit,
    onCapturePage: () -> Unit,
    onSavePdf: () -> Unit,
    onPickImages: () -> Unit,
    onClearPages: () -> Unit,
    onUpdateCropBounds: (String, List<PointF>) -> Unit,
    onUpdateRotationTurns: (String, Int) -> Unit,
    onUpdateFilterMode: (String, Int) -> Unit,
    onClearCropBounds: () -> Unit,
    onDismissError: () -> Unit,
    onReorderPages: (Int, Int) -> Unit,
    onRemovePage: (Int) -> Unit
) {
    val navController = rememberNavController()
    var scrollToIndexHint by remember { mutableStateOf<Int?>(null) }

    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = Screen.CAMERA) {
            composable(
                route = Screen.CAMERA,
                enterTransition = {
                    fadeIn()
                },
                exitTransition = {
                    fadeOut()
                },
                popEnterTransition = {
                    fadeIn()
                },
                popExitTransition = {
                    fadeOut()
                }
            ) {
                ScannerScreen(
                    hasCameraPermission = hasCameraPermission,
                    capturedPageUris = capturedPageUris,
                    detectedCorners = detectedCorners,
                    imageAspectRatio = imageAspectRatio,
                    isBusy = isBusy,
                    errorMessage = errorMessage,
                    onCameraPreviewReady = onCameraPreviewReady,
                    onRequestPermission = onRequestPermission,
                    onCapturePage = onCapturePage,
                    onSavePdf = onSavePdf,
                    onOpenGallery = { startIndex ->
                        if (startIndex < 0) {
                            onPickImages()
                        } else {
                            navController.navigate(Screen.galleryRoute(startIndex))
                        }
                    },
                    onClearPages = {
                        onClearPages()
                        onClearCropBounds()
                    },
                    onDismissError = onDismissError,
                    onReorderPages = onReorderPages,
                    onRemovePage = onRemovePage,
                    scrollToIndexHint = scrollToIndexHint,
                    onScrollHintConsumed = { scrollToIndexHint = null },
                    cropBoundsByUri = pageCropBounds,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    sharedElementKeyForUri = { uri -> "page-$uri" }
                )
            }

            composable(
                route = Screen.GALLERY_ROUTE,
                arguments = listOf(
                    navArgument(Screen.GALLERY_START_ARG) {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                ),
                enterTransition = {
                    fadeIn()
                },
                exitTransition = {
                    fadeOut()
                },
                popEnterTransition = {
                    fadeIn()
                },
                popExitTransition = {
                    fadeOut()
                }
            ) { backStackEntry ->
                val startIndex = backStackEntry.arguments?.getInt(Screen.GALLERY_START_ARG) ?: 0
                FullScreenImagePager(
                    imageUris = capturedPageUris,
                    cropBoundsByUri = pageCropBounds,
                    rotationTurnsByUri = pageRotationTurns,
                    filterModeByUri = pageFilterModes,
                    onUpdateCropBounds = onUpdateCropBounds,
                    onUpdateRotationTurns = onUpdateRotationTurns,
                    onUpdateFilterMode = onUpdateFilterMode,
                    onRemovePage = onRemovePage,
                    initialPage = startIndex,
                    onDismiss = { currentPage ->
                        scrollToIndexHint = currentPage
                        navController.popBackStack()
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    sharedElementKeyForUri = { uri -> "page-$uri" }
                )
            }
        }
    }
}
