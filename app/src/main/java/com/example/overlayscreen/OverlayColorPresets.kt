package com.example.overlayscreen

import android.graphics.Color

data class OverlayColorPreset(
    val key: String,
    val title: String,
    val color: Int,
)

object OverlayColorPresets {
    val presets: List<OverlayColorPreset> = listOf(
        OverlayColorPreset("black", "Black", Color.parseColor("#000000")),
        OverlayColorPreset("white", "White", Color.parseColor("#FFFFFF")),
        OverlayColorPreset("red", "Red", Color.parseColor("#F44336")),
        OverlayColorPreset("orange", "Orange", Color.parseColor("#FF9800")),
        OverlayColorPreset("yellow", "Yellow", Color.parseColor("#FFEB3B")),
        OverlayColorPreset("green", "Green", Color.parseColor("#4CAF50")),
        OverlayColorPreset("teal", "Teal", Color.parseColor("#009688")),
        OverlayColorPreset("cyan", "Cyan", Color.parseColor("#00BCD4")),
        OverlayColorPreset("blue", "Blue", Color.parseColor("#2196F3")),
        OverlayColorPreset("indigo", "Indigo", Color.parseColor("#3F51B5")),
        OverlayColorPreset("purple", "Purple", Color.parseColor("#9C27B0")),
        OverlayColorPreset("pink", "Pink", Color.parseColor("#E91E63")),
    )

    fun indexOfColor(color: Int): Int {
        val exact = presets.indexOfFirst { it.color == color }
        return if (exact >= 0) exact else 0
    }
}
