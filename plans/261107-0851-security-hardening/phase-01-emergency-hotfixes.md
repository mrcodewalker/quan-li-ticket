---
phase: 1
title: "Emergency Hotfixes"
status: pending
priority: P1
effort: "1-2d"
dependencies: []
---

# Phase 1: Emergency Hotfixes (Vá Lỗ Hổng Nguy Cấp P0)

## Overview
Khắc phục khẩn cấp các lỗ hổng chí mạng có thể bị khai thác từ Internet mà không làm gián đoạn hệ thống hiện có: chuyển đổi Webhook SePay sang mô hình Fail-Closed loại bỏ hoàn toàn khả năng gọi khống cấp vé; triển khai kiểm tra môi trường Fail-Fast lúc khởi động server Node.js để loại bỏ các chuỗi bí mật tĩnh; đóng kênh truyền thông cleartext HTTP và triệt tiêu rò rỉ dữ liệu phiên/mã vé qua Android Logcat.

---

## Requirements

### Functional Requirements
- **FR-01 (Webhook Fail-Closed):** Endpoint `POST /api/payment/sepay-webhook` bắt buộc có cả `x-sepay-signature` và `x-sepay-timestamp`. Nếu thiếu hoặc sai lệch $\rightarrow$ Reject HTTP 401 ngay lập tức.
- **FR-02 (Anti-Replay Webhook):** Độ lệch thời gian giữa timestamp của SePay và thời gian máy chủ không được vượt quá 300 giây (5 phút).
- **FR-03 (Timing-Safe Comparison):** So sánh chữ ký HMAC-SHA256 bằng hàm `crypto.timingSafeEqual` để chống tấn công phân tích thời gian (Timing Attack).
- **FR-04 (Fail-Fast Environment Validation):** Server Node.js từ chối khởi động (`process.exit(1)`) nếu thiếu một trong các biến: `JWT_SECRET`, `PAYMENT_WEBHOOK_SECRET`, `SEPAY_WEBHOOK_SECRET`, `DB_PASSWORD`, `DB_HOST`, `DB_USER`, `DB_NAME`.
- **FR-05 (Loại bỏ Fallback Secrets):** Xóa toàn bộ chuỗi bí mật mặc định trong mã nguồn (`authMiddleware.js`, `index.js`, `paymentRoutes.js`).
- **FR-06 (Vô hiệu hóa Cleartext HTTP):** Android App cấm hoàn toàn kết nối HTTP không mã hóa đến IP máy chủ `42.96.5.186`.
- **FR-07 (Bảo vệ Logcat):** Tắt log toàn bộ request/response body trên bản Release APK để không in Token và dữ liệu vé ra hệ thống log.

### Non-functional Requirements
- **NFR-01 (Hiệu năng):** Việc kiểm tra chữ ký Webhook và timestamp không làm tăng quá 5ms thời gian phản hồi API.
- **NFR-02 (Bảo toàn dữ liệu):** Không làm ảnh hưởng đến các đơn hàng và vé đã xử lý hợp lệ trước đó.

---

## Architecture & Data Flow

```mermaid
flowchart TD
    subgraph WebhookFlow["Luồng Webhook Thanh toán Mới (Fail-Closed)"]
        W1[SePay Server] -->|POST /api/payment/sepay-webhook| W2{Có đủ headers & Secret?}
        W2 -- Thiếu Sig / Timestamp / Secret --> W3[Reject 401 & Audit Log CRITICAL]
        W2 -- Đầy đủ --> W4{Kiểm tra Replay: |Now - Ts| <= 300s?}
        W4 -- Lệch > 300s --> W5[Reject 401 & Audit Log REPLAY]
        W4 -- Hợp lệ --> W6[Tính HMAC-SHA256 trên rawBody]
        W6 --> W7{crypto.timingSafeEqual?}
        W7 -- Không khớp --> W8[Reject 401 & Audit Log INVALID_SIG]
        W7 -- Khớp chữ ký --> W9[Tiến hành Atomic DB Transaction cấp vé]
    end
```

---

## Related Code Files

### Create
- `backend/src/config/envValidator.js`

