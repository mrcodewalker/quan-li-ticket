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

// --- Auth & Dual-Token Models ---
data class User(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName(value = "fullName", alternate = ["full_name"]) val fullName: String,
    @SerializedName("phone") val phone: String? = null,
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
    @SerializedName("expiresIn") val expiresIn: Int = 900,
    @SerializedName("user") val user: User
)

data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class RefreshTokenData(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("expiresIn") val expiresIn: Int = 900
)

data class LogoutRequest(
    @SerializedName("refreshToken") val refreshToken: String?
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
    @SerializedName(value = "current_owner_id", alternate = ["currentOwnerId", "ownerId"]) val currentOwnerId: String? = null,
    @SerializedName(value = "status", alternate = ["ticketStatus"]) val status: String? = "ACTIVE",
    @SerializedName(value = "issued_at", alternate = ["issuedAt"]) val issuedAt: String? = null,
    @SerializedName(value = "issued_timestamp", alternate = ["issuedTimestamp"]) val issuedTimestamp: Long? = null,
    @SerializedName(value = "server_signature", alternate = ["serverSignature"]) val serverSignature: String = "",
    @SerializedName(value = "nonce") val nonce: String = "",
    @SerializedName(value = "qr_secret_seed", alternate = ["qrSecretSeed"]) val qrSecretSeed: String? = null,
    @SerializedName(value = "device_bound_public_key", alternate = ["deviceBoundPublicKey"]) val deviceBoundPublicKey: String? = null,
    @SerializedName(value = "ticket_price", alternate = ["ticketPrice"]) val ticketPrice: Double? = null,
    @SerializedName(value = "transfer_count", alternate = ["transferCount"]) val transferCount: Int? = 0,
    @SerializedName(value = "seat_zone", alternate = ["seatZone"]) val seatZone: String? = null,
    @SerializedName(value = "seat_number", alternate = ["seatNumber"]) val seatNumber: String? = null
)

// --- Anti-scam: xác minh người nhận trước khi chuyển nhượng ---
data class VerifyRecipientRequest(
    @SerializedName("recipientEmail") val recipientEmail: String,
    @SerializedName("ticketId") val ticketId: String? = null
)

data class RecipientVerifyData(
    @SerializedName("recipientId") val recipientId: String,
    @SerializedName("email") val email: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("phoneMasked") val phoneMasked: String?,
    @SerializedName("isDeviceBound") val isDeviceBound: Boolean = false,
    @SerializedName("kycVerified") val kycVerified: Boolean = false,
    @SerializedName("transferCount") val transferCount: Int = 0,
    @SerializedName("maxTransfers") val maxTransfers: Int = 2,
    @SerializedName("canTransfer") val canTransfer: Boolean = true
)

data class BuyTicketRequest(
    @SerializedName("eventId") val eventId: String,
    @SerializedName("passToken") val passToken: String? = null,
    @SerializedName("clientNonce") val clientNonce: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("deviceSignature") val deviceSignature: String? = null
)

// --- Queue / Virtual Waiting Room Models ---
data class JoinQueueRequest(
    @SerializedName("eventId") val eventId: String
)

data class LeaveQueueRequest(
    @SerializedName("queueId") val queueId: String
)

data class CancelOrderRequest(
    @SerializedName("orderId") val orderId: String
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
    @SerializedName("recipientEmail") val recipientEmail: String,
    @SerializedName("clientNonce") val clientNonce: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("deviceSignature") val deviceSignature: String? = null
)

// --- Validation Models ---
data class ValidateQrRequest(
    @SerializedName("qrString") val qrString: String
)

// --- Payment & VietQR Models ---
data class CreateOrderRequest(
    @SerializedName("eventId") val eventId: String,
    @SerializedName("passToken") val passToken: String? = null,
    @SerializedName("clientNonce") val clientNonce: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("deviceSignature") val deviceSignature: String? = null,
    @SerializedName("seatZone") val seatZone: String? = null,
    @SerializedName("seatNumber") val seatNumber: String? = null
)

