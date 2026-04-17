package com.example.overlayscreen

import android.graphics.Color

data class OverlayColorPreset(
    val key: String,
    val title: String,
    val color: Int,
)

object OverlayColorPresets {
    val presets: List<OverlayColorPreset> = listOf(
        OverlayColorPreset("night_black", "Night Black", Color.parseColor("#000000")),
        OverlayColorPreset("charcoal", "Charcoal", Color.parseColor("#141414")),
        OverlayColorPreset("ink", "Ink Navy", Color.parseColor("#08101A")),
        OverlayColorPreset("forest", "Forest", Color.parseColor("#08130C")),
        OverlayColorPreset("burgundy", "Burgundy", Color.parseColor("#200508")),
        OverlayColorPreset("espresso", "Espresso", Color.parseColor("#1A120C")),
        OverlayColorPreset("slate", "Slate", Color.parseColor("#1C2328")),
    )

    fun indexOfColor(color: Int): Int {
        val exact = presets.indexOfFirst { it.color == color }
        return if (exact >= 0) exact else 0
    }
}
