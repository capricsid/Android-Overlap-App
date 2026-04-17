package com.example.overlayscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 54f
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    private var config: OverlayConfig = OverlayConfig()
    private var backgroundBitmap: Bitmap? = null
    private var loadedBitmapUri: String? = null

    init {
        setWillNotDraw(false)
    }

    fun updateConfig(nextConfig: OverlayConfig) {
        config = nextConfig
        if (loadedBitmapUri != nextConfig.backgroundUri) {
            backgroundBitmap = loadBitmap(nextConfig.backgroundUri)
            loadedBitmapUri = nextConfig.backgroundUri
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val overlayRect = calculateOverlayRect()
        val peekRects = config.peekRects()
        canvas.save()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipRect(overlayRect)
            peekRects.forEach(canvas::clipOutRect)
        } else {
            val maskPath = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(
                    overlayRect.left.toFloat(),
                    overlayRect.top.toFloat(),
                    overlayRect.right.toFloat(),
                    overlayRect.bottom.toFloat(),
                    Path.Direction.CW,
                )
                peekRects.forEach { peekRect ->
                    addRect(
                        peekRect.left.toFloat(),
                        peekRect.top.toFloat(),
                        peekRect.right.toFloat(),
                        peekRect.bottom.toFloat(),
                        Path.Direction.CCW,
                    )
                }
            }
            canvas.clipPath(maskPath)
            drawContent(canvas, overlayRect)
            canvas.restore()
            return
        }

        drawContent(canvas, overlayRect)
        canvas.restore()
    }

    private fun drawContent(canvas: Canvas, overlayRect: Rect) {
        fillPaint.color = Color.argb(
            255,
            Color.red(config.color),
            Color.green(config.color),
            Color.blue(config.color),
        )

        backgroundBitmap?.let { bitmap ->
            bitmapPaint.alpha = 255
            canvas.drawBitmap(bitmap, null, overlayRect, bitmapPaint)
        } ?: canvas.drawRect(overlayRect, fillPaint)

        if (config.customText.isNotBlank()) {
            textPaint.color = if (isDarkColor(config.color)) Color.WHITE else Color.BLACK
            textPaint.textSize = min(overlayRect.width(), overlayRect.height()) / 7.5f
            val centerX = overlayRect.exactCenterX()
            val centerY = overlayRect.exactCenterY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(config.customText.trim(), centerX, centerY, textPaint)
        }
    }

    private fun calculateOverlayRect(): Rect {
        val overlayWidth = (width * config.overlayWidthPercent / 100f).toInt()
        val left = (width - overlayWidth) / 2
        val top = (height * config.overlayTopPercent / 100f).toInt()
        val bottom = (height * config.overlayBottomPercent / 100f).toInt()
        return Rect(left, top, left + overlayWidth, bottom.coerceAtLeast(top + 1))
    }

    private fun isDarkColor(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.45
    }

    private fun loadBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            BuiltinBackgrounds.decode(uriString)?.let { preset ->
                return@runCatching BitmapFactory.decodeResource(resources, preset.drawableRes)
            }
            val uri = Uri.parse(uriString)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull()
    }

    private fun OverlayConfig.peekRects(): List<Rect> = listOf(peekOne, peekTwo, peekThree, peekFour)
        .take(peekWindowCount.coerceIn(1, 4))
        .map { Rect(it.left, it.top, it.left + it.width, it.top + it.height) }
}
