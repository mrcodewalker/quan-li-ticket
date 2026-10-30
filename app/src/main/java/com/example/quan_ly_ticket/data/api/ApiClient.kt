package com.example.quan_ly_ticket.data.api

import android.content.Context
import com.example.quan_ly_ticket.data.local.SecurePreferences
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // URL Backend chính thức chạy 24/7 trên VPS Ubuntu (chạy được trên cả máy thật 4G/Wifi lẫn giả lập Emulator)
    var baseUrl: String = "http://42.96.5.186:5000/"

    // Certificate Pinning chống nghe lén và giải mã SSL bởi Burp Suite / Mitmproxy
    var enableCertificatePinning: Boolean = false
    private val certificatePinner = CertificatePinner.Builder()
        // Ghim mã băm SHA-256 Public Key của chứng chỉ Server HTTPS
        .add("42.96.5.186", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .build()

    private var retrofit: Retrofit? = null
    private var apiService: TicketApiService? = null

    fun getService(context: Context): TicketApiService {
        if (apiService != null) return apiService!!

        val securePrefs = SecurePreferences(context)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val token = securePrefs.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(loggingInterceptor)

        if (enableCertificatePinning) {
            clientBuilder.certificatePinner(certificatePinner)
        }

        val okHttpClient = clientBuilder.build()

        val instance = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit = instance
        apiService = instance.create(TicketApiService::class.java)
        return apiService!!
    }

    fun updateBaseUrl(newUrl: String, context: Context) {
        baseUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        retrofit = null
        apiService = null
        getService(context)
    }
}

