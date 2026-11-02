package com.mirror.app.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val WATERMARK_TEXT = "made with mirror"

/** A copy of [photo] with the free tier's watermark in the bottom-right corner. */
fun addWatermark(photo: Bitmap): Bitmap {
    val result = photo.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val textSize = result.width * 0.04f
    val padding = textSize * 0.7f
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.textSize = textSize
    }
    val pillWidth = text.measureText(WATERMARK_TEXT) + padding * 2
    val pillHeight = textSize + padding * 2
    val right = result.width - padding
    val bottom = result.height - padding
    val pill = RectF(right - pillWidth, bottom - pillHeight, right, bottom)
    canvas.drawRoundRect(pill, pillHeight / 2, pillHeight / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
    })
    canvas.drawText(WATERMARK_TEXT, pill.left + padding, pill.bottom - padding - text.descent() / 2, text)
    return result
}

/** Saves [photo] to the phone's gallery (Pictures/mirror), watermarked on free accounts. Needs
 *  Android 10+, where saving to the gallery takes no storage permission; on older phones it
 *  returns false and SHARE LOOK (which can save too) is the way. */
suspend fun saveLookToPhone(context: Context, photo: Bitmap, withWatermark: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return withContext(Dispatchers.IO) {
        val output = if (withWatermark) addWatermark(photo) else photo
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mirror-look-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/mirror")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        val written = resolver.openOutputStream(uri)?.use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: false
        if (!written) resolver.delete(uri, null, null)
        written
    }
}

/** Opens the system share sheet (WhatsApp, Instagram, ...) with [photo] as a JPEG. */
suspend fun shareLook(context: Context, photo: Bitmap, withWatermark: Boolean) {
    val file = withContext(Dispatchers.IO) {
        val output = if (withWatermark) addWatermark(photo) else photo
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        File(directory, "mirror-look.jpg").also { file ->
            file.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your look"))
}
