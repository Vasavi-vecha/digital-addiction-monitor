package com.example.digitaladdictionmonitor.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * Targets the backend over WiFi -- both this phone and the laptop
     * running `mvn spring-boot:run` must be on the same WiFi network.
     *
     * TODO before running: replace 192.168.1.100 below with your laptop's
     * actual WiFi IPv4 address. Find it with:
     *   Windows:      ipconfig            -> "Wireless LAN adapter Wi-Fi" -> IPv4 Address
     *   macOS/Linux:  ifconfig | ipconfig getifaddr en0
     * This address can change each time the laptop reconnects to WiFi
     * (unless the router assigns a static/reserved IP), so re-check it if
     * the app suddenly can't reach the backend.
     *
     * Two other options, only if you're not on the same WiFi:
     *  - USB + `adb reverse tcp:8080 tcp:8080`, then use "http://localhost:8080/"
     *  - Android *emulator* (not a real device): "http://10.0.2.2:8080/"
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
