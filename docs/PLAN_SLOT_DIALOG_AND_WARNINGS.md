# PLAN: スロット長押しダイアログ表示不具合の修正および非推奨警告の解消

## 1. 概要
- **目的**: 
  1. `AppMenu` 上でアプリアイコン（スロット 01〜40）を長押しした際に、スロット機能変更・カスタマイズダイアログが確実に表示されるようにする。
  2. Kotlin コンパイル時に発生している以下の 6 件の警告（非推奨 API / 型不一致）を完全に解消し、最新 Android 仕様に適合した堅牢でシンプルなコードにする。
     - `AppMenu.kt`: `SOFT_INPUT_ADJUST_RESIZE` (API 30+)
     - `AppMenu.kt`: `TYPE_PHONE` (API 26+) -> **ダイアログが表示されない根本原因**
     - `MenuSlotAction.kt`: `optString` における `Nothing?` 型不一致
     - `Vibration.kt`: `VIBRATOR_SERVICE` および `vibrate(Long)` (API 26+ / API 31+)

---

## 2. 原因調査と Android 仕様分析

### (1) 長押しダイアログが表示されない原因
- **原因**: 
  - `AccessibilityService` は `Activity` ではないため、サービスから `AlertDialog` を生成して `show()` を呼ぶ場合、適切な Window Type をダイアログの Window に明示的に設定しなければならない。
  - 現在のコードでは `dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)` が指定されていたため、Android 8.0 (API 26) 以降ではセキュリティ例外（`BadTokenException`）またはサイレントに無視されて画面に描画されなかった。
- **Android 公式仕様に基づく解決法**:
  - `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` をダイアログの Window Type に設定する。
  - `AccessibilityService` 自身が使用しているオーバーレイ Window と同じトークン種別（`TYPE_ACCESSIBILITY_OVERLAY`）を使用することで、余計な権限エラーを起こさずに最前面に確実にダイアログを描画する。
  - テーマ適用漏れによる不可視化を防ぐため、`ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)` を統一して使用する。

### (2) `MenuSlotAction.kt` の型不一致
- **原因**: 
  - `json.optString("mime", null)` を実行した際、Kotlin コンパイラが第 2 引数の `null` を `Nothing?` と推論し、`String` を期待する `optString` オーバーロードと型不一致の警告を出している。
- **解決法**:
  - `if (json.has("mime") && !json.isNull("mime")) json.getString("mime") else null` のように明示的にパースする。

### (3) `Vibration.kt` の非推奨警告
- **原因**: 
  - `context.getSystemService(Context.VIBRATOR_SERVICE)` および `vibrator.vibrate(50L)` が Android 8.0 (API 26) および Android 12 (API 31) で非推奨となっている。
- **解決法**:
  - API 31 以降: `VibratorManager` から `defaultVibrator` を取得。
  - API 26 以降: `VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)` を使用。
  - API 26 未満: 従来の `vibrator.vibrate(duration)` で安全にフォールバック。

---

## 3. 実装計画・タスク一覧

- [ ] **Step 1**: `docs/PLAN_SLOT_DIALOG_AND_WARNINGS.md` の作成（本ドキュメント）
- [ ] **Step 2**: `Vibration.kt` のリファクタリング（最新 VibrationEffect API 対応）
- [ ] **Step 3**: `MenuSlotAction.kt` の型安全な JSON パース修正
- [ ] **Step 4**: `AppMenu.kt` の修正
  - [ ] `SOFT_INPUT_ADJUST_RESIZE` の警告解消
  - [ ] ダイアログ生成ヘルパー関数（`showOverlayDialog`）の共通化と `TYPE_ACCESSIBILITY_OVERLAY` 統一
  - [ ] 各種ダイアログ（スロット変更、アプリ選択、システムアクション選択）の表示テスト確認
- [ ] **Step 5**: ビルド・静的検証の実施
- [ ] **Step 6**: Graphify ナレッジグラフ（`graph.json`, `graph.html`, `GRAPH_REPORT.md`）の更新
- [ ] **Step 7**: Git コミットおよびプッシュ
