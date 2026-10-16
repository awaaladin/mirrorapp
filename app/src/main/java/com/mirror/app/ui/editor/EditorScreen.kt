package com.mirror.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mirror.app.domain.model.LashStyle
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.theme.MirrorColorTokens
import com.mirror.app.ui.upload.UploadErrorMessage
import com.mirror.app.ui.upload.UploadUiState
import com.mirror.app.ui.upload.UploadViewModel
import kotlinx.coroutines.delay

// Tabs hidden for now (renderers kept intact): Gele, Eyeliner, and - per tester feedback that
// the simulation looked off - Eyeshadow and Lashes.
private val hiddenEditorCategories = setOf(
    EditorCategory.GELE, EditorCategory.EYELINER, EditorCategory.EYES, EditorCategory.LASHES
)

@Composable
fun EditorScreen(
    viewModel: UploadViewModel,
    onBack: () -> Unit,
    onSplitDiff: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val shadeCatalog by viewModel.shadeCatalog.collectAsState()
    val lashStyle by viewModel.lashStyle.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    // Refreshes once per time the editor is opened - mirrorcentral's shade catalog can grow
    // over time without an app update, so this is what makes newly-added shades show up here.
    // Silently keeps the bundled offline set if there's no network.
    LaunchedEffect(Unit) { viewModel.refreshShadeCatalog() }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onPhotoPicked(it, context.contentResolver) } }

    fun launchPicker() {
        pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var category by remember { mutableStateOf(EditorCategory.LIPS) }
    var searchQuery by remember(category) { mutableStateOf("") }
    var showAddShadeDialog by remember { mutableStateOf(false) }
    // Fraction of the resizable region given to the live preview vs. the shade panel below it -
    // dragged via the handle between them (hold and swipe up/down).
    var previewWeight by remember { mutableFloatStateOf(0.5f) }

    val categoryShades = shadeCatalog[category].orEmpty()
    val activeShades = if (searchQuery.isBlank()) {
        categoryShades
    } else {
        categoryShades.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val currentSelection = selections[category] ?: MakeupSelection()
    val activeShade = categoryShades.firstOrNull { it.id == currentSelection.shadeId }

    fun resolvedLayer(cat: EditorCategory): MakeupLayer? = resolveMakeupLayer(cat, selections, shadeCatalog)

    if (showAddShadeDialog) {
        AddCustomShadeDialog(
            initialCategory = category,
            onDismiss = { showAddShadeDialog = false },
            onCreate = { cat, name, hex -> viewModel.addCustomShade(cat, name, hex) }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AtelierControlBar(onBack = onBack)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val totalHeightPx = with(density) { maxHeight.toPx() }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(previewWeight)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    when (val state = uiState) {
                        is UploadUiState.Idle -> UploadPrompt(onUpload = ::launchPicker)
                        is UploadUiState.Detecting -> DetailingState()
                        is UploadUiState.Detected -> {
                            // Cropped tight to the face so the canvas shows just the person, not
                            // the original photo's background - recomputed only when the source
                            // photo changes.
                            val faceCrop = remember(state.photo) { cropToFace(state.photo, state.landmarks) }

                            FaceCanvas(
                                photo = faceCrop.bitmap,
                                landmarks = faceCrop.landmarks,
                                foundation = resolvedLayer(EditorCategory.FOUNDATION),
                                cheek = resolvedLayer(EditorCategory.CHEEK),
                                lips = resolvedLayer(EditorCategory.LIPS),
                                eyeshadow = resolvedLayer(EditorCategory.EYES),
                                eyeliner = resolvedLayer(EditorCategory.EYELINER),
                                eyeColor = resolvedLayer(EditorCategory.EYE_COLOR),
                                lashStyle = lashStyle,
                                brow = resolvedLayer(EditorCategory.BROWS),
                                gele = resolvedLayer(EditorCategory.GELE),
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
                            if (state.isSteepAngle) {
                                SteepAngleBanner(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                            }
                        }
                        is UploadUiState.Error -> EditorErrorState(message = state.message, onRetry = ::launchPicker)
                    }
                }

                ResizeHandle(onDragDeltaPx = { deltaPx ->
                    if (totalHeightPx > 0f) {
                        previewWeight = (previewWeight + deltaPx / totalHeightPx).coerceIn(0.22f, 0.78f)
                    }
                })

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - previewWeight)
                        .background(MaterialTheme.colorScheme.inverseSurface),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Gele is disabled for now - the procedural head-wrap shape isn't
                            // ready yet. Eyeliner is disabled too - it isn't rendering at all on
                            // device; commented out at the enum level would ripple through
                            // resolveMakeupLayer/FaceCanvas, so both are filtered here instead.
                            EditorCategory.entries.filter { it !in hiddenEditorCategories }.forEach { cat ->
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

                    if (category == EditorCategory.LASHES) {
                        item {
                            LashStylePicker(
                                selected = lashStyle,
                                onSelect = { viewModel.selectLashStyle(it) },
                                onClear = { viewModel.clearLashStyle() }
                            )
                        }
                    } else {
                        item {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "SHADE SPECTRUM",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            activeShade?.name?.uppercase().orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MirrorColorTokens.PrimaryFixedDim
                                        )
                                        if (activeShade != null) {
                                            TextButton(onClick = { viewModel.clearShade(category) }) {
                                                Text("REMOVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    placeholder = { Text("Search ${category.label.lowercase()} shades", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.06f),
                                        focusedContainerColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.1f),
                                        unfocusedTextColor = MaterialTheme.colorScheme.inverseOnSurface,
                                        focusedTextColor = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    activeShades.forEach { shade ->
                                        ShadeSwatch(
                                            shade = shade,
                                            selected = shade.id == activeShade?.id,
                                            onClick = {
                                                if (shade.id == activeShade?.id) viewModel.clearShade(category)
                                                else viewModel.selectShade(category, shade.id)
                                            }
                                        )
                                    }
                                    AddShadeTile(onClick = { showAddShadeDialog = true })
                                }
                                if (activeShades.isEmpty() && searchQuery.isNotBlank()) {
                                    Text(
                                        "No shades match \"$searchQuery\".",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
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
                                    onValueChange = { viewModel.setIntensity(category, it) },
                                    valueRange = 0.1f..1f,
                                    // Active and inactive track need visibly different colors -
                                    // they used to both resolve to roughly the same warm tone
                                    // against this panel's background, so the fill level (and
                                    // therefore whether dragging did anything) was invisible.
                                    colors = SliderDefaults.colors(
                                        thumbColor = MirrorColorTokens.SecondaryFixed,
                                        activeTrackColor = MirrorColorTokens.SecondaryFixed,
                                        inactiveTrackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f)
                                    )
                                )
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
                                        if (activeShade != null) {
                                            append(" · ")
                                            append(activeShade.finish.name)
                                        }
                                        append(" · ${(currentSelection.intensity * 100).toInt()}% INTENSITY")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MirrorColorTokens.PrimaryFixed,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    item {
                        var justSaved by remember { mutableStateOf(false) }
                        var showSaveDialog by remember { mutableStateOf(false) }
                        var saveName by remember { mutableStateOf("") }
                        LaunchedEffect(justSaved) { if (justSaved) { delay(1600); justSaved = false } }

                        if (showSaveDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showSaveDialog = false },
                                title = { Text("Name this look") },
                                text = {
                                    OutlinedTextField(
                                        value = saveName,
                                        onValueChange = { saveName = it },
                                        placeholder = { Text("e.g. Bridal Glam") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.saveCurrentLook(saveName)
                                        showSaveDialog = false
                                        saveName = ""
                                        justSaved = true
                                    }) { Text("SAVE") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSaveDialog = false }) { Text("CANCEL") }
                                }
                            )
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            enabled = uiState is UploadUiState.Detected,
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
    }
}

@Composable
private fun ResizeHandle(onDragDeltaPx: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDeltaPx(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.4f), RoundedCornerShape(50))
        )
    }
}

@Composable
private fun LashStylePicker(
    selected: LashStyle?,
    onSelect: (LashStyle) -> Unit,
    onClear: () -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LASH STYLE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
            )
            if (selected != null) {
                TextButton(onClick = onClear) {
                    Text("REMOVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LashStyle.entries.forEach { style ->
                val isSelected = selected == style
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.08f),
                            MaterialTheme.shapes.small
                        )
                        .clickable { if (isSelected) onClear() else onSelect(style) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        style.name.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                "Detailing your photo…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "Profiling facial geometry for precise shade placement",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AtelierControlBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .statusBarsPadding()
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

/** Non-blocking nudge shown over the preview when [isSteepAngle] flags the photo as close to
 *  profile - the triangle-mesh renderer still draws something reasonable, but can't invent skin
 *  that's foreshortened out of view, so a more front-facing photo will look noticeably better. */
@Composable
private fun SteepAngleBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.size(14.dp)
        )
        Text(
            "For the most accurate look, try a more front-facing photo",
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

@Composable
private fun AddShadeTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Create custom shade",
                tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
