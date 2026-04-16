package com.example.pdfscanner.navigation

object Screen {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val GALLERY_START_ARG = "startIndex"
    const val GALLERY_ROUTE = "$GALLERY/{$GALLERY_START_ARG}"

    fun galleryRoute(startIndex: Int): String = "$GALLERY/$startIndex"
}
