package app.gov.uidai.capture.utils.extension

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.annotation.StringRes
import androidx.core.animation.doOnEnd
import com.google.android.material.chip.Chip

fun Chip.enablePulseOnTextChange() {
    val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { pulse() }
    }
    this.addTextChangedListener(watcher)
}

fun Chip.pulse() {
    val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f)
    ).apply {
        duration = 120
        interpolator = AccelerateDecelerateInterpolator()
    }

    val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1.1f, 1f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.1f, 1f)
    ).apply {
        duration = 120
        interpolator = OvershootInterpolator()
    }

    AnimatorSet().apply {
        playSequentially(scaleUp, scaleDown)
        start()
    }
}

fun Chip.animateToFitText(@StringRes textRes: Int) {
    val newText = resources.getString(textRes)
    // Measure current size
    val startWidth = width
    val startHeight = height

    // Temporarily set new text and measure offscreen
    val oldText = text
    text = newText
    measure(
        View.MeasureSpec.makeMeasureSpec((parent as View).width, View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec((parent as View).height, View.MeasureSpec.AT_MOST)
    )
    val targetWidth = measuredWidth
    val targetHeight = measuredHeight

    // Restore old text before animating
    text = oldText

    // Animate width/height
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 120
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { anim ->
            val fraction = anim.animatedFraction
            val newWidth = (startWidth + (targetWidth - startWidth) * fraction).toInt()
            val newHeight = (startHeight + (targetHeight - startHeight) * fraction).toInt()
            layoutParams = layoutParams.apply {
                width = newWidth
                height = newHeight
            }
            requestLayout()
        }
        doOnEnd {
            // Finally set the new text and let Chip settle
            text = newText
            layoutParams = layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            requestLayout()
        }
    }
    animator.start()
}


