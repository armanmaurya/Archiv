package com.armanmaurya.archiv.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import kotlin.math.max

fun decodeSampledBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2000
): Bitmap? {
    return try {
        if (uri.scheme == "file") {
            val filePath = uri.path
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    return decodeSampledBitmapFromFile(filePath, maxDimension)
                }
            }
        }

        // Fallback to ContentResolver
        decodeSampledBitmapViaContentResolver(context, uri, maxDimension)

    } catch (e: Exception) {
        Log.e("BitmapDecoder", "Error decoding bitmap: ${e.message}", e)
        null
    }
}

private fun decodeSampledBitmapFromFile(
    filePath: String,
    maxDimension: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(filePath, bounds)

    val largest = max(bounds.outWidth, bounds.outHeight)
    val sampleSize = calculateSampleSize(largest, maxDimension)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }

    return BitmapFactory.decodeFile(filePath, options)
}

private fun decodeSampledBitmapViaContentResolver(
    context: Context,
    uri: Uri,
    maxDimension: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    } ?: return null

    val largest = max(bounds.outWidth, bounds.outHeight)
    val sampleSize = calculateSampleSize(largest, maxDimension)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }

    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

private fun calculateSampleSize(
    largestDimension: Int,
    maxDimension: Int
): Int {
    return if (largestDimension <= maxDimension) {
        1
    } else {
        max(1, largestDimension / maxDimension)
    }
}