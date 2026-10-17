package com.mirror.app.ui.upload

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.landmark.FaceLandmarkerHelper
import com.mirror.app.data.looks.LoadedLook
import com.mirror.app.data.looks.LookStore
import com.mirror.app.data.shades.CustomShadeStore
import com.mirror.app.data.shades.ShadeRepository
import com.mirror.app.domain.model.FaceLandmarkResult
import com.mirror.app.domain.model.LashStyle
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.editor.EditorCategory
import com.mirror.app.ui.editor.MakeupLayer
import com.mirror.app.ui.editor.MakeupSelection
import com.mirror.app.ui.editor.cropToFace
import com.mirror.app.ui.editor.editorShadesByCategory
import com.mirror.app.ui.editor.isSteepAngle
import com.mirror.app.ui.editor.renderMakeupBitmap
import com.mirror.app.ui.editor.resolveMakeupLayer
import com.mirror.app.ui.editor.toShadeCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** A makeup formulation saved for this app session (kept in memory for the lifetime of this
 *  ViewModel, not persisted to disk yet) - shows up in Dashboard's Recent Looks. Carries the
 *  full state needed to re-open it in the editor (not just a flattened thumbnail), so tapping
 *  it from Dashboard can actually resume editing rather than just showing a picture. */
data class SavedLook(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val thumbnail: Bitmap,
    val sourcePhoto: Bitmap,
    val landmarks: FaceLandmarkResult,
    val selections: Map<EditorCategory, MakeupSelection>,
    val lashStyle: LashStyle?
)

/** Default production bitmap decoder, extracted so tests can substitute a fake without needing
 *  a real Android runtime (Robolectric) just to exercise ViewModel state transitions. */
