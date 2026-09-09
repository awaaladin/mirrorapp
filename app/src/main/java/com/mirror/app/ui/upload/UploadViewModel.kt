package com.mirror.app.ui.upload

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.landmark.FaceLandmarkerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Default production bitmap decoder, extracted so tests can substitute a fake without needing
 *  a real Android runtime (Robolectric) just to exercise ViewModel state transitions. */
internal suspend fun decodeBitmapFromUri(uri: Uri, contentResolver: ContentResolver): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

class UploadViewModel(
    private val faceLandmarkerHelper: FaceLandmarkerHelper,
    private val decodeBitmap: suspend (Uri, ContentResolver) -> Bitmap? = ::decodeBitmapFromUri
) : ViewModel() {

    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    /** Called when the user picks a photo from the gallery/photo picker. */
    fun onPhotoPicked(uri: Uri, contentResolver: ContentResolver) {
        _uiState.value = UploadUiState.Detecting
        viewModelScope.launch {
            val bitmap = decodeBitmap(uri, contentResolver)
            if (bitmap == null) {
                _uiState.value = UploadUiState.Error(
                    photo = null,
                    message = UploadErrorMessage.IMAGE_LOAD_FAILED
                )
                return@launch
            }

            when (val result = faceLandmarkerHelper.detect(bitmap)) {
                is AppResult.Success -> {
                    _uiState.value = UploadUiState.Detected(
                        photo = bitmap,
                        landmarks = result.data
                    )
                }
                is AppResult.Failure -> {
                    _uiState.value = UploadUiState.Error(
                        photo = bitmap,
                        message = result.error.toUiMessage()
                    )
                }
            }
        }
    }

    /** Toggles the debug landmark overlay on/off while a detection result is showing. */
    fun toggleDebugOverlay() {
        val current = _uiState.value
        if (current is UploadUiState.Detected) {
            _uiState.value = current.copy(showDebugOverlay = !current.showDebugOverlay)
        }
    }

    fun reset() {
        _uiState.value = UploadUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        faceLandmarkerHelper.close()
    }

    // Only the photo/landmark pipeline cases below can actually occur here - onPhotoPicked()
    // only ever calls faceLandmarkerHelper.detect(), which never returns an auth AppError - but
    // AppError is a single shared closed set across the app, so the `when` needs a catch-all.
    private fun AppError.toUiMessage(): UploadErrorMessage = when (this) {
        AppError.NoFaceDetected -> UploadErrorMessage.NO_FACE_DETECTED
        AppError.MultipleFacesDetected -> UploadErrorMessage.MULTIPLE_FACES_DETECTED
        AppError.ImageLoadFailed -> UploadErrorMessage.IMAGE_LOAD_FAILED
        AppError.LandmarkDetectionFailed -> UploadErrorMessage.LANDMARK_DETECTION_FAILED
        else -> UploadErrorMessage.LANDMARK_DETECTION_FAILED
    }
}
