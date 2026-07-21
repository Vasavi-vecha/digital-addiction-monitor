package com.example.digitaladdictionmonitor.data

import android.content.Context
import java.util.UUID

/**
 * Persists a stable per-install userId so the same device always syncs to
 * the same backend/ml-service user record. Not tied to any real identity --
 * just a locally generated UUID, created once and reused.
 */
object UserIdProvider {
    private const val PREFS_NAME = "digital_addiction_monitor_prefs"
    private const val KEY_USER_ID = "user_id"

    fun getOrCreateUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, newId).apply()
        return newId
    }
}
