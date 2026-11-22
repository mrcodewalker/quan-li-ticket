---
title: "production-ticketing-and-security-showcase"
description: "Transform SecureTicket from a shortcut-driven prototype into an end-to-end production ticketing application (Registration, Search/Sort, Event Detail + Interactive Stage Map & Real-Time Seat Picker, Cryptographic VietQR Checkout & Order History, Ticket Provenance & Refund, 2-Stage Offline+Online Gate Scanner) paired with an interactive 5-Module Security & Red-Team Center."
status: pending
priority: P1
effort: "2d"
tags: ["android", "jetpack-compose", "nodejs", "security", "ecdsa", "tee", "vietqr", "red-team", "booking"]
created: 2026-11-22
---

# Production Ticketing & Interactive Security Showcase Plan (SecureTicket)

## Overview

This plan executes the accepted `/ak:brainstorm --ultra --advice` and `/ak:advise` contract to transform **SecureTicket (`quan-li-ticket`)** into a commercial-grade event ticketing platform whose primary differentiator is **verifiable, hardware-backed security engineering**.

It eliminates every "toy demo" shortcut on primary user journeys while introducing:
1. **End-to-End Production Booking (`EventBookingScreen.kt` & `/api/events/:id/booking-context`)**: Replaces the 1-tap jump from `HomeScreen` to `PaymentScreen` with a full Event Detail & Booking experience featuring a **Visual Stage & Arena Map**, **6 Ticket Tiers (`STAGE-V`, `VIP-A`, `VIP-B`, `PLATINUM`, `FANZONE`, `PREM-S`)** priced at `2,000–3,000 ₫` for real VietQR micro-transfers, an **Interactive Real-Time Seat Grid Picker** (`Available`, `Held 5m`, `Sold`), a **Personal Anti-Scalping Quota Meter (`X / 6 vé`)**, and **Hardware TEE ECDSA P-256 Seat Locking** (`SELECT ... FOR UPDATE` preventing seat collisions and cURL bots).
2. **Authentic Payment Pipeline, Order Recovery & Signed Webhook Sandbox (`PaymentScreen.kt` & `OrderHistorySheet`)**: Removes the primary blue `"Xác nhận đã chuyển khoản"` bypass button; adds a live **4-Stage Cryptographic Payment Pipeline Stepper**; tucks webhook testing into a collapsible **"Security & Webhook Sandbox Inspector"** (`Valid HMAC-SHA256 200 OK` vs. `Forged HMAC 401 Blocked`); and adds `GET /api/payment/my-orders` so users can resume `PENDING` orders, cancel held seats, or inspect `PAID` cryptographic receipts.
3. **Verifiable Ticket Custody, Ownership Provenance, Signed Refund & 2-Stage Gate Scanner (`TicketDetailScreen.kt` & `ScannerScreen.kt`)**: Performs on-device offline ECDSA P-256 signature verification (`CryptoHelper.verifyServerSignature`), displays the immutable **Ownership & Transfer Provenance Chain** (`GET /api/tickets/:id/provenance`), supports hardware-signed **Self-Service Ticket Cancellation/Refund** (`POST /api/tickets/:id/refund`), and upgrades `ScannerScreen.kt` with a **4-Point Offline+Online Cryptographic Gate Checklist** and a collapsible **Gate Red-Team Test Vectors** drawer.
4. **Interactive 5-Module Security & Red-Team Center (`CryptoLabScreen.kt` & `SettingsView`)**: Renders the orphaned token rotation state in `HomeScreen.kt` and provides an interactive cybersecurity console covering (1) Hardware TEE/StrongBox Key Attestation, (2) AES-256-GCM Encrypted Vault & PII Hex Inspector + Offline Airplane-Mode proof, (3) Dual-Token Rotation & Stolen Refresh-Token Reuse Detector, (4) Live 5-Attack Red-Team Simulator firing real backend probes (`403 BOT_BUY_ATTEMPT_BLOCKED`, `403 BUY_REPLAY_NONCE_BLOCKED`, `401 SEPAY_WEBHOOK_INVALID_SIGNATURE`, `400 FORGED_TICKET_DETECTED`, `409 TICKET_REPLAY_ATTEMPT`), and (5) Adaptive RASP & Anti-Time-Cheat Clock Drift Matrix.

---

## Goals