data class CreateOrderData(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("orderCode") val orderCode: String,
    @SerializedName("eventId") val eventId: String,
    @SerializedName("eventTitle") val eventTitle: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("seatZone") val seatZone: String? = null,
    @SerializedName("seatNumber") val seatNumber: String? = null,
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
    @SerializedName("seatZone") val seatZone: String? = null,
    @SerializedName("seatNumber") val seatNumber: String? = null,
    @SerializedName("eventTitle") val eventTitle: String,
    @SerializedName("transferContent") val transferContent: String,
    @SerializedName("qrImageUrl") val qrImageUrl: String,
    @SerializedName("qrPayload") val qrPayload: String? = null,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("paidAt") val paidAt: String? = null,
    @SerializedName("ticket") val ticket: Ticket? = null
)

// --- Booking Context, Order History, Signed Webhook & Ticket Provenance Models ---
data class ZoneTierInfo(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("multiplier") val multiplier: Double = 1.0,
    @SerializedName("price") val price: Double,
    @SerializedName("perks") val perks: String = "",
    @SerializedName("totalSeats") val totalSeats: Int = 40,
    @SerializedName("availableSeats") val availableSeats: Int = 40,
    @SerializedName("seatNumbers") val seatNumbers: List<String> = emptyList()
)

data class BookingContextData(
    @SerializedName("event") val event: Event,
    @SerializedName("maxTicketsPerEvent") val maxTicketsPerEvent: Int = 6,
    @SerializedName("userActiveTickets") val userActiveTickets: Int = 0,
    @SerializedName("userPendingOrders") val userPendingOrders: Int = 0,
    @SerializedName("userTotalActive") val userTotalActive: Int = 0,
    @SerializedName("zones") val zones: List<ZoneTierInfo> = emptyList(),
    @SerializedName("soldSeats") val soldSeats: List<String> = emptyList(),
    @SerializedName("heldSeats") val heldSeats: List<String> = emptyList()
)

data class OrderHistoryItem(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("orderCode") val orderCode: String,
    @SerializedName("eventId") val eventId: String,
    @SerializedName("eventTitle") val eventTitle: String,
    @SerializedName("eventLocation") val eventLocation: String? = null,
    @SerializedName("eventStartTime") val eventStartTime: String? = null,
    @SerializedName("amount") val amount: Double,
    @SerializedName("status") val status: String,
    @SerializedName("seatZone") val seatZone: String? = null,
    @SerializedName("seatNumber") val seatNumber: String? = null,
    @SerializedName("transferContent") val transferContent: String,
    @SerializedName("qrImageUrl") val qrImageUrl: String? = null,
    @SerializedName("qrPayload") val qrPayload: String? = null,
    @SerializedName("expiresAt") val expiresAt: String,
    @SerializedName("paidAt") val paidAt: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("ttlSeconds") val ttlSeconds: Int = 0,
    @SerializedName("ticketId") val ticketId: String? = null,
    @SerializedName("ticketCode") val ticketCode: String? = null,
    @SerializedName("serverSignatureSnippet") val serverSignatureSnippet: String? = null
)

data class SignedWebhookSimRequest(
    @SerializedName("orderId") val orderId: String? = null,
    @SerializedName("orderCode") val orderCode: String? = null,
    @SerializedName("attackMode") val attackMode: String = "VALID"
)

data class SignedWebhookSimData(
    @SerializedName("ticket") val ticket: Ticket? = null,
    @SerializedName("hmacDigestSnippet") val hmacDigestSnippet: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("receivedSignature") val receivedSignature: String? = null,
    @SerializedName("expectedSignatureSnippet") val expectedSignatureSnippet: String? = null
)

data class ProvenanceTransferHop(
    @SerializedName("id") val id: String,
    @SerializedName("fromName") val fromName: String,
    @SerializedName("fromEmailMasked") val fromEmailMasked: String,
    @SerializedName("toName") val toName: String,
    @SerializedName("toEmailMasked") val toEmailMasked: String,
    @SerializedName("transferSignatureSnippet") val transferSignatureSnippet: String? = null,
    @SerializedName("transferredAt") val transferredAt: String? = null
)

