package com.example.quan_ly_ticket.data.api

import android.content.Context
import com.example.quan_ly_ticket.BuildConfig
import com.example.quan_ly_ticket.data.local.SecurePreferences
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // URL Backend chạy 24/7 trên VPS Ubuntu (chạy được trên cả máy thật 4G/Wifi lẫn giả lập Emulator)
    var baseUrl: String = "http://42.96.5.186:5000/"

    // Phase 3 (FR-18): Certificate Pinning chống nghe lén SSL MITM (Burp Suite / Mitmproxy)
    // Ghim 2 mã băm SPKI SHA-256 (Primary Server Cert + Backup Let's Encrypt ISRG Root X1)
    var enableCertificatePinning: Boolean = !BuildConfig.DEBUG
    private val certificatePinner = CertificatePinner.Builder()
        .add("api.secureticket.vn", "sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=")
        .add("api.secureticket.vn", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        .build()

    private var retrofit: Retrofit? = null
    private var apiService: TicketApiService? = null

    fun getService(context: Context): TicketApiService {
        if (apiService != null) return apiService!!

        val appContext = context.applicationContext
        val securePrefs = SecurePreferences(appContext)

        // Phase 1 (FR-07): Bảo vệ Logcat - Không in Body chứa mật khẩu, JWT hay qr_secret_seed ra Logcat
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("X-Device-Signature")
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val token = securePrefs.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .authenticator(TokenAuthenticator(appContext) { baseUrl })
            .addInterceptor(loggingInterceptor)

        if (enableCertificatePinning && baseUrl.startsWith("https://")) {
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
