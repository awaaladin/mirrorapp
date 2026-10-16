package com.mirror.app.data.landmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PorterDuff
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around MediaPipe Tasks Vision's ImageSegmenter, configured with the bundled
 * `selfie_segmenter.tflite` model for single-image (IMAGE) running mode. Same offline,
 * bitmap-in/bitmap-out shape as FaceLandmarkerHelper - no network I/O, ever.
 *
 * Used by the "Simulate" feature to cut a subject out from their background so it can be
 * replaced with a clean backdrop and relit; this class only produces the person mask, it
 * doesn't do any compositing or color grading itself.
 */
class SelfieSegmenterHelper(private val context: Context) {

    private var segmenter: ImageSegmenter? = null

    private fun getOrCreateSegmenter(): ImageSegmenter {
        return segmenter ?: run {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)
                .build()

            ImageSegmenter.createFromOptions(context, options).also { segmenter = it }
        }
    }

    /**
     * Runs segmentation on [bitmap] and returns a same-size ALPHA_8 mask where opaque pixels
     * are the person and transparent pixels are background - ready to use as a compositing
     * mask (e.g. via [Canvas.drawBitmap] with [PorterDuff.Mode.DST_IN]).
     *
     * Must be called off the main thread; this suspends on [Dispatchers.Default] internally.
     */
    suspend fun segmentPersonMask(bitmap: Bitmap): AppResult<Bitmap> =
        withContext(Dispatchers.Default) {
            val categoryMask = try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = getOrCreateSegmenter().segment(mpImage)
                val maskImage = result.categoryMask().orElse(null)
                    ?: return@withContext AppResult.Failure(AppError.LandmarkDetectionFailed)
                BitmapExtractor.extract(maskImage)
            } catch (e: Exception) {
                return@withContext AppResult.Failure(AppError.LandmarkDetectionFailed)
            }

            AppResult.Success(categoryMask.toAlphaMask())
        }

    /** Releases native resources. Call when the helper is no longer needed (e.g. onCleared). */
    fun close() {
        segmenter?.close()
        segmenter = null
    }

    private companion object {
        const val MODEL_ASSET_PATH = "selfie_segmenter.tflite"
    }
}

/**
 * The raw category mask MediaPipe returns encodes each pixel's class as a literal small integer
 * (0 = background, 1 = person) in its color channels, which reads as near-black and is not
 * itself usable as an alpha channel - this remaps it to a proper 0/255 ALPHA_8 mask.
 */
private fun Bitmap.toAlphaMask(): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        // Person class is index 1 in the bundled selfie_segmenter model; any nonzero channel
        // means "person" since the category index is stored directly in the RGB channels.
        val isPerson = (pixels[i] and 0x00FFFFFF) != 0
        pixels[i] = if (isPerson) 0xFF000000.toInt() else 0x00000000
    }
    val alphaMask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    alphaMask.setPixels(pixels, 0, width, 0, 0, width, height)
    return alphaMask
}
