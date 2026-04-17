package com.example.overlayscreen
import kotlin.math.roundToInt

object OverlayOpacityPolicy {
    const val DISPLAY_MAX_PERCENT = 100
    const val ACTUAL_MAX_PERCENT = 79

    fun maxActualPercent(fullOpaqueMaskEnabled: Boolean): Int =
        if (fullOpaqueMaskEnabled) DISPLAY_MAX_PERCENT else ACTUAL_MAX_PERCENT

    fun displayPercentToActual(
        displayPercent: Int,
        fullOpaqueMaskEnabled: Boolean,
    ): Int =
        (displayPercent.coerceIn(0, DISPLAY_MAX_PERCENT) *
            maxActualPercent(fullOpaqueMaskEnabled) /
            DISPLAY_MAX_PERCENT.toFloat()).roundToInt()

    fun actualPercentToDisplay(
        actualPercent: Int,
        fullOpaqueMaskEnabled: Boolean,
    ): Int {
        val maxActual = maxActualPercent(fullOpaqueMaskEnabled)
        return (actualPercent.coerceIn(0, maxActual) * DISPLAY_MAX_PERCENT / maxActual.toFloat()).roundToInt()
    }

    fun normalizeActualPercent(
        requestedActualPercent: Int,
        fullOpaqueMaskEnabled: Boolean,
    ): Int =
        requestedActualPercent.coerceIn(0, maxActualPercent(fullOpaqueMaskEnabled))
}
