package com.example.quan_ly_ticket.data.api

import com.example.quan_ly_ticket.data.model.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface TicketApiService {

    // --- Authentication & Dual-Token Rotation ---
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<User>>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginData>>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<ApiResponse<RefreshTokenData>>

    @POST("api/auth/refresh")
    fun refreshTokenSync(@Body request: RefreshTokenRequest): Call<ApiResponse<RefreshTokenData>>

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<ApiResponse<Any>>

    @POST("api/auth/bind-device")
    suspend fun bindDevice(@Body request: BindDeviceRequest): Response<ApiResponse<Any>>

    @GET("api/auth/profile")
    suspend fun getProfile(): Response<ApiResponse<User>>

    // --- Events ---
    @GET("api/events")
    suspend fun getEvents(): Response<ApiResponse<List<Event>>>

    @GET("api/events/{id}/booking-context")
    suspend fun getEventBookingContext(@Path("id") eventId: String): Response<ApiResponse<BookingContextData>>

    // --- Virtual Waiting Room / Queue ---
    @POST("api/queue/join")
    suspend fun joinQueue(@Body request: JoinQueueRequest): Response<ApiResponse<QueueData>>

    @GET("api/queue/status/{queueId}")
    suspend fun getQueueStatus(@Path("queueId") queueId: String): Response<ApiResponse<QueueData>>

    @POST("api/queue/leave")
    suspend fun leaveQueue(@Body request: LeaveQueueRequest): Response<ApiResponse<Any>>

    // --- Tickets ---
    @POST("api/tickets/buy")
    suspend fun buyTicket(@Body request: BuyTicketRequest): Response<ApiResponse<Ticket>>

    @GET("api/tickets/my-tickets")
    suspend fun getMyTickets(): Response<ApiResponse<List<Ticket>>>

    @GET("api/tickets/{id}/dynamic-qr")
    suspend fun getDynamicQr(@Path("id") ticketId: String): Response<ApiResponse<DynamicQrData>>

    @POST("api/tickets/verify-recipient")
    suspend fun verifyRecipient(@Body request: VerifyRecipientRequest): Response<ApiResponse<RecipientVerifyData>>

    @POST("api/tickets/transfer")
    suspend fun transferTicket(@Body request: TransferTicketRequest): Response<ApiResponse<Any>>

    @GET("api/tickets/{id}/provenance")
    suspend fun getTicketProvenance(@Path("id") ticketId: String): Response<ApiResponse<TicketProvenanceData>>

    @POST("api/tickets/{id}/refund")
    suspend fun refundTicket(
        @Path("id") ticketId: String,
        @Body request: RefundTicketRequest
    ): Response<ApiResponse<Any>>

    // --- Payment & VietQR ---
    @POST("api/payment/create-vietqr")
    suspend fun createPaymentOrder(@Body request: CreateOrderRequest): Response<ApiResponse<CreateOrderData>>

    @GET("api/payment/my-orders")
    suspend fun getMyOrders(): Response<ApiResponse<List<OrderHistoryItem>>>

    @GET("api/payment/order/{orderId}")
    suspend fun getOrderStatus(@Path("orderId") orderId: String): Response<ApiResponse<OrderStatusData>>

    @POST("api/payment/simulate-transfer")
    suspend fun simulatePayment(@Body request: SimulatePaymentRequest): Response<ApiResponse<Ticket>>

    @POST("api/payment/simulate-signed-webhook")
    suspend fun simulateSignedWebhook(@Body request: SignedWebhookSimRequest): Response<ApiResponse<SignedWebhookSimData>>

    @POST("api/payment/cancel-order")
    suspend fun cancelOrder(@Body request: CancelOrderRequest): Response<ApiResponse<Any>>

    // --- Staff Validation ---
    @POST("api/validate/validate-qr")
    suspend fun validateQr(@Body request: ValidateQrRequest): Response<ApiResponse<ValidationResultData>>

    @POST("api/validate/nfc-challenge")
    suspend fun getNfcChallenge(): Response<ApiResponse<NfcChallengeData>>

    @POST("api/validate/validate-nfc")
    suspend fun validateNfc(@Body request: ValidateNfcRequest): Response<ApiResponse<ValidationResultData>>

    @GET("api/validate/logs")
    suspend fun getValidationLogs(): Response<ApiResponse<ValidationLogsResponse>>

    // --- Organizer Management & SIEM ---
    @GET("api/events/organizer/dashboard")
    suspend fun getOrganizerDashboard(): Response<ApiResponse<OrganizerDashboardData>>

    @GET("api/events/organizer/audit-logs")
    suspend fun getSecurityAuditLogs(): Response<ApiResponse<List<SecurityAuditLogItem>>>

    @POST("api/events")
    suspend fun createEvent(@Body request: CreateEventRequest): Response<ApiResponse<Any>>

    @PUT("api/events/{id}/status")
    suspend fun updateEventStatus(
        @Path("id") eventId: String,
        @Body request: UpdateEventStatusRequest
    ): Response<ApiResponse<Any>>

    @POST("api/tickets/{id}/revoke")
    suspend fun revokeTicket(
        @Path("id") ticketId: String,
        @Body request: RevokeTicketRequest
    ): Response<ApiResponse<Any>>

    // --- Cryptography Keys ---
    @GET("api/crypto/server-public-key")
    suspend fun getServerPublicKey(): Response<ApiResponse<ServerCryptoData>>
}