data class TicketProvenanceData(
    @SerializedName("ticketId") val ticketId: String,
    @SerializedName("ticketCode") val ticketCode: String,
    @SerializedName("status") val status: String,
    @SerializedName("seatZone") val seatZone: String? = null,
    @SerializedName("seatNumber") val seatNumber: String? = null,
    @SerializedName("canonicalString") val canonicalString: String,
    @SerializedName("serverSignature") val serverSignature: String,
    @SerializedName("deviceBoundPublicKey") val deviceBoundPublicKey: String? = null,
    @SerializedName("issuedAt") val issuedAt: String? = null,
    @SerializedName("issuedTimestamp") val issuedTimestamp: Long = 0L,
    @SerializedName("originalOwnerName") val originalOwnerName: String = "",
    @SerializedName("originalOwnerEmailMasked") val originalOwnerEmailMasked: String = "",
    @SerializedName("currentOwnerName") val currentOwnerName: String = "",
    @SerializedName("currentOwnerEmailMasked") val currentOwnerEmailMasked: String = "",
    @SerializedName("transfers") val transfers: List<ProvenanceTransferHop> = emptyList()
)

data class RefundTicketRequest(
    @SerializedName("clientNonce") val clientNonce: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("deviceSignature") val deviceSignature: String,
    @SerializedName("reason") val reason: String = "Chủ vé tự yêu cầu hoàn trả"
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

// --- Staff Check-in Logs & Stats ---
data class ValidationStats(
    @SerializedName("totalScans") val totalScans: Int = 0,
    @SerializedName("successCount") val successCount: Int = 0,
    @SerializedName("replayBlockedCount") val replayBlockedCount: Int = 0,
    @SerializedName("rejectedCount") val rejectedCount: Int = 0
)

data class ValidationLogItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("ticket_code") val ticketCode: String? = null,
    @SerializedName("event_title") val eventTitle: String? = null,
    @SerializedName("owner_name") val ownerName: String? = null,
    @SerializedName("method") val method: String? = "QR_ROTATING",
    @SerializedName("result") val result: String? = "SUCCESS",
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("scanned_at") val scannedAt: String? = null
)

data class ValidationLogsResponse(
    @SerializedName("stats") val stats: ValidationStats = ValidationStats(),
    @SerializedName("logs") val logs: List<ValidationLogItem> = emptyList()
)

// --- Organizer Dashboard, Event Management, Ticket Revocation & SIEM Audit Logs ---
data class OrganizerSummary(
    @SerializedName("totalRevenue") val totalRevenue: Double = 0.0,
    @SerializedName("paidOrders") val paidOrders: Int = 0,
    @SerializedName("totalIssued") val totalIssued: Int = 0,
    @SerializedName("activeTickets") val activeTickets: Int = 0,
    @SerializedName("checkedInTickets") val checkedInTickets: Int = 0,
    @SerializedName("revokedTickets") val revokedTickets: Int = 0,
    @SerializedName("securityAlerts") val securityAlerts: Int = 0
)

data class OrganizerEventItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("location") val location: String = "",
    @SerializedName("start_time") val startTime: String = "",
    @SerializedName("total_tickets") val totalTickets: Int = 0,
    @SerializedName("available_tickets") val availableTickets: Int = 0,
    @SerializedName("ticket_price") val ticketPrice: Double = 0.0,
    @SerializedName("status") val status: String = "UPCOMING",
    @SerializedName("issued_count") val issuedCount: Int = 0,
    @SerializedName("used_count") val usedCount: Int = 0
)

data class OrganizerTicketItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("ticket_code") val ticketCode: String = "",
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("issued_at") val issuedAt: String? = null,
    @SerializedName("event_title") val eventTitle: String? = null,
    @SerializedName("owner_name") val ownerName: String? = null,
    @SerializedName("owner_email") val ownerEmail: String? = null
)

data class OrganizerDashboardData(
    @SerializedName("summary") val summary: OrganizerSummary = OrganizerSummary(),
    @SerializedName("events") val events: List<OrganizerEventItem> = emptyList(),
    @SerializedName("recentTickets") val recentTickets: List<OrganizerTicketItem> = emptyList()
)

data class SecurityAuditLogItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("action") val action: String = "",
    @SerializedName("severity") val severity: String = "INFO",
    @SerializedName("ip_address") val ipAddress: String? = null,
    @SerializedName("details") val details: Any? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateEventRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("location") val location: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("totalTickets") val totalTickets: Int,
    @SerializedName("ticketPrice") val ticketPrice: Double
)

data class UpdateEventStatusRequest(
    @SerializedName("status") val status: String
)

data class RevokeTicketRequest(
    @SerializedName("reason") val reason: String
)
