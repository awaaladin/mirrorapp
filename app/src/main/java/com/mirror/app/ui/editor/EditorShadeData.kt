package com.mirror.app.ui.editor

import com.mirror.app.domain.model.ShadeCategory
import com.mirror.app.domain.model.ShadeFinish
import com.mirror.app.domain.model.ShadeItem

/** Editor tool categories. Every category except [LASHES] picks a color [ShadeItem] through the
 *  shared shade-swatch + intensity-slider UI; lash "color" isn't a real makeup concept, so
 *  Lashes instead picks a style (see LashStyle) through its own picker. */
enum class EditorCategory(val label: String) {
    LIPS("Lips"),
    FOUNDATION("Foundation"),
    EYES("Eyeshadow"),
    EYELINER("Eyeliner"),
    EYE_COLOR("Eye Color"),
    LASHES("Lashes"),
    CHEEK("Blush"),
    BROWS("Brows"),
    GELE("Gele / Wrap")
}

/** Maps an editor tool tab to the domain [ShadeCategory] used when constructing a new
 *  [ShadeItem] for it (e.g. a user-created custom shade). [EditorCategory.LASHES] has no color
 *  category (see class doc) and must never be passed here. */
fun EditorCategory.toShadeCategory(): ShadeCategory = when (this) {
    EditorCategory.LIPS -> ShadeCategory.LIPSTICK
    EditorCategory.FOUNDATION -> ShadeCategory.FOUNDATION
    EditorCategory.EYES -> ShadeCategory.EYESHADOW
    EditorCategory.EYELINER -> ShadeCategory.EYELINER
    EditorCategory.EYE_COLOR -> ShadeCategory.EYE_COLOR
    EditorCategory.CHEEK -> ShadeCategory.BLUSH
    EditorCategory.BROWS -> ShadeCategory.EYEBROW
    EditorCategory.GELE -> ShadeCategory.GELE
    EditorCategory.LASHES -> throw IllegalArgumentException("Lashes has no color shade category")
}

/**
 * Bundled offline-first shade catalogue - the fallback shown before/without a successful
 * mirrorcentral fetch (see ShadeRepository). Shade families and naming are grounded in real
 * makeup shade vocabulary for deep/Nigerian skin tones (brick red, oxblood, terracotta, warm
 * cocoa, etc.), with the foundation range extended up to fair tones so it also works for
 * non-Nigerian users, per the product's "Nigerian-focused, not Nigerian-only" positioning.
 */
