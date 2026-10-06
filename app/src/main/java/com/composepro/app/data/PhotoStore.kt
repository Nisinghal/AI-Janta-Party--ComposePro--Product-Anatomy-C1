package com.composepro.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.camera.core.ImageCapture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Photos are saved to the phone's own photos, in Pictures/Compose Pro, so people find them
 * in their normal gallery too. The in-app Gallery shows only Compose Pro's photos.
 * (Answers the open BRIEF.md question: one copy, in the phone's photos.)
 */
object PhotoStore {
    private const val PREFIX = "ComposePro_"
    private const val FOLDER = "Pictures/Compose Pro"

    fun outputOptions(context: Context): ImageCapture.OutputFileOptions {
        val name = PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
        ).build()
    }

    /** Compose Pro's photos, newest first. */
    fun list(context: Context): List<Uri> {
        val out = mutableListOf<Uri>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$PREFIX%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(id))
        }
        return out
    }

    /** True if deleted. Fails for photos taken by an earlier install of the app (Android treats them as someone else's). */
    fun delete(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (e: SecurityException) {
        false
    }

    /**
     * Downsampled bitmap, so big photos don't run the phone out of memory. Small thumbnails use Android's
     * cached ones; anything bigger is decoded from the real photo. Android's cached thumbnail is only about
     * 500px whatever size is asked for, which made Review and the photo viewer look blurry (phone test, 2026-10-06).
     */
    fun load(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && maxSide <= 400) {
            context.contentResolver.loadThumbnail(uri, Size(maxSide, maxSide), null)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also turns the photo the right way up (from its EXIF orientation).
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val s = info.size
                val scale = minOf(1f, maxSide.toFloat() / maxOf(s.width, s.height))
                decoder.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Exception) {
        null
    }
}
