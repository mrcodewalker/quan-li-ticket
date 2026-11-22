---
phase: 3
title: "Phase 3: Ticket Pass, VietQR Checkout & Scanner HUD"
status: completed
priority: P1
effort: "2h"
dependencies: [1]
---

# Phase 3: Ticket Pass, VietQR Checkout & Scanner HUD

## Overview
Unify `TicketDetailScreen.kt`, `PaymentScreen.kt`, and `ScannerScreen.kt` under the new design system: frosted top navigation controls, dark terminal cryptographic proof blocks on the ticket pass, a fintech-grade VietQR payment checkout experience (zero raw M3 `Card`s or emojis), and a frosted camera/NFC scanner HUD.

## Requirements
- Functional:
  - In `TicketDetailScreen.kt`:
    - Use `FrostedIconButton` for the Back and Transfer top bar actions.
    - Use `CryptoTerminalBox` inside the 4-layer anti-counterfeiting section to display ECDSA signature and TOTP nonce cleanly.
    - Replace `✓` and `✗` string prefixes in `signatureCheckResult` with `SemanticAlertBanner`.
  - In `PaymentScreen.kt`:
    - Replace all 3 raw Material `Card` blocks and `Color.Gray` / `Color.DarkGray` with `GlassCard` and semantic tokens from `Theme.kt`.
    - Replace the `⚠️ Vui lòng giữ nguyên nội dung...` text with `SemanticAlertBanner(AlertType.WARNING, ...)`.
    - Replace the `⚡ Giả lập Quét mã & Thanh toán` button and toast emoji with `Icons.Default.Bolt` vector icon.
    - Style the bank transfer details table with crisp copy pills (`Icons.Default.ContentCopy`) and tabular monospace numbers.
  - In `ScannerScreen.kt`:
    - Replace the text `"✕"` close button in the NFC overlay with `Icons.Default.Close` inside a frosted dark HUD button.
    - Upgrade the bottom `[QR Camera | NFC]` mode switcher pill to match the spring-animated frosted capsule style.
- Non-functional:
  - Keep the QR code container in both `TicketDetailScreen` and `PaymentScreen` on a pure high-contrast `#FFFFFF` surface so hardware camera scanning works instantaneously.

## Architecture
- `PaymentScreen` adopts the same `AppBackground` + frosted top header + `GlassCard` stack used by `TicketDetailScreen`.
- `TicketDetailScreen` retains its 30s TOTP loop, `DisplayManager` mirror detection, `ON_PAUSE` privacy shield, and 3-step `SecureTransferSheet` while upgrading visual chrome to `FrostedIconButton`, `CryptoTerminalBox`, and `SemanticAlertBanner`.

## Related Code Files
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/TicketDetailScreen.kt`
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/PaymentScreen.kt`
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/ScannerScreen.kt`

## Implementation Steps
1. Update `TicketDetailScreen.kt` top bar buttons to `FrostedIconButton`, wrap ECDSA signature & nonce inspection in `CryptoTerminalBox`, and render `signatureCheckResult` via `SemanticAlertBanner`.
2. Refactor `PaymentScreen.kt` to replace all raw `Card`s with `GlassCard`, add a frosted top bar with countdown pill badge, add vector copy buttons for Account Number and Transfer Content, and remove `⚠️`/`⚡` emojis.
3. Update `ScannerScreen.kt` to replace `"✕"` with `Icons.Default.Close` and refine the bottom mode switcher pill and top translucent HUD header.

## Todo
- [x] Upgrade `TicketDetailScreen.kt` top bar, crypto proof terminal box, and vector verification banner
- [x] Redesign `PaymentScreen.kt` with `GlassCard`, vector copy pills, countdown badge, and `SemanticAlertBanner`
- [x] Replace `"✕"` text icon and polish HUD controls in `ScannerScreen.kt`

## Success Criteria
- [x] `PaymentScreen.kt` contains 0 raw `Card(` calls and 0 emojis (`⚠️`, `⚡`).
- [x] `TicketDetailScreen.kt` and `ScannerScreen.kt` contain 0 emojis (`✓`, `✗`, `✕`).
- [x] Both VietQR and TOTP QR bitmaps remain high-contrast `#FFFFFF` pads with `≥ 216dp` scan area.

## Risk Assessment
- **Risk:** Styling changes around the QR box could clip or shrink the QR code bitmap below scannable dimensions.
  - **Signal:** QR code appears cramped or lacks quiet-zone white padding.
  - **Mitigation:** Preserve at least `14.dp` pure white quiet-zone padding around both `qrBitmap` images.
