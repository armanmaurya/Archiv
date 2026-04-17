package com.example.pdfscanner.navigation

object Screen {
    const val CAMERA = "camera"
    const val EDITOR = "editor"
    const val EDITOR_START_ARG = "startIndex"
    const val EDITOR_ROUTE = "$EDITOR/{$EDITOR_START_ARG}"

    fun editorRoute(startIndex: Int): String = "$EDITOR/$startIndex"
}
