package com.armanmaurya.archiv.navigation

import android.net.Uri

object Screen {
    const val SCANNER_FLOW = "scanner_flow"
    const val CAMERA = "camera"
    const val EDITOR = "editor"
    const val DOCUMENTS = "documents"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val PDF_VIEWER = "pdf_viewer"
    const val PDF_VIEWER_DOCUMENT_ID = "documentId"
    const val PDF_EXTERNAL_VIEWER = "pdf_external_viewer"
    const val PDF_EXTERNAL_URI = "uri"
    const val EDITOR_START_ARG = "startIndex"
    const val EDITOR_ROUTE = "$EDITOR/{$EDITOR_START_ARG}"
    const val PDF_VIEWER_ROUTE = "$PDF_VIEWER/{$PDF_VIEWER_DOCUMENT_ID}"
    const val PDF_EXTERNAL_VIEWER_ROUTE = "$PDF_EXTERNAL_VIEWER/{$PDF_EXTERNAL_URI}"

    fun editorRoute(startIndex: Int): String = "$EDITOR/$startIndex"

    fun pdfViewerRoute(documentId: String): String {
        return "$PDF_VIEWER/${Uri.encode(documentId)}"
    }

    fun pdfExternalViewerRoute(uri: Uri): String {
        return "$PDF_EXTERNAL_VIEWER/${Uri.encode(uri.toString())}"
    }
}