### Modify
- `backend/src/index.js`
- `backend/src/routes/paymentRoutes.js`
- `backend/src/middlewares/authMiddleware.js`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/java/com/example/quan_ly_ticket/data/api/ApiClient.kt`

---

## Implementation Steps

### 1. Backend: Xây dựng Module Kiểm Tra Môi Trường Fail-Fast
- Tạo file `backend/src/config/envValidator.js`:
  - Khai báo danh sách các biến môi trường bắt buộc: `['JWT_SECRET', 'PAYMENT_WEBHOOK_SECRET', 'SEPAY_WEBHOOK_SECRET', 'DB_HOST', 'DB_USER', 'DB_PASSWORD', 'DB_NAME']`.
  - Kiểm tra từng biến: nếu `undefined`, rỗng, hoặc chứa các chuỗi mặc định (`TopSecret`, `Secret_2026`) $\rightarrow$ in thông báo lỗi rõ ràng ra console và gọi `process.exit(1)`.
- Nhập và gọi `validateEnv()` tại dòng đầu tiên của hàm `startServer()` trong `backend/src/index.js`.
- Xóa bỏ toàn bộ các toán tử fallback `|| 'fallback_secret'` trong `authMiddleware.js`, `index.js`, `paymentRoutes.js`.

### 2. Backend: Vá Triệt Để Lỗ Hổng Webhook SePay
- Trong `backend/src/routes/paymentRoutes.js` tại route `/sepay-webhook`:
  - Chuyển kiểm tra header thành điều kiện bắt buộc:
    ```javascript
    const sepaySigHeader = req.headers['x-sepay-signature'];
    const sepayTimestamp = req.headers['x-sepay-timestamp'];
    const webhookSecret = process.env.SEPAY_WEBHOOK_SECRET;

    if (!sepaySigHeader || !sepayTimestamp || !webhookSecret) {
      await logSecurityEvent({ action: 'SEPAY_WEBHOOK_UNAUTHORIZED', severity: 'CRITICAL', ip, userAgent });
      return res.status(401).json({ success: false, error: 'Chữ ký webhook và cấu hình bí mật là bắt buộc.' });
    }
    ```
  - Xóa bỏ hoàn toàn nhánh `else` lỏng lẻo (nhánh cho phép bypass khi không có header).
  - Giữ nguyên logic kiểm tra timestamp chống Replay ($\le 300\text{ s}$) và `crypto.timingSafeEqual` khi so sánh chữ ký HMAC-SHA256.

### 3. Android: Đóng Kết Nối Cleartext HTTP
- Chỉnh sửa `app/src/main/res/xml/network_security_config.xml`:
  - Xóa bỏ IP `42.96.5.186` khỏi vùng `cleartextTrafficPermitted="true"`.
  - Cấu hình thẻ `<base-config cleartextTrafficPermitted="false" />` để ép buộc 100% traffic mạng qua HTTPS.

### 4. Android: Chống Rò Rỉ Dữ Liệu Qua Logcat
- Chỉnh sửa `app/src/main/java/com/example/quan_ly_ticket/data/api/ApiClient.kt`:
  - Cấu hình `HttpLoggingInterceptor` dựa trên `BuildConfig.DEBUG`:
    ```kotlin
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
    }
    ```
  - Đảm bảo ngay cả trong chế độ Debug cũng không log toàn bộ Body chứa mật khẩu hay chuỗi seed của vé.

---

## Success Criteria
- [ ] Gửi request POST giả lập thanh toán tới `/api/payment/sepay-webhook` không có header `x-sepay-signature` $\rightarrow$ Nhận phản hồi HTTP `401 Unauthorized`, không có đơn hàng nào được kích hoạt vé.
- [ ] Xóa biến `JWT_SECRET` trong file `backend/.env` và chạy `npm start` $\rightarrow$ Server in thông báo lỗi thiếu cấu hình và tự động dừng tiến trình với exit code 1.
- [ ] Khởi động server với đủ file `.env` $\rightarrow$ Server khởi động bình thường không có cảnh báo.
- [ ] Kiểm tra file `network_security_config.xml` $\rightarrow$ Không còn thẻ cho phép cleartext traffic đến IP `42.96.5.186`.
- [ ] Build bản Release của Android App và chạy lệnh `adb logcat | grep -E "Bearer|qr_secret_seed|password"` $\rightarrow$ Không có bất kỳ dữ liệu nhạy cảm nào xuất hiện trong logcat.

---

## Risk Assessment
- **Rủi ro:** SePay gửi webhook hợp lệ nhưng bị server từ chối do lệch đồng hồ hệ thống.
  - *Dấu hiệu nhận biết:* Logcat hoặc log server ghi nhận `SEPAY_WEBHOOK_REPLAY_ATTACK` liên tục dù chữ ký đúng.
  - *Biện pháp dự phòng:* Đồng bộ thời gian máy chủ VPS qua giao thức NTP (`timedatectl set-ntp true`) trước khi mở bán vé.
- **Rủi ro:** Nhà phát triển quên tạo file `.env` trên môi trường deploy khiến server không thể khởi động.
  - *Dấu hiệu nhận biết:* Server dừng ngay lúc boot với thông báo của `envValidator`.
  - *Biện pháp dự phòng:* Cập nhật file `.env.example` đầy đủ và có tài liệu checklist triển khai.
