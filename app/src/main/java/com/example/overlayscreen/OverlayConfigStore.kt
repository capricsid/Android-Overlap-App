package com.example.overlayscreen

import android.content.Context
import android.graphics.Color
import kotlin.math.roundToInt

data class PeekRectState(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

enum class OverlayDismissAnimationMode(
    val key: String,
    val title: String,
) {
    OFF("off", "Off"),
    SHATTER("shatter", "Shatter"),
    GLASS_CRACK("glass_crack", "Glass crack"),
    CURTAIN_SPLIT("curtain_split", "Curtain split"),
    IRIS_OPEN("iris_open", "Iris open"),
    VENETIAN_BLINDS("venetian_blinds", "Venetian blinds"),
    STATIC_BURN("static_burn", "Static burn"),
    LIQUID_MELT("liquid_melt", "Liquid melt"),
    CARD_FOLD("card_fold", "Card fold"),
    RIPPLE_FADE("ripple_fade", "Ripple fade"),
    GLITCH_SCATTER("glitch_scatter", "Glitch scatter"),
    SPOTLIGHT_REVEAL("spotlight_reveal", "Spotlight reveal"),
    FADE_OUT("fade_out", "Fade out"),
    SHRINK("shrink", "Shrink"),
    PIXEL_DISSOLVE("pixel_dissolve", "Pixel dissolve"),
    SLIDE_DOWN("slide_down", "Slide down");

    companion object {
        val valuesList: List<OverlayDismissAnimationMode> = entries.toList()

        fun fromKey(key: String?): OverlayDismissAnimationMode =
            valuesList.firstOrNull { it.key == key } ?: SHATTER
    }
}

enum class OverlayThemePreset(
    val key: String,
    val title: String,
    val color: Int,
    val backgroundKey: String?,
) {
    CUSTOM("custom", "Custom", Color.BLACK, null),
    CINEMA("cinema", "Cinema", Color.parseColor("#090909"), "pixelated_midnight_shards"),
    NIGHT("night", "Night", Color.parseColor("#040A14"), "pixelated_cobalt_blocks"),
    PIXEL("pixel", "Pixel", Color.parseColor("#050505"), "pixelated_background"),
    PAPER("paper", "Paper", Color.parseColor("#19110B"), "pixelated_sandstorm"),
    CARBON("carbon", "Graphite", Color.parseColor("#050505"), "pixelated_grain_background"),
    EMBER("ember", "Ember", Color.parseColor("#120101"), "pixelated_ember_fade"),
    FROSTED_GLASS("frosted_glass", "Frosted Glass", Color.parseColor("#25313B"), "frosted_glass"),
    CARBON_FIBER("carbon_fiber", "Carbon Fiber", Color.parseColor("#050505"), "carbon_fiber"),
    MATTE_INK("matte_ink", "Matte Ink", Color.parseColor("#080B10"), "matte_ink"),
    NOISE_SHIELD("noise_shield", "Noise Shield", Color.parseColor("#040404"), "noise_shield"),
    BLUEPRINT("blueprint", "Blueprint", Color.parseColor("#08152D"), "blueprint"),
    CONCRETE("concrete", "Concrete", Color.parseColor("#202224"), "concrete"),
    CRT_STATIC("crt_static", "CRT Static", Color.parseColor("#030707"), "crt_static"),
    SMOKE("smoke", "Smoke", Color.parseColor("#121318"), "smoke"),
    VELVET("velvet", "Velvet", Color.parseColor("#210012"), "velvet"),
    CIPHER("cipher", "Cipher", Color.parseColor("#021109"), "cipher"),
    CAMOUFLAGE("camouflage", "Camouflage", Color.parseColor("#171712"), "camouflage"),
    HALFTONE("halftone", "Halftone", Color.parseColor("#080808"), "halftone");

    fun backgroundRef(): String? = backgroundKey?.let(BuiltinBackgrounds::encode)

    companion object {
        val valuesList: List<OverlayThemePreset> = entries.toList()

        fun fromKey(key: String?): OverlayThemePreset =
            valuesList.firstOrNull { it.key == key } ?: CUSTOM
    }
}

data class OverlayConfig(
    val opacityPercent: Int = OverlayOpacityPolicy.ACTUAL_MAX_PERCENT,
    val fullOpaqueMaskEnabled: Boolean = false,
    val overlayWidthPercent: Int = 100,
    val overlayTopPercent: Int = 0,
    val overlayBottomPercent: Int = 100,
    val peekWindowCount: Int = 4,
    val controlsX: Int = -1,
    val controlsY: Int = -1,
    val controlsOpacityPercent: Int = 100,
    val controlsCollapsed: Boolean = false,
    val dismissAnimationModeKey: String = OverlayDismissAnimationMode.SHATTER.key,
    val selectedThemeKey: String = OverlayThemePreset.CUSTOM.key,
    val color: Int = Color.BLACK,
    val customText: String = "",
    val backgroundUri: String? = null,
    val peekOne: PeekRectState = PeekRectState(0, 0, 0, 0),
    val peekTwo: PeekRectState = PeekRectState(0, 0, 0, 0),
    val peekThree: PeekRectState = PeekRectState(0, 0, 0, 0),
    val peekFour: PeekRectState = PeekRectState(0, 0, 0, 0),
) {
    fun withPeekWindowCount(count: Int): OverlayConfig =
        copy(peekWindowCount = count.coerceIn(1, 4))

    fun withTopEdge(percent: Int): OverlayConfig {
        val top = percent.coerceIn(0, overlayBottomPercent - MIN_OVERLAY_HEIGHT_PERCENT)
        return copy(overlayTopPercent = top)
    }

    fun withBottomEdge(percent: Int): OverlayConfig {
        val bottom = percent.coerceIn(overlayTopPercent + MIN_OVERLAY_HEIGHT_PERCENT, 100)
        return copy(overlayBottomPercent = bottom)
    }

    fun withTheme(theme: OverlayThemePreset): OverlayConfig =
        if (theme == OverlayThemePreset.CUSTOM) {
            copy(selectedThemeKey = theme.key)
        } else {
            copy(
                selectedThemeKey = theme.key,
                color = theme.color,
                backgroundUri = theme.backgroundRef(),
            )
        }

    fun seeded(screenWidth: Int, screenHeight: Int): OverlayConfig {
        if (
            peekOne.width > 0 && peekOne.height > 0 &&
            peekTwo.width > 0 && peekTwo.height > 0 &&
            peekThree.width > 0 && peekThree.height > 0 &&
            peekFour.width > 0 && peekFour.height > 0
        ) {
            return this
        }

        val first = PeekRectState(
            left = (screenWidth * 0.12f).roundToInt(),
            top = (screenHeight * 0.18f).roundToInt(),
            width = (screenWidth * 0.22f).roundToInt(),
            height = (screenHeight * 0.14f).roundToInt(),
        )
        val second = PeekRectState(
            left = (screenWidth * 0.62f).roundToInt(),
            top = (screenHeight * 0.18f).roundToInt(),
            width = (screenWidth * 0.22f).roundToInt(),
            height = (screenHeight * 0.14f).roundToInt(),
        )
        val third = PeekRectState(
            left = (screenWidth * 0.12f).roundToInt(),
            top = (screenHeight * 0.60f).roundToInt(),
            width = (screenWidth * 0.22f).roundToInt(),
            height = (screenHeight * 0.14f).roundToInt(),
        )
        val fourth = PeekRectState(
            left = (screenWidth * 0.62f).roundToInt(),
            top = (screenHeight * 0.60f).roundToInt(),
            width = (screenWidth * 0.22f).roundToInt(),
            height = (screenHeight * 0.14f).roundToInt(),
        )
        return copy(
            peekOne = first,
            peekTwo = second,
            peekThree = third,
            peekFour = fourth,
        )
    }

    companion object {
        const val MIN_OVERLAY_HEIGHT_PERCENT = 30
    }
}

class OverlayConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): OverlayConfig {
        val legacyHeightPercent = prefs.getInt(KEY_HEIGHT_PERCENT, 100).coerceIn(OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT, 100)
        val legacyInset = ((100 - legacyHeightPercent) / 2).coerceAtLeast(0)
        val topPercent = if (prefs.contains(KEY_TOP_PERCENT)) {
            prefs.getInt(KEY_TOP_PERCENT, 0)
        } else {
            legacyInset
        }
        val bottomPercent = if (prefs.contains(KEY_BOTTOM_PERCENT)) {
            prefs.getInt(KEY_BOTTOM_PERCENT, 100)
        } else {
            100 - legacyInset
        }

