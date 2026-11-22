# Technical Journal — 2026-11-22: Production Ticketing Lifecycle & Interactive Security Showcase Plan

## Context
Following `/ak:brainstorm --ultra --advice` and `/ak:advise`, we analyzed why `SecureTicket` (`quan-li-ticket`) felt like a prototype despite having strong underlying cryptography (`AndroidKeyStore` ECDSA P-256 & AES-256-GCM, Dual-Token Rotation, SePay HMAC-SHA256 webhooks, 30s rotating TOTP QR, NFC HCE, RootBeer + Frida RASP):
1. Tapping an event in `HomeScreen.kt` skipped any Event Detail, Ticket Type/Zone, or Seat Selection screen and immediately created a generic 2,000 VND order (`Math.random()` zone/seat in `paymentRoutes.js`).
2. `POST /api/payment/create-vietqr` (`paymentRoutes.js`) did not enforce `verifyDeviceSignature` or `MAX_ACTIVE_TICKETS_PER_EVENT = 6` (`ticketRoutes.js`).
3. Primary screens exposed overt demo shortcuts (`"Tài khoản dùng thử"` card on `LoginScreen.kt`, `"Xác nhận đã chuyển khoản"` primary bypass CTA on `PaymentScreen.kt`, and `"Mô phỏng"` hardcoded `SEC-P12-2026` button on `ScannerScreen.kt`).
4. `TicketDetailScreen.kt` and `ScannerScreen.kt` did not execute or surface on-device offline ECDSA P-256 verification (`CryptoHelper.verifyServerSignature`), ticket provenance chains (`ticket_transfers`), or self-service hardware-signed refunds.
5. `HomeScreen.kt` (`SettingsView`) had orphaned token-rotation state (`accessSnippet`, `refreshSnippet`, `rotationCount`, `isRotating`), and `CryptoLabScreen.kt` was missing.

## Plan Created
Created and validated `/Users/nc/Develop/Android/quan-li-ticket/plans/261122-1530-production-ticketing-and-security-showcase`:
- **Phase 1**: Backend Security Parity, Seat-Locking Booking (`/events/:id/booking-context`, hardened `/payment/create-vietqr` with mandatory TEE signature, 6-ticket quota, and `(seat_zone, seat_number)` locking) & Lifecycle APIs (`/payment/my-orders`, `/payment/simulate-signed-webhook`, `/tickets/:id/provenance`, `/tickets/:id/refund`).
- **Phase 2**: Production Auth (`LoginScreen.kt` Register + discreet Evaluator Role Switcher), Discovery Search/Sort (`HomeScreen.kt`), and `EventBookingScreen.kt` (Visual Stage Map, 6 Zone Tiers `2,000–3,000 ₫`, Interactive 40-seat-per-zone Matrix Picker, `X/6` Quota Bar, and Hardware TEE Seat Lock).
- **Phase 3**: `PaymentScreen.kt` 4-stage cryptographic pipeline & collapsible HMAC Webhook Inspector, `OrderHistorySheet`, `TicketDetailScreen.kt` offline ECDSA proof + Provenance Chain + Signed Refund, and `ScannerScreen.kt` 2-stage Offline+Online verifier + Gate Red-Team Test Vectors.
- **Phase 4**: `CryptoLabScreen.kt` 5-Module Security & Red-Team Center, wired `SettingsView` & `MainActivity.kt`, and full-stack verification.
