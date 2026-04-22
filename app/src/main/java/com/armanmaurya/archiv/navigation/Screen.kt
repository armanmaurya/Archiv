package com.armanmaurya.archiv.navigation

object Screen {
    const val SCANNER_FLOW = "scanner_flow"
    const val CAMERA = "camera"
    const val EDITOR = "editor"
    const val DOCUMENTS = "documents"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val EDITOR_START_ARG = "startIndex"
    const val EDITOR_ROUTE = "$EDITOR/{$EDITOR_START_ARG}"

    fun editorRoute(startIndex: Int): String = "$EDITOR/$startIndex"
}