        return OverlayConfig(
            opacityPercent = prefs.getInt(KEY_OPACITY, OverlayOpacityPolicy.ACTUAL_MAX_PERCENT),
            fullOpaqueMaskEnabled = prefs.getBoolean(KEY_FULL_OPAQUE_MASK, false),
            overlayWidthPercent = prefs.getInt(KEY_WIDTH_PERCENT, 100),
            overlayTopPercent = topPercent.coerceIn(0, 100 - OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT),
            overlayBottomPercent = bottomPercent.coerceIn(OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT, 100),
            peekWindowCount = prefs.getInt(KEY_PEEK_COUNT, 4).coerceIn(1, 4),
            controlsX = prefs.getInt(KEY_CONTROLS_X, -1),
            controlsY = prefs.getInt(KEY_CONTROLS_Y, -1),
            controlsOpacityPercent = prefs.getInt(KEY_CONTROLS_OPACITY, 100).coerceIn(15, 100),
            controlsCollapsed = prefs.getBoolean(KEY_CONTROLS_COLLAPSED, false),
            dismissAnimationModeKey = prefs.getString(KEY_DISMISS_ANIMATION_MODE, OverlayDismissAnimationMode.SHATTER.key)
                ?: OverlayDismissAnimationMode.SHATTER.key,
            selectedThemeKey = prefs.getString(KEY_THEME, OverlayThemePreset.CUSTOM.key)
                ?: OverlayThemePreset.CUSTOM.key,
            color = prefs.getInt(KEY_COLOR, Color.BLACK),
            customText = prefs.getString(KEY_TEXT, "").orEmpty(),
            backgroundUri = prefs.getString(KEY_BACKGROUND_URI, null),
            peekOne = PeekRectState(
                left = prefs.getInt(KEY_PEEK1_LEFT, 0),
                top = prefs.getInt(KEY_PEEK1_TOP, 0),
                width = prefs.getInt(KEY_PEEK1_WIDTH, 0),
                height = prefs.getInt(KEY_PEEK1_HEIGHT, 0),
            ),
            peekTwo = PeekRectState(
                left = prefs.getInt(KEY_PEEK2_LEFT, 0),
                top = prefs.getInt(KEY_PEEK2_TOP, 0),
                width = prefs.getInt(KEY_PEEK2_WIDTH, 0),
                height = prefs.getInt(KEY_PEEK2_HEIGHT, 0),
            ),
            peekThree = PeekRectState(
                left = prefs.getInt(KEY_PEEK3_LEFT, 0),
                top = prefs.getInt(KEY_PEEK3_TOP, 0),
                width = prefs.getInt(KEY_PEEK3_WIDTH, 0),
                height = prefs.getInt(KEY_PEEK3_HEIGHT, 0),
            ),
            peekFour = PeekRectState(
                left = prefs.getInt(KEY_PEEK4_LEFT, 0),
                top = prefs.getInt(KEY_PEEK4_TOP, 0),
                width = prefs.getInt(KEY_PEEK4_WIDTH, 0),
                height = prefs.getInt(KEY_PEEK4_HEIGHT, 0),
            ),
        ).let { config ->
            val normalized = config.copy(
                opacityPercent = OverlayOpacityPolicy.normalizeActualPercent(
                    config.opacityPercent,
                    config.fullOpaqueMaskEnabled,
                ),
            )
            if (normalized.overlayBottomPercent - normalized.overlayTopPercent >= OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT) {
                normalized
            } else {
                normalized.withBottomEdge(normalized.overlayTopPercent + OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT)
            }
        }
    }

    fun save(config: OverlayConfig) {
        prefs.edit()
            .putInt(KEY_OPACITY, OverlayOpacityPolicy.normalizeActualPercent(config.opacityPercent, config.fullOpaqueMaskEnabled))
            .putBoolean(KEY_FULL_OPAQUE_MASK, config.fullOpaqueMaskEnabled)
            .putInt(KEY_WIDTH_PERCENT, config.overlayWidthPercent.coerceIn(30, 100))
            .putInt(KEY_TOP_PERCENT, config.overlayTopPercent.coerceIn(0, 100 - OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT))
            .putInt(KEY_BOTTOM_PERCENT, config.overlayBottomPercent.coerceIn(OverlayConfig.MIN_OVERLAY_HEIGHT_PERCENT, 100))
            .putInt(KEY_PEEK_COUNT, config.peekWindowCount.coerceIn(1, 4))
            .putInt(KEY_CONTROLS_X, config.controlsX)
            .putInt(KEY_CONTROLS_Y, config.controlsY)
            .putInt(KEY_CONTROLS_OPACITY, config.controlsOpacityPercent.coerceIn(15, 100))
            .putBoolean(KEY_CONTROLS_COLLAPSED, config.controlsCollapsed)
            .putString(KEY_DISMISS_ANIMATION_MODE, OverlayDismissAnimationMode.fromKey(config.dismissAnimationModeKey).key)
            .putString(KEY_THEME, OverlayThemePreset.fromKey(config.selectedThemeKey).key)
            .putInt(KEY_COLOR, config.color)
            .putString(KEY_TEXT, config.customText)
            .putString(KEY_BACKGROUND_URI, config.backgroundUri)
            .putInt(KEY_PEEK1_LEFT, config.peekOne.left)
            .putInt(KEY_PEEK1_TOP, config.peekOne.top)
            .putInt(KEY_PEEK1_WIDTH, config.peekOne.width)
            .putInt(KEY_PEEK1_HEIGHT, config.peekOne.height)
            .putInt(KEY_PEEK2_LEFT, config.peekTwo.left)
            .putInt(KEY_PEEK2_TOP, config.peekTwo.top)
            .putInt(KEY_PEEK2_WIDTH, config.peekTwo.width)
            .putInt(KEY_PEEK2_HEIGHT, config.peekTwo.height)
            .putInt(KEY_PEEK3_LEFT, config.peekThree.left)
            .putInt(KEY_PEEK3_TOP, config.peekThree.top)
            .putInt(KEY_PEEK3_WIDTH, config.peekThree.width)
            .putInt(KEY_PEEK3_HEIGHT, config.peekThree.height)
            .putInt(KEY_PEEK4_LEFT, config.peekFour.left)
            .putInt(KEY_PEEK4_TOP, config.peekFour.top)
            .putInt(KEY_PEEK4_WIDTH, config.peekFour.width)
            .putInt(KEY_PEEK4_HEIGHT, config.peekFour.height)
            .apply()
    }

    fun update(transform: (OverlayConfig) -> OverlayConfig): OverlayConfig {
        val next = transform(load())
        save(next)
        return next
    }

    companion object {
        private const val PREFS_NAME = "overlay_config"
        private const val KEY_OPACITY = "opacity_percent"
        private const val KEY_FULL_OPAQUE_MASK = "full_opaque_mask"
        private const val KEY_WIDTH_PERCENT = "overlay_width_percent"
        private const val KEY_TOP_PERCENT = "overlay_top_percent"
        private const val KEY_BOTTOM_PERCENT = "overlay_bottom_percent"
        private const val KEY_HEIGHT_PERCENT = "overlay_height_percent"
        private const val KEY_PEEK_COUNT = "peek_count"
        private const val KEY_CONTROLS_X = "controls_x"
        private const val KEY_CONTROLS_Y = "controls_y"
        private const val KEY_CONTROLS_OPACITY = "controls_opacity"
        private const val KEY_CONTROLS_COLLAPSED = "controls_collapsed"
        private const val KEY_DISMISS_ANIMATION_MODE = "dismiss_animation_mode"
        private const val KEY_THEME = "theme"
        private const val KEY_COLOR = "overlay_color"
        private const val KEY_TEXT = "overlay_text"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_PEEK1_LEFT = "peek1_left"
        private const val KEY_PEEK1_TOP = "peek1_top"
        private const val KEY_PEEK1_WIDTH = "peek1_width"
        private const val KEY_PEEK1_HEIGHT = "peek1_height"
        private const val KEY_PEEK2_LEFT = "peek2_left"
        private const val KEY_PEEK2_TOP = "peek2_top"
        private const val KEY_PEEK2_WIDTH = "peek2_width"
        private const val KEY_PEEK2_HEIGHT = "peek2_height"
        private const val KEY_PEEK3_LEFT = "peek3_left"
        private const val KEY_PEEK3_TOP = "peek3_top"
        private const val KEY_PEEK3_WIDTH = "peek3_width"
        private const val KEY_PEEK3_HEIGHT = "peek3_height"
        private const val KEY_PEEK4_LEFT = "peek4_left"
        private const val KEY_PEEK4_TOP = "peek4_top"
        private const val KEY_PEEK4_WIDTH = "peek4_width"
        private const val KEY_PEEK4_HEIGHT = "peek4_height"
    }
}
