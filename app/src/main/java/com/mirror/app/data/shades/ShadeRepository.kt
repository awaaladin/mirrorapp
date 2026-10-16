package com.mirror.app.data.shades

import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.data.network.ShadeDto
import com.mirror.app.domain.model.ShadeCategory
import com.mirror.app.domain.model.ShadeFinish
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.editor.EditorCategory
import java.io.IOException

/**
 * Fetches the live shade catalog from mirrorcentral so shades aren't limited to the small
 * bundled set baked into the app - new shades added on the backend show up here without an
 * app update, and [query] lets the editor search across the whole catalog. Tries [localApi]
 * first then [deployedApi], same fallback pattern as auth; returns null (rather than throwing)
 * on total failure so callers can just keep showing whatever catalog they already had - most
 * likely the bundled offline default - instead of clearing the screen.
 */
class ShadeRepository(
    private val localApi: MirrorBackendApi,
    private val deployedApi: MirrorBackendApi
) {
    suspend fun fetchCatalog(query: String? = null): Map<EditorCategory, List<ShadeItem>>? {
        val dtos = fetchDtos(query) ?: return null
        val grouped = mutableMapOf<EditorCategory, MutableList<ShadeItem>>()
        for (dto in dtos) {
            val (editorCategory, shade) = dto.toDomainOrNull() ?: continue
            grouped.getOrPut(editorCategory) { mutableListOf() }.add(shade)
        }
        return EditorCategory.entries.associateWith { grouped[it].orEmpty() }
    }

    private suspend fun fetchDtos(query: String?): List<ShadeDto>? {
        val response = try {
            localApi.listShades(query = query)
        } catch (e: IOException) {
            try {
                deployedApi.listShades(query = query)
            } catch (e2: IOException) {
                return null
            }
        }
        return if (response.isSuccessful) response.body() else null
    }
}

/** "powder" has no editor tool yet, so DTOs in that backend category are dropped here. */
private fun ShadeDto.toDomainOrNull(): Pair<EditorCategory, ShadeItem>? {
    val editorCategory = when (category) {
        "lipstick" -> EditorCategory.LIPS
        "foundation" -> EditorCategory.FOUNDATION
        "eyeshadow" -> EditorCategory.EYES
        "blush" -> EditorCategory.CHEEK
        "eyebrow" -> EditorCategory.BROWS
        "gele" -> EditorCategory.GELE
        "eyeliner" -> EditorCategory.EYELINER
        "eye_color" -> EditorCategory.EYE_COLOR
        else -> null
    } ?: return null
    val shadeCategory = ShadeCategory.entries.firstOrNull { it.name.equals(category, ignoreCase = true) } ?: return null
    val shadeFinish = ShadeFinish.entries.firstOrNull { it.name.equals(finish, ignoreCase = true) } ?: ShadeFinish.MATTE
    return editorCategory to ShadeItem(id = id, name = name, hexColor = hexColor, category = shadeCategory, finish = shadeFinish)
}