internal suspend fun decodeBitmapFromUri(uri: Uri, contentResolver: ContentResolver): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            // Bounds first, so a 12MP camera photo is sampled down while decoding instead of
            // being fully expanded (slow, and a real out-of-memory risk on budget phones).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_SIDE) sample *= 2

            val decoded = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null

            // Phone cameras store portraits sideways plus a rotation flag that BitmapFactory
            // ignores. Without applying it the photo loads rotated and landmarks/makeup land wrong
            // (or no face is found at all).
            val orientation = try {
                contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
            applyExifOrientation(decoded, orientation)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

private const val MAX_DECODE_SIDE = 2048

/** Returns [bitmap] rotated/flipped upright for an EXIF orientation value (recycling the original
 *  when a new bitmap is made). Orientation 1 / unknown values return [bitmap] untouched. */
internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.postRotate(180f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

class UploadViewModel(
    private val faceLandmarkerHelper: FaceLandmarkerHelper,
    private val shadeRepository: ShadeRepository? = null,
    private val customShadeStore: CustomShadeStore? = null,
    private val decodeBitmap: suspend (Uri, ContentResolver) -> Bitmap? = ::decodeBitmapFromUri,
    private val lookStore: LookStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    // Seeded with the bundled offline set so the editor always has something to show, then
    // replaced with the live backend catalog once/if a fetch succeeds - see refreshShadeCatalog().
    private val _baseCatalog = MutableStateFlow(editorShadesByCategory)

    // User-created shades (see addCustomShade) - kept separate from _baseCatalog and merged in
    // below, rather than folded into it directly, so a later refreshShadeCatalog() replacing
    // _baseCatalog wholesale from the network can never wipe out something the user made.
    private val _customShades = MutableStateFlow(customShadeStore?.loadAll() ?: emptyMap())

    val shadeCatalog: StateFlow<Map<EditorCategory, List<ShadeItem>>> =
        combine(_baseCatalog, _customShades) { base, custom ->
            EditorCategory.entries.associateWith { category -> base[category].orEmpty() + custom[category].orEmpty() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, editorShadesByCategory)

    private var catalogRefreshJob: kotlinx.coroutines.Job? = null

    // Keyed per category so switching tabs in the editor never loses what was picked elsewhere,
    // and so lips/foundation/eyes/etc. can all render on the face at the same time. Held here
    // rather than as local Compose state in EditorScreen so BeforeAfterScreen and Save
    // Formulation can see the same live selections without threading them through navigation.
    private val _selections = MutableStateFlow<Map<EditorCategory, MakeupSelection>>(emptyMap())
    val selections: StateFlow<Map<EditorCategory, MakeupSelection>> = _selections.asStateFlow()

    private val _savedLooks = MutableStateFlow<List<SavedLook>>(emptyList())
    val savedLooks: StateFlow<List<SavedLook>> = _savedLooks.asStateFlow()

    init {
        // Bring back looks saved in earlier sessions. Read off the main thread: it decodes images.
        lookStore?.let { store ->
            viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) { store.loadAll() }
                restoreLooks(loaded)
            }
        }
    }

    /** Adds looks read from disk, first making sure every shade they use still exists in the catalog
     *  (a bundled or backend shade id can disappear between sessions) by re-registering the stored
     *  copy as a local custom shade under the same id. */
    private fun restoreLooks(loaded: List<LoadedLook>) {
        for (item in loaded) {
            for ((category, shade) in item.shades) {
                val known = _baseCatalog.value[category].orEmpty() + _customShades.value[category].orEmpty()
                if (known.none { it.id == shade.id }) {
                    customShadeStore?.add(category, shade)
                    _customShades.value = _customShades.value + (category to (_customShades.value[category].orEmpty() + shade))
                }
            }
        }
        _savedLooks.value = (_savedLooks.value + loaded.map { it.look })
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtEpochMillis }
    }

    /** The shades [selections] point at, from [catalog], to be stored alongside a saved look. */
    private fun shadesUsed(
        selections: Map<EditorCategory, MakeupSelection>,
        catalog: Map<EditorCategory, List<ShadeItem>>
    ): Map<EditorCategory, ShadeItem> = selections.mapNotNull { (category, selection) ->
        val shade = catalog[category]?.firstOrNull { it.id == selection.shadeId } ?: return@mapNotNull null
        category to shade
    }.toMap()

    // Lashes isn't a color pick like the other categories (mascara is always near-black
    // regardless of "shade"), so it gets its own style-based state rather than a MakeupSelection.
    private val _lashStyle = MutableStateFlow<LashStyle?>(null)
    val lashStyle: StateFlow<LashStyle?> = _lashStyle.asStateFlow()

    fun selectLashStyle(style: LashStyle) {
        _lashStyle.value = style
    }

    fun clearLashStyle() {
        _lashStyle.value = null
    }

    /** Called when the user picks a photo from the gallery/photo picker. */
    fun onPhotoPicked(uri: Uri, contentResolver: ContentResolver) {
        _uiState.value = UploadUiState.Detecting
        _selections.value = emptyMap()
        _lashStyle.value = null
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
                        landmarks = result.data,
                        isSteepAngle = isSteepAngle(result.data)
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
        _selections.value = emptyMap()
        _lashStyle.value = null
    }

    fun selectShade(category: EditorCategory, shadeId: String) {
        val current = _selections.value[category] ?: MakeupSelection()
        _selections.value = _selections.value + (category to current.copy(shadeId = shadeId))
    }

    /** Removes whatever shade is applied to [category], so that layer stops rendering entirely -
     *  the "take it off" counterpart to selectShade(). */
    fun clearShade(category: EditorCategory) {
        val current = _selections.value[category] ?: return
        _selections.value = _selections.value + (category to current.copy(shadeId = null))
    }

    fun setIntensity(category: EditorCategory, value: Float) {
        val current = _selections.value[category] ?: MakeupSelection()
        _selections.value = _selections.value + (category to current.copy(intensity = value))
    }

    fun resolvedLayer(category: EditorCategory): MakeupLayer? =
        resolveMakeupLayer(category, _selections.value, shadeCatalog.value)

    /** Fetches the full live shade catalog from mirrorcentral and swaps it in on success. Called
     *  once when the editor opens. On failure (offline, server unreachable) this is a no-op -
     *  whatever catalog is already showing (the bundled default) stays put rather than being
     *  cleared. Search within a category is done client-side against whatever catalog is
     *  currently loaded (see EditorScreen), not re-fetched per keystroke. */
    fun refreshShadeCatalog() {
        val repository = shadeRepository ?: return
        if (catalogRefreshJob?.isActive == true) return
        catalogRefreshJob = viewModelScope.launch {
            val result = repository.fetchCatalog()
            if (result != null) {
                // Per-category fallback, not a wholesale replace: a category the backend
                // doesn't (yet) have any shades for would otherwise get wiped to an empty list
                // on every successful refresh, silently breaking anything backend-less (e.g. a
                // brand-new local-only category) even though the network call "succeeded".
                _baseCatalog.value = EditorCategory.entries.associateWith { category ->
                    result[category]?.takeIf { it.isNotEmpty() } ?: editorShadesByCategory[category].orEmpty()
                }
            }
        }
    }

    /** Creates a user-defined shade for [category] from a hex color string, persists it locally
     *  (available offline, on every account tier - see CustomShadeStore), and makes it
     *  immediately selectable alongside the certified catalog. Returns false without changing
     *  anything if [hexColor] isn't a valid color or [name] is blank. */
    fun addCustomShade(category: EditorCategory, name: String, hexColor: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return false
        val normalizedHex = if (hexColor.startsWith("#")) hexColor else "#$hexColor"
        try {
            android.graphics.Color.parseColor(normalizedHex)
        } catch (e: IllegalArgumentException) {
            return false
        }

        val shade = ShadeItem(
            id = "custom-${UUID.randomUUID()}",
            name = trimmedName,
            hexColor = normalizedHex,
            category = category.toShadeCategory(),
            finish = com.mirror.app.domain.model.ShadeFinish.MATTE
        )
        customShadeStore?.add(category, shade)
        _customShades.value = _customShades.value + (category to (_customShades.value[category].orEmpty() + shade))
        return true
    }

    /** Renders the current selections onto the detected photo and stores the result as a
     *  SavedLook for this session - this is what "Save Formulation" actually does. No-ops if
     *  there's no detected face to render against. [name] is whatever the user typed in the
     *  save dialog; falls back to an auto-numbered name if left blank. */
    fun saveCurrentLook(name: String) {
        val state = _uiState.value as? UploadUiState.Detected ?: return
        val currentSelections = _selections.value
        val currentCatalog = shadeCatalog.value
        val currentLashStyle = _lashStyle.value
        viewModelScope.launch(Dispatchers.Default) {
            val faceCrop = cropToFace(state.photo, state.landmarks)
            val thumbnail = renderMakeupBitmap(
                photo = faceCrop.bitmap,
                landmarks = faceCrop.landmarks,
                foundation = resolveMakeupLayer(EditorCategory.FOUNDATION, currentSelections, currentCatalog),
                cheek = resolveMakeupLayer(EditorCategory.CHEEK, currentSelections, currentCatalog),
                lips = resolveMakeupLayer(EditorCategory.LIPS, currentSelections, currentCatalog),
                eyeshadow = resolveMakeupLayer(EditorCategory.EYES, currentSelections, currentCatalog),
                eyeliner = resolveMakeupLayer(EditorCategory.EYELINER, currentSelections, currentCatalog),
                eyeColor = resolveMakeupLayer(EditorCategory.EYE_COLOR, currentSelections, currentCatalog),
                lashStyle = currentLashStyle,
                brow = resolveMakeupLayer(EditorCategory.BROWS, currentSelections, currentCatalog),
                gele = resolveMakeupLayer(EditorCategory.GELE, currentSelections, currentCatalog)
            )
            val trimmedName = name.trim()
            val look = SavedLook(
                id = UUID.randomUUID().toString(),
                name = trimmedName.ifEmpty { "Look ${_savedLooks.value.size + 1}" },
                createdAtEpochMillis = System.currentTimeMillis(),
                thumbnail = thumbnail,
                sourcePhoto = state.photo,
                landmarks = state.landmarks,
                selections = currentSelections,
                lashStyle = currentLashStyle
            )
            _savedLooks.value = listOf(look) + _savedLooks.value
            lookStore?.save(look, shadesUsed(currentSelections, currentCatalog))
        }
    }

    /** Saves a look made outside the Studio (Showroom) into Recent Looks, in the same [SavedLook]
     *  form the Studio produces, so tapping it reopens it for editing. Showroom's shades (its AI
     *  picks, or bundled ids the live Studio catalog may not contain) are registered as local custom
     *  shades first, so the saved look can still resolve every shade it uses. */
    fun saveExternalLook(
        name: String,
        photo: Bitmap,
        landmarks: FaceLandmarkResult,
        selections: Map<EditorCategory, MakeupSelection>,
        sourceCatalog: Map<EditorCategory, List<ShadeItem>>
    ) {
        val remapped = selections.mapNotNull { (category, selection) ->
            val shadeId = selection.shadeId ?: return@mapNotNull null
            val shade = sourceCatalog[category]?.firstOrNull { it.id == shadeId } ?: return@mapNotNull null
            category to selection.copy(shadeId = ensureStudioShade(category, shade))
        }.toMap()
        if (remapped.isEmpty()) return
        // Read the maps directly: shadeCatalog is a stateIn() of these and may not have caught up yet.
        val catalog = EditorCategory.entries.associateWith { _baseCatalog.value[it].orEmpty() + _customShades.value[it].orEmpty() }
        viewModelScope.launch(Dispatchers.Default) {
            val faceCrop = cropToFace(photo, landmarks)
            val thumbnail = renderMakeupBitmap(
                photo = faceCrop.bitmap,
                landmarks = faceCrop.landmarks,
                foundation = resolveMakeupLayer(EditorCategory.FOUNDATION, remapped, catalog),
                cheek = resolveMakeupLayer(EditorCategory.CHEEK, remapped, catalog),
                lips = resolveMakeupLayer(EditorCategory.LIPS, remapped, catalog),
                brow = resolveMakeupLayer(EditorCategory.BROWS, remapped, catalog),
                eyeColor = resolveMakeupLayer(EditorCategory.EYE_COLOR, remapped, catalog)
            )
            val trimmedName = name.trim()
            val look = SavedLook(
                id = UUID.randomUUID().toString(),
                name = trimmedName.ifEmpty { "Look ${_savedLooks.value.size + 1}" },
                createdAtEpochMillis = System.currentTimeMillis(),
                thumbnail = thumbnail,
                sourcePhoto = photo,
                landmarks = landmarks,
                selections = remapped,
                lashStyle = null
            )
            _savedLooks.value = listOf(look) + _savedLooks.value
            lookStore?.save(look, shadesUsed(remapped, catalog))
        }
    }

    /** The id of a Studio-catalog shade matching [shade] (same name and color), creating a local
     *  custom shade for it if there isn't one. */
    private fun ensureStudioShade(category: EditorCategory, shade: ShadeItem): String {
        val existing = (_baseCatalog.value[category].orEmpty() + _customShades.value[category].orEmpty())
            .firstOrNull { it.name == shade.name && it.hexColor.equals(shade.hexColor, ignoreCase = true) }
        if (existing != null) return existing.id
        val custom = shade.copy(id = "custom-${UUID.randomUUID()}", category = category.toShadeCategory())
        customShadeStore?.add(category, custom)
        _customShades.value = _customShades.value + (category to (_customShades.value[category].orEmpty() + custom))
        return custom.id
    }

    /** Reopens a previously saved look in the editor - restores the source photo, landmarks,
     *  and every category's selection exactly as they were when it was saved, so tapping a
     *  Recent Look from Dashboard resumes editing instead of just showing a static picture. */
    fun loadLook(look: SavedLook) {
        _uiState.value = UploadUiState.Detected(
            photo = look.sourcePhoto,
            landmarks = look.landmarks,
            isSteepAngle = isSteepAngle(look.landmarks)
        )
        _selections.value = look.selections
        _lashStyle.value = look.lashStyle
    }

    /** Removes a saved look from Recent Looks for good - there's no on-device persistence to
     *  clean up, since SavedLook only ever lived in this in-memory list. */
    fun deleteLook(id: String) {
        _savedLooks.value = _savedLooks.value.filterNot { it.id == id }
        lookStore?.let { store -> viewModelScope.launch(Dispatchers.IO) { store.delete(id) } }
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
