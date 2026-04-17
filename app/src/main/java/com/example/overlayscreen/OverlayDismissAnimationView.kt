package com.example.overlayscreen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class OverlayDismissAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private data class PieceBuildSpec(
        val columns: Int,
        val rows: Int,
        val spreadX: Float,
        val spreadY: Float,
        val rotation: Float,
        val delayMax: Float,
    )

    private data class Piece(
        val src: Rect,
        val base: Rect,
        val dx: Float,
        val dy: Float,
        val rotation: Float,
        val delay: Float,
    )

    private data class Strip(
        val src: Rect,
        val base: Rect,
        val delay: Float,
        val drift: Float,
        val direction: Float,
    )

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tempRect = Rect()
    private val tempRectF = RectF()
    private val clipPath = Path()

    private var bitmap: Bitmap? = null
    private var mode: OverlayDismissAnimationMode = OverlayDismissAnimationMode.SHATTER
    private var progress: Float = 0f
    private var pieces: List<Piece> = emptyList()
    private var horizontalStrips: List<Strip> = emptyList()
    private var verticalStrips: List<Strip> = emptyList()
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
        horizontalStrips = buildStrips(sourceBitmap, animationMode, horizontal = true)
        verticalStrips = buildStrips(sourceBitmap, animationMode, horizontal = false)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration(animationMode)
            interpolator = animationInterpolator(animationMode)
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
            OverlayDismissAnimationMode.OFF -> Unit
            OverlayDismissAnimationMode.SHATTER -> drawPieces(canvas, source, explosive = true, glassCrack = false)
            OverlayDismissAnimationMode.GLASS_CRACK -> drawPieces(canvas, source, explosive = true, glassCrack = true)
            OverlayDismissAnimationMode.CURTAIN_SPLIT -> drawCurtainSplit(canvas, source)
            OverlayDismissAnimationMode.IRIS_OPEN -> drawIrisOpen(canvas, source)
            OverlayDismissAnimationMode.VENETIAN_BLINDS -> drawVenetianBlinds(canvas, source)
            OverlayDismissAnimationMode.STATIC_BURN -> drawStaticBurn(canvas, source)
            OverlayDismissAnimationMode.LIQUID_MELT -> drawLiquidMelt(canvas, source)
            OverlayDismissAnimationMode.CARD_FOLD -> drawCardFold(canvas, source)
            OverlayDismissAnimationMode.RIPPLE_FADE -> drawRippleFade(canvas, source)
            OverlayDismissAnimationMode.GLITCH_SCATTER -> drawGlitchScatter(canvas, source)
            OverlayDismissAnimationMode.SPOTLIGHT_REVEAL -> drawSpotlightReveal(canvas, source)
            OverlayDismissAnimationMode.FADE_OUT -> drawWhole(canvas, source, alpha = 1f - progress)
            OverlayDismissAnimationMode.SHRINK -> drawShrink(canvas, source)
            OverlayDismissAnimationMode.PIXEL_DISSOLVE -> drawPieces(canvas, source, explosive = false, glassCrack = false)
            OverlayDismissAnimationMode.SLIDE_DOWN -> drawSlideDown(canvas, source)
        }
    }

    private fun drawShrink(canvas: Canvas, source: Bitmap) {
        val scale = 1f - (progress * 0.86f)
        val alpha = 1f - progress
        canvas.save()
        canvas.scale(scale, scale, width / 2f, height / 2f)
        drawWhole(canvas, source, alpha)
        canvas.restore()
    }

    private fun drawSlideDown(canvas: Canvas, source: Bitmap) {
        val alpha = 1f - progress
        canvas.save()
        canvas.translate(0f, height * 0.22f * progress)
        drawWhole(canvas, source, alpha)
        canvas.restore()
    }

    private fun drawCurtainSplit(canvas: Canvas, source: Bitmap) {
        val eased = progress * progress
        val alpha = (1f - progress * 0.9f).coerceIn(0f, 1f)
        bitmapPaint.alpha = (alpha * 255).toInt()

        val halfSource = source.width / 2
        val leftShift = width * 0.34f * eased
        val rightShift = width * 0.34f * eased

        val leftSrc = Rect(0, 0, halfSource, source.height)
        val rightSrc = Rect(halfSource, 0, source.width, source.height)
        val leftDest = RectF(-leftShift, 0f, width / 2f - leftShift, height.toFloat())
        val rightDest = RectF(width / 2f + rightShift, 0f, width.toFloat() + rightShift, height.toFloat())

        canvas.drawBitmap(source, leftSrc, leftDest, bitmapPaint)
        canvas.drawBitmap(source, rightSrc, rightDest, bitmapPaint)
    }

    private fun drawIrisOpen(canvas: Canvas, source: Bitmap) {
        val radius = hypot(width.toDouble(), height.toDouble()).toFloat() * progress
        drawOutsideCircle(canvas, source, width / 2f, height / 2f, radius, alpha = 1f)
        accentPaint.color = Color.argb((110 * (1f - progress)).toInt(), 255, 255, 255)
        accentPaint.strokeWidth = max(2f, width * 0.004f)
        canvas.drawCircle(width / 2f, height / 2f, radius, accentPaint)
    }

    private fun drawSpotlightReveal(canvas: Canvas, source: Bitmap) {
        val centerX = width * (0.16f + 0.68f * progress)
        val centerY = height * (0.30f + 0.12f * sin(progress * PI).toFloat())
        val radius = min(width, height) * (0.14f + 0.42f * progress)
        drawOutsideCircle(canvas, source, centerX, centerY, radius, alpha = 1f)
        accentPaint.color = Color.argb((95 * (1f - progress)).toInt(), 255, 248, 220)
        accentPaint.strokeWidth = max(2f, width * 0.005f)
        canvas.drawCircle(centerX, centerY, radius, accentPaint)
    }

    private fun drawRippleFade(canvas: Canvas, source: Bitmap) {
        val baseRadius = min(width, height) * (0.08f + 0.48f * progress)
        drawOutsideCircle(canvas, source, width / 2f, height / 2f, baseRadius, alpha = 1f - progress * 0.18f)
        accentPaint.color = Color.argb((120 * (1f - progress)).toInt(), 255, 255, 255)
        accentPaint.strokeWidth = max(2f, width * 0.004f)
        canvas.drawCircle(width / 2f, height / 2f, baseRadius, accentPaint)
        canvas.drawCircle(width / 2f, height / 2f, baseRadius * (1.18f + progress * 0.08f), accentPaint)
    }

    private fun drawVenetianBlinds(canvas: Canvas, source: Bitmap) {
        horizontalStrips.forEach { strip ->
            val local = localProgress(strip.delay)
            val visibleHeight = strip.base.height() * (1f - local)
            if (visibleHeight <= 1f) return@forEach
            val drift = strip.base.height() * 0.22f * local
            tempRect.set(
                strip.src.left,
                strip.src.top,
                strip.src.right,
                strip.src.top + max(1, (strip.src.height() * (1f - local)).toInt()),
            )
            tempRectF.set(
                strip.base.left.toFloat(),
                strip.base.top + drift,
                strip.base.right.toFloat(),
                strip.base.top + drift + visibleHeight,
            )
            bitmapPaint.alpha = ((1f - local * 0.7f) * 255).toInt()
            canvas.drawBitmap(source, tempRect, tempRectF, bitmapPaint)
        }
    }

    private fun drawLiquidMelt(canvas: Canvas, source: Bitmap) {
        verticalStrips.forEach { strip ->
            val local = localProgress(strip.delay)
            val melt = height * (0.06f + strip.drift * 0.26f) * local * local
            val stretch = strip.base.height() * (1f + local * 0.18f)
            tempRectF.set(
                strip.base.left.toFloat(),
                strip.base.top + melt,
                strip.base.right.toFloat(),
                strip.base.top + melt + stretch,
            )
            bitmapPaint.alpha = ((1f - local * 0.85f) * 255).toInt()
            canvas.drawBitmap(source, strip.src, tempRectF, bitmapPaint)
        }
    }

    private fun drawCardFold(canvas: Canvas, source: Bitmap) {
        verticalStrips.forEachIndexed { index, strip ->
            val local = localProgress(strip.delay)
            val scaleX = max(0.05f, 1f - local)
            bitmapPaint.alpha = ((1f - local * 0.92f) * 255).toInt()
            canvas.save()
            val centerX = strip.base.exactCenterX()
            val centerY = strip.base.exactCenterY()
            val tilt = if (index % 2 == 0) -1f else 1f
            canvas.translate(tilt * strip.base.width() * 0.18f * local, 0f)
            canvas.scale(scaleX, 1f - local * 0.08f, centerX, centerY)
            canvas.drawBitmap(source, strip.src, strip.base, bitmapPaint)
            canvas.restore()
        }
    }

    private fun drawStaticBurn(canvas: Canvas, source: Bitmap) {
        drawPieces(canvas, source, explosive = false, glassCrack = false)
        val flashAlpha = (110 * (1f - progress)).toInt()
        pieces.forEachIndexed { index, piece ->
            if (index % 5 != 0) return@forEachIndexed
            if (progress < piece.delay * 0.8f) return@forEachIndexed
            val local = localProgress(piece.delay)
            val tint = if (index % 2 == 0) Color.argb(flashAlpha, 255, 180, 110) else Color.argb(flashAlpha, 140, 255, 255)
            flashPaint.color = tint
            val inset = piece.base.width() * 0.08f
            canvas.drawRect(
                piece.base.left + inset,
                piece.base.top + inset,
                piece.base.right - inset,
                piece.base.bottom - inset,
                flashPaint,
            )
            if (local > 0.5f) {
                flashPaint.color = Color.argb((65 * (1f - progress)).toInt(), 255, 255, 255)
                canvas.drawRect(piece.base, flashPaint)
            }
        }
    }

    private fun drawGlitchScatter(canvas: Canvas, source: Bitmap) {
        pieces.forEachIndexed { index, piece ->
            val local = localProgress(piece.delay)
            if (local <= 0f) {
                canvas.drawBitmap(source, piece.src, piece.base, bitmapPaint)
                return@forEachIndexed
            }
            val jitterX = piece.dx * local * 0.65f
            val jitterY = piece.dy * local * 0.12f
            val split = if (index % 3 == 0) 7f * (1f - local) else 0f
            bitmapPaint.alpha = ((1f - local * 0.82f) * 255).toInt()

            tempRectF.set(
                piece.base.left + jitterX - split,
                piece.base.top + jitterY,
                piece.base.right + jitterX - split,
                piece.base.bottom + jitterY,
            )
            canvas.drawBitmap(source, piece.src, tempRectF, bitmapPaint)

            if (split > 0f) {
                bitmapPaint.alpha = ((0.34f * (1f - local)) * 255).toInt()
                tempRectF.offset(split * 2f, 0f)
                canvas.drawBitmap(source, piece.src, tempRectF, bitmapPaint)
            }
        }
    }

    private fun drawPieces(
        canvas: Canvas,
        source: Bitmap,
        explosive: Boolean,
        glassCrack: Boolean,
    ) {
        pieces.forEachIndexed { index, piece ->
            val local = localProgress(piece.delay)
            if (local <= 0f) {
                canvas.drawBitmap(source, piece.src, piece.base, bitmapPaint)
                return@forEachIndexed
            }

            val motionScale = if (explosive) local * local else local * 0.45f
            val dx = piece.dx * motionScale
            val dy = piece.dy * if (explosive) motionScale else local * 0.2f
            val alpha = if (explosive) 1f - local else 1f - (local * 1.08f)
            bitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()

            canvas.save()
            val centerX = piece.base.exactCenterX()
            val centerY = piece.base.exactCenterY()
            canvas.translate(dx, dy)
            canvas.rotate(piece.rotation * local, centerX, centerY)
            canvas.drawBitmap(source, piece.src, piece.base, bitmapPaint)

            if (glassCrack && index % 7 == 0) {
                accentPaint.color = Color.argb((140 * (1f - local)).toInt(), 255, 255, 255)
                accentPaint.strokeWidth = max(1.2f, width * 0.0022f)
                canvas.drawLine(width / 2f, height / 2f, centerX, centerY, accentPaint)
            }
            canvas.restore()
        }
    }

    private fun drawWhole(canvas: Canvas, source: Bitmap, alpha: Float) {
        bitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), bitmapPaint)
    }

    private fun drawOutsideCircle(
        canvas: Canvas,
        source: Bitmap,
        centerX: Float,
        centerY: Float,
        radius: Float,
        alpha: Float,
    ) {
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(centerX, centerY, radius, Path.Direction.CW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(clipPath)
        } else {
            @Suppress("DEPRECATION")
            canvas.clipPath(clipPath, Region.Op.DIFFERENCE)
        }
        drawWhole(canvas, source, alpha)
        canvas.restore()
    }

    private fun animationDuration(animationMode: OverlayDismissAnimationMode): Long =
        when (animationMode) {
            OverlayDismissAnimationMode.OFF -> 0L
            OverlayDismissAnimationMode.FADE_OUT -> 180L
            OverlayDismissAnimationMode.SHRINK,
            OverlayDismissAnimationMode.SLIDE_DOWN,
            OverlayDismissAnimationMode.CURTAIN_SPLIT,
            OverlayDismissAnimationMode.VENETIAN_BLINDS,
            OverlayDismissAnimationMode.CARD_FOLD,
            -> 240L

            OverlayDismissAnimationMode.IRIS_OPEN,
            OverlayDismissAnimationMode.RIPPLE_FADE,
            OverlayDismissAnimationMode.SPOTLIGHT_REVEAL,
            OverlayDismissAnimationMode.PIXEL_DISSOLVE,
            OverlayDismissAnimationMode.GLITCH_SCATTER,
            -> 300L

            OverlayDismissAnimationMode.LIQUID_MELT,
            OverlayDismissAnimationMode.STATIC_BURN,
            -> 340L

            OverlayDismissAnimationMode.SHATTER,
            OverlayDismissAnimationMode.GLASS_CRACK,
            -> 420L
        }

    private fun animationInterpolator(animationMode: OverlayDismissAnimationMode) =
        when (animationMode) {
            OverlayDismissAnimationMode.SHATTER,
            OverlayDismissAnimationMode.GLASS_CRACK,
            OverlayDismissAnimationMode.SHRINK,
            OverlayDismissAnimationMode.SLIDE_DOWN,
            OverlayDismissAnimationMode.CURTAIN_SPLIT,
            OverlayDismissAnimationMode.LIQUID_MELT,
            OverlayDismissAnimationMode.CARD_FOLD,
            -> AccelerateInterpolator()

            else -> AccelerateDecelerateInterpolator()
        }

    private fun localProgress(delay: Float): Float =
        ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)

    private fun buildPieces(
        sourceBitmap: Bitmap,
        animationMode: OverlayDismissAnimationMode,
    ): List<Piece> {
        val spec = when (animationMode) {
            OverlayDismissAnimationMode.PIXEL_DISSOLVE -> PieceBuildSpec(22, 30, 0.12f, 0.14f, 8f, 0.68f)
            OverlayDismissAnimationMode.STATIC_BURN -> PieceBuildSpec(18, 24, 0.16f, 0.16f, 14f, 0.54f)
            OverlayDismissAnimationMode.GLITCH_SCATTER -> PieceBuildSpec(16, 22, 0.28f, 0.06f, 10f, 0.48f)
            OverlayDismissAnimationMode.GLASS_CRACK -> PieceBuildSpec(12, 18, 0.15f, 0.24f, 26f, 0.20f)
            else -> PieceBuildSpec(10, 14, 0.12f, 0.26f, 30f, 0.18f)
        }
        val random = Random(sourceBitmap.width * 31 + sourceBitmap.height * 17 + animationMode.ordinal * 997)
        val cellWidth = max(1, sourceBitmap.width / spec.columns)
        val cellHeight = max(1, sourceBitmap.height / spec.rows)

        return buildList {
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) {
                    val left = column * cellWidth
                    val top = row * cellHeight
                    val right = if (column == spec.columns - 1) sourceBitmap.width else left + cellWidth
                    val bottom = if (row == spec.rows - 1) sourceBitmap.height else top + cellHeight
                    add(
                        Piece(
                            src = Rect(left, top, right, bottom),
                            base = Rect(left, top, right, bottom),
                            dx = random.nextFloat() * sourceBitmap.width * spec.spreadX * 2f - sourceBitmap.width * spec.spreadX,
                            dy = random.nextFloat() * sourceBitmap.height * spec.spreadY + sourceBitmap.height * 0.04f,
                            rotation = random.nextFloat() * spec.rotation * 2f - spec.rotation,
                            delay = random.nextFloat() * spec.delayMax,
                        ),
                    )
                }
            }
        }
    }

    private fun buildStrips(
        sourceBitmap: Bitmap,
        animationMode: OverlayDismissAnimationMode,
        horizontal: Boolean,
    ): List<Strip> {
        val count = when {
            horizontal && animationMode == OverlayDismissAnimationMode.VENETIAN_BLINDS -> 13
            !horizontal && animationMode == OverlayDismissAnimationMode.LIQUID_MELT -> 18
            !horizontal && animationMode == OverlayDismissAnimationMode.CARD_FOLD -> 14
            else -> 0
        }
        if (count == 0) return emptyList()

        val random = Random(sourceBitmap.width * 13 + sourceBitmap.height * 29 + animationMode.ordinal * 313 + if (horizontal) 1 else 2)
        return buildList {
            for (index in 0 until count) {
                if (horizontal) {
                    val top = index * sourceBitmap.height / count
                    val bottom = if (index == count - 1) sourceBitmap.height else (index + 1) * sourceBitmap.height / count
                    val rect = Rect(0, top, sourceBitmap.width, bottom)
                    add(
                        Strip(
                            src = Rect(rect),
                            base = Rect(rect),
                            delay = index / (count * 1.18f),
                            drift = 0.35f + random.nextFloat() * 0.6f,
                            direction = 1f,
                        ),
                    )
                } else {
                    val left = index * sourceBitmap.width / count
                    val right = if (index == count - 1) sourceBitmap.width else (index + 1) * sourceBitmap.width / count
                    val rect = Rect(left, 0, right, sourceBitmap.height)
                    add(
                        Strip(
                            src = Rect(rect),
                            base = Rect(rect),
                            delay = random.nextFloat() * 0.24f,
                            drift = 0.25f + random.nextFloat() * 0.8f,
                            direction = if (index % 2 == 0) -1f else 1f,
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
