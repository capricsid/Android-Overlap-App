package com.example.overlayscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1).toFloat()
    }
    private val selectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val selectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = dp(5).toFloat()
    }

    private val hsv = floatArrayOf(0f, 1f, 1f)
    private var centerX = 0f
    private var centerY = 0f
    private var wheelRadius = 0f
    private val selectorRadius = dp(10).toFloat()

    var onColorChanged: ((Int) -> Unit)? = null

    init {
        isClickable = true
    }

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        hsv[2] = 1f
        invalidate()
    }

    fun currentColor(): Int = Color.HSVToColor(hsv)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        wheelRadius = min(w, h) / 2f - selectorRadius - selectorStrokePaint.strokeWidth

        huePaint.shader = SweepGradient(
            centerX,
            centerY,
            intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED,
            ),
            null,
        )
        saturationPaint.shader = RadialGradient(
            centerX,
            centerY,
            wheelRadius,
            Color.WHITE,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (wheelRadius <= 0f) return

        canvas.save()
        canvas.rotate(-90f, centerX, centerY)
        canvas.drawCircle(centerX, centerY, wheelRadius, huePaint)
        canvas.drawCircle(centerX, centerY, wheelRadius, saturationPaint)
        canvas.restore()
        canvas.drawCircle(centerX, centerY, wheelRadius, borderPaint)

        val sat = hsv[1].coerceIn(0f, 1f)
        val angleRadians = Math.toRadians((hsv[0] - 90f).toDouble())
        val selectorX = centerX + cos(angleRadians).toFloat() * wheelRadius * sat
        val selectorY = centerY + sin(angleRadians).toFloat() * wheelRadius * sat

        selectorFillPaint.color = currentColor()
        canvas.drawCircle(selectorX, selectorY, selectorRadius, selectorShadowPaint)
        canvas.drawCircle(selectorX, selectorY, selectorRadius, selectorFillPaint)
        canvas.drawCircle(selectorX, selectorY, selectorRadius, selectorStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                updateFromTouch(event.x, event.y)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val clampedDistance = distance.coerceIn(0f, wheelRadius)
        val hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 450.0) % 360.0).toFloat()
        val saturation = if (wheelRadius == 0f) 0f else (clampedDistance / wheelRadius).coerceIn(0f, 1f)

        hsv[0] = hue
        hsv[1] = saturation
        hsv[2] = 1f
        invalidate()
        onColorChanged?.invoke(currentColor())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
