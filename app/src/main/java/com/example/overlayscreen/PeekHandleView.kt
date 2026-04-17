package com.example.overlayscreen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.roundToInt

class PeekHandleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private enum class DragMode {
        MOVE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var onUpdate: ((PeekRectState) -> Unit)? = null
    private var onInteractionChanged: ((Boolean) -> Unit)? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private val minSize = context.dp(36)
    private val edgeZone = context.dp(20)

    private var startRawX = 0f
    private var startRawY = 0f
    private var startWindowX = 0
    private var startWindowY = 0
    private var startWidth = 0
    private var startHeight = 0
    private var dragMode = DragMode.MOVE

    fun bind(
        windowManager: WindowManager,
        layoutParams: WindowManager.LayoutParams,
        screenWidth: Int,
        screenHeight: Int,
        onUpdate: (PeekRectState) -> Unit,
        onInteractionChanged: (Boolean) -> Unit,
    ) {
        this.windowManager = windowManager
        this.layoutParams = layoutParams
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        this.onUpdate = onUpdate
        this.onInteractionChanged = onInteractionChanged
        invalidate()
    }

    fun applyState(state: PeekRectState) {
        layoutParams.x = state.left
        layoutParams.y = state.top
        layoutParams.width = state.width
        layoutParams.height = state.height
        if (isAttachedToWindow) {
            windowManager.updateViewLayout(this, layoutParams)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(
            borderPaint.strokeWidth,
            borderPaint.strokeWidth,
            width - borderPaint.strokeWidth,
            height - borderPaint.strokeWidth,
            borderPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                startWindowX = layoutParams.x
                startWindowY = layoutParams.y
                startWidth = layoutParams.width
                startHeight = layoutParams.height
                dragMode = resolveDragMode(event.x, event.y)
                onInteractionChanged?.invoke(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startRawX).roundToInt()
                val dy = (event.rawY - startRawY).roundToInt()

                applyDrag(dx, dy)

                windowManager.updateViewLayout(this, layoutParams)
                onUpdate?.invoke(currentState())
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                performClick()
                onUpdate?.invoke(currentState())
                onInteractionChanged?.invoke(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun currentState(): PeekRectState = PeekRectState(
        left = layoutParams.x,
        top = layoutParams.y,
        width = layoutParams.width,
        height = layoutParams.height,
    )

    private fun resolveDragMode(touchX: Float, touchY: Float): DragMode {
        val nearLeft = touchX <= edgeZone
        val nearRight = touchX >= width - edgeZone
        val nearTop = touchY <= edgeZone
        val nearBottom = touchY >= height - edgeZone

        return when {
            nearLeft && nearTop -> DragMode.TOP_LEFT
            nearRight && nearTop -> DragMode.TOP_RIGHT
            nearLeft && nearBottom -> DragMode.BOTTOM_LEFT
            nearRight && nearBottom -> DragMode.BOTTOM_RIGHT
            nearLeft -> DragMode.LEFT
            nearRight -> DragMode.RIGHT
            nearTop -> DragMode.TOP
            nearBottom -> DragMode.BOTTOM
            else -> DragMode.MOVE
        }
    }

    private fun applyDrag(dx: Int, dy: Int) {
        if (dragMode == DragMode.MOVE) {
            layoutParams.x = (startWindowX + dx).coerceIn(0, screenWidth - layoutParams.width)
            layoutParams.y = (startWindowY + dy).coerceIn(0, screenHeight - layoutParams.height)
            return
        }

        var left = startWindowX
        var top = startWindowY
        var right = startWindowX + startWidth
        var bottom = startWindowY + startHeight

        when (dragMode) {
            DragMode.LEFT, DragMode.TOP_LEFT, DragMode.BOTTOM_LEFT -> {
                left = (startWindowX + dx).coerceIn(0, startWindowX + startWidth - minSize)
            }

            DragMode.RIGHT, DragMode.TOP_RIGHT, DragMode.BOTTOM_RIGHT -> {
                right = (startWindowX + startWidth + dx).coerceIn(startWindowX + minSize, screenWidth)
            }

            else -> Unit
        }

        when (dragMode) {
            DragMode.TOP, DragMode.TOP_LEFT, DragMode.TOP_RIGHT -> {
                top = (startWindowY + dy).coerceIn(0, startWindowY + startHeight - minSize)
            }

            DragMode.BOTTOM, DragMode.BOTTOM_LEFT, DragMode.BOTTOM_RIGHT -> {
                bottom = (startWindowY + startHeight + dy).coerceIn(startWindowY + minSize, screenHeight)
            }

            else -> Unit
        }

        layoutParams.x = left
        layoutParams.y = top
        layoutParams.width = max(minSize, right - left)
        layoutParams.height = max(minSize, bottom - top)
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
