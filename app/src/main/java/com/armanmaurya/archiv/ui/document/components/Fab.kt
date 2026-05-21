package com.armanmaurya.archiv.ui.document.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armanmaurya.archiv.R

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Fab(
    onImportPdf: () -> Unit,
    onOpenScanner: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val sharedFabModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState("scan_button"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        SmallFloatingActionButton(
            onClick = onImportPdf,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = Icons.Outlined.PictureAsPdf,
                contentDescription = stringResource(R.string.document_list_fab_import_pdf)
            )
        }
        ExtendedFloatingActionButton(
            onClick = onOpenScanner,
            modifier = sharedFabModifier,
            icon = {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.document_list_fab_scan),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}
