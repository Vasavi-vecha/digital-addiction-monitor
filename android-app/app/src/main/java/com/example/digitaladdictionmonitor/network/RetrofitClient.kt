package com.example.digitaladdictionmonitor.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * Targets a real device over USB: run `adb reverse tcp:8080 tcp:8080`
     * once per debug session first, which makes the device's own
     * localhost:8080 forward to the host PC's localhost:8080 where the
     * Spring Boot backend runs.
     *
     * If this ever moves back to an Android *emulator* instead of a real
     * device, change this to "http://10.0.2.2:8080/" -- that's the
     * emulator's special alias for the host's localhost, and `adb reverse`
     * doesn't apply there (the emulator has its own virtual network).
     *
     * Cleartext (non-HTTPS) is allowed for this local-dev target via
     * android:usesCleartextTraffic in the manifest -- fine for a local
     * backend during development, not for a shipped app talking to a real
     * server.
     */
    private const val BASE_URL = "http://localhost:8080/"

    val api: BackendApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }
}
