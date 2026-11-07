---
title: "security-hardening"
description: "Kế hoạch kiên cố hóa bảo mật toàn diện cho hệ thống SecureTicket (Backend Node.js & Android App) theo chuẩn OWASP ASVS v4.0 L2 và MASVS v2.0"
status: pending
priority: P1
effort: "5-6d"
tags: ["security", "owasp", "backend", "android", "ecdsa", "rasp"]
created: 2026-11-07
---

# Kế Hoạch Kiên Cố Hóa Bảo Mật Toàn Diện (SecureTicket)

## Tổng Quan Kiến Trúc (Overview)
Kế hoạch triển khai kiến trúc bảo mật **Zero-Trust & Hardware-Bound Defense-in-Depth** cho hệ thống bán và soát vé thương mại SecureTicket, giải quyết triệt để 7 lỗ hổng cốt tử đã xác minh:
1. Webhook SePay hở xác thực cho phép gọi khống gạch nợ cấp vé.
2. Hardcoded fallback secrets trong mã nguồn.
3. API `/buy` và `/transfer` không yêu cầu chữ ký thiết bị phần cứng, dễ bị bot script cURL gom vé và cướp vé khi lộ token.
4. Giao tiếp mạng chạy HTTP cleartext qua địa chỉ IP trần, tắt Certificate Pinning.
5. Logcat in toàn bộ body payload chứa Bearer JWT và mã vé trên bản Release.
6. Kho vé cục bộ `EncryptedTicketVault` dẫn xuất khóa từ chuỗi JWT access token và fallback tĩnh.
7. Cơ chế RASP mới chỉ mang tính thụ động hiển thị badge, chưa bảo vệ luồng thực thi.

Đồng thời, kế hoạch giải quyết bài toán nghiệp vụ sự kiện: **Khóa chặt khâu mua và chuyển nhượng vé bằng chữ ký số phần cứng (chống bot), nhưng nới lỏng và bảo đảm tốc độ soát vé tại cổng < 150ms qua NFC và < 300ms qua QR ngay cả khi mất sóng 4G.**

---

## Mục Tiêu Đo Lường (Goals)

| # | Mục Tiêu (Goal) | Tiêu Chuẩn / Đo Lường | Độ Ưu Tiên |
|---|----------------|------------------------|------------|
| 1 | Bịt 100% lỗ hổng Webhook thanh toán (Fail-Closed HMAC-SHA256) | Chặn 100% request không chữ ký (HTTP 401) | P1 |
| 2 | Loại bỏ 100% Hardcoded Secrets & Kích hoạt Fail-Fast | Server dừng khởi động (`process.exit(1)`) nếu thiếu `.env` | P1 |
| 3 | Chặn đứng Bot cURL gom vé và cướp vé trên API `/buy` và `/transfer` | Bắt buộc chữ ký ECDSA P-256 từ AndroidKeyStore + Biometrics | P1 |
| 4 | Mã hóa kho vé cục bộ bằng AndroidKeyStore MasterKey TEE độc lập | Mở vé offline mượt mà không phụ thuộc phiên mạng | P1 |
| 5 | Đóng Cleartext HTTP, bảo vệ Logcat & Ghim Chứng chỉ TLS 1.3 an toàn | Zero cleartext, Logcat clean, Pinning có Backup Pin | P1 |
| 6 | Vòng đời Dual-Token (Access 15m + Refresh Token Rotation 7d) | Thu hồi phiên tức thì, phát hiện Token Reuse | P2 |
| 7 | Vành đai HTTP Hardening (Helmet, Strict CORS, Zod, Rate Limiting) | Chặn brute-force login và spam API (HTTP 429) | P2 |
| 8 | Adaptive RASP & Anti-Tampering trên Android | Phát hiện Root/Frida/Debugger có phân tách Debug/Release | P2 |

---

## Các Giai Đoạn Triển Khai (Phases)

| # | Giai Đoạn (Phase) | Trọng Tâm Giao Hàng | Thời Gian | Trạng Thái |
|---|-------------------|---------------------|-----------|------------|
| 1 | [Phase 1: Emergency Hotfixes](./phase-01-emergency-hotfixes.md) | Vá Webhook SePay Fail-Closed, EnvValidator Fail-Fast, Nginx TLS 1.3 & Tắt Logcat Body | 1-2 ngày | Pending |
| 2 | [Phase 2: Crypto Identity & Functional Security](./phase-02-crypto-identity-functional-security.md) | MasterKey Ticket Vault, Hardware-bound ECDSA Buy/Transfer, BiometricPrompt & Dual-Token Rotation | 2-3 ngày | Pending |
| 3 | [Phase 3: Perimeter Hardening, RASP & Verification](./phase-03-perimeter-rasp-verification.md) | Helmet, CORS, Zod, Rate Limiting, Certificate Pinning (Backup Pin), Adaptive RASP & Red-Team Test | 1-2 ngày | Pending |

---

## Tiêu Chí Nghiệm Thu Chung (Success Criteria)

- [ ] Webhook `/api/payment/sepay-webhook` từ chối 100% request thiếu header chữ ký hoặc sai lệch timestamp (HTTP 401).
- [ ] Server Node.js từ chối khởi động nếu thiếu biến môi trường bắt buộc, không còn fallback secret nào trong mã nguồn.
- [ ] Lệnh cURL gọi `POST /api/tickets/buy` hoặc `POST /api/tickets/transfer` có Bearer Token hợp lệ nhưng thiếu chữ ký `X-Device-Signature` bị từ chối HTTP 403 Forbidden.
- [ ] Kho vé `EncryptedTicketVault` giải mã vé bình thường khi bật Chế độ máy bay (Airplane mode) và sau khi đăng xuất.
- [ ] Không còn cấu hình `cleartextTrafficPermitted="true"` cho IP máy chủ; `HttpLoggingInterceptor` không in token/vé ra Logcat bản Release.
- [ ] Kích hoạt OkHttp `CertificatePinner` với 2 mã băm SPKI; kiểm tra qua proxy Burp Suite bị ngắt kết nối bắt tay TLS ngay lập tức.
- [ ] Phiên đăng nhập được tự động làm mới qua Refresh Token Rotation; Refresh Token cũ bị dùng lại sẽ thu hồi toàn bộ phiên tài khoản.
- [ ] Bộ kiểm thử tự động Red-team chạy thành công 100% các ca thử nghiệm bảo mật (Webhook bypass, Bot buy, Token reuse, Rate limit).

<!-- slug: security-hardening -->
