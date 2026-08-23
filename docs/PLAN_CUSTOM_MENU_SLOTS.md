# 実装計画・仕様書: メニュースロット（01〜40）の自由割り当て機能

## 1. 概要・目的
現在のアプリメニューはインストール済みアプリの起動のみに固定されていますが、これを 01〜40 の各スロットごとにユーザーが **「アプリ」「OS標準操作（ホーム・戻る・通知・履歴・電源・スクショ）」「特定ファイル/ドキュメント」「OneHandCommander独自機能（タッチパッド・設定画面）」** を自由に割り当てられるように拡張します。

アイコンはユーザー要望に基づき、グラデーションや装飾を排除した **「超軽量・単色・フラット・シンプルな標準アイコン」** とし、描画負荷 0ms・視認性最大化を両立します。

---

## 2. 最速アイコン描画設計方針 (`DirectIconDrawable` / Canvas直接描画)
- XMLファイル読み込みやパースオーバーヘッドを完全にゼロ (0ms) にするため、**`DirectIconDrawable` (純粋なKotlinコードによるCanvas直接描画)** を採用。
- 静的に生成された `Paint` / `Path` を再利用し、アロケーション・GC負荷を完全に排除。
- 単色フラット（#FFFFFF またはアクセントカラー）で、視認性に優れた標準幾何学アイコンを即座にレンダリング：
  - **ホーム (`SystemAction.HOME`)**: 屋根と本体のシャープな家型アウトライン
  - **戻る (`SystemAction.BACK`)**: シンプルなシェブロン左矢印
  - **アプリ履歴 (`SystemAction.RECENTS`)**: 2枚の重なるスクエア
  - **通知 (`SystemAction.NOTIFICATIONS`)**: ベル形状アウトライン
  - **スクリーンショット (`SystemAction.SCREENSHOT`)**: 四隅のキャプチャコーナー枠
  - **電源メニュー (`SystemAction.POWER_DIALOG`)**: 標準的な電源シンボル（円弧＋垂直バー）
  - **ファイル (`SystemAction.OPEN_FILE`)**: 右上角折れドキュメント
  - **タッチパッド (`FeatureAction.LAUNCH_TOUCHPAD`)**: スタイリッシュなカーソルポインター
  - **設定 (`FeatureAction.OPEN_SETTINGS`)**: シンプルな歯車形状

---

## 3. アーキテクチャとデータモデル設計

### ① スロットアクションモデル (`MenuSlotItem.kt`)
```kotlin
sealed class MenuSlotAction {
    data class LaunchApp(val packageName: String, val appLabel: String) : MenuSlotAction()
    data class SystemCommand(val actionType: SystemAction) : MenuSlotAction()
    data class OpenFile(val filePath: String, val fileName: String) : MenuSlotAction()
    data class CustomFeature(val featureType: FeatureAction) : MenuSlotAction()
    object AutoApp : MenuSlotAction() // 未設定時の自動アプリ割り当て（従来互換）
}

enum class SystemAction {
    HOME, BACK, RECENTS, NOTIFICATIONS, SCREENSHOT, POWER_DIALOG, QUICK_SETTINGS
}

enum class FeatureAction {
    LAUNCH_TOUCHPAD, OPEN_SETTINGS
}
```

### ② 永続化設計 (`SavedData.kt`)
- `SavedData.getSlotConfig(slotIndex: Int): MenuSlotAction?`
- `SavedData.saveSlotConfig(slotIndex: Int, action: MenuSlotAction)`
- `SavedData.resetSlotConfig(slotIndex: Int)`
- `SavedData.resetAllSlots()`
- キー形式: `slot_custom_{slotIndex}`（JSON形式またはシリアライズ形式でSharedPreferencesに保存）

---

## 4. UI/UX フロー（長押しカスタマイズ ＆ 実行）

### ① スロット長押し時の設定メニュー表示
- 01〜40 の任意のスロットを長押しすると、軽量なアクション選択ダイアログを表示：
  1. 📱 **アプリを選択**（インストール済みアプリ一覧から選択）
  2. ⚡ **システム操作を選択**（ホーム / 戻る / 通知 / 履歴 / スクショ / 電源）
  3. 📄 **ファイルを選択**（最近のファイルまたはストレージから選択）
  4. ⚙️ **便利機能を選択**（タッチパッド起動 / 設定画面を開く）
  5. 🔄 **デフォルト（自動アプリ割り当て）に戻す**

### ② タップ / テンキー入力時の実行
- スロットタップ時、またはテンキーで番号（01〜40）を入力した際：
  - `LaunchApp` → アプリを起動
  - `SystemCommand` → `AccessibilityService.performGlobalAction` を即座に実行
  - `OpenFile` → `UiHelper.shareFile` / `Intent.ACTION_VIEW` でファイルを開く
  - `CustomFeature` → StateManager 経由でタッチパッド起動や設定画面遷移

---

## 5. 保証事項
- 0キーでの検索機能、および検索絞り込み時のアプリ一覧表示は100%維持
- テンキー入力（01〜40）でのプレビューハイライト＆即時実行は100%維持
- ドラッグハンドルによる位置移動・座標保存も100%維持
- アイコンキャッシュおよび 0ms 描画パフォーマンスを維持
