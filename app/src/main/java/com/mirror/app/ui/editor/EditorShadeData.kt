package com.mirror.app.ui.editor

import com.mirror.app.domain.model.ShadeCategory
import com.mirror.app.domain.model.ShadeFinish
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.domain.model.EyebrowStyle
import com.mirror.app.domain.model.GeleStyle

/** Editor tool categories. Lips/Foundation/Eyes/Cheek pick a color [ShadeItem]; Brows/Gele
 *  pick a named style from the domain enums instead, since those aren't pigment colors. */
enum class EditorCategory(val label: String) {
    LIPS("Lips"),
    FOUNDATION("Foundation"),
    EYES("Eyes"),
    CHEEK("Cheek"),
    BROWS("Brows"),
    GELE("Gele / Wrap")
}

enum class ApplicationFinish(val label: String) {
    VELVET_MATTE("Velvet Matte"),
    SATIN_GLOSS("Satin Gloss"),
    SHEER_BALM("Sheer Balm")
}

/**
 * Placeholder shade catalogue standing in for the real ~15-20-shade-per-category asset-backed
 * library planned for a later phase. Structured as a plain map now so swapping in a real
 * ShadeRepository later only touches this file, not the Editor UI.
 */
val editorShadesByCategory: Map<EditorCategory, List<ShadeItem>> = mapOf(
    EditorCategory.LIPS to listOf(
        ShadeItem("lip-agbani-04", "Agbani 04 · Terracotta Earth", "#994422", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-zaria-02", "Zaria 02 · Deep Cocoa Plum", "#FFDBCF", ShadeCategory.LIPSTICK, ShadeFinish.SATIN),
        ShadeItem("lip-enugu-07", "Enugu 07 · Spiced Ochre", "#7B5804", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-ibadan-01", "Ibadan 01 · Warm Raw Umber", "#55433C", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-calabar-09", "Calabar 09 · Burnished Coral", "#B85C38", ShadeCategory.LIPSTICK, ShadeFinish.GLOSSY)
    ),
    EditorCategory.FOUNDATION to listOf(
        ShadeItem("fdn-oyin-08", "Oyin 08 · Warm Sand", "#D9A876", ShadeCategory.FOUNDATION, ShadeFinish.SATIN),
        ShadeItem("fdn-oyin-12", "Oyin 12 · Rich Golden", "#B87F4E", ShadeCategory.FOUNDATION, ShadeFinish.SATIN),
        ShadeItem("fdn-oyin-14", "Oyin 14 · Deep Olive Gold", "#8C5E36", ShadeCategory.FOUNDATION, ShadeFinish.MATTE),
        ShadeItem("fdn-lekki-18", "Lekki 18 · Espresso Neutral", "#5A3826", ShadeCategory.FOUNDATION, ShadeFinish.MATTE),
        ShadeItem("fdn-lekki-20", "Lekki 20 · Deep Ebony", "#3B2219", ShadeCategory.FOUNDATION, ShadeFinish.SATIN)
    ),
    EditorCategory.EYES to listOf(
        ShadeItem("eye-sahara-dusk", "Sahara Dusk", "#A47C2B", ShadeCategory.EYESHADOW, ShadeFinish.SHIMMER),
        ShadeItem("eye-nile-bronze", "Nile Bronze", "#8C4F2E", ShadeCategory.EYESHADOW, ShadeFinish.SHIMMER),
        ShadeItem("eye-charcoal-smoke", "Charcoal Smoke", "#34302B", ShadeCategory.EYESHADOW, ShadeFinish.MATTE),
        ShadeItem("eye-copper-glow", "Copper Glow", "#B85C38", ShadeCategory.EYESHADOW, ShadeFinish.SHIMMER)
    ),
    EditorCategory.CHEEK to listOf(
        ShadeItem("cheek-coral-clay", "Coral Clay", "#B85C38", ShadeCategory.BLUSH, ShadeFinish.SATIN),
        ShadeItem("cheek-terra-rose", "Terra Rose", "#994422", ShadeCategory.BLUSH, ShadeFinish.MATTE),
        ShadeItem("cheek-golden-lustre", "Golden Lustre 18K", "#EFBF67", ShadeCategory.BLUSH, ShadeFinish.SHIMMER)
    )
)

val editorEyebrowStyles: List<EyebrowStyle> = EyebrowStyle.entries
val editorGeleStyles: List<GeleStyle> = GeleStyle.entries
