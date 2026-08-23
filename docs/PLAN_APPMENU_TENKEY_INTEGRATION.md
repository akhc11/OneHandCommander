# Plan: AppMenu とジェスチャーテンキーの単一Window統合

## 1. 課題の根本原因
- `OverlayManager.kt` において、`ServiceState.MenuNormal` / `MenuSearch` 時に `appMenu.show()` と `tenkeyManager.show()` を別々のWindowManager Window（共に `MATCH_PARENT` 全画面）として重ねて表示していた。
- 前面に重なった `Tenkey` のWindowが全画面のタッチイベントを消費するため、背後にある `AppMenu` のカード内（アプリアイコン、移動ハンドル、検索バー）にタッチが届かなくなっていた。

## 2. 解決方針
- **単一Window統合 (Single Window Architecture)**:
  - `MenuNormal` / `MenuSearch` 表示時は `tenkeyManager.hide()` とし、`AppMenu`（全画面Window）単体で完結させる。
  - `AppMenu` のルートView（`menu_dim_background`）において：
    - `menu_card` 内部のタッチ: 通常通り各View（アプリアイコン、ドラッグハンドル、検索バー）が処理。
    - `menu_card` 外部のタッチ＆スワイプ: `GestureTenkeyHudDrawable` を用いた相対座標ジェスチャーテンキーとして動作し、1〜40番のスロットをハイライト＆即時実行。
    - `menu_card` 外部の静止タップ: メニューを閉じる（直感的なバックグラウンドタップ終了）。
- **テンキー単体起動時 (`ServiceState.TenkeyActive`)**:
  - これまで通り `Tenkey.kt` が独立して動作。

## 3. 影響範囲
- `app/src/main/java/com/example/onehandcommander/core/OverlayManager.kt`
- `app/src/main/java/com/example/onehandcommander/ui/overlays/AppMenu.kt`
