# Proguard rules for SecureTicket

# Retrofit & OkHttp
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson data models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.example.quan_ly_ticket.data.model.** { *; }

# RootBeer
-keep class com.scottyab.rootbeer.** { *; }

# AndroidX Biometric & Security Crypto
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }

