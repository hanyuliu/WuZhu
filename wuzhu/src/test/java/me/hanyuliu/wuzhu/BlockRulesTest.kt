package me.hanyuliu.wuzhu

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BlockRulesTest {

    private fun node(className: String, bounds: Rect = Rect()): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        node.className = className
        node.setBoundsInScreen(bounds)
        return node
    }

    private fun addChild(parent: AccessibilityNodeInfo, child: AccessibilityNodeInfo) {
        shadowOf(parent).addChild(child)
    }

    @Test
    fun `video surface nested inside a RecyclerView feed is overlaid`() {
        val recyclerView = node("androidx.recyclerview.widget.RecyclerView")
        val itemRow = node("android.widget.FrameLayout")
        val textureView = node("android.view.TextureView")
        addChild(recyclerView, itemRow)
        addChild(itemRow, textureView)

        assertTrue(BlockRules.shouldOverlay(textureView))
    }

    @Test
    fun `video surface nested inside a ViewPager feed is overlaid`() {
        val viewPager = node("androidx.viewpager.widget.ViewPager")
        val page = node("android.widget.FrameLayout")
        val surfaceView = node("me.example.XYVideoView")
        addChild(viewPager, page)
        addChild(page, surfaceView)

        assertTrue(BlockRules.shouldOverlay(surfaceView))
    }

    @Test
    fun `video surface with no paging ancestor and no bounds match is not overlaid`() {
        val root = node("android.widget.FrameLayout")
        val detailScreen = node("android.widget.FrameLayout")
        val videoView = node("android.widget.VideoView", Rect(0, 0, 1080, 1920))
        addChild(root, detailScreen)
        addChild(detailScreen, videoView)

        assertFalse(BlockRules.shouldOverlay(videoView))
    }

    @Test
    fun `video surface sharing bounds with a sibling paging container is overlaid`() {
        val fullScreenBounds = Rect(0, 0, 1080, 1920)
        val root = node("android.widget.FrameLayout")
        val recyclerView = node("androidx.recyclerview.widget.RecyclerView", fullScreenBounds)
        val sharedSurface = node("android.view.SurfaceView", fullScreenBounds)
        addChild(root, recyclerView)
        addChild(root, sharedSurface)

        assertTrue(BlockRules.shouldOverlay(sharedSurface))
    }

    @Test
    fun `video surface with different bounds than a sibling paging container is not overlaid`() {
        val root = node("android.widget.FrameLayout")
        val recyclerView = node("androidx.recyclerview.widget.RecyclerView", Rect(0, 0, 1080, 1920))
        val unrelatedSurface = node("android.view.SurfaceView", Rect(0, 0, 100, 100))
        addChild(root, recyclerView)
        addChild(root, unrelatedSurface)

        assertFalse(BlockRules.shouldOverlay(unrelatedSurface))
    }
}
