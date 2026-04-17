package com.example.overlayscreen

import android.graphics.Rect

data class OverlayMaskScene(
    val overlayRect: Rect,
    val peekRects: List<Rect>,
    val segments: List<Rect>,
)

object OverlayMaskLayout {
    fun buildScene(
        config: OverlayConfig,
        screenWidth: Int,
        screenHeight: Int,
    ): OverlayMaskScene {
        val overlayWidth = (screenWidth * config.overlayWidthPercent / 100f).toInt()
        val left = (screenWidth - overlayWidth) / 2
        val top = (screenHeight * config.overlayTopPercent / 100f).toInt()
        val bottom = (screenHeight * config.overlayBottomPercent / 100f).toInt().coerceAtLeast(top + 1)
        val overlayRect = Rect(left, top, left + overlayWidth, bottom)

        val activePeeks = listOf(config.peekOne, config.peekTwo, config.peekThree, config.peekFour)
            .take(config.peekWindowCount.coerceIn(1, 4))
            .map { Rect(it.left, it.top, it.left + it.width, it.top + it.height) }
            .mapNotNull { peek ->
                Rect(peek).takeIf { it.intersect(overlayRect) && it.width() > 0 && it.height() > 0 }
            }

        if (activePeeks.isEmpty()) {
            return OverlayMaskScene(
                overlayRect = overlayRect,
                peekRects = emptyList(),
                segments = listOf(overlayRect),
            )
        }

        val xs = buildSet {
            add(overlayRect.left)
            add(overlayRect.right)
            activePeeks.forEach {
                add(it.left)
                add(it.right)
            }
        }.sorted()

        val ys = buildSet {
            add(overlayRect.top)
            add(overlayRect.bottom)
            activePeeks.forEach {
                add(it.top)
                add(it.bottom)
            }
        }.sorted()

        val cells = mutableListOf<Rect>()
        for (xIndex in 0 until xs.lastIndex) {
            for (yIndex in 0 until ys.lastIndex) {
                val cell = Rect(xs[xIndex], ys[yIndex], xs[xIndex + 1], ys[yIndex + 1])
                if (cell.width() <= 0 || cell.height() <= 0) continue
                val centerX = cell.left + cell.width() / 2
                val centerY = cell.top + cell.height() / 2
                val insidePeek = activePeeks.any { it.contains(centerX, centerY) }
                if (!insidePeek && overlayRect.contains(centerX, centerY)) {
                    cells += cell
                }
            }
        }

        val mergedHorizontal = cells
            .groupBy { it.top to it.bottom }
            .values
            .flatMap { row -> mergeSorted(row.sortedBy { it.left }, horizontal = true) }

        val mergedVertical = mergedHorizontal
            .groupBy { it.left to it.right }
            .values
            .flatMap { column -> mergeSorted(column.sortedBy { it.top }, horizontal = false) }
            .sortedWith(compareBy({ it.top }, { it.left }))

        return OverlayMaskScene(
            overlayRect = overlayRect,
            peekRects = activePeeks,
            segments = mergedVertical,
        )
    }

    private fun mergeSorted(
        rects: List<Rect>,
        horizontal: Boolean,
    ): List<Rect> {
        if (rects.isEmpty()) return emptyList()
        val merged = mutableListOf<Rect>()
        var current = Rect(rects.first())
        rects.drop(1).forEach { next ->
            val touches = if (horizontal) current.right == next.left else current.bottom == next.top
            if (touches) {
                current = if (horizontal) {
                    Rect(current.left, current.top, next.right, current.bottom)
                } else {
                    Rect(current.left, current.top, current.right, next.bottom)
                }
            } else {
                merged += current
                current = Rect(next)
            }
        }
        merged += current
        return merged
    }
}
