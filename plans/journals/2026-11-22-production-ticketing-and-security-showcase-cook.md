---
title: "Production Ticketing & Interactive Security Showcase Implementation"
date: 2026-11-22
plan: plans/261122-1530-production-ticketing-and-security-showcase/plan.md
status: completed
---

# Technical Journal — SecureTicket Production & Security Showcase

## Summary
Executed all 4 phases of `plans/261122-1530-production-ticketing-and-security-showcase/plan.md` under `--advice` (`kongming`) supervision and mandatory `code-reviewer` verification.

## Key Changes
- **Phase 1 (Backend)**: Added `backend/src/config/ticketTiers.js` (6 zone tiers, `MAX_ACTIVE_TICKETS_PER_EVENT = 6`), idempotent `ensureOrderColumns()` in `paymentRoutes.js` + `index.js`, `GET /api/events/:id/booking-context` in `eventRoutes.js`, hardened `POST /api/payment/create-vietqr` (TEE `deviceSignature`, `clientNonce`, `±90s` timestamp, atomic seat locking, lazy expired-order sweep), `GET /api/payment/my-orders`, `POST /api/payment/simulate-signed-webhook`, `GET /api/tickets/:id/provenance`, and `POST /api/tickets/:id/refund`.
- **Phase 2 (Auth, Discovery & Booking)**: Updated `Models.kt`, `TicketApiService.kt`, and `KeystoreManager.kt` (`signRefundPayload`). Added Registration + discreet Evaluator Role Switcher sheet in `LoginScreen.kt`, search/sort in `HomeScreen.kt`, and built `EventBookingScreen.kt` (Visual Stage Map, 6 Zone Tiers, 40-seat interactive grid, `X/6` quota bar, FIFO queue, TEE seat lock).
- **Phase 3 (Payment, Orders, Provenance & Gate)**: Upgraded `PaymentScreen.kt` (4-stage pipeline, collapsible HMAC Webhook Sandbox Inspector, decoupled Back vs Cancel), added `OrderHistorySheet` in `HomeScreen.kt`, added offline ECDSA P-256 verification + Provenance timeline + TEE-signed Refund in `TicketDetailScreen.kt`, and upgraded `ScannerScreen.kt` with a 4-point gate checklist and Gate Red-Team Test Vectors drawer.
- **Phase 4 (Security Center & Verification)**: Built `CryptoLabScreen.kt` (5 interactive modules using an isolated `OkHttpClient` for Red-Team/token-reuse probes), wired `SettingsView` token rotation + Red-Team Lab CTA in `HomeScreen.kt`, and wired `Screen.CRYPTO_LAB` in `MainActivity.kt`.

## Verification
- `node --check` on modified backend files: passed (exit 0).
- `node backend/src/test/securityRedTeamTest.js`: 9/9 passed (exit 0).
- `./gradlew assembleDebug testDebugUnitTest`: `BUILD SUCCESSFUL` (exit 0).
- Static grep checks (`Tài khoản dùng thử`, `SEC-P12-2026`, raw `\bCard(` in `ui/screens/`): 0 matches.
