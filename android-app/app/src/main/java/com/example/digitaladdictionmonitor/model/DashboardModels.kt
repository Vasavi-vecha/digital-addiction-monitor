package com.example.digitaladdictionmonitor.model

/** Wire shape for GET /api/dashboard/{userId} -- matches backend/.../dto/DashboardResponse.java. */
data class DashboardResponse(
    val userId: String,
    val riskScore: Double,
    val trendDirection: String,
    val weeklyCategoryTotalsMinutes: Map<String, Double>,
    val focusMetrics: FocusMetricsDto,
    val baselineDeltas: List<BaselineDeltaDto>,
    val clusterPersona: String,
    val suggestions: List<SuggestionDto>
)

data class FocusMetricsDto(
    val contextSwitchRate: Double,
    val deepFocusMinutes: Double
)

data class BaselineDeltaDto(
    val metric: String,
    val currentValue: Double,
    val previousValue: Double,
    val pctChange: Double
)

data class SuggestionDto(
    val metric: String,
    val message: String,
    val expectedImpact: Double
)

/** Matches backend/.../dto/ErrorResponse.java, for parsing error bodies into a readable message. */
data class ErrorResponseDto(
    val status: Int,
    val error: String,
    val message: String,
    val path: String?,
    val details: List<String>?
)
