package com.example.pdfscanner.bitmap

import android.graphics.Bitmap
import android.util.LruCache

object BitmapCache {
    private val thumbnailCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val previewCache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun getThumbnail(key: String): Bitmap? = thumbnailCache.get(key)

    fun putThumbnail(key: String, bitmap: Bitmap) {
        thumbnailCache.put(key, bitmap)
    }

    fun getPreview(key: String): Bitmap? = previewCache.get(key)

    fun putPreview(key: String, bitmap: Bitmap) {
        previewCache.put(key, bitmap)
    }
}
