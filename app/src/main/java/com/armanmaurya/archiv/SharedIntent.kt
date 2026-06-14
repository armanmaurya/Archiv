package com.armanmaurya.archiv

import android.net.Uri

sealed interface SharedIntent {
    data object None : SharedIntent
    data class Pdfs(val uris: List<Uri>) : SharedIntent
    data class Images(val uris: List<Uri>) : SharedIntent
    data class Viewer(val uri: Uri) : SharedIntent
}
