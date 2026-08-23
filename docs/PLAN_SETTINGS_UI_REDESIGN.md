# 実装計画・仕様書: 設定画面UIの刷新 (Settings UI Redesign)

## 1. 概要・目的
既存の設定画面はテキストと標準チェックボックス・シークバーが縦一列に並んでおり、項目の境界線が曖昧で視認性・片手での操作性が不足していました。
Material 3 に準拠したカード型コンテナ構造、ピル型数値インジケータ、分かりやすい説明テキスト、ワンタップリセット、サービス稼働ステータス表示を導入し、使いやすく洗練されたUIに改修しました。

---

## 2. 変更対象ファイルとアーキテクチャ設計
| 対象ファイル | 変更内容 |
|---|---|
| `app/src/main/res/layout/activity_main.xml` | アプリヘッダー、アクセシビリティ状態カード、設定リセットボタンの配置 |
| `app/src/main/res/values/colors.xml` | テーマカラー (Primary, Container, Status, Card, Text) の定義 |
| `app/src/main/res/values/strings.xml` | セクション名・各設定項目の分かりやすいラベルと説明文の定義 |
| `app/src/main/res/drawable/bg_settings_card.xml` | セクションごとのカード背景・境界線 Drawable |
| `app/src/main/res/drawable/bg_pill_value.xml` | リアルタイム数値用ピル型バッジ Drawable |
| `app/src/main/res/drawable/bg_status_badge_active.xml` | サービス稼働中ステータスバッジ |
| `app/src/main/res/drawable/bg_status_badge_inactive.xml` | サービス停止中ステータスバッジ |
| `app/src/main/java/.../settings/model/SettingItem.kt` | `subtitleResId`, `descriptionResId` プロパティの追加 |
| `app/src/main/java/.../settings/SettingsConfigProvider.kt` | 各設定項目への説明文リソースIDの割り当て |
| `app/src/main/java/.../settings/SettingsViewBinder.kt` | カード型動的レンダリング、ピル型等幅数値バッジ、SwitchCompat 全行タップ対応 |
| `app/src/main/java/.../settings/SavedData.kt` | `resetToDefaults()` (全設定の一括初期化メソッド) の追加 |
| `app/src/main/java/.../settings/SettingsActivity.kt` | サービス稼働判定ロジック、設定リセット確認ダイアログの連携 |

---

## 3. UI/UX 改善ポイント
1. **カード型セクション構造**: 「フローティングボタン」「テンキー」「タッチパッド」ごとに独立したカードでまとめ、視覚的なノイズを低減。
2. **ピル型数値バッジ**: スライダー操作時に等幅フォントでリアルタイムに値を視認可能。
3. **親切な説明テキスト**: 各設定項目が「何に影響するか」をサブテキストで明示。
4. **サービス状態表示**: ユーザー補助機能がONかOFFかを上部で即時把握可能。
5. **安全な初期化機能**: 誤って極端な値を設定しても、ダイアログ確認後にワンタップで推奨値へ復帰可能。
