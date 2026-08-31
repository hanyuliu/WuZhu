package me.hanyuliu.wuzhu

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/** Stores the daily skipped-video count. */
class PrefsManager(context: Context, private val today: () -> LocalDate = LocalDate::now) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Increments and returns today's count of fully-covered videos, resetting it daily. */
    fun incrementTodaysSkippedCount(): Int {
        val today = today().toString()
        val count = if (prefs.getString(KEY_SKIPPED_COUNT_DATE, null) == today) {
            prefs.getInt(KEY_SKIPPED_COUNT, 0) + 1
        } else {
            1
        }
        prefs.edit {
            putString(KEY_SKIPPED_COUNT_DATE, today)
            putInt(KEY_SKIPPED_COUNT, count)
        }
        return count
    }

    companion object {
        private const val PREFS_NAME = "wuzhu_prefs"
        private const val KEY_SKIPPED_COUNT = "skipped_count"
        private const val KEY_SKIPPED_COUNT_DATE = "skipped_count_date"
    }
}
