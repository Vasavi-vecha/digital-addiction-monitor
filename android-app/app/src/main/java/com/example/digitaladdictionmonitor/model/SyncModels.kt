package com.example.digitaladdictionmonitor.model

/**
 * Wire shapes for POST /api/logs/sync. Field names match the backend's
 * UsageLogDto/LogSyncRequest/LogSyncResponse (backend/src/main/java/.../dto)
 * exactly, camelCase-for-camelCase, so Gson needs no field mapping.
 */
data class UsageLogDto(
    val timestamp: String, // ISO-8601 instant, e.g. "2025-01-06T08:30:00Z"
    val appName: String,
    val appCategory: String,
    val sessionDurationSec: Double,
    val unlockCount: Int,
    val isNightUsage: Boolean
)

data class LogSyncRequest(
    val userId: String,
    val logs: List<UsageLogDto>
)

data class LogSyncResponse(
    val userId: String,
    val acceptedCount: Int,
    val message: String
)
