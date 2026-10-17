package com.mirror.app.data.looks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mirror.app.domain.model.FaceLandmarkResult
import com.mirror.app.domain.model.LashStyle
import com.mirror.app.domain.model.NormalizedLandmark
import com.mirror.app.domain.model.ShadeCategory
import com.mirror.app.domain.model.ShadeFinish
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.editor.EditorCategory
import com.mirror.app.ui.editor.MakeupSelection
import com.mirror.app.ui.upload.SavedLook
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** A saved look read back from disk, plus the shades it uses so the caller can make sure each one
 *  still resolves (a shade id from the bundled or backend catalog may not exist any more). */
class LoadedLook(val look: SavedLook, val shades: Map<EditorCategory, ShadeItem>)

/**
 * Keeps saved looks on the phone so they survive closing the app: per look, the photo (JPEG,
 * capped in size), the thumbnail (JPEG) and a small JSON file holding the landmarks, the applied
 * shades and their strengths. Nothing here touches the network. The JSON is written last and
 * atomically, so a look interrupted mid-save is simply ignored on the next launch.
 */
class LookStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "saved_looks").also { it.mkdirs() }

    /** Writes [look]; [shades] are the shades its selections use (stored so they can be restored). */
    fun save(look: SavedLook, shades: Map<EditorCategory, ShadeItem>) {
        try {
            writeJpeg(File(dir, "${look.id}_photo.jpg"), look.sourcePhoto.downscaledTo(MAX_PHOTO_SIDE), 92)
            writeJpeg(File(dir, "${look.id}_thumb.jpg"), look.thumbnail, 88)
            val stored = buildStoredLook(
                id = look.id,
                name = look.name,
                createdAt = look.createdAtEpochMillis,
                landmarks = look.landmarks,
                selections = look.selections,
                lashStyle = look.lashStyle,
                shades = shades
            )
            val tmp = File(dir, "${look.id}.json.tmp")
            tmp.writeText(Json.encodeToString(stored))
            if (!tmp.renameTo(File(dir, "${look.id}.json"))) tmp.delete()
            prune()
        } catch (e: IOException) {
            // A look that couldn't be written just won't survive a restart; it stays in memory.
        }
    }

    /** Newest first, at most [MAX_LOOKS]. Looks with missing or unreadable files are skipped. */
    fun loadAll(): List<LoadedLook> {
        val metas = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { f -> decodeStoredLook(runCatching { f.readText() }.getOrNull() ?: return@mapNotNull null) }
            .sortedByDescending { it.createdAt }
            .take(MAX_LOOKS)
        return metas.mapNotNull { meta ->
            val photo = BitmapFactory.decodeFile(File(dir, "${meta.id}_photo.jpg").path) ?: return@mapNotNull null
            val thumb = BitmapFactory.decodeFile(File(dir, "${meta.id}_thumb.jpg").path) ?: photo
            LoadedLook(
                look = SavedLook(
                    id = meta.id,
                    name = meta.name,
                    createdAtEpochMillis = meta.createdAt,
                    thumbnail = thumb,
                    sourcePhoto = photo,
                    landmarks = meta.toLandmarks(),
                    selections = meta.toSelections(),
                    lashStyle = meta.lashStyle?.let { n -> LashStyle.entries.firstOrNull { it.name == n } }
                ),
                shades = meta.toShades()
            )
        }
    }

    fun delete(id: String) {
        listOf("${id}.json", "${id}_photo.jpg", "${id}_thumb.jpg").forEach { File(dir, it).delete() }
    }

    /** Drops the oldest looks beyond [MAX_LOOKS] so storage can't grow without bound. */
    private fun prune() {
        val ids = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { it.name.removeSuffix(".json") }
        ids.drop(MAX_LOOKS).forEach(::delete)
    }

    private fun writeJpeg(file: File, bitmap: Bitmap, quality: Int) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    private fun Bitmap.downscaledTo(maxSide: Int): Bitmap {
        val scale = maxSide.toFloat() / maxOf(width, height)
        if (scale >= 1f) return this
        return Bitmap.createScaledBitmap(this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
    }

    private companion object {
        const val MAX_LOOKS = 30
        const val MAX_PHOTO_SIDE = 1600
    }
}

// ---- Serialised form (plain data, so it round-trips in JVM unit tests) ----

@Serializable
internal data class StoredShade(
    val id: String,
    val name: String,
    val hexColor: String,
    val shadeCategory: String,
    val finish: String
)

@Serializable
internal data class StoredSelection(
    val category: String,
    val shadeId: String?,
    val intensity: Float,
    val shade: StoredShade?
)

@Serializable
internal data class StoredLook(
    val id: String,
    val name: String,
    val createdAt: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    /** x,y,z of every landmark, flattened. */
    val landmarks: List<Float>,
    val selections: List<StoredSelection>,
    val lashStyle: String?
)

internal fun buildStoredLook(
    id: String,
    name: String,
    createdAt: Long,
    landmarks: FaceLandmarkResult,
    selections: Map<EditorCategory, MakeupSelection>,
    lashStyle: LashStyle?,
    shades: Map<EditorCategory, ShadeItem>
) = StoredLook(
    id = id,
    name = name,
    createdAt = createdAt,
    imageWidth = landmarks.imageWidth,
    imageHeight = landmarks.imageHeight,
    landmarks = landmarks.points.flatMap { listOf(it.x, it.y, it.z) },
    selections = selections.map { (category, selection) ->
        StoredSelection(
            category = category.name,
            shadeId = selection.shadeId,
            intensity = selection.intensity,
            shade = shades[category]?.let { StoredShade(it.id, it.name, it.hexColor, it.category.name, it.finish.name) }
        )
    },
    lashStyle = lashStyle?.name
)

internal fun decodeStoredLook(raw: String): StoredLook? = try {
    Json { ignoreUnknownKeys = true }.decodeFromString<StoredLook>(raw)
} catch (e: Exception) {
    null
}

internal fun StoredLook.toLandmarks() = FaceLandmarkResult(
    points = landmarks.chunked(3).filter { it.size == 3 }.map { NormalizedLandmark(it[0], it[1], it[2]) },
    imageWidth = imageWidth,
    imageHeight = imageHeight
)

internal fun StoredLook.toSelections(): Map<EditorCategory, MakeupSelection> =
    selections.mapNotNull { s ->
        val category = EditorCategory.entries.firstOrNull { it.name == s.category } ?: return@mapNotNull null
        category to MakeupSelection(shadeId = s.shadeId, intensity = s.intensity)
    }.toMap()

internal fun StoredLook.toShades(): Map<EditorCategory, ShadeItem> =
    selections.mapNotNull { s ->
        val category = EditorCategory.entries.firstOrNull { it.name == s.category } ?: return@mapNotNull null
        val shade = s.shade ?: return@mapNotNull null
        val shadeCategory = ShadeCategory.entries.firstOrNull { it.name == shade.shadeCategory } ?: return@mapNotNull null
        val finish = ShadeFinish.entries.firstOrNull { it.name == shade.finish } ?: ShadeFinish.MATTE
        category to ShadeItem(shade.id, shade.name, shade.hexColor, shadeCategory, finish)
    }.toMap()
