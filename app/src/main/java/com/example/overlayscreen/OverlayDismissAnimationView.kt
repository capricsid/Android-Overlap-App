package com.example.overlayscreen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import kotlin.math.max
import kotlin.random.Random

class OverlayDismissAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private data class Piece(
        val src: Rect,
        val base: Rect,
        val dx: Float,
        val dy: Float,
        val rotation: Float,
        val delay: Float,
    )

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null
    private var mode: OverlayDismissAnimationMode = OverlayDismissAnimationMode.SHATTER
    private var progress: Float = 0f
    private var pieces: List<Piece> = emptyList()
    private var finishAction: (() -> Unit)? = null
    private var animator: ValueAnimator? = null
    private var cancelled = false
    private var finished = false

    fun play(
        sourceBitmap: Bitmap,
        animationMode: OverlayDismissAnimationMode,
        onFinished: () -> Unit,
    ) {
        animator?.cancel()
        releaseBitmap()
        bitmap = sourceBitmap
        mode = animationMode
        finishAction = onFinished
        progress = 0f
        cancelled = false
        finished = false
        pieces = buildPieces(sourceBitmap, animationMode)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (animationMode) {
                OverlayDismissAnimationMode.SHATTER -> 420L
                OverlayDismissAnimationMode.FADE_OUT -> 180L
                OverlayDismissAnimationMode.SHRINK -> 220L
                OverlayDismissAnimationMode.PIXEL_DISSOLVE -> 260L
                OverlayDismissAnimationMode.SLIDE_DOWN -> 220L
                OverlayDismissAnimationMode.OFF -> 0L
            }
            interpolator = when (animationMode) {
                OverlayDismissAnimationMode.SHATTER,
                OverlayDismissAnimationMode.SHRINK,
                OverlayDismissAnimationMode.SLIDE_DOWN -> AccelerateInterpolator()

                else -> AccelerateDecelerateInterpolator()
            }
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        invokeFinish()
                    }
                }
            })
            if (animationMode == OverlayDismissAnimationMode.OFF) {
                invokeFinish()
            } else {
                start()
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        if (animator?.isRunning == true) {
            cancelled = true
            animator?.cancel()
        }
        animator = null
        releaseBitmap()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val source = bitmap ?: return
        when (mode) {
            OverlayDismissAnimationMode.SHATTER -> drawPieces(canvas, source, explosive = true)
            OverlayDismissAnimationMode.PIXEL_DISSOLVE -> drawPieces(canvas, source, explosive = false)
            OverlayDismissAnimationMode.FADE_OUT -> drawWhole(canvas, source, alpha = 1f - progress)
            OverlayDismissAnimationMode.SHRINK -> {
                val scale = 1f - (progress * 0.86f)
                val alpha = 1f - progress
                canvas.save()
                canvas.scale(scale, scale, width / 2f, height / 2f)
                drawWhole(canvas, source, alpha)
                canvas.restore()
            }

            OverlayDismissAnimationMode.SLIDE_DOWN -> {
                val alpha = 1f - progress
                canvas.save()
                canvas.translate(0f, height * 0.22f * progress)
                drawWhole(canvas, source, alpha)
                canvas.restore()
            }

            OverlayDismissAnimationMode.OFF -> Unit
        }
    }

    private fun drawWhole(canvas: Canvas, source: Bitmap, alpha: Float) {
        bitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), bitmapPaint)
    }

    private fun drawPieces(canvas: Canvas, source: Bitmap, explosive: Boolean) {
        val eased = progress * progress
        pieces.forEach { piece ->
            val localProgress = ((progress - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (localProgress <= 0f) return@forEach
            val dx = if (explosive) piece.dx * eased else piece.dx * localProgress * 0.35f
            val dy = if (explosive) piece.dy * eased else piece.dy * localProgress * 0.15f
            val alpha = if (explosive) 1f - localProgress else 1f - (localProgress * 1.15f)
            bitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()

            canvas.save()
            val centerX = piece.base.exactCenterX()
            val centerY = piece.base.exactCenterY()
            canvas.translate(dx, dy)
            canvas.rotate(piece.rotation * localProgress, centerX, centerY)
            val destination = Rect(
                piece.base.left,
                piece.base.top,
                piece.base.right,
                piece.base.bottom,
            )
            canvas.drawBitmap(source, piece.src, destination, bitmapPaint)
            canvas.restore()
        }
    }

    private fun buildPieces(
        sourceBitmap: Bitmap,
        animationMode: OverlayDismissAnimationMode,
    ): List<Piece> {
        val columns = when (animationMode) {
            OverlayDismissAnimationMode.PIXEL_DISSOLVE -> 18
            else -> 10
        }
        val rows = when (animationMode) {
            OverlayDismissAnimationMode.PIXEL_DISSOLVE -> 24
            else -> 14
        }
        val cellWidth = max(1, sourceBitmap.width / columns)
        val cellHeight = max(1, sourceBitmap.height / rows)

        return buildList {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val left = column * cellWidth
                    val top = row * cellHeight
                    val right = if (column == columns - 1) sourceBitmap.width else left + cellWidth
                    val bottom = if (row == rows - 1) sourceBitmap.height else top + cellHeight
                    add(
                        Piece(
                            src = Rect(left, top, right, bottom),
                            base = Rect(left, top, right, bottom),
                            dx = Random.nextFloat() * sourceBitmap.width * 0.24f - sourceBitmap.width * 0.12f,
                            dy = Random.nextFloat() * sourceBitmap.height * 0.26f + sourceBitmap.height * 0.05f,
                            rotation = Random.nextFloat() * 30f - 15f,
                            delay = if (animationMode == OverlayDismissAnimationMode.PIXEL_DISSOLVE) {
                                Random.nextFloat() * 0.62f
                            } else {
                                Random.nextFloat() * 0.18f
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun invokeFinish() {
        if (finished) return
        finished = true
        finishAction?.also {
            finishAction = null
            it()
        }
        releaseBitmap()
    }

    private fun releaseBitmap() {
        bitmap?.let { source ->
            if (!source.isRecycled) {
                source.recycle()
            }
        }
        bitmap = null
    }
}
