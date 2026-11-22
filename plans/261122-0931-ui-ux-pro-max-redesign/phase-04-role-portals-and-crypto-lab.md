---
phase: 4
title: "Phase 4: Role Portals, Crypto Lab & Verification"
status: completed
priority: P1
effort: "2h"
dependencies: [1, 2, 3]
---

# Phase 4: Role Portals, Crypto Lab & Verification

## Overview
Unify the Staff and Organizer RBAC portals and the 5-module `CryptoLabScreen` under the same design system: apply `FrostedCapsuleBottomNav` to `OrganizerDashboardScreen`, convert all 5 `CryptoLabScreen` modules from raw `Card`s into structured `GlassCard` + `CryptoTerminalBox` + `SemanticAlertBanner` modules, and verify the full Android build and unit test suite.

## Requirements
- Functional:
  - In `OrganizerDashboardScreen.kt`:
    - Replace the docked white bottom bar (`OrgTabItem`) with the shared floating `FrostedCapsuleBottomNav` (`Sự kiện`, `Thu hồi vé`, `SIEM An ninh`) and add `96.dp` bottom scroll clearance.
    - Replace the raw text warning in Tab 1 (`Thu hồi vé`) with `SemanticAlertBanner(AlertType.ERROR, ...)`.
  - In `CryptoLabScreen.kt`:
    - Replace all 5 raw Material `Card` blocks with `GlassCard` featuring numbered module badges (`01`..`05`) and clean section subtitles.
    - Replace all light gray/green raw dump boxes with `CryptoTerminalBox` so JWT tokens, `customer_pii_vault.enc` AES-256-GCM ciphertext, `secure_ticket_prefs.xml` AES256-SIV ciphertext, ECDSA signatures, and APK SHA-256 hashes render in high-contrast dark terminal blocks.
    - Replace all `✓` and `✗` string prefixes in `tokenStatusMsg`, `ecdsaVerifyResult`, and `aesErrorMessage` with `SemanticAlertBanner`.
  - In `StaffDashboardScreen.kt`:
    - Polish stat tiles and check-in log items to align with the updated semantic badge and border tokens.
- Non-functional:
  - Zero changes to cryptographic operations (`KeystoreManager`, `HybridCrypto`, `EncryptedTicketVault`, `SecurityCheck`) in `CryptoLabScreen`.

## Architecture
- `OrganizerDashboardScreen` mirrors `HomeScreen`'s floating bottom navigation architecture (`Box` overlay + `FrostedCapsuleBottomNav` at `Alignment.BottomCenter`).
- `CryptoLabScreen` uses `GlassCard` for each of the 5 modules, `CryptoTerminalBox` for live cryptographic outputs, and `SemanticAlertBanner` for verification/tampering outcomes.

## Related Code Files
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/OrganizerDashboardScreen.kt`
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/CryptoLabScreen.kt`
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/screens/StaffDashboardScreen.kt`

## Implementation Steps
1. Update `OrganizerDashboardScreen.kt` to float `FrostedCapsuleBottomNav` over the scroll view with `96.dp` bottom clearance and upgrade the instant-revocation notice to `SemanticAlertBanner`.
2. Refactor `CryptoLabScreen.kt` modules 1 through 5 to use `GlassCard`, `CryptoTerminalBox`, and `SemanticAlertBanner`, removing all raw `Card(` instances and `✓`/`✗` characters.
3. Polish `StaffDashboardScreen.kt` header and stat cards for token consistency.
4. Run grep audits for emojis, raw `Card(`, and `isDarkMode`, followed by `./gradlew assembleDebug testDebugUnitTest`.

## Todo
- [x] Upgrade `OrganizerDashboardScreen.kt` to floating `FrostedCapsuleBottomNav` with bottom clearance
- [x] Refactor all 5 modules in `CryptoLabScreen.kt` to `GlassCard`, `CryptoTerminalBox`, and `SemanticAlertBanner`
- [x] Polish `StaffDashboardScreen.kt` visual consistency
- [x] Verify zero emojis, zero raw `Card(`, and passing `./gradlew assembleDebug testDebugUnitTest`

## Success Criteria
- [x] `CryptoLabScreen.kt` contains 0 raw `Card(` calls and 0 `✓`/`✗` characters.
- [x] `OrganizerDashboardScreen.kt` uses the floating frosted capsule bottom navigation bar.
- [x] `./gradlew assembleDebug testDebugUnitTest` exits with code `0`.

## Risk Assessment
- **Risk:** Long Base64 ciphertext or hex strings in `CryptoTerminalBox` overflow or wrap awkwardly on narrow screens (360dp).
  - **Signal:** Clipped or misaligned terminal rows in `CryptoLabScreen`.
  - **Mitigation:** Use `maxLines = 2` with `TextOverflow.Ellipsis` on preview lines inside `CryptoTerminalBox` and `11.sp` monospace font with `15.sp` line height.
