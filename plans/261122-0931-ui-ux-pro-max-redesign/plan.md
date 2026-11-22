---
title: "ui-ux-pro-max-redesign"
description: "Surgical UI/UX redesign of the SecureTicket Android app using ui-ux-pro-max design intelligence: Swiss Precision Daylight palette, floating frosted-acrylic capsule bottom navigation, dark terminal crypto proof blocks, and 100% vector icon/token unification across all 8 screens."
status: completed
priority: P1
effort: "1d"
tags: ["android", "jetpack-compose", "ui-ux-pro-max", "design-system", "frosted-glass", "navigation"]
created: 2026-11-22
---

# UI/UX Pro Max Redesign Plan (SecureTicket Android)

## Overview

This plan implements the accepted `/ak:brainstorm` and `/ak:advise` contract to transform **SecureTicket** into a cohesive, high-craft consumer product ready for academic defense and classroom projector demonstrations.

The redesign eliminates "AI-generated" visual artifacts (mixed raw Material 3 `Card`s vs custom `GlassCard`s, inline structural emojis `🎉 ⚠️ ⚡ ✓ ✗ ✕`, hardcoded `Color.Gray`/`Color.Red`, dead dark-mode settings toggles, and flat rigid bottom bars with `indication = null`) while introducing:
1. **Floating Frosted-Acrylic Capsule Navigation (`FrostedCapsuleBottomNav`)** for both Customer (`HomeScreen`) and Organizer (`OrganizerDashboardScreen`) portals, featuring spring-animated active pill indicators, specular 1dp gradient borders, press-scale feedback, badge counts, and `96.dp` bottom scroll clearance.
2. **High-Contrast Daylight Cards + Dark Obsidian Crypto Terminals (`CryptoTerminalBox`)** so event listings and QR codes remain razor-sharp while raw ECDSA signatures, AES-256-GCM ciphertexts, and TOTP tokens look like an authentic cybersecurity console.
3. **Full Screen Unification (`PaymentScreen`, `CryptoLabScreen`, `LoginScreen`, `TicketDetailScreen`, `ScannerScreen`, `StaffDashboardScreen`, `OrganizerDashboardScreen`)** with zero new Gradle dependencies and zero regression to cryptographic or backend workflows.

---

## Goals

| # | Goal | Target / Measurement | Priority |
|---|------|----------------------|----------|
| 1 | Semantic Design Tokens & Frosted Primitives | 100% of screens consume `Theme.kt` tokens and `CommonComponents.kt` primitives (`FrostedCapsuleBottomNav`, `FrostedIconButton`, `CryptoTerminalBox`, `SemanticAlertBanner`) | P1 |
| 2 | Floating Frosted Capsule Bottom Navigation | Replaced flat docked bars in `HomeScreen` and `OrganizerDashboardScreen` with floating capsule nav (`≥ 44dp` targets, spring physics, `≥ 88dp` bottom scroll padding) | P1 |
| 3 | Eliminate AI-Slop Emojis & Raw Legacy Cards | 0 matches for `[🎉⚠️⚡✓✗✕]` in `ui/` and 0 raw `Card(` instances in `ui/screens/` | P1 |
| 4 | Unify Payment & Crypto Lab Screens | `PaymentScreen` and `CryptoLabScreen` refactored to `GlassCard`, `CryptoTerminalBox`, and vector status badges | P1 |
| 5 | Remove Dead Dark-Mode Toggle & Build Clean | Dead `isDarkMode` row replaced with truthful Hardware TEE/RASP status row; `./gradlew assembleDebug testDebugUnitTest` passes (exit 0) | P1 |

---

## Phases

| # | Phase | Focus | Effort | Status |
|---|-------|-------|--------|--------|
| 1 | [Phase 1: Tokens & Frosted Navigation Primitives](./phase-01-tokens-and-frosted-nav.md) | `Theme.kt` semantic color/typography tokens + `FrostedCapsuleBottomNav`, `FrostedIconButton`, `CryptoTerminalBox`, `SemanticAlertBanner` in `CommonComponents.kt` | 2h | Completed |
| 2 | [Phase 2: Customer Shell, Settings & Login Polish](./phase-02-customer-shell-and-login.md) | Floating nav overlay & scroll clearance in `HomeScreen.kt`, replace dead Dark Mode row, vector RASP banner in `MainActivity.kt`, RBAC demo switcher in `LoginScreen.kt` | 2h | Completed |
| 3 | [Phase 3: Ticket Pass, VietQR Checkout & Scanner HUD](./phase-03-ticket-payment-and-scanner.md) | Frosted top chrome & `CryptoTerminalBox` in `TicketDetailScreen.kt`, full fintech redesign of `PaymentScreen.kt`, vector close & frosted switcher in `ScannerScreen.kt` | 2h | Completed |
| 4 | [Phase 4: Role Portals, Crypto Lab & Verification](./phase-04-role-portals-and-crypto-lab.md) | Floating capsule nav in `OrganizerDashboardScreen.kt`, 5-module security console redesign in `CryptoLabScreen.kt`, polish `StaffDashboardScreen.kt`, and run Gradle verification | 2h | Completed |

---

## Success Criteria

- [x] `grep -rn "[🎉⚠️⚡✓✗✕]" app/src/main/java/com/example/quan_ly_ticket/ui/` returns **0 matches**.
- [x] `grep -rn "\bCard(" app/src/main/java/com/example/quan_ly_ticket/ui/screens/` returns **0 matches**.
- [x] `grep -rn "isDarkMode" app/src/main/java/com/example/quan_ly_ticket/ui/` returns **0 matches**.
- [x] Customer (`HomeScreen`) and Organizer (`OrganizerDashboardScreen`) portals use `FrostedCapsuleBottomNav` floating over scroll content with `≥ 88dp` bottom content clearance.
- [x] `./gradlew assembleDebug testDebugUnitTest` exits with code `0`.

<!-- slug: ui-ux-pro-max-redesign -->
