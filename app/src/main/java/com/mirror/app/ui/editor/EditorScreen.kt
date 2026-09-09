package com.mirror.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.theme.MirrorColorTokens
import com.mirror.app.ui.upload.UploadErrorMessage
import com.mirror.app.ui.upload.UploadUiState
import com.mirror.app.ui.upload.UploadViewModel
import kotlinx.coroutines.delay

/** What the user has picked for one color category: which shade, and how strongly to apply it. */
private data class CategorySelection(val shadeId: String? = null, val intensity: Float = 0.85f)

@Composable
fun EditorScreen(
    viewModel: UploadViewModel,
    onBack: () -> Unit,
    onSplitDiff: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onPhotoPicked(it, context.contentResolver) } }

    fun launchPicker() {
        pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var category by remember { mutableStateOf(EditorCategory.LIPS) }
    var finish by remember { mutableStateOf(ApplicationFinish.VELVET_MATTE) }
    // Keyed per category so switching tabs never loses what you picked elsewhere, and so
    // lips/foundation/cheek can all render on the face at the same time.
    var selections by remember { mutableStateOf(mapOf<EditorCategory, CategorySelection>()) }

    val activeShades = editorShadesByCategory[category].orEmpty()
    val currentSelection = selections[category] ?: CategorySelection()
    val activeShade = activeShades.firstOrNull { it.id == currentSelection.shadeId }

    fun selectShade(shadeId: String) {
        selections = selections + (category to currentSelection.copy(shadeId = shadeId))
    }

    fun setIntensity(value: Float) {
        selections = selections + (category to currentSelection.copy(intensity = value))
    }

    fun resolvedLayer(cat: EditorCategory): MakeupLayer? {
        val selection = selections[cat] ?: return null
        val shadeId = selection.shadeId ?: return null
        val shade = editorShadesByCategory[cat]?.firstOrNull { it.id == shadeId } ?: return null
        val color = try {
            Color(android.graphics.Color.parseColor(shade.hexColor))
        } catch (e: IllegalArgumentException) {
            return null
        }
        return MakeupLayer(color, selection.intensity)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AtelierControlBar(onBack = onBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            when (val state = uiState) {
                is UploadUiState.Idle -> UploadPrompt(onUpload = ::launchPicker)
                is UploadUiState.Detecting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UploadUiState.Detected -> {
                    // Cropped tight to the face so the canvas shows just the person, not the
                    // original photo's background - recomputed only when the source photo changes.
                    val faceCrop = remember(state.photo) { cropToFace(state.photo, state.landmarks) }

                    FaceCanvas(
                        photo = faceCrop.bitmap,
                        landmarks = faceCrop.landmarks,
                        foundation = resolvedLayer(EditorCategory.FOUNDATION),
                        cheek = resolvedLayer(EditorCategory.CHEEK),
                        lips = resolvedLayer(EditorCategory.LIPS),
                        modifier = Modifier.fillMaxSize()
                    )
                    SpectralBadge(
                        landmarkCount = state.landmarks.points.size,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    )
                    IconButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    ChromeChipButton(
                        icon = Icons.Filled.Compare,
                        label = "Split Diff",
                        onClick = onSplitDiff,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    )
                }
                is UploadUiState.Error -> EditorErrorState(message = state.message, onRetry = ::launchPicker)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.inverseSurface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    EditorCategory.entries.forEach { cat ->
                        val selected = category == cat
                        Text(
                            cat.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clickable { category = cat }
                                .padding(bottom = 4.dp)
                        )
                    }
                }
            }

            when (category) {
                EditorCategory.BROWS -> item {
                    StyleChipRow(labels = editorEyebrowStyles.map { it.name.replace('_', ' ') })
                }
                EditorCategory.GELE -> item {
                    StyleChipRow(labels = editorGeleStyles.map { it.name.replace('_', ' ') })
                }
                else -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.06f), MaterialTheme.shapes.small)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ApplicationFinish.entries.forEach { option ->
                                val selected = finish == option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.15f) else Color.Transparent,
                                            MaterialTheme.shapes.small
                                        )
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TextButton(onClick = { finish = option }) {
                                        Text(
                                            option.label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.inverseOnSurface
                                            else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "SHADE SPECTRUM",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    activeShade?.name?.uppercase().orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MirrorColorTokens.PrimaryFixedDim
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeShades.forEach { shade ->
                                    ShadeSwatch(
                                        shade = shade,
                                        selected = shade.id == activeShade?.id,
                                        onClick = { selectShade(shade.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.06f), MaterialTheme.shapes.small)
                        .padding(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "PIGMENT SATURATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            "INTENSITY ${(currentSelection.intensity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MirrorColorTokens.SecondaryFixed
                        )
                    }
                    Slider(
                        value = currentSelection.intensity,
                        onValueChange = { setIntensity(it) },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MirrorColorTokens.SecondaryFixed,
                            activeTrackColor = MirrorColorTokens.SecondaryFixed
                        )
                    )
                    if (category == EditorCategory.BROWS || category == EditorCategory.GELE) {
                        Text(
                            "Style preview rendering is coming in a later update - selection is saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        .padding(12.dp)
                ) {
                    Text(
                        "CURRENT SELECTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        buildString {
                            append(activeShade?.name?.uppercase() ?: "NONE SELECTED")
                            append(" · ")
                            append(finish.label.uppercase())
                            append(" · ${(currentSelection.intensity * 100).toInt()}% INTENSITY")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MirrorColorTokens.PrimaryFixed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item {
                var justSaved by remember { mutableStateOf(false) }
                LaunchedEffect(justSaved) { if (justSaved) { delay(1600); justSaved = false } }
                Button(
                    onClick = { justSaved = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        if (justSaved) "SAVED FOR THIS SESSION" else "SAVE FORMULATION",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AtelierControlBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    "NEW SESSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MirrorColorTokens.PrimaryFixedDim
                )
                Text(
                    "New Look",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

@Composable
private fun UploadPrompt(onUpload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(40.dp)
        )
        Text(
            "Upload a portrait to begin",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "On-device face detection runs fully offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Button(onClick = onUpload) {
            Text("CHOOSE A PHOTO", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EditorErrorState(message: UploadErrorMessage, onRetry: () -> Unit) {
    val text = when (message) {
        UploadErrorMessage.NO_FACE_DETECTED -> "We couldn't find a face in this photo. Try a clearer, front-facing photo with good lighting."
        UploadErrorMessage.MULTIPLE_FACES_DETECTED -> "This photo has more than one face. Mirror works best with a single face per photo for now."
        UploadErrorMessage.IMAGE_LOAD_FAILED -> "We couldn't open that image. Please try a different photo."
        UploadErrorMessage.LANDMARK_DETECTION_FAILED -> "Something went wrong while analyzing this photo. Please try again."
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) { Text("TRY AGAIN", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun SpectralBadge(landmarkCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
        Text(
            "$landmarkCount FACE LANDMARK POINTS TRACKED · ON-DEVICE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun ChromeChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp)
        )
    }
}

@Composable
private fun StyleChipRow(labels: List<String>) {
    var selectedIndex by remember { mutableStateOf(0) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.08f),
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                TextButton(onClick = { selectedIndex = index }, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ShadeSwatch(shade: ShadeItem, selected: Boolean, onClick: () -> Unit) {
    val color = remember(shade.hexColor) {
        try {
            Color(android.graphics.Color.parseColor(shade.hexColor))
        } catch (e: IllegalArgumentException) {
            Color.Gray
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
