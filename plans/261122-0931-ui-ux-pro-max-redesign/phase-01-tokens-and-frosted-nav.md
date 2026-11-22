---
phase: 1
title: "Phase 1: Tokens & Frosted Navigation Primitives"
status: completed
priority: P1
effort: "2h"
dependencies: []
---

# Phase 1: Tokens & Frosted Navigation Primitives

## Overview
Establish the single-source-of-truth design token system in `Theme.kt` and build the reusable frosted-acrylic navigation, terminal code block, and semantic alert primitives in `CommonComponents.kt`.

## Requirements
- Functional:
  - Expand `Theme.kt` with semantic surface tokens (`FrostedSurface`, `FrostedBorderTop`, `FrostedBorderBottom`, `TerminalBg`, `TerminalBorder`, `TerminalTextCyan`, `TerminalTextEmerald`), status badge pairs (`SuccessBg`/`SuccessFg`, `WarningBg`/`WarningFg`, `DangerBg`/`DangerFg`, `InfoBg`/`InfoFg`), and explicit typography hierarchy.
  - Create `FrostedCapsuleBottomNav` supporting generic tab items (icon, label, optional badge count) so both `HomeScreen` (3 customer tabs) and `OrganizerDashboardScreen` (3 organizer tabs) share the exact same floating frosted pill bar.
  - Create `FrostedIconButton` (44x44dp minimum touch target, translucent frosted acrylic fill, top-lit 1dp specular border, press-scale animation).
  - Create `CryptoTerminalBox` (dark `#0F172A` surface, `#1E293B` 1dp border, optional header label + status pill, monospace tabular content) for cryptographic proofs.
  - Create `SemanticAlertBanner` (vector icon + title/message + semantic tint) to replace all emoji-prefixed status texts and warning callouts.
  - Upgrade `GlassCard` and `TicketStubCard` with subtle press-scale (`0.985f`) feedback when clickable and refined holographic/perforation details.
- Non-functional:
  - Zero external Gradle dependencies; 100% compatible with `minSdk = 24` through `compileSdk = 35`.
  - Animations use `spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)` (~180–250ms perceptual response).

## Architecture
- `Theme.kt` exports semantic colors and helper brushes (`FrostedAcrylicBrush`, `SpecularBorderBrush`, `AmbientHeaderBrush`).
- `CommonComponents.kt` encapsulates all shared UI primitives:
  - `FrostedCapsuleBottomNav(items, selectedIndex, onSelect, modifier)`
  - `SecureBottomNav(currentTab, onSelectTab, badgeCount, modifier)` (wraps `FrostedCapsuleBottomNav`)
  - `FrostedIconButton(icon, contentDescription, onClick, tint, modifier)`
  - `CryptoTerminalBox(title, badgeText, lines, modifier)`
  - `SemanticAlertBanner(type, message, title, modifier)` where `type` is `AlertType.SUCCESS | WARNING | ERROR | INFO`.

## Related Code Files
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/example/quan_ly_ticket/ui/components/CommonComponents.kt`

## Implementation Steps
1. Update `Theme.kt` with semantic color tokens, frosted acrylic gradient brushes, specular border brushes, and refined Material 3 `Typography` definitions.
2. Update `AppBackground` in `Theme.kt` to provide a subtle top editorial radial light wash (`#E8EFFD` fading into `#F5F7FB`) that gives floating frosted components visible depth without washing out cards.
3. In `CommonComponents.kt`, implement `FrostedCapsuleBottomNav` and wire `SecureBottomNav` to it with spring-animated sliding/expanding pill selection, press-scale interaction, and optional ticket count badge.
4. In `CommonComponents.kt`, add `FrostedIconButton`, `CryptoTerminalBox`, and `SemanticAlertBanner`.
5. Refine `TicketStubCard` in `CommonComponents.kt` with vector security micro-badge and crisp tabular ticket code formatting.

## Todo
- [x] Add semantic color & frosted brush tokens to `Theme.kt`
- [x] Implement `FrostedCapsuleBottomNav` and update `SecureBottomNav` in `CommonComponents.kt`
- [x] Implement `FrostedIconButton`, `CryptoTerminalBox`, and `SemanticAlertBanner` in `CommonComponents.kt`
- [x] Upgrade `TicketStubCard` and `GlassCard` interaction states in `CommonComponents.kt`

## Success Criteria
- [x] `FrostedCapsuleBottomNav`, `FrostedIconButton`, `CryptoTerminalBox`, and `SemanticAlertBanner` compile cleanly and enforce `≥ 44dp` touch targets.
- [x] `SecureBottomNav` renders as a floating frosted pill capsule with spring-animated active state and badge support.

## Risk Assessment
- **Risk:** `Modifier.blur()` in Compose blurs child text/icons rather than background content.
  - **Signal:** Blurred or fuzzy nav labels/icons.
  - **Mitigation:** Do not apply `Modifier.blur()` to the parent container holding text/icons; use multi-layer translucent acrylic (`0.88f` alpha gradient), a top-lit specular `Brush.verticalGradient` border (`White 0.95f` -> `Slate 0.35f`), and soft ambient shadow diffusion.
