package me.hanyuliu.wuzhu

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShortVideoAccessibilityService : AccessibilityService() {

    private lateinit var overlay: OverlayController
    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())

    private var scanScheduled = false
    private var scrollDetected = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(this)
        overlay = OverlayController(
            service = this,
            onFullyCovered = {
                val count = prefs.incrementTodaysSkippedCount()
                resources.getQuantityString(R.plurals.overlay_stat_format, count, count)
            }
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Deliberately ignore event.packageName here: events arrive from whichever window
        // last changed, including our own FLAG_NOT_FOCUSABLE overlay (its add/remove fires
        // TYPE_WINDOW_STATE_CHANGED) and unrelated system windows (status bar, IME). Trusting
        // that as "the current app" caused the overlay to hide itself within ~1s of appearing.
        // rootInActiveWindow, read in performScan(), reflects the true focused window instead —
        // our overlay can never become that, since it's explicitly non-focusable.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            scrollDetected = true
        }
        scheduleScan()
    }

    override fun onInterrupt() {
        overlay.hideAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        overlay.hideAll()
    }

    private fun scheduleScan() {
        if (scanScheduled) return
        scanScheduled = true
        handler.postDelayed({
            scanScheduled = false
            performScan()
        }, SCAN_DEBOUNCE_MS)
    }

    private fun performScan() {
        val root = rootInActiveWindow

        if (root == null) {
            overlay.hideAll()
            scrollDetected = false
            return
        }

        val rects = mutableListOf<Rect>()
        collectVideoRects(root, rects, depth = 0)
        overlay.syncOverlays(rects)

        if (scrollDetected) {
            overlay.resetAnimations()
            scrollDetected = false
        }
    }

    private fun collectVideoRects(
        node: AccessibilityNodeInfo?,
        out: MutableList<Rect>,
        depth: Int
    ) {
        if (node == null || depth > MAX_TRAVERSAL_DEPTH) return

        val cls = node.className?.toString()
        val isVideoSurface = cls != null && BlockRules.VIDEO_SURFACE_CLASSES.any { cls.contains(it, ignoreCase = true) }
        if (isVideoSurface && BlockRules.shouldOverlay(node)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) out.add(rect)
        }

        for (i in 0 until node.childCount) {
            collectVideoRects(node.getChild(i), out, depth + 1)
        }
    }

    companion object {
        private const val SCAN_DEBOUNCE_MS = 150L
        private const val MAX_TRAVERSAL_DEPTH = 60
    }
}
