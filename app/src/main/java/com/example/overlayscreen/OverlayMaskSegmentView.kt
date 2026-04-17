package com.example.overlayscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class OverlayMaskSegmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var config: OverlayConfig = OverlayConfig()
    private var overlayRect: Rect = Rect()
    private var segmentRect: Rect = Rect()

    init {
        setWillNotDraw(false)
    }

    fun updateSegment(
        config: OverlayConfig,
        overlayRect: Rect,
        segmentRect: Rect,
    ) {
        this.config = config
        this.overlayRect = Rect(overlayRect)
        this.segmentRect = Rect(segmentRect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        OverlayMaskPainter.drawSceneSegment(
            canvas = canvas,
            context = context,
            config = config,
            overlayRect = overlayRect,
            segmentRect = segmentRect,
            localCanvas = true,
        )
    }
}
