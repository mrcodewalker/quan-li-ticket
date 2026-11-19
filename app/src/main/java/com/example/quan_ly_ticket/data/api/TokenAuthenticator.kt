package com.example.quan_ly_ticket.data.api

import android.content.Context
import android.util.Log
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.RefreshTokenRequest
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Tự động xoay vòng cặp khóa phiên (Dual-Token Refresh Rotation - Phase 2 FR-13):
 * Khi Access Token (15 phút) hết hạn và Server trả về HTTP 401:
 * 1. Khóa đồng bộ (Synchronized) để tránh Race Condition khi nhiều request cùng hết hạn.
 * 2. Lấy Refresh Token từ EncryptedSharedPreferences gọi lên `/api/auth/refresh`.
 * 3. Lưu cặp Access Token + Refresh Token mới (Token Rotation) vào vùng nhớ mã hóa.
 * 4. Tự động gửi lại request gốc với Bearer Token mới mà người dùng không hề bị gián đoạn.
 */
class TokenAuthenticator(
    private val context: Context,
    private val baseUrlProvider: () -> String
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Tránh vòng lặp vô hạn nếu chính API refresh hoặc login trả về 401
        val path = response.request.url.encodedPath
        if (path.contains("/api/auth/login") || path.contains("/api/auth/refresh")) {
            return null
        }

        // Nếu đã thử lại quá 2 lần thì dừng
        if (responseCount(response) >= 2) {
            return null
        }

        val securePrefs = SecurePreferences(context)
        val currentRefreshToken = securePrefs.getRefreshToken()
        if (currentRefreshToken.isNullOrBlank()) {
            return null
        }

        // Nếu một luồng khác vừa mới refresh token thành công thì dùng luôn token mới đó
        val latestAccessToken = securePrefs.getAccessToken()
        val requestAuthHeader = response.request.header("Authorization")
        if (!latestAccessToken.isNullOrBlank() && requestAuthHeader != "Bearer $latestAccessToken") {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $latestAccessToken")
                .build()
        }

        return try {
            val refreshClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val refreshRetrofit = Retrofit.Builder()
                .baseUrl(baseUrlProvider())
                .client(refreshClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val authService = refreshRetrofit.create(TicketApiService::class.java)
            val refreshResponse = authService.refreshTokenSync(RefreshTokenRequest(currentRefreshToken)).execute()

            if (refreshResponse.isSuccessful && refreshResponse.body()?.success == true && refreshResponse.body()?.data != null) {
                val newTokens = refreshResponse.body()!!.data!!
                securePrefs.saveAuthTokens(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken,
                    expiresInSeconds = newTokens.expiresIn
                )
                securePrefs.incrementRefreshRotationCount()
                Log.i(TAG, "Dual-Token Refresh Rotation succeeded automatically.")

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
            } else {
                Log.w(TAG, "Refresh token expired or revoked (${refreshResponse.code()}). Clearing session.")
                securePrefs.clear()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
