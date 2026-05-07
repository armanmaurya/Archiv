package com.armanmaurya.archiv.navigation

object Screen {
    const val SCANNER_FLOW = "scanner_flow"
    const val PDF_TOOLS_FLOW = "pdf_tools_flow"
    const val CAMERA = "camera"
    const val EDITOR = "editor"
    const val DOCUMENTS = "documents"
    const val PDF_TOOLS = "pdf_tools"
    const val PDF_TOOLS_MERGE = "pdf_tools_merge"
    const val PDF_TOOLS_SPLIT = "pdf_tools_split"
    const val PDF_TOOLS_REORDER = "pdf_tools_reorder"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val EDITOR_START_ARG = "startIndex"
    const val EDITOR_ROUTE = "$EDITOR/{$EDITOR_START_ARG}"

    fun editorRoute(startIndex: Int): String = "$EDITOR/$startIndex"
}
