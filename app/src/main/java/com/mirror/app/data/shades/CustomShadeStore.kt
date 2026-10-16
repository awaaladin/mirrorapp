package com.mirror.app.data.shades

import android.content.Context
import com.mirror.app.domain.model.ShadeCategory
import com.mirror.app.domain.model.ShadeFinish
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.editor.EditorCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredCustomShade(
    val id: String,
    val name: String,
    val hexColor: String,
    val shadeCategory: String,
    val editorCategory: String
)

/**
 * Persists user-created custom shades locally on-device (SharedPreferences + JSON) rather than
 * on mirrorcentral - custom shades are personal, available to every account tier (matching the
 * free/offline-capable design of the rest of the app), and don't belong in the admin-curated
 * "Certified Shade Library" table, so they never touch the network.
 */
class CustomShadeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mirror_custom_shades", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): Map<EditorCategory, List<ShadeItem>> =
        readStored().mapNotNull { it.toDomainOrNull() }.groupBy({ it.first }, { it.second })

    fun add(editorCategory: EditorCategory, shade: ShadeItem) {
        val updated = readStored() + StoredCustomShade(
            id = shade.id,
            name = shade.name,
            hexColor = shade.hexColor,
            shadeCategory = shade.category.name,
            editorCategory = editorCategory.name
        )
        prefs.edit().putString(KEY_SHADES, json.encodeToString(updated)).apply()
    }

    private fun readStored(): List<StoredCustomShade> {
        val raw = prefs.getString(KEY_SHADES, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun StoredCustomShade.toDomainOrNull(): Pair<EditorCategory, ShadeItem>? {
        val ec = EditorCategory.entries.firstOrNull { it.name == editorCategory } ?: return null
        val sc = ShadeCategory.entries.firstOrNull { it.name == shadeCategory } ?: return null
        return ec to ShadeItem(id = id, name = name, hexColor = hexColor, category = sc, finish = ShadeFinish.MATTE)
    }

    private companion object {
        const val KEY_SHADES = "custom_shades_json"
    }
}
