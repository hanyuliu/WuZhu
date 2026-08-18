package me.hanyuliu.wuzhu

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Video, in any app, is ultimately drawn to one of these Android framework render surfaces.
 * Matching by class-name substring catches custom subclasses too (e.g. RedNote's XYVideoView,
 * which itself contains "VideoView"). This is how WuZhu recognizes "a video is playing here,"
 * regardless of which app or UI wraps it — and unlike an app's internal resource IDs, these
 * class names don't change across app updates.
 */
object BlockRules {
    val VIDEO_SURFACE_CLASSES = listOf("VideoView", "TextureView", "SurfaceView")

    /**
     * The two swipeable-feed container widgets seen across target apps so far: YouTube Shorts'
     * `reel_recycler` is a RecyclerView, TikTok's `viewpager` (id, ironically, still named
     * "viewpager") is an androidx ViewPager. Both get treated the same by the two rules below —
     * "is this video inside/under a paging feed" doesn't care which widget implements the paging.
     */
    private val PAGING_CONTAINER_CLASSES = listOf("RecyclerView", "ViewPager")

    private fun AccessibilityNodeInfo?.isPagingContainer(): Boolean {
        val cls = this?.className ?: return false
        return PAGING_CONTAINER_CLASSES.any { cls.contains(it, ignoreCase = true) }
    }

    /**
     * True if a paging container (see [PAGING_CONTAINER_CLASSES]) sits somewhere above [node]
     * in the tree. Feeds that give each swiped item its own player (TikTok, and presumably
     * Instagram/Facebook-style) put the video surface inside that item's row/page, so this
     * alone distinguishes a feed video from one that isn't (e.g. a video sitting in a plain,
     * non-scrolling detail screen). Confirmed against TikTok's real tree: its video
     * `TextureView` (id `vki`) sits inside `player_view` → ... → the `viewpager` ViewPager.
     *
     * Known gap: YouTube Shorts doesn't fit this — its `reel_watch_player` surface is a
     * *sibling* of `reel_recycler`, not a descendant, because it reuses one shared SurfaceView
     * across items rather than giving each RecyclerView row its own. YouTube is registered
     * against [sharesBoundsWithPagingContainer] below instead.
     */
    private fun isDescendantOfPagingContainer(node: AccessibilityNodeInfo): Boolean {
        var ancestor = node.parent
        while (ancestor != null) {
            if (ancestor.isPagingContainer()) return true
            ancestor = ancestor.parent
        }
        return false
    }

    /**
     * True if some paging container elsewhere in the window exactly overlaps [node]'s bounds,
     * even though it isn't an ancestor. This exists for apps like YouTube Shorts that keep a
     * single shared SurfaceView for playback instead of one per feed item (creating/destroying
     * a real Surface per item is expensive) — that surface is laid out as a *sibling* of the
     * RecyclerView/ViewPager driving the swipe, both stretched to the same full-screen rect, so
     * [isDescendantOfPagingContainer] can never see it. Matching by identical bounds instead
     * catches this "shared surface stacked under the feed" pattern regardless of where in the
     * tree the surface actually lives.
     */
    private fun sharesBoundsWithPagingContainer(node: AccessibilityNodeInfo): Boolean {
        val targetBounds = Rect().also { node.getBoundsInScreen(it) }
        val root = generateSequence(node) { it.parent }.last()
        return hasMatchingPagingContainer(root, targetBounds, depth = 0)
    }

    private fun hasMatchingPagingContainer(node: AccessibilityNodeInfo, targetBounds: Rect, depth: Int): Boolean {
        if (depth > MAX_SIBLING_SEARCH_DEPTH) return false
        if (node.isPagingContainer()) {
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (bounds == targetBounds) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasMatchingPagingContainer(child, targetBounds, depth + 1)) return true
        }
        return false
    }

    /**
     * "Should this matched surface be covered." Class-name matching alone only proves a
     * surface renders video, not that it's the short-form feed WuZhu targets (a regular,
     * long-form YouTube video also uses a SurfaceView). A surface counts as feed video if
     * either paging-container heuristic matches — covers apps that give each feed item its
     * own player (descendant) and apps that reuse one shared surface across the feed
     * (shares bounds), without needing to know which app is in the foreground.
     */
    fun shouldOverlay(surfaceNode: AccessibilityNodeInfo): Boolean =
        isDescendantOfPagingContainer(surfaceNode) || sharesBoundsWithPagingContainer(surfaceNode)

    private const val MAX_SIBLING_SEARCH_DEPTH = 60
}
