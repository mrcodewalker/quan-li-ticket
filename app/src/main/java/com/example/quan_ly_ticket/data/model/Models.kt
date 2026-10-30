package com.example.quan_ly_ticket.data.model

import com.google.gson.annotations.SerializedName

// --- Generic API Wrapper ---
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("data") val data: T? = null
)

// --- Auth Models ---
data class User(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("role") val role: String,
    @SerializedName("isDeviceBound") val isDeviceBound: Boolean = false
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginData(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("user") val user: User
)

data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("role") val role: String = "CUSTOMER"
)

data class BindDeviceRequest(
    @SerializedName("devicePublicKey") val devicePublicKey: String
)

// --- Event Models ---
data class Event(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("location") val location: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("total_tickets") val totalTickets: Int,
    @SerializedName("available_tickets") val availableTickets: Int,
    @SerializedName("ticket_price") val ticketPrice: Double,
    @SerializedName("status") val status: String
)

// --- Ticket Models ---
data class Ticket(
    @SerializedName(value = "id", alternate = ["ticketId", "ticket_id"]) val id: String = "",
    @SerializedName(value = "ticket_code", alternate = ["ticketCode"]) val ticketCode: String = "",
    @SerializedName(value = "event_id", alternate = ["eventId"]) val eventId: String? = null,
    @SerializedName(value = "event_title", alternate = ["eventTitle"]) val eventTitle: String? = null,
    @SerializedName(value = "event_location", alternate = ["eventLocation", "location"]) val eventLocation: String? = null,
    @SerializedName(value = "event_start_time", alternate = ["eventStartTime", "startTime"]) val eventStartTime: String? = null,
    @SerializedName(value = "status", alternate = ["ticketStatus"]) val status: String? = "ACTIVE",
    @SerializedName(value = "issued_at", alternate = ["issuedAt"]) val issuedAt: String? = null,
    @SerializedName(value = "server_signature", alternate = ["serverSignature"]) val serverSignature: String = "",
    @SerializedName(value = "nonce") val nonce: String = "",
    @SerializedName(value = "qr_secret_seed", alternate = ["qrSecretSeed"]) val qrSecretSeed: String? = null,
    @SerializedName(value = "device_bound_public_key", alternate = ["deviceBoundPublicKey"]) val deviceBoundPublicKey: String? = null
)

data class BuyTicketRequest(
    @SerializedName("eventId") val eventId: String,
    @SerializedName("passToken") val passToken: String? = null
)

// --- Queue / Virtual Waiting Room Models ---
data class JoinQueueRequest(
    @SerializedName("eventId") val eventId: String
)

data class QueueData(
    @SerializedName("queueId") val queueId: String,
    @SerializedName("status") val status: String,
    @SerializedName("passToken") val passToken: String? = null,
    @SerializedName("queueNumber") val queueNumber: Int? = null,
    @SerializedName("usersAhead") val usersAhead: Int? = null,
    @SerializedName("estimatedWaitSeconds") val estimatedWaitSeconds: Int? = null,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Int? = null
)

data class DynamicQrData(
    @SerializedName("ticketId") val ticketId: String,
    @SerializedName("timeSlice") val timeSlice: Long,
    @SerializedName("totpToken") val totpToken: String,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Int,
    @SerializedName("qrString") val qrString: String
)

data class TransferTicketRequest(
    @SerializedName("ticketId") val ticketId: String,
    @SerializedName("recipientEmail") val recipientEmail: String
)

// --- Validation Models ---
data class ValidateQrRequest(
    @SerializedName("qrString") val qrString: String
)

// --- Payment & VietQR Models ---
data class CreateOrderRequest(
    @SerializedName("eventId") val eventId: String,
    @SerializedName("passToken") val passToken: String? = null
)

data class CreateOrderData(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("orderCode") val orderCode: String,
    @SerializedName("eventId") val eventId: String,
    @SerializedName("eventTitle") val eventTitle: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("bankId") val bankId: String,
    @SerializedName("bankAccountNo") val bankAccountNo: String,
    @SerializedName("accountName") val accountName: String,
    @SerializedName("transferContent") val transferContent: String,
    @SerializedName("qrImageUrl") val qrImageUrl: String,
    @SerializedName("qrPayload") val qrPayload: String? = null,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("ttlSeconds") val ttlSeconds: Int
)

data class OrderStatusData(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("orderCode") val orderCode: String,
    @SerializedName("status") val status: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("eventTitle") val eventTitle: String,
    @SerializedName("transferContent") val transferContent: String,
    @SerializedName("qrImageUrl") val qrImageUrl: String,
    @SerializedName("qrPayload") val qrPayload: String? = null,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("paidAt") val paidAt: String? = null,
    @SerializedName("ticket") val ticket: Ticket? = null
)

data class SimulatePaymentRequest(
    @SerializedName("orderId") val orderId: String? = null,
    @SerializedName("orderCode") val orderCode: String? = null
)

data class NfcChallengeData(
    @SerializedName("challengeId") val challengeId: String,
    @SerializedName("challenge") val challenge: String,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Int
)

data class ValidateNfcRequest(
    @SerializedName("ticketId") val ticketId: String,
    @SerializedName("challengeId") val challengeId: String,
    @SerializedName("deviceSignature") val deviceSignature: String
)

data class ValidationResultData(
    @SerializedName("ticketCode") val ticketCode: String?,
    @SerializedName("eventTitle") val eventTitle: String?,
    @SerializedName("ownerName") val ownerName: String?,
    @SerializedName("checkInTime") val checkInTime: String?
)

// --- Server Public Key Response ---
data class ServerCryptoData(
    @SerializedName("algorithm") val algorithm: String,
    @SerializedName("curve") val curve: String,
    @SerializedName("publicKeyPem") val publicKeyPem: String,
    @SerializedName("publicKeyBase64") val publicKeyBase64: String
)

