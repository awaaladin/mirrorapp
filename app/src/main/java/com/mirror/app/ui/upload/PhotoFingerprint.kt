package com.mirror.app.ui.upload

import android.graphics.Bitmap
import java.security.MessageDigest

/** A stable id for a photo, so reopening the same picture isn't counted as a new one: a hash of a
 *  tiny downscaled copy, which survives the decode and EXIF handling coming out the same way. */
fun photoFingerprint(photo: Bitmap): String {
    val small = Bitmap.createScaledBitmap(photo, 32, 32, true)
    val pixels = IntArray(32 * 32)
    small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
    if (small !== photo) small.recycle()
    val digest = MessageDigest.getInstance("SHA-256")
    for (pixel in pixels) {
        digest.update(byteArrayOf((pixel shr 16).toByte(), (pixel shr 8).toByte(), pixel.toByte()))
    }
    return digest.digest().joinToString("") { "%02x".format(it) }.take(40)
}
