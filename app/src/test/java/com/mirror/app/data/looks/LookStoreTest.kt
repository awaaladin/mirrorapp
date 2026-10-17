package com.mirror.app.data.looks

import com.mirror.app.domain.model.FaceLandmarkResult
import com.mirror.app.domain.model.LashStyle
import com.mirror.app.domain.model.NormalizedLandmark
import com.mirror.app.domain.model.ShadeCategory
import com.mirror.app.domain.model.ShadeFinish
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.editor.EditorCategory
import com.mirror.app.ui.editor.MakeupSelection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LookStoreTest {

    private val landmarks = FaceLandmarkResult(
        points = List(478) { NormalizedLandmark(it / 1000f, 1f - it / 1000f, 0.01f * it) },
        imageWidth = 640,
        imageHeight = 800
    )

    @Test
    fun savedLookRoundTripsThroughJson() {
        val shade = ShadeItem("ai-0-#5b3826", "Warm Cocoa", "#5B3826", ShadeCategory.FOUNDATION, ShadeFinish.SATIN)
        val stored = buildStoredLook(
            id = "look-1",
            name = "Bridal Glam",
            createdAt = 1_760_000_000_000L,
            landmarks = landmarks,
            selections = mapOf(
                EditorCategory.FOUNDATION to MakeupSelection("ai-0-#5b3826", 0.7f),
                EditorCategory.LIPS to MakeupSelection(null, 0.5f)
            ),
            lashStyle = LashStyle.WISPY,
            shades = mapOf(EditorCategory.FOUNDATION to shade)
        )

        val back = decodeStoredLook(Json.encodeToString(stored))!!

        assertEquals("Bridal Glam", back.name)
        assertEquals(1_760_000_000_000L, back.createdAt)
        assertEquals(landmarks, back.toLandmarks())
        assertEquals(
            mapOf(
                EditorCategory.FOUNDATION to MakeupSelection("ai-0-#5b3826", 0.7f),
                EditorCategory.LIPS to MakeupSelection(null, 0.5f)
            ),
            back.toSelections()
        )
        assertEquals(mapOf(EditorCategory.FOUNDATION to shade), back.toShades())
        assertEquals("WISPY", back.lashStyle)
    }

    @Test
    fun corruptOrUnknownDataIsIgnoredNotFatal() {
        assertNull(decodeStoredLook("{not json"))
        assertNull(decodeStoredLook(""))
    }

    @Test
    fun unknownCategoriesAreDroppedWhenReading() {
        val raw = """{"id":"x","name":"n","createdAt":1,"imageWidth":1,"imageHeight":1,"landmarks":[],
            "selections":[{"category":"FUTURE_THING","shadeId":"a","intensity":0.5,"shade":null}],"lashStyle":null,"extra":1}"""
        val look = decodeStoredLook(raw)!!
        assertEquals(emptyMap<EditorCategory, MakeupSelection>(), look.toSelections())
    }
}