| # | Goal | Target / Measurement | Priority |
|---|------|----------------------|----------|
| 1 | Cryptographic Parity, Anti-Scalping & Seat-Locking Backend | `POST /api/payment/create-vietqr` enforces mandatory TEE `deviceSignature` (`verifyDeviceSignature`), `consumeClientNonce`, `MAX_ACTIVE_TICKETS_PER_EVENT = 6`, and atomic `(event_id, seat_zone, seat_number)` locking | P1 |
| 2 | Production Auth, Discovery & Interactive Seat Booking | `LoginScreen.kt` supports Registration + discreet Evaluator Role Switcher; `HomeScreen.kt` adds Search/Sort and navigates to `EventBookingScreen.kt` (Stage Map, 6 Zone Tiers, Interactive Seat Grid, `X/6` Quota Bar) | P1 |
| 3 | Authentic Payment Pipeline, Order History & Ticket Provenance/Refund | `PaymentScreen.kt` shows 4-stage pipeline + collapsible HMAC Webhook Inspector; `OrderHistorySheet` manages `PENDING`/`PAID` orders; `TicketDetailScreen.kt` shows local ECDSA proof, Provenance Chain, and Signed Refund | P1 |
| 4 | 2-Stage Gate Scanner & 5-Module Interactive Red-Team Security Center | `ScannerScreen.kt` runs pre-flight local ECDSA verification + 4-step checklist; `CryptoLabScreen.kt` renders all 5 interactive security/Red-Team modules and resolves orphaned `SettingsView` state | P1 |

---

## Phases

| # | Phase | Focus | Effort | Status |
|---|-------|-------|--------|--------|
| 1 | [Phase 1: Backend Security Parity, Seat-Locking Booking & Lifecycle APIs](./phase-01-start.md) | Idempotent `orders` migration (`seat_zone`, `seat_number`), mandatory TEE signature & 6-ticket quota on `/create-vietqr`, `/events/:id/booking-context`, `/payment/my-orders`, `/payment/simulate-signed-webhook`, `/tickets/:id/provenance`, `/tickets/:id/refund` | 4h | Pending |
| 2 | [Phase 2: Production Auth, Discovery & Interactive Seat-Picker Booking](./phase-02-booking-and-auth-experience.md) | `Models.kt`/`TicketApiService.kt` DTOs, `LoginScreen.kt` Register + Evaluator Switcher, `HomeScreen.kt` Search/Sort, and `EventBookingScreen.kt` (Visual Stage Map, 6 Zone Tiers, Interactive Seat Matrix, Hardware TEE Lock) | 4h | Pending |
| 3 | [Phase 3: Payment Pipeline, Order History, Ticket Provenance & 2-Stage Gate Scanner](./phase-03-payment-ticket-and-gate-lifecycle.md) | `PaymentScreen.kt` 4-stage pipeline & HMAC Webhook Inspector, `OrderHistorySheet`, `TicketDetailScreen.kt` local ECDSA proof + Provenance Timeline + Signed Refund, `ScannerScreen.kt` 2-stage verifier & Gate Red-Team vectors | 4h | Pending |
| 4 | [Phase 4: Interactive 5-Module Security & Red-Team Center & Verification](./phase-04-security-center-and-red-team-lab.md) | `CryptoLabScreen.kt` (TEE Attestation, AES-256-GCM Vault Hex Inspector, Dual-Token Reuse Detector, 5-Attack Live Red-Team Simulator, RASP Matrix), wire `SettingsView` & `MainActivity.kt`, run backend + Gradle verification | 4h | Pending |

---

## Success Criteria

- [ ] `POST /api/payment/create-vietqr` without a valid hardware `deviceSignature` or with a replayed `clientNonce` is rejected with **HTTP `403 Forbidden`** and logged in `security_audit_logs`.
- [ ] Booking a 7th active ticket/order for the same event is rejected with **HTTP `403`/`429` (`ANTI_SCALPING_QUOTA_EXCEEDED`)**, and booking an already-held or sold seat `(event_id, seat_zone, seat_number)` is rejected with **HTTP `409 Conflict` (`SEAT_ALREADY_TAKEN`)**.
- [ ] Tapping an event on `HomeScreen.kt` opens `EventBookingScreen.kt` with the Visual Stage Map, 6 Ticket Tiers (`2,000–3,000 ₫`), Interactive Seat Matrix (`Available` / `Held 5m` / `Sold`), and Personal Quota Meter (`X / 6 vé`) before entering the Virtual Waiting Room and `PaymentScreen.kt`.
- [ ] Primary screens contain zero overt demo shortcuts (`grep -rn "Tài khoản dùng thử" app/src/main/java/` and `grep -rn "SEC-P12-2026" app/src/main/java/` return **0 matches**), while collapsible Evaluator/Sandbox drawers preserve 1-tap role switching and HMAC-signed webhook testing.
- [ ] `TicketDetailScreen.kt` verifies `serverSignature` offline on-device via `CryptoHelper.verifyServerSignature`, renders the ownership provenance chain (`GET /api/tickets/:id/provenance`), and supports hardware-signed ticket refund (`POST /api/tickets/:id/refund`).
- [ ] `CryptoLabScreen.kt` and `HomeScreen.kt` (`SettingsView`) render all 5 interactive security/Red-Team modules with zero unused-variable warnings, and `./gradlew assembleDebug testDebugUnitTest` + backend tests pass with exit code `0`.

<!-- slug: production-ticketing-and-security-showcase -->
