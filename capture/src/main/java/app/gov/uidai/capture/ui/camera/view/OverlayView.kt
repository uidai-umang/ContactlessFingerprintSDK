package app.gov.uidai.capture.ui.camera.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import app.gov.uidai.capture.R

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- State Management ---
    // Defines the visual style and animation behavior of the overlay
    sealed class State(
        @ColorRes val colorRes: Int,
        val hasThinBase: Boolean
    ) {
        data object INITIAL : State(R.color.md_theme_surface, false)
        data object WARNING : State(R.color.md_theme_error, false)
        data object PRE_AUTO_CAPTURE : State(R.color.colorWarningContainer, true)
        data object AUTO_CAPTURE_SUCCESS : State(R.color.colorSuccess, true)
        data object SUCCESS : State(R.color.colorSuccess, false)
        data object FAILURE : State(R.color.md_theme_error, false)
    }

    // --- Configuration ---
    companion object {
        private const val RECT_HEIGHT_F = 80f
        private const val SEMICIRCLE_RADIUS_F = 85f

        private const val COLOR_ANIMATION_DURATION = 300L
        private const val TRANSITION_DURATION = 300L

        private const val DASH_LENGTH = 150f
        private const val DASH_GAP = 60f
    }

    // --- Sizing ---
    private val thickStrokeWidth = dpToPx(8f)
    private val thinStrokeWidth = dpToPx(2f)

    // --- State & Drawing Objects ---
    private var currentState: State = State.INITIAL
    private var currentColor: Int = ContextCompat.getColor(context, currentState.colorRes)

    private val path = Path()
    private val segmentPath = Path()
    private val pathMeasure = PathMeasure()
    private val overflowPath = Path()

    // Cache for dash effect to avoid per-frame allocations
    private var cachedDashGap = Float.NaN
    private var cachedDashEffect: DashPathEffect? = null

    // --- NEW: Dual Paint System ---
    private val baseBorderPaint = createPaint().apply {
        strokeWidth = thickStrokeWidth
        color = Color.WHITE
    }
    private val animationBorderPaint = createPaint()
    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.colorBackgroundOverlay)
    }
    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // --- Animation Properties ---
    private var currentProgress = 0f
    private var animatedDashGap = DASH_GAP
    private var animationLineAlpha = 0 // Alpha for the top animation layer

    // --- Animators ---
    private var currentAnimatorSet: AnimatorSet? = null
    private val loopAnimator = ValueAnimator.ofFloat(0f, 1f)

    private var indeterminateStart = 0f // tail
    private var indeterminateEnd = 0f   // head
    private var rotationOffset = 0f

    // Optional minimum visible segment proportion to avoid zero-length collapse
    private val minVisibleSegment = 0.06f

    // Tail (start trim)
    private val startAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1333L
        repeatCount = ValueAnimator.INFINITE
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener {
            val f = it.animatedFraction
            indeterminateEnd = f
            postInvalidateOnAnimation()
        }
    }

    // Head (end trim), phase shifted
    private val endAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1333L
        repeatCount = ValueAnimator.INFINITE
        startDelay = 1333L / 2 // ~666–667ms
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener {
            val f = it.animatedFraction
            indeterminateStart = f
            postInvalidateOnAnimation()
        }
    }

    private val rotationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3333L // Material spec ~3.3s per full rotation
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationOffset = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    private fun startAutoCaptureSuccessLooper(){
        startAnimator.start()
        endAnimator.start()
        rotationAnimator.start()
    }

    private fun stopAutoCaptureSuccessLooper(){
        startAnimator.cancel()
        endAnimator.cancel()
        rotationAnimator.cancel()
    }

    init {
        // Start in the initial state without animations
        baseBorderPaint.strokeWidth = thickStrokeWidth
        animationBorderPaint.strokeWidth = thickStrokeWidth
        animatedDashGap = DASH_GAP

        // Ensure hardware layer to make CLEAR xfermode and animations smoother
        setLayerType(LAYER_TYPE_HARDWARE, null)

        setState(State.INITIAL, animate = false)
    }

    private fun createPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setState(newState: State, animate: Boolean = true, progressAnimationDuration: Long = 1000) {
        if (currentState == newState) return
        val oldState = currentState
        currentState = newState

        currentAnimatorSet?.cancel()
        animateColor(newState.colorRes, animate)

        if (animate) {
            animateVisualTransition(oldState, newState, progressAnimationDuration)
        } else {
            // Jump to the final state without transitions
            baseBorderPaint.strokeWidth = if (newState.hasThinBase) thinStrokeWidth else thickStrokeWidth

            animatedDashGap = if (newState is State.INITIAL) DASH_GAP else 0f
            animationLineAlpha = if (newState.hasThinBase) 255 else 0

            stopAutoCaptureSuccessLooper()
            if(newState is State.AUTO_CAPTURE_SUCCESS) startAutoCaptureSuccessLooper()

            invalidate()
        }
    }

    private fun animateVisualTransition(fromState: State, toState: State, progressAnimationDuration: Long = 1000) {
        val animatorList = mutableListOf<Animator>()

        // 1. Animate the Base Line (Stroke Width and Dash Style)
        val targetStrokeWidth = if (toState.hasThinBase) thinStrokeWidth else thickStrokeWidth
        if (baseBorderPaint.strokeWidth != targetStrokeWidth) {
            animatorList.add(ValueAnimator.ofFloat(baseBorderPaint.strokeWidth, targetStrokeWidth).apply {
                addUpdateListener { baseBorderPaint.strokeWidth = it.animatedValue as Float; postInvalidateOnAnimation() }
            })
        }

        val targetDashGap = if (toState is State.INITIAL) DASH_GAP else 0f
        if (animatedDashGap != targetDashGap) {
            animatorList.add(ValueAnimator.ofFloat(animatedDashGap, targetDashGap).apply {
                addUpdateListener { animatedDashGap = it.animatedValue as Float; postInvalidateOnAnimation() }
            })
        }

        // 2. Animate the Animation Line (Alpha and Progress)
        val targetAlpha = if (toState.hasThinBase) 255 else 0
        if (animationLineAlpha != targetAlpha) {
            animatorList.add(ValueAnimator.ofInt(animationLineAlpha, targetAlpha).apply {
                addUpdateListener { animationLineAlpha = it.animatedValue as Int; postInvalidateOnAnimation() }
            })
        }

        // Handle case where an animation is reversing (e.g., Pre-Capture -> Warning)
        if (fromState is State.PRE_AUTO_CAPTURE && !toState.hasThinBase) {
            animatorList.add(ValueAnimator.ofFloat(currentProgress, 0f).apply {
                addUpdateListener { currentProgress = it.animatedValue as Float; postInvalidateOnAnimation() }
            })
        }

        currentAnimatorSet = AnimatorSet().apply {
            playTogether(animatorList)
            duration = TRANSITION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Settle into the new state's continuous animation
                    postTransitionUpdate(toState, progressAnimationDuration)
                }
                override fun onAnimationCancel(animation: Animator) {
                    postTransitionUpdate(toState, progressAnimationDuration)
                }
            })
            start()
        }
    }

    /** Called after a transition finishes to start the new state's persistent animation. */
    private fun postTransitionUpdate(state: State, progressAnimationDuration: Long = 1000) {
        currentAnimatorSet = null

        stopAutoCaptureSuccessLooper()

        when (state) {
            is State.INITIAL -> {
                // Nothing
            }
            is State.PRE_AUTO_CAPTURE -> {
                // Start the 0-100% progress animation
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = progressAnimationDuration
                    addUpdateListener { currentProgress = it.animatedValue as Float; postInvalidateOnAnimation() }
                    start()
                }
            }
            is State.AUTO_CAPTURE_SUCCESS -> {
                startAutoCaptureSuccessLooper()
            }
            else -> { // Warning, Success, Failure have no continuous animations
                invalidate()
            }
        }
    }

    private fun animateColor(@ColorRes colorRes: Int, animate: Boolean) {
        val targetColor = ContextCompat.getColor(context, colorRes)
        if (currentColor == targetColor) return
        ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            duration = if (animate) COLOR_ANIMATION_DURATION else 0
            addUpdateListener {
                currentColor = it.animatedValue as Int
                baseBorderPaint.color = currentColor
                animationBorderPaint.color = currentColor
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        configurePath(w, h)
        pathMeasure.setPath(path, false)
    }

    private fun configurePath(w: Int, h: Int) {
        path.reset()
        val centerX = w / 2f; val centerY = h / 2f
        val rectHalfHeightPx = dpToPx(RECT_HEIGHT_F / 2); val radiusPx = dpToPx(SEMICIRCLE_RADIUS_F)

        val topArcRect = RectF(centerX - radiusPx, centerY - rectHalfHeightPx - radiusPx, centerX + radiusPx, centerY - rectHalfHeightPx + radiusPx)
        val bottomArcRect = RectF(centerX - radiusPx, centerY + rectHalfHeightPx - radiusPx, centerX + radiusPx, centerY + rectHalfHeightPx + radiusPx)

        path.moveTo(centerX - radiusPx, centerY + rectHalfHeightPx)
        path.lineTo(centerX - radiusPx, centerY - rectHalfHeightPx)
        path.arcTo(topArcRect, 180f, 180f)
        path.lineTo(centerX + radiusPx, centerY + rectHalfHeightPx)
        path.arcTo(bottomArcRect, 0f, 180f)
        path.close()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawPath(path, cutoutPaint)

        // --- 1. Draw the Base Line ---
        if (animatedDashGap > 0.1f) {
            if (cachedDashGap != animatedDashGap) {
                cachedDashEffect = DashPathEffect(floatArrayOf(DASH_LENGTH, animatedDashGap), 0f)
                cachedDashGap = animatedDashGap
            }
            baseBorderPaint.pathEffect = cachedDashEffect
        } else {
            baseBorderPaint.pathEffect = null
        }
        canvas.drawPath(path, baseBorderPaint)

        // --- 2. Draw the Animation Line on top (if visible) ---
        if (animationLineAlpha > 0) {
            animationBorderPaint.alpha = animationLineAlpha
            segmentPath.reset()
            val pathLength = pathMeasure.length
            if (pathLength <= 0) return

            when (currentState) {
                is State.PRE_AUTO_CAPTURE -> {
                    pathMeasure.getSegment(0f, pathLength * currentProgress, segmentPath, true)
                }
                is State.AUTO_CAPTURE_SUCCESS -> {
                    val pathLength = pathMeasure.length
                    if (pathLength <= 0f) return

                    // Add rotation offset to trims
                    val startT = (indeterminateStart + rotationOffset) % 1f
                    val endT   = (indeterminateEnd + rotationOffset) % 1f

                    val startD = pathLength * startT
                    val stopD  = pathLength * endT

                    segmentPath.reset()
                    if (endT < startT) {
                        pathMeasure.getSegment(startD, pathLength, segmentPath, true)
                        overflowPath.reset()
                        pathMeasure.getSegment(0f, stopD, overflowPath, true)
                        segmentPath.addPath(overflowPath)
                    } else {
                        pathMeasure.getSegment(startD, stopD, segmentPath, true)
                    }

                    canvas.drawPath(segmentPath, animationBorderPaint)

                }
                else -> { /* No animation line for other states */ }
            }
            canvas.drawPath(segmentPath, animationBorderPaint)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentAnimatorSet?.cancel()
        stopAutoCaptureSuccessLooper()
    }

    fun getCutoutRect(): RectF {
        val centerX = width / 2f
        val centerY = height / 2f
        val rectHalfHeightPx = dpToPx(RECT_HEIGHT_F / 2f)
        val radiusPx = dpToPx(SEMICIRCLE_RADIUS_F)
        return RectF(
            centerX - radiusPx,
            centerY - rectHalfHeightPx - radiusPx,
            centerX + radiusPx,
            centerY + rectHalfHeightPx + radiusPx
        )
    }
}