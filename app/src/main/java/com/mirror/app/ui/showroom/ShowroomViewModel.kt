package com.mirror.app.ui.showroom

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.landmark.FaceLandmarkerHelper
import com.mirror.app.data.showroom.ShowroomRepository
import com.mirror.app.domain.model.FaceAnalysis
import com.mirror.app.domain.model.ShadeFinish
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.editor.EditorCategory
import com.mirror.app.ui.editor.MakeupLayer
import com.mirror.app.ui.editor.MakeupSelection
import com.mirror.app.ui.editor.editorShadesByCategory
import com.mirror.app.ui.editor.isSteepAngle
import com.mirror.app.ui.editor.resolveMakeupLayer
import com.mirror.app.ui.editor.toShadeCategory
import com.mirror.app.ui.upload.UploadErrorMessage
import com.mirror.app.ui.upload.UploadUiState
import com.mirror.app.ui.upload.decodeBitmapFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Ready(val analysis: FaceAnalysis) : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

/** The four categories Showroom offers - a small subset of the Studio's. */
val showroomCategories = listOf(
    EditorCategory.FOUNDATION, EditorCategory.LIPS, EditorCategory.CHEEK, EditorCategory.BROWS
)

/** Maps the backend's suggestion category names to Studio categories. */
private fun wireCategory(category: String): EditorCategory? = when (category.lowercase()) {
    "foundation" -> EditorCategory.FOUNDATION
    "lipstick" -> EditorCategory.LIPS
    "blush" -> EditorCategory.CHEEK
    "eyebrow" -> EditorCategory.BROWS
    else -> null
}

/**
 * Backs the Showroom screen. Completely separate from UploadViewModel (its own photo, selections
 * and landmarker) so nothing here can alter the Studio or saved looks. The AI analysis is
 * advisory: it fills in "AI picks" but applies nothing to the photo - the face stays untouched
 * until the user taps a shade or moves a slider.
 */
class ShowroomViewModel(
    private val faceLandmarkerHelper: FaceLandmarkerHelper,
    private val repository: ShowroomRepository,
    private val decodeBitmap: suspend (Uri, ContentResolver) -> Bitmap? = ::decodeBitmapFromUri
) : ViewModel() {

    private val _photoState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val photoState: StateFlow<UploadUiState> = _photoState.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    private val _selections = MutableStateFlow<Map<EditorCategory, MakeupSelection>>(emptyMap())
    val selections: StateFlow<Map<EditorCategory, MakeupSelection>> = _selections.asStateFlow()

    private val _catalog = MutableStateFlow(editorShadesByCategory)

    /** Bundled shades plus (once analysis lands) the AI's picks, listed first per category. */
    val catalog: StateFlow<Map<EditorCategory, List<ShadeItem>>> = _catalog.asStateFlow()

    private val _aiPickIds = MutableStateFlow<Set<String>>(emptySet())
    val aiPickIds: StateFlow<Set<String>> = _aiPickIds.asStateFlow()

    fun onPhotoPicked(uri: Uri, contentResolver: ContentResolver) {
        resetResults()
        _photoState.value = UploadUiState.Detecting
        viewModelScope.launch {
            val bitmap = decodeBitmap(uri, contentResolver)
            if (bitmap == null) {
                _photoState.value = UploadUiState.Error(null, UploadErrorMessage.IMAGE_LOAD_FAILED)
                return@launch
            }
            when (val result = faceLandmarkerHelper.detect(bitmap)) {
                is AppResult.Success -> {
                    _photoState.value = UploadUiState.Detected(
                        photo = bitmap,
                        landmarks = result.data,
                        showDebugOverlay = false,
                        isSteepAngle = isSteepAngle(result.data)
                    )
                    analyze(bitmap)
                }
                is AppResult.Failure -> _photoState.value = UploadUiState.Error(bitmap, result.error.toUploadError())
            }
        }
    }

    fun retryAnalysis() {
        val state = _photoState.value as? UploadUiState.Detected ?: return
        analyze(state.photo)
    }

    private fun analyze(photo: Bitmap) {
        _analysis.value = AnalysisState.Loading
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.Default) { photo.toUploadJpeg() }
            when (val result = repository.analyze(jpeg)) {
                is AppResult.Success -> {
                    applyAiPicks(result.data)
                    _analysis.value = AnalysisState.Ready(result.data)
                }
                is AppResult.Failure -> _analysis.value = AnalysisState.Failed(result.error.toMessage())
            }
        }
    }

    private fun applyAiPicks(analysis: FaceAnalysis) {
        val picks = mutableMapOf<EditorCategory, MutableList<ShadeItem>>()
        analysis.suggestions.forEachIndexed { index, suggestion ->
            val category = wireCategory(suggestion.category) ?: return@forEachIndexed
            picks.getOrPut(category) { mutableListOf() } += ShadeItem(
                id = "ai-$index-${suggestion.hexColor}",
                name = suggestion.name,
                hexColor = suggestion.hexColor,
                category = category.toShadeCategory(),
                finish = ShadeFinish.SATIN
            )
        }
        _aiPickIds.value = picks.values.flatten().map { it.id }.toSet()
        _catalog.value = editorShadesByCategory.mapValues { (category, base) -> picks[category].orEmpty() + base }
    }

    fun selectShade(category: EditorCategory, shadeId: String) {
        val current = _selections.value[category] ?: MakeupSelection()
        _selections.value = _selections.value + (category to current.copy(shadeId = shadeId))
    }

    fun clearShade(category: EditorCategory) {
        val current = _selections.value[category] ?: return
        _selections.value = _selections.value + (category to current.copy(shadeId = null))
    }

    fun setIntensity(category: EditorCategory, value: Float) {
        val current = _selections.value[category] ?: MakeupSelection()
        _selections.value = _selections.value + (category to current.copy(intensity = value))
    }

    fun layer(category: EditorCategory): MakeupLayer? =
        resolveMakeupLayer(category, _selections.value, _catalog.value)

    fun reset() {
        resetResults()
        _photoState.value = UploadUiState.Idle
    }

    private fun resetResults() {
        _analysis.value = AnalysisState.Idle
        _selections.value = emptyMap()
        _aiPickIds.value = emptySet()
        _catalog.value = editorShadesByCategory
    }

    override fun onCleared() {
        super.onCleared()
        faceLandmarkerHelper.close()
    }

    private fun AppError.toUploadError(): UploadErrorMessage = when (this) {
        AppError.NoFaceDetected -> UploadErrorMessage.NO_FACE_DETECTED
        AppError.MultipleFacesDetected -> UploadErrorMessage.MULTIPLE_FACES_DETECTED
        AppError.ImageLoadFailed -> UploadErrorMessage.IMAGE_LOAD_FAILED
        else -> UploadErrorMessage.LANDMARK_DETECTION_FAILED
    }

    private fun AppError.toMessage(): String = when (this) {
        AppError.NetworkUnavailable -> "No connection. Check your internet and try again."
        is AppError.AuthFailed -> message
        is AppError.RequestFailed -> message
        else -> "Something went wrong. Please try again."
    }
}

/** Longest side capped at 1280px and JPEG-compressed: plenty for reading skin tone, and keeps
 *  the upload small on mobile data (the server rejects anything over 6 MB). */
private fun Bitmap.toUploadJpeg(maxSide: Int = 1280, quality: Int = 85): ByteArray {
    val scale = maxSide.toFloat() / maxOf(width, height)
    val source = if (scale < 1f) {
        Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    } else this
    return ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
