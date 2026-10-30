package com.example.quan_ly_ticket.data.api

import com.example.quan_ly_ticket.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface TicketApiService {

    // --- Authentication ---
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<User>>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginData>>

    @POST("api/auth/bind-device")
    suspend fun bindDevice(@Body request: BindDeviceRequest): Response<ApiResponse<Any>>

    @GET("api/auth/profile")
    suspend fun getProfile(): Response<ApiResponse<User>>

    // --- Events ---
    @GET("api/events")
    suspend fun getEvents(): Response<ApiResponse<List<Event>>>

    // --- Virtual Waiting Room / Queue ---
    @POST("api/queue/join")
    suspend fun joinQueue(@Body request: JoinQueueRequest): Response<ApiResponse<QueueData>>

    @GET("api/queue/status/{queueId}")
    suspend fun getQueueStatus(@Path("queueId") queueId: String): Response<ApiResponse<QueueData>>

    // --- Tickets ---
    @POST("api/tickets/buy")
    suspend fun buyTicket(@Body request: BuyTicketRequest): Response<ApiResponse<Ticket>>

    @GET("api/tickets/my-tickets")
    suspend fun getMyTickets(): Response<ApiResponse<List<Ticket>>>

    @GET("api/tickets/{id}/dynamic-qr")
    suspend fun getDynamicQr(@Path("id") ticketId: String): Response<ApiResponse<DynamicQrData>>

    @POST("api/tickets/transfer")
    suspend fun transferTicket(@Body request: TransferTicketRequest): Response<ApiResponse<Any>>

    // --- Payment & VietQR ---
    @POST("api/payment/create-vietqr")
    suspend fun createPaymentOrder(@Body request: CreateOrderRequest): Response<ApiResponse<CreateOrderData>>

    @GET("api/payment/order/{orderId}")
    suspend fun getOrderStatus(@Path("orderId") orderId: String): Response<ApiResponse<OrderStatusData>>

    @POST("api/payment/simulate-transfer")
    suspend fun simulatePayment(@Body request: SimulatePaymentRequest): Response<ApiResponse<Ticket>>

    // --- Staff Validation ---
    @POST("api/validate/validate-qr")
    suspend fun validateQr(@Body request: ValidateQrRequest): Response<ApiResponse<ValidationResultData>>

    @POST("api/validate/nfc-challenge")
    suspend fun getNfcChallenge(): Response<ApiResponse<NfcChallengeData>>

    @POST("api/validate/validate-nfc")
    suspend fun validateNfc(@Body request: ValidateNfcRequest): Response<ApiResponse<ValidationResultData>>

    // --- Cryptography Keys ---
    @GET("api/crypto/server-public-key")
    suspend fun getServerPublicKey(): Response<ApiResponse<ServerCryptoData>>
}

