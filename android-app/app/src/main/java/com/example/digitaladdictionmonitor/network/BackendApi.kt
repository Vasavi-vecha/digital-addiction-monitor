package com.example.digitaladdictionmonitor.network

import com.example.digitaladdictionmonitor.model.DashboardResponse
import com.example.digitaladdictionmonitor.model.LogSyncRequest
import com.example.digitaladdictionmonitor.model.LogSyncResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BackendApi {

    @POST("api/logs/sync")
    suspend fun syncLogs(@Body request: LogSyncRequest): LogSyncResponse

    @GET("api/dashboard/{userId}")
    suspend fun getDashboard(@Path("userId") userId: String): DashboardResponse
}
