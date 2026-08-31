package me.hanyuliu.wuzhu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `first increment of the day returns 1`() {
        val prefs = PrefsManager(context) { LocalDate.of(2026, 1, 1) }

        assertEquals(1, prefs.incrementTodaysSkippedCount())
    }

    @Test
    fun `repeated increments on the same day accumulate`() {
        val prefs = PrefsManager(context) { LocalDate.of(2026, 1, 1) }

        prefs.incrementTodaysSkippedCount()
        prefs.incrementTodaysSkippedCount()
        val third = prefs.incrementTodaysSkippedCount()

        assertEquals(3, third)
    }

    @Test
    fun `count resets when the day changes`() {
        var today = LocalDate.of(2026, 1, 1)
        val prefs = PrefsManager(context) { today }

        prefs.incrementTodaysSkippedCount()
        prefs.incrementTodaysSkippedCount()
        today = LocalDate.of(2026, 1, 2)
        val afterRollover = prefs.incrementTodaysSkippedCount()

        assertEquals(1, afterRollover)
    }

    @Test
    fun `count persists separately across PrefsManager instances backed by the same context`() {
        val day = LocalDate.of(2026, 1, 1)
        val first = PrefsManager(context) { day }
        first.incrementTodaysSkippedCount()
        first.incrementTodaysSkippedCount()

        val second = PrefsManager(context) { day }
        val result = second.incrementTodaysSkippedCount()

        assertEquals(3, result)
    }
}
