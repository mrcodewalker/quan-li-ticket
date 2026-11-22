---
phase: 2
title: "Phase 2: Customer Shell, Settings & Login Polish"
status: completed
priority: P1
effort: "2h"
dependencies: [1]
---

# Phase 2: Customer Shell, Settings & Login Polish

## Overview
Integrate the floating `FrostedCapsuleBottomNav` into `HomeScreen.kt` with proper scroll clearance, replace the dead Dark Mode toggle in Settings with a live Hardware Security & TEE Attestation card, upgrade the RASP banner in `MainActivity.kt`, and organize `LoginScreen.kt` into a clean product login + structured RBAC demo switcher.

## Requirements
- Functional:
  - In `MainActivity.kt`, replace the raw red box + `⚠️` emoji RASP banner with a structured vector icon (`Icons.Default.GppMaybe`) banner.
  - In `HomeScreen.kt`, overlay `SecureBottomNav` floating at `Alignment.BottomCenter` so scrolling content glides underneath the frosted capsule, and add `96.dp` bottom padding to `HomeMainView`, `TicketsWalletView`, and `SettingsView`.
  - Pass active ticket count to `SecureBottomNav` so the "Ví vé" tab shows a live pill count badge.
  - Remove `isDarkMode` state and the non-functional "Giao diện (Tối / Theo hệ thống)" row from `SettingsView`; replace it with a truthful **"Cấp độ Bảo mật Phần cứng"** row showing `KeystoreManager.getHardwareSecurityLevel()` and `FLAG_SECURE` status.
  - Use `CryptoTerminalBox` inside `SettingsView`'s Dual-Token card to display the Access JWT and Refresh Token snippets with terminal clarity.
  - Remove `🎉` emoji from queue toast in `HomeScreen.kt`.
  - In `LoginScreen.kt`, refine the hero identity header and group the 5 quick-test account buttons (`KH A`, `KH B`, `KH C`, `Staff`, `Organizer`) inside a clean `GlassCard` with role badges.
- Non-functional:
  - Preserve 100% of existing login, device binding, 5-second ticket transfer polling, and 2.5-second FIFO waiting room polling logic.

## Architecture
- `HomeScreen` uses a root `Box(Modifier.fillMaxSize())` containing the current tab view and the floating `SecureBottomNav(modifier = Modifier.align(Alignment.BottomCenter))` plus the top incoming-ticket notification banner.
- Each scrollable tab view applies `Modifier.padding(bottom = 96.dp)` (or `Spacer(Modifier.height(88.dp))`) so the final card never sits behind the floating capsule.

## Related Code Files
- Modify: `app/src/main/java/com/example/quan_ly_ticket/MainActivity.kt`
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/LoginScreen.kt`

## Implementation Steps
1. Update `MainActivity.kt` RASP banner to use `Icons.Default.GppMaybe` and semantic danger tokens without the `⚠️` emoji.
2. Refactor `HomeScreen.kt` scaffold/box structure to float `SecureBottomNav` at the bottom center with active ticket badge count, and add bottom scroll clearance to all 3 tabs.
3. In `HomeScreen.kt` (`SettingsView`), remove `isDarkMode`, upgrade Dual-Token token inspection to `CryptoTerminalBox`, and replace the dead theme toggle row with live hardware security status.
4. Polish `LoginScreen.kt` layout, input field styling, and the RBAC quick-demo account switcher card.

## Todo
- [x] Replace emoji RASP banner in `MainActivity.kt`
- [x] Float `SecureBottomNav` over content in `HomeScreen.kt` with active ticket badge & bottom scroll padding
- [x] Remove dead `isDarkMode` toggle and upgrade Dual-Token display to `CryptoTerminalBox` in `HomeScreen.kt`
- [x] Redesign `LoginScreen.kt` quick-demo RBAC account switcher card

## Success Criteria
- [x] `HomeScreen` scrolls smoothly underneath the floating frosted bottom navigation bar and the bottom-most elements are fully reachable above the bar.
- [x] Zero references to `isDarkMode` remain in `HomeScreen.kt`.
- [x] Zero emojis remain in `MainActivity.kt`, `HomeScreen.kt`, and `LoginScreen.kt`.

## Risk Assessment
- **Risk:** Floating bottom bar overlaps the "Đăng xuất" button at the bottom of `SettingsView` or the last event card in `HomeMainView`.
  - **Signal:** Bottom buttons partially covered by the nav capsule.
  - **Mitigation:** Add explicit `Spacer(Modifier.height(92.dp))` at the end of every vertical scroll column in `HomeMainView`, `TicketsWalletView`, and `SettingsView`.
