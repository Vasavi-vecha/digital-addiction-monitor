package com.example.digitaladdictionmonitor.data

import android.content.Context
import com.example.digitaladdictionmonitor.model.LogSyncRequest
import com.example.digitaladdictionmonitor.model.LogSyncResponse
import com.example.digitaladdictionmonitor.model.UsageLogDto
import com.example.digitaladdictionmonitor.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class SyncRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val usageCollector = UsageCollector(context)

    /**
     * Collects everything since the last successful sync (first run defaults
     * to 28 days back, which is enough for ml-service's 4-week personal
     * baseline) and uploads it. Returns null if there was nothing new to
     * send -- that's a normal outcome, not an error.
     */
    suspend fun syncNow(): Result<LogSyncResponse?> = withContext(Dispatchers.IO) {
        try {
            val sinceEpochMillis = prefs.getLong(KEY_LAST_SYNC_END, defaultSinceEpochMillis())
            val collectionStartedAt = System.currentTimeMillis()

            val sessions = usageCollector.collectSince(sinceEpochMillis)
            if (sessions.isEmpty()) {
                prefs.edit().putLong(KEY_LAST_SYNC_END, collectionStartedAt).apply()
                return@withContext Result.success(null)
            }

            val userId = UserIdProvider.getOrCreateUserId(context)
            val logs = sessions.map { it.toDto(context) }
            val response = RetrofitClient.api.syncLogs(LogSyncRequest(userId, logs))

            prefs.edit().putLong(KEY_LAST_SYNC_END, collectionStartedAt).apply()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun currentUserId(): String = UserIdProvider.getOrCreateUserId(context)

    private fun defaultSinceEpochMillis(): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(28)

    private fun CollectedSession.toDto(context: Context): UsageLogDto {
        val instant = Instant.ofEpochMilli(startEpochMillis)
        val localHour = instant.atZone(ZoneId.systemDefault()).hour
        return UsageLogDto(
            timestamp = instant.toString(),
            appName = AppCategoryMapper.displayNameFor(context, packageName),
            appCategory = AppCategoryMapper.categoryFor(context, packageName),
            sessionDurationSec = durationSec,
            unlockCount = if (unlockPreceded) 1 else 0,
            isNightUsage = localHour in 0..4
        )
    }

    companion object {
        private const val PREFS_NAME = "digital_addiction_monitor_prefs"
        private const val KEY_LAST_SYNC_END = "last_sync_end_epoch_millis"
    }
}
