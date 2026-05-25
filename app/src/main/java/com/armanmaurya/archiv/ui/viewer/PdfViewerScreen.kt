package com.armanmaurya.archiv.ui.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.armanmaurya.archiv.ui.document.DocumentViewModel
import com.armanmaurya.archiv.ui.viewer.components.BrightnessSlider
import com.armanmaurya.archiv.ui.viewer.components.TopBar
import kotlinx.coroutines.delay

@Composable
fun PdfViewerScreen(
    documentId: String,
    viewModel: DocumentViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val fragmentActivity = remember(context) { context.findFragmentActivity() }
    val documentUri = remember(documentId) { viewModel.getDocumentFileUri(documentId) }
    val supportsViewer = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13
    }
    var isTopBarVisible by remember { mutableStateOf(false) }
    val autoHideMillis = 2500L
    
    var manualBrightness by remember { mutableStateOf(0.5f) }
    var isAutoBrightness by remember { mutableStateOf(true) }
    var isInteractingWithSlider by remember { mutableStateOf(false) }

    DisposableEffect(manualBrightness, isAutoBrightness, fragmentActivity) {
        val window = fragmentActivity?.window
        val layoutParams = window?.attributes
        val originalBrightness = layoutParams?.screenBrightness ?: -1f
        
        layoutParams?.screenBrightness = if (isAutoBrightness) -1f else manualBrightness
        window?.attributes = layoutParams
        
        onDispose {
            layoutParams?.screenBrightness = originalBrightness
            window?.attributes = layoutParams
        }
    }

    LaunchedEffect(isTopBarVisible, isInteractingWithSlider) {
        if (isTopBarVisible && !isInteractingWithSlider) {
            delay(autoHideMillis)
            isTopBarVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var accumulatedDrag = Offset.Zero
                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val changes = event.changes
                            if (changes.any { it.pressed }) {
                                val change = changes.first()
                                accumulatedDrag += change.position - change.previousPosition
                                if (!isDragging && accumulatedDrag.getDistance() > touchSlop) {
                                    isDragging = true
                                    isTopBarVisible = false
                                }
                            } else {
                                if (!isDragging) {
                                    isTopBarVisible = !isTopBarVisible
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            if (!supportsViewer || fragmentActivity == null || documentUri == null) {
                PdfViewerFallback(
                    modifier = Modifier.fillMaxSize(),
                    onOpenWith = { launchOpenWith(context, viewModel, documentId) },
                    isSupported = supportsViewer
                )
            } else {
                PdfViewerFragmentHost(
                    documentUri = documentUri,
                    fragmentActivity = fragmentActivity,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        AnimatedVisibility(
            visible = isTopBarVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopBar(
                    title = documentId,
                    onBackClick = onBackClick
                )
                BrightnessSlider(
                    manualBrightness = manualBrightness,
                    isAutoBrightness = isAutoBrightness,
                    onBrightnessChange = { 
                        manualBrightness = it
                        isAutoBrightness = false
                        isInteractingWithSlider = true
                    },
                    onToggleAuto = { isAutoBrightness = true },
                    onInteractionFinished = { isInteractingWithSlider = false }
                )
            }
        }
    }
}

@Composable
private fun PdfViewerFallback(
    modifier: Modifier,
    onOpenWith: () -> Unit,
    isSupported: Boolean
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val message = if (isSupported) {
            "Unable to load this PDF in app."
        } else {
            "In-app viewer is not supported on this device."
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Button(
            onClick = onOpenWith,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Open with")
        }
    }
}

@Composable
private fun PdfViewerFragmentHost(
    documentUri: Uri,
    fragmentActivity: FragmentActivity,
    modifier: Modifier = Modifier
) {
    val containerId = remember { View.generateViewId() }
    val fragmentTag = remember(documentUri) { "pdf_viewer_${documentUri.hashCode()}" }

    DisposableEffect(fragmentActivity, fragmentTag) {
        onDispose {
            val fragmentManager = fragmentActivity.supportFragmentManager
            val fragment = fragmentManager.findFragmentByTag(fragmentTag)
            if (fragment != null) {
                fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = {
            val fragmentManager = fragmentActivity.supportFragmentManager
            var fragment = fragmentManager.findFragmentByTag(fragmentTag) as? PdfViewerFragment
            if (fragment == null) {
                fragment = PdfViewerFragment()
                fragmentManager.beginTransaction()
                    .replace(containerId, fragment, fragmentTag)
                    .commitAllowingStateLoss()
                fragmentManager.executePendingTransactions()
            }
            if (fragment.documentUri != documentUri) {
                fragment.documentUri = documentUri
            }
        }
    )
}

private fun launchOpenWith(
    context: Context,
    viewModel: DocumentViewModel,
    documentId: String
) {
    val openIntent = viewModel.createOpenIntent(documentId) ?: return
    try {
        context.startActivity(Intent.createChooser(openIntent, "Open scan"))
    } catch (_: ActivityNotFoundException) {
        viewModel.onOpenAppUnavailable()
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
