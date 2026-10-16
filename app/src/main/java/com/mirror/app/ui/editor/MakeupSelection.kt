package com.mirror.app.ui.editor

import androidx.compose.ui.graphics.Color
import com.mirror.app.domain.model.ShadeFinish

/** What the user has picked for one color category: which shade, and how strongly to apply it.
 *  Lives outside EditorScreen (and is held by UploadViewModel, not local Compose state) so the
 *  same picks are visible from BeforeAfterScreen and Save Formulation, not just the editor. */
data class MakeupSelection(val shadeId: String? = null, val intensity: Float = 0.5f)

/** Resolves a category's current selection into a paintable [MakeupLayer], or null if nothing
 *  has been picked for it yet (or the stored hex color is somehow invalid). [catalog] defaults
 *  to the bundled offline set but callers with a live (possibly network-fetched) catalog should
 *  pass it explicitly, since a selected shade might only exist there. */
fun resolveMakeupLayer(
    category: EditorCategory,
    selections: Map<EditorCategory, MakeupSelection>,
    catalog: Map<EditorCategory, List<com.mirror.app.domain.model.ShadeItem>> = editorShadesByCategory
): MakeupLayer? {
    val selection = selections[category] ?: return null
    val shadeId = selection.shadeId ?: return null
    val shade = catalog[category]?.firstOrNull { it.id == shadeId } ?: return null
    val color = try {
        Color(android.graphics.Color.parseColor(shade.hexColor))
    } catch (e: IllegalArgumentException) {
        return null
    }
    return MakeupLayer(color, selection.intensity, shade.finish)
}