val editorShadesByCategory: Map<EditorCategory, List<ShadeItem>> = mapOf(
    EditorCategory.LIPS to listOf(
        ShadeItem("lip-warm-mahogany", "Warm Mahogany", "#7B3B2E", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-toffee-nude", "Toffee Nude", "#A4623B", ShadeCategory.LIPSTICK, ShadeFinish.SATIN),
        ShadeItem("lip-rosewood", "Rosewood", "#65323A", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-clay-taupe", "Clay Taupe", "#8B5E52", ShadeCategory.LIPSTICK, ShadeFinish.SATIN),
        ShadeItem("lip-brick-red", "Brick Red", "#B33921", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-oxblood", "Oxblood", "#4A1420", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-cherry-red", "Cherry Red", "#9E1B32", ShadeCategory.LIPSTICK, ShadeFinish.GLOSSY),
        ShadeItem("lip-wine", "Wine", "#722F37", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-deep-plum-berry", "Deep Plum Berry", "#6E2142", ShadeCategory.LIPSTICK, ShadeFinish.SATIN),
        ShadeItem("lip-burgundy", "Burgundy", "#5C0923", ShadeCategory.LIPSTICK, ShadeFinish.MATTE),
        ShadeItem("lip-coral-gloss", "Coral Gloss", "#E2725B", ShadeCategory.LIPSTICK, ShadeFinish.GLOSSY),
        ShadeItem("lip-nude-gloss", "Nude Gloss", "#C98374", ShadeCategory.LIPSTICK, ShadeFinish.GLOSSY)
    ),
    EditorCategory.FOUNDATION to listOf(
        ShadeItem("fdn-porcelain-fair", "Porcelain Fair", "#F1D5C0", ShadeCategory.FOUNDATION, ShadeFinish.SATIN),
        ShadeItem("fdn-warm-beige", "Warm Beige", "#E0AC81", ShadeCategory.FOUNDATION, ShadeFinish.SATIN),
        ShadeItem("fdn-honey-tan", "Honey Tan", "#C68863", ShadeCategory.FOUNDATION, ShadeFinish.SATIN),
        ShadeItem("fdn-caramel", "Caramel", "#A96A43", ShadeCategory.FOUNDATION, ShadeFinish.SATIN),
        ShadeItem("fdn-golden-toffee", "Golden Toffee", "#8B5A34", ShadeCategory.FOUNDATION, ShadeFinish.MATTE),
        ShadeItem("fdn-deep-cinnamon", "Deep Cinnamon", "#6F4228", ShadeCategory.FOUNDATION, ShadeFinish.MATTE),
        ShadeItem("fdn-rich-cocoa", "Rich Cocoa", "#5A3A24", ShadeCategory.FOUNDATION, ShadeFinish.SATIN),
        ShadeItem("fdn-dark-chocolate", "Dark Chocolate", "#3E2415", ShadeCategory.FOUNDATION, ShadeFinish.MATTE),
        ShadeItem("fdn-deep-mystery-ebony", "Deep Mystery Ebony", "#2C160C", ShadeCategory.FOUNDATION, ShadeFinish.SATIN)
    ),
    EditorCategory.EYES to listOf(
        ShadeItem("eye-golden-bronze", "Golden Bronze", "#B8752E", ShadeCategory.EYESHADOW, ShadeFinish.SHIMMER),
        ShadeItem("eye-copper-shimmer", "Copper Shimmer", "#B36A34", ShadeCategory.EYESHADOW, ShadeFinish.SHIMMER),
        ShadeItem("eye-tangerine-pop", "Tangerine Pop", "#D9702E", ShadeCategory.EYESHADOW, ShadeFinish.SATIN),
        ShadeItem("eye-deep-plum-brown", "Deep Plum Brown", "#5A3A44", ShadeCategory.EYESHADOW, ShadeFinish.MATTE),
        ShadeItem("eye-charcoal-smoke", "Charcoal Smoke", "#3A3532", ShadeCategory.EYESHADOW, ShadeFinish.MATTE),
        ShadeItem("eye-jet-black-matte", "Jet Black Matte", "#1B1714", ShadeCategory.EYESHADOW, ShadeFinish.MATTE),
        ShadeItem("eye-champagne-gold", "Champagne Gold", "#D4AF6A", ShadeCategory.EYESHADOW, ShadeFinish.SHIMMER),
        ShadeItem("eye-espresso-brown", "Espresso Brown", "#4A3123", ShadeCategory.EYESHADOW, ShadeFinish.MATTE)
    ),
    EditorCategory.EYELINER to listOf(
        ShadeItem("liner-jet-black", "Jet Black", "#14100D", ShadeCategory.EYELINER, ShadeFinish.MATTE),
        ShadeItem("liner-espresso-brown", "Espresso Brown", "#3B2418", ShadeCategory.EYELINER, ShadeFinish.MATTE),
        ShadeItem("liner-deep-navy", "Deep Navy", "#1B2A4A", ShadeCategory.EYELINER, ShadeFinish.SATIN),
        ShadeItem("liner-bronze-shimmer", "Bronze Shimmer", "#8C5A2A", ShadeCategory.EYELINER, ShadeFinish.SHIMMER)
    ),
    EditorCategory.EYE_COLOR to listOf(
        ShadeItem("eyecolor-warm-brown", "Warm Brown", "#4E2E1B", ShadeCategory.EYE_COLOR, ShadeFinish.SATIN),
        ShadeItem("eyecolor-hazel", "Hazel", "#6B4423", ShadeCategory.EYE_COLOR, ShadeFinish.SATIN),
        ShadeItem("eyecolor-honey-amber", "Honey Amber", "#9A6324", ShadeCategory.EYE_COLOR, ShadeFinish.SHIMMER),
        ShadeItem("eyecolor-forest-green", "Forest Green", "#3A5F3A", ShadeCategory.EYE_COLOR, ShadeFinish.SATIN),
        ShadeItem("eyecolor-ocean-blue", "Ocean Blue", "#2E5C7A", ShadeCategory.EYE_COLOR, ShadeFinish.SATIN),
        ShadeItem("eyecolor-storm-gray", "Storm Gray", "#5B6066", ShadeCategory.EYE_COLOR, ShadeFinish.SATIN),
        ShadeItem("eyecolor-violet", "Violet", "#5B3A6E", ShadeCategory.EYE_COLOR, ShadeFinish.SHIMMER)
    ),
    EditorCategory.CHEEK to listOf(
        ShadeItem("cheek-rich-terracotta", "Rich Terracotta", "#C1552E", ShadeCategory.BLUSH, ShadeFinish.SATIN),
        ShadeItem("cheek-warm-brick", "Warm Brick", "#B33921", ShadeCategory.BLUSH, ShadeFinish.MATTE),
        ShadeItem("cheek-deep-berry", "Deep Berry", "#8E2A4E", ShadeCategory.BLUSH, ShadeFinish.SATIN),
        ShadeItem("cheek-burnt-sienna", "Burnt Sienna", "#A85327", ShadeCategory.BLUSH, ShadeFinish.MATTE),
        ShadeItem("cheek-vibrant-fuchsia", "Vibrant Fuchsia", "#C2226E", ShadeCategory.BLUSH, ShadeFinish.SHIMMER),
        ShadeItem("cheek-deep-coral", "Deep Coral", "#E2603E", ShadeCategory.BLUSH, ShadeFinish.SATIN)
    ),
    EditorCategory.BROWS to listOf(
        ShadeItem("brow-jet-black", "Jet Black", "#1C1611", ShadeCategory.EYEBROW, ShadeFinish.MATTE),
        ShadeItem("brow-espresso", "Espresso Brown", "#3B2418", ShadeCategory.EYEBROW, ShadeFinish.MATTE),
        ShadeItem("brow-chocolate", "Warm Chocolate", "#4E2F1D", ShadeCategory.EYEBROW, ShadeFinish.MATTE),
        ShadeItem("brow-auburn", "Soft Auburn", "#6B3A22", ShadeCategory.EYEBROW, ShadeFinish.SATIN),
        ShadeItem("brow-ash-taupe", "Ash Taupe", "#5C4A3E", ShadeCategory.EYEBROW, ShadeFinish.MATTE)
    ),
    EditorCategory.GELE to listOf(
        ShadeItem("gele-royal-gold", "Royal Gold Aso-Oke", "#C9971F", ShadeCategory.GELE, ShadeFinish.SHIMMER),
        ShadeItem("gele-coral-red", "Coral Red Damask", "#B8322A", ShadeCategory.GELE, ShadeFinish.SATIN),
        ShadeItem("gele-royal-blue", "Royal Blue Adire", "#1F3E7A", ShadeCategory.GELE, ShadeFinish.SATIN),
        ShadeItem("gele-emerald", "Emerald Ankara", "#1E5C45", ShadeCategory.GELE, ShadeFinish.SATIN),
        ShadeItem("gele-plum", "Deep Plum Velvet", "#5A2A4A", ShadeCategory.GELE, ShadeFinish.MATTE),
        ShadeItem("gele-ivory-lace", "Ivory Celebrant Lace", "#EDE3D3", ShadeCategory.GELE, ShadeFinish.SATIN)
    )
)
