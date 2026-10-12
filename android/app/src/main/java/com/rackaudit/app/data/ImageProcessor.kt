package com.rackaudit.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.rackaudit.app.domain.ImageMath
import com.rackaudit.app.domain.Limits
import java.io.File
import java.io.FileOutputStream

/** Shrinks a camera photo to Limits (long edge, bytes), honouring EXIF rotation. */
object ImageProcessor {

    /** @return true if [output] now holds a JPEG within the size limit. */
    fun compress(input: File, output: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = ImageMath.inSampleSize(bounds.outWidth, bounds.outHeight, Limits.PHOTO_MAX_EDGE_PX)
        }
        val decoded = BitmapFactory.decodeFile(input.path, decodeOpts) ?: return false
        val rotated = rotate(decoded, orientationDegrees(input))
        val (w, h) = ImageMath.scaledSize(rotated.width, rotated.height, Limits.PHOTO_MAX_EDGE_PX)
        val scaled = if (w == rotated.width && h == rotated.height) rotated else Bitmap.createScaledBitmap(rotated, w, h, true)

        // Stored inside a Firestore document (1 MiB cap), so try descending quality until it fits.
        for (quality in intArrayOf(80, 65, 50, 35)) {
            FileOutputStream(output).use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            if (output.length() <= Limits.PHOTO_MAX_BYTES) return true
        }
        return false
    }

    private fun orientationDegrees(file: File): Int =
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
