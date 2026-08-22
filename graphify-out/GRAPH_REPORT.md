# Graph Report - OneHandCommander (2026-08-22 Refactored)

## Corpus Check
- **Files analyzed**: 19 Kotlin source files
- **Package**: `com.example.onehandcommander`
- **Verdict**: Complete modular Android Accessibility overlay architecture mapped successfully with a clean, decoupled settings domain layer.

## Summary
- **Nodes**: 228
- **Edges**: 512
- **Communities detected**: 5
- **Extraction Confidence**: 62% EXTRACTED · 38% INFERRED · 0% AMBIGUOUS

## God Nodes (Most connected core abstractions)
1. `SavedData` - 178 connections
2. `Touchpad` - 90 connections
3. `SettingsActivity` - 52 connections
4. `Tenkey` - 58 connections
5. `UiHelper` - 56 connections
6. `FloatingButton` - 52 connections
7. `Constants` - 46 connections
8. `SettingsConfigProvider` - 38 connections
9. `ErrorHandler` - 36 connections
10. `BaseOverlay` - 28 connections

## Communities

### Community 0 - "Core & Service Lifecycle"
**Key Entities (12)**: MainService.kt, ServiceState.kt, Idle, MenuNormal, MenuSearch, AppMenu.kt, AppItem, FileItem, AppsAdapter, AppViewHolder, RecentFilesAdapter, FileViewHolder

### Community 1 - "Overlay Framework & Window Management"
**Key Entities (2)**: OverlayManager.kt, BaseOverlay.kt

### Community 2 - "Interactive Overlay Components (FAB, Touchpad, Tenkey)"
**Key Entities (3)**: FloatingButton.kt, Tenkey.kt, Touchpad.kt

### Community 3 - "Persistence, Domain Models & Preference Settings"
**Key Entities (7)**: SavedData.kt, ButtonConfig.kt, TenkeyConfig.kt, TouchpadConfig.kt, SettingItem.kt, SettingsConfigProvider.kt, SettingsViewBinder.kt, SettingsActivity.kt

### Community 4 - "Utilities, System Helpers & Haptics"
**Key Entities (8)**: Constants.kt, Defaults, SystemActions, UI, Colors, ErrorHandler.kt, UiHelper.kt, Vibration.kt

## Key Interaction Pathways (High Value Callflows)
- `SettingsActivity` --[delegates UI]--> `SettingsViewBinder` & `SettingsConfigProvider` (Declarative settings UI generation)
- `SettingsConfigProvider` --[reads/writes]--> `SavedData` (Domain model & typed preference storage)
- `FloatingButton` --[onSwipe]--> `MainService` --[toggleTouchpad]--> `Touchpad` (Enables one-handed virtual cursor overlay)
- `FloatingButton` --[onVerticalSwipe]--> `MainService` --[performGlobalAction]--> `Android OS` (Home / Recents navigation)
- `OverlayManager` --[coordinates]--> `FloatingButton`, `Touchpad`, `Tenkey` (Unified lifecycle & WindowManager control)
- `SavedData` --[syncs]--> `SettingsActivity` & all overlays (Dynamic dimension/opacity tuning)

## Architectural Integrity & Improvements Completed
1. **Domain Model Layer (`settings/model/`)**: Created immutable `ButtonConfig`, `TenkeyConfig`, and `TouchpadConfig` models to eliminate flat monolithic state.
2. **Declarative Settings (`SettingItem` & `SettingsConfigProvider`)**: Decoupled setting definitions, range bounds, and formatting logic from Android Activity / View hierarchies.
3. **Dedicated UI Renderer (`SettingsViewBinder`)**: Encapsulated UI component creation (Sliders, Toggles, Section Headers) with high contrast and consistent padding.
4. **Magic Number Elimination**: All defaults, slider ranges, steps, and threshold bounds are consolidated in `Constants.Defaults`.
5. **Touchpad Cursor Lifecycle Integration**: Synchronized `cursorView` WindowManager attachment and release with `BaseOverlay` (`onHidden()`, `cleanup()`, `onRemoved()`) using guarded WindowManager calls to eliminate window leaks and orphaned views.
6. **MVI / State-Machine Unidirectional Data Flow (`StateManager`, `ServiceIntent`, `ServiceState`)**: Transitioned `MainService` and `OverlayManager` from imperative, scattered method calls to deterministic MVI state reduction (`StateManager.reduce()`) and rendering (`OverlayManager.render(state)`). Eliminated race conditions and arbitrary timing delays (`postDelayed`) on screen lock/unlock transitions.
7. **App Icon Memory Optimization (`AppIconCache`) & Package Lifecycle Awareness**: Replaced static `Drawable` references with an `LruCache<String, Bitmap>` cache to eliminate context memory leaks, paired with `PACKAGE_ADDED` / `PACKAGE_REMOVED` BroadcastReceivers to invalidate and refresh app lists automatically.
8. **Inversion of Control for Gesture Execution (`GestureDispatcher`)**: Extracted `GestureDispatcher` interface and implemented `AccessibilityGestureDispatcher`, completely decoupling UI overlay components (`Touchpad`, `FloatingButton`) from the concrete `AccessibilityService` instance for unit testability and clean architecture.
9. **Touchpad Hot-Path Optimization & Natural Drag Dynamics (`Touchpad`)**: Introduced dirty-checking for `WindowManager.updateViewLayout` to eliminate redundant IPCs during static frames, cached SharedPreferences reads outside the 60-120Hz VSYNC loop, resolved cursor jump/warp issues on micro-movements, and implemented seamless, single-instance `dragPath` tracking with `GestureDescription.getMaxStrokeDuration()` dynamic duration bounds for natural drag velocity.
10. **Tenkey & Floating Button Canvas-Direct Optimization (`Tenkey`, `FloatingButton`, `HudCornerDrawable`, `FloatRingDrawable`)**: Replaced heavy VectorDrawable XML and 6-group matrix rotations with direct `Canvas.drawArc` (`FloatRingDrawable`) and unified HUD corners (`HudCornerDrawable`), flattened `layout_float_button.xml` from `FrameLayout + ImageView` into a single `View`, and completely removed redundant XML parse cycles from the overlay render loops.
11. **Floating Button Architectural Hardening (`FloatingButton`)**: Completely decoupled `FloatingButton` from `AccessibilityService` global actions (strictly routing intents through MVI `StateManager`), introduced `activePointerId` tracking for multi-touch immunity, enforced screen boundary clamping, eliminated dual-lifecycle `applyParams` / `FLAG_NOT_TOUCHABLE` hacks in favor of `BaseOverlay` lifecycle, and added dirty-checking to prevent IPC flooding during drag updates.
12. **Settings Repository, DiffUtil ListAdapter & Unit Test Hardening (`SavedData`, `AppMenu`, `StateManagerTest`)**: Upgraded `SavedData` to fully asynchronous disk persistence (`apply()` / Coroutines IO) with SharedPreferences test injection support, refactored `AppMenu` to `ListAdapter` + `DiffUtil` with payload-based partial binds for $O(1)$ 60FPS number preview transitions, and added a full unit test suite (`StateManagerTest`) verifying all MVI state machine transitions.
