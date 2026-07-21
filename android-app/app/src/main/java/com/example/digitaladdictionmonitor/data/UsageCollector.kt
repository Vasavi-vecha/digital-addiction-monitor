package com.example.digitaladdictionmonitor.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/** One reconstructed foreground session for a single app. */
data class CollectedSession(
    val packageName: String,
    val startEpochMillis: Long,
    val durationSec: Double,
    val unlockPreceded: Boolean
)

/**
 * Rebuilds per-app foreground sessions from Android's raw UsageEvents stream
 * -- UsageStatsManager only gives aggregate per-day totals directly, but the
 * backend's sync schema wants one row per session (matching how
 * ml-service/data/generator.py models usage), so this pairs up
 * MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND events per package to reconstruct
 * that shape from real device data.
 */
class UsageCollector(private val context: Context) {

    fun collectSince(sinceEpochMillis: Long): List<CollectedSession> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(sinceEpochMillis, end)
        val event = UsageEvents.Event()

        val foregroundStart = mutableMapOf<String, Long>()
        val unlockTimestamps = mutableListOf<Long>()
        val sessions = mutableListOf<CollectedSession>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    foregroundStart[event.packageName] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = foregroundStart.remove(event.packageName)
                    if (start != null && event.timeStamp > start) {
                        sessions += CollectedSession(
                            packageName = event.packageName,
                            startEpochMillis = start,
                            durationSec = (event.timeStamp - start) / 1000.0,
                            unlockPreceded = unlockTimestamps.any { it in (start - UNLOCK_WINDOW_MS)..start }
                        )
                    }
                }
                KEYGUARD_HIDDEN_EVENT_TYPE -> unlockTimestamps += event.timeStamp
            }
        }
        // Sessions still open at query end (foregroundStart left over) are
        // dropped rather than guessed at -- they'll show up complete on the
        // next sync once the app is actually backgrounded.
        return sessions
    }

    companion object {
        private const val UNLOCK_WINDOW_MS = 30_000L

        // UsageEvents.Event.KEYGUARD_HIDDEN was added in API 29; minSdk here
        // is 26, so this degrades to "0 unlocks detected" on API 26-28
        // instead of failing to compile/crashing on older devices.
        private val KEYGUARD_HIDDEN_EVENT_TYPE: Int =
            if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.KEYGUARD_HIDDEN else -1
    }
}
