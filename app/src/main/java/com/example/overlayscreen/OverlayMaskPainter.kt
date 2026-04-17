package com.example.overlayscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.text.TextPaint
import kotlin.math.min

object OverlayMaskPainter {
    private var cachedBackgroundRef: String? = null
    private var cachedBackground: Bitmap? = null

    fun drawSceneSegment(
        canvas: Canvas,
        context: Context,
        config: OverlayConfig,
        overlayRect: Rect,
        segmentRect: Rect,
        localCanvas: Boolean,
    ) {
        canvas.save()
        if (localCanvas) {
            canvas.translate(-segmentRect.left.toFloat(), -segmentRect.top.toFloat())
        } else {
            canvas.clipRect(segmentRect)
        }
        drawOverlayContent(canvas, context, config, overlayRect)
        canvas.restore()
    }

    fun captureSceneBitmap(
        context: Context,
        config: OverlayConfig,
        screenWidth: Int,
        screenHeight: Int,
    ): Bitmap? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val scene = OverlayMaskLayout.buildScene(config, screenWidth, screenHeight)
        return runCatching {
            Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                scene.segments.forEach { segment ->
                    drawSceneSegment(canvas, context, config, scene.overlayRect, segment, localCanvas = false)
                }
            }
        }.getOrNull()
    }

    private fun drawOverlayContent(
        canvas: Canvas,
        context: Context,
        config: OverlayConfig,
        overlayRect: Rect,
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                255,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color),
            )
        }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        loadBitmap(context, config.backgroundUri)?.let { bitmap ->
            bitmapPaint.alpha = 255
            canvas.drawBitmap(bitmap, centerCropSourceRect(bitmap, overlayRect), overlayRect, bitmapPaint)
        } ?: canvas.drawRect(overlayRect, fillPaint)

        if (config.customText.isNotBlank()) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDarkColor(config.color)) Color.WHITE else Color.BLACK
                textAlign = Paint.Align.CENTER
                textSize = min(overlayRect.width(), overlayRect.height()) / 7.5f
                setShadowLayer(10f, 0f, 0f, Color.BLACK)
            }
            val centerX = overlayRect.exactCenterX()
            val centerY = overlayRect.exactCenterY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(config.customText.trim(), centerX, centerY, textPaint)
        }
    }

    private fun loadBitmap(
        context: Context,
        backgroundRef: String?,
    ): Bitmap? {
        if (backgroundRef.isNullOrBlank()) return null
        if (cachedBackgroundRef == backgroundRef && cachedBackground?.isRecycled == false) {
            return cachedBackground
        }

        cachedBackground?.takeIf { !it.isRecycled }?.recycle()
        cachedBackground = runCatching {
            BuiltinBackgrounds.decode(backgroundRef)?.let { preset ->
                return@runCatching BitmapFactory.decodeResource(context.resources, preset.drawableRes)
            }
            val uri = Uri.parse(backgroundRef)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull()
        cachedBackgroundRef = backgroundRef
        return cachedBackground
    }

    private fun isDarkColor(color: Int): Boolean {
        val darkness =
            1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.45
    }

    private fun centerCropSourceRect(bitmap: Bitmap, targetRect: Rect): Rect {
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetAspect = targetRect.width().toFloat() / targetRect.height().coerceAtLeast(1).toFloat()
        return if (bitmapAspect > targetAspect) {
            val croppedWidth = (bitmap.height * targetAspect).toInt().coerceAtMost(bitmap.width)
            val left = ((bitmap.width - croppedWidth) / 2).coerceAtLeast(0)
            Rect(left, 0, left + croppedWidth, bitmap.height)
        } else {
            val croppedHeight = (bitmap.width / targetAspect).toInt().coerceAtMost(bitmap.height)
            val top = ((bitmap.height - croppedHeight) / 2).coerceAtLeast(0)
            Rect(0, top, bitmap.width, top + croppedHeight)
        }
    }
}
