package com.mirror.app.ui.upload

import android.graphics.Bitmap
import com.mirror.app.domain.model.FaceLandmarkResult

/** Everything the Upload screen needs to render, expressed as a single closed state. */
sealed class UploadUiState {
    data object Idle : UploadUiState()
    data object Detecting : UploadUiState()

    data class Detected(
        val photo: Bitmap,
        val landmarks: FaceLandmarkResult,
        val showDebugOverlay: Boolean = true,
        /** True when the photo is close enough to profile that the render will visibly
         *  foreshorten one side of the face - see [com.mirror.app.ui.editor.isSteepAngle]. */
        val isSteepAngle: Boolean = false
    ) : UploadUiState()

    data class Error(
        val photo: Bitmap?,
        val message: UploadErrorMessage
    ) : UploadUiState()
}

enum class UploadErrorMessage {
    NO_FACE_DETECTED,
    MULTIPLE_FACES_DETECTED,
    IMAGE_LOAD_FAILED,
    LANDMARK_DETECTION_FAILED
}
