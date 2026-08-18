package me.hanyuliu.wuzhu

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.TextView

/**
 * Draws a cover over each given rect that fades in from fully transparent to fully opaque
 * over [REVEAL_DURATION_MS] — a video is visible for the first ~10s, then hidden behind an
 * app icon, a rotating reflective prompt, and a running count of videos skipped today. A
 * swipe/scroll resets every active cover back to transparent via [resetAnimations], so the
 * next video (or the same one, scrolled back to) gets its own 10s.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which an enabled AccessibilityService may add without the
 * SYSTEM_ALERT_WINDOW permission. Windows are NOT_TOUCHABLE so taps and swipes always reach
 * the app underneath — a swipe has to actually land on the app to change videos, so this is
 * purely a visual cover; it no longer blocks tapping or scrubbing the video like before.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onFullyCovered: () -> String
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inflater = LayoutInflater.from(service)
    private val prompts = service.resources.getStringArray(R.array.overlay_prompts)
    private val pool = mutableListOf<OverlayHandle>()

    private class OverlayHandle(
        val view: View,
        val params: WindowManager.LayoutParams,
        val animator: ObjectAnimator,
        val promptText: TextView,
        val statText: TextView
    )

    fun syncOverlays(rects: List<Rect>) {
        val usable = rects.filter { it.width() > 0 && it.height() > 0 }

        while (pool.size < usable.size) {
            val handle = createHandle(Rect(0, 0, 1, 1))
            addSafely(handle)
            pool.add(handle)
        }
        while (pool.size > usable.size) {
            removeSafely(pool.removeAt(pool.size - 1))
        }

        for (i in usable.indices) {
            updateBounds(pool[i], usable[i])
        }
    }

    /** Restarts every active cover's 10s reveal countdown from fully transparent. */
    fun resetAnimations() {
        for (handle in pool) {
            handle.animator.cancel()
            handle.view.alpha = 0f
            handle.promptText.text = randomPrompt()
            handle.animator.start()
        }
    }

    fun hideAll() {
        pool.forEach { removeSafely(it) }
        pool.clear()
    }

    private fun createHandle(rect: Rect): OverlayHandle {
        val view = inflater.inflate(R.layout.overlay_block, null)
        view.alpha = 0f
        val promptText = view.findViewById<TextView>(R.id.overlayPromptText)
        val statText = view.findViewById<TextView>(R.id.overlayStatText)
        promptText.text = randomPrompt()

        val params = WindowManager.LayoutParams(
            rect.width().coerceAtLeast(1),
            rect.height().coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }
        val animator = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = REVEAL_DURATION_MS
            interpolator = LinearInterpolator()
        }
        val handle = OverlayHandle(view, params, animator, promptText, statText)

        var wasCanceled = false
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                wasCanceled = false
            }

            override fun onAnimationCancel(animation: Animator) {
                wasCanceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!wasCanceled) {
                    handle.statText.text = onFullyCovered()
                }
            }
        })

        return handle
    }

    private fun randomPrompt(): String = prompts.random()

    private fun updateBounds(handle: OverlayHandle, rect: Rect) {
        val params = handle.params
        if (params.x == rect.left && params.y == rect.top &&
            params.width == rect.width() && params.height == rect.height()
        ) {
            return
        }
        params.x = rect.left
        params.y = rect.top
        params.width = rect.width()
        params.height = rect.height()
        runCatching { windowManager.updateViewLayout(handle.view, params) }
    }

    private fun addSafely(handle: OverlayHandle) {
        runCatching { windowManager.addView(handle.view, handle.params) }
        handle.animator.start()
    }

    private fun removeSafely(handle: OverlayHandle) {
        handle.animator.cancel()
        runCatching { windowManager.removeView(handle.view) }
    }

    companion object {
        private const val REVEAL_DURATION_MS = 10_000L
    }
}
