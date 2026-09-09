package com.mirror.app.data.landmark

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.domain.model.FaceLandmarkResult
import com.mirror.app.domain.model.NormalizedLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around MediaPipe Tasks Vision's FaceLandmarker, configured for single-image
 * (IMAGE) running mode.
 *
 * IMPORTANT: this class only ever operates on a [Bitmap] already in memory - it never performs
 * network I/O. The `face_landmarker.task` model bundled in assets/ is a self-contained on-device
 * model, so detection works fully offline. This is the ONLY detector used for both the static
 * photo flow (Phase 1) and the future live-camera Pro flow, keeping the "no frames leave the
 * device" guarantee in one place.
 */
class FaceLandmarkerHelper(private val context: Context) {

    private var landmarker: FaceLandmarker? = null

    private fun getOrCreateLandmarker(): FaceLandmarker {
        return landmarker ?: run {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                // Request up to 2 so we can distinguish "exactly one face" from "more than one",
                // rather than silently keeping only the first face MediaPipe happens to return.
                .setNumFaces(MAX_FACES_TO_REQUEST)
                .setMinFaceDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinFacePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .build()

            FaceLandmarker.createFromOptions(context, options).also { landmarker = it }
        }
    }

    /**
     * Runs detection on [bitmap] and maps the result into our domain model.
     * Must be called off the main thread; this suspends on [Dispatchers.Default] internally.
     */
    suspend fun detect(bitmap: Bitmap): AppResult<FaceLandmarkResult> =
        withContext(Dispatchers.Default) {
            val result: FaceLandmarkerResult = try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                getOrCreateLandmarker().detect(mpImage)
            } catch (e: Exception) {
                return@withContext AppResult.Failure(AppError.LandmarkDetectionFailed)
            }

            val faces = result.faceLandmarks()
            when {
                faces.isEmpty() -> AppResult.Failure(AppError.NoFaceDetected)
                faces.size > 1 -> AppResult.Failure(AppError.MultipleFacesDetected)
                else -> {
                    val points = faces[0].map { landmark ->
                        NormalizedLandmark(x = landmark.x(), y = landmark.y(), z = landmark.z())
                    }
                    AppResult.Success(
                        FaceLandmarkResult(
                            points = points,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height
                        )
                    )
                }
            }
        }

    /** Releases native resources. Call when the helper is no longer needed (e.g. onCleared). */
    fun close() {
        landmarker?.close()
        landmarker = null
    }

    private companion object {
        const val MODEL_ASSET_PATH = "face_landmarker.task"
        const val MAX_FACES_TO_REQUEST = 2
        const val MIN_DETECTION_CONFIDENCE = 0.5f
        const val MIN_PRESENCE_CONFIDENCE = 0.5f
        const val MIN_TRACKING_CONFIDENCE = 0.5f
    }
}
