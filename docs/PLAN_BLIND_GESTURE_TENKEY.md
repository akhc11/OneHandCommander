# 開発計画書: ブラインド・ジェスチャーテンキー（Relative Gesture Tenkey）

## 1. 概要と背景
### 課題
- 従来の固定レイアウト型テンキー（3x4グリッド）は、画面上でのサイズや位置に依存し、アプリメニュー（`AppMenu`）と重なって視認性を損ねる問題があった。
- また、ユーザーが画面上のボタン位置を目視しながらタップする必要があり、片手操作でのブラインドタッチが困難だった。

### 目的・新仕様
- **画面非依存・ブラインド入力**: 画面のどこを触っても、そのタッチ開始点（DOWN座標）を原点 `(startX, startY)` とし、上下左右・斜め方向のフリック量と角度で数字（1〜9）を判定する。
- **2ストローク入力（シンプル＆確実）**:
  - 1ストローク目: フリックまたはタップで1桁目を決定（ストック）。
  - 2ストローク目: 200ms以内に2回目のフリックまたはタップで2桁目を決定し即確定実行。
  - 1桁確定タイマー: 1桁目入力後、200ms次の入力がなければ1桁の数字として即確定実行。
- **「0」の特殊処理（長押し即確定）**:
  - タッチ長押し（例: 200msホールド）で即座に「0」と判定し、1桁待機タイマーを待たずに即時確定（検索機能等の起動）。
- **触覚フィードバック（Haptics）の差別化**:
  - 直交方向（2:上, 4:左, 6:右, 8:下）: 軽微なパルス（微小クリック / 10ms）
  - 斜め方向（1:左上, 3:右上, 7:左下, 9:右下）: 強めのクリック（25ms / 中振動）で斜め判定を指先に確実に伝達。
  - 中心タップ（5）: 短いダブルパルス。
  - 長押し（0）: 重厚なホールド確定振動。

---

## 2. Android OS仕様・技術調査

### ① MotionEvent & 角度・距離の数学的判定
- **原点設定**: `MotionEvent.ACTION_DOWN` 時の `(event.rawX, event.rawY)` を記録。
- **移動ベクトル**: `dx = currentX - startX`, `dy = currentY - startY`, `distance = sqrt(dx^2 + dy^2)`
- **スワイプ閾値**:
  - `distance < SWIPE_THRESHOLD_PX` (例: 24dp): 「中心 (5)」判定。
  - `distance >= SWIPE_THRESHOLD_PX`: 角度判定。
- **角度計算 (`Math.atan2`)**:
  - `angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))` （-180° 〜 +180°）
  - 8方向のセクター分割（45度刻み、または親指操作に合わせた非対称セクター）：
    - **1 (左上)**: -157.5° 〜 -112.5°
    - **2 (上)**: -112.5° 〜 -67.5°
    - **3 (右上)**: -67.5° 〜 -22.5°
    - **6 (右)**: -22.5° 〜 +22.5°
    - **9 (右下)**: +22.5° 〜 +67.5°
    - **8 (下)**: +67.5° 〜 +112.5°
    - **7 (左下)**: +112.5° 〜 +157.5°
    - **4 (左)**: +157.5° 〜 +180° および -180° 〜 -157.5°

### ② 長押し判定とタイマー制御
- **長押しタイマー (Handler / Runnable)**:
  - `ACTION_DOWN` 時に `handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS = 200ms)` をスケジュール。
  - `ACTION_MOVE` でスワイプ閾値を超えた場合、または `ACTION_UP` で指が離れた場合は `handler.removeCallbacks(longPressRunnable)` でキャンセル。
  - 200ms経過で指が動いていなければ「0」として確定し、`Vibration.vibrateHeavy()` を発火。
- **1桁確定タイマー**:
  - 1桁目の入力完了（`ACTION_UP`）時に `handler.postDelayed(singleDigitCommitRunnable, DIGIT_TIMEOUT_MS = 200ms)` をスケジュール。
  - 200ms以内に次の `ACTION_DOWN` が発生した場合はタイマーをキャンセルし、2桁目受付モードへ遷移。

### ③ 触覚フィードバック (Vibrator / VibrationEffect)
- Android 8.0 (API 26) 以上: `VibrationEffect.createOneShot(duration, amplitude)` または `VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK / EFFECT_TICK / EFFECT_HEAVY_CLICK)` を利用。
- `Vibration.kt` に以下のメソッドを追加/整理：
  - `vibrateOrthogonal()`: 上下左右用（短・軽）
  - `vibrateDiagonal()`: 斜め用（中・強）
  - `vibrateCenterTap()`: 中心5用（ダブルパルス）
  - `vibrateLongPress()`: 0用（長・重）

### ④ Overlay UI / WindowManager
- 巨大なボタン群を持たない**フルスクリーン透明タッチサーフェス（または小型ジェスチャーパッド）**として動作。
- 指を置いた瞬間に、タッチ位置 `(startX, startY)` を中心とする薄いHUD（8方向ガイドリングや入力中数値のミニマルバッジ）を描画。指を離すと消去。
- これにより、アプリメニューのグリッド表示を一切遮らない。

---

## 3. 影響範囲・変更対象ファイル

| 対象ファイル | 変更種別 | 変更内容 |
| :--- | :--- | :--- |
| `docs/PLAN_BLIND_GESTURE_TENKEY.md` | 新規作成 | 本計画書 |
| `app/src/main/java/com/example/onehandcommander/utils/Vibration.kt` | 修正 | 上下左右（弱）、斜め（強）、0長押し、中心タップ用の振動メソッド追加 |
| `app/src/main/java/com/example/onehandcommander/ui/overlays/Tenkey.kt` | 大幅改修 | 固定ボタン式から相対ジェスチャー判定（8方向フリック＋長押し0＋2ストローク受付）へ刷新 |
| `app/src/main/java/com/example/onehandcommander/ui/drawables/GestureTenkeyHudDrawable.kt` | 新規作成 | タッチ開始点に描画する軽量HUD（8方向コンパス・入力軌跡） |
| `app/src/main/java/com/example/onehandcommander/utils/Constants.kt` | 修正 | ジェスチャー判定閾値、タイマー値（200ms）等の定数定義 |

---

## 4. UI/UX 動作フロー

1. **画面タッチ (ACTION_DOWN)**:
   - 画面の任意の場所をタッチ。その座標を中心とする半透明HUDが小さくフェードイン。
   - 長押しタイマー（200ms）起動。
2. **操作分岐**:
   - **パターンA (0入力)**: 200ms間指を動かさず保持 → 「0」と判定、重い振動が鳴り、即座に検索機能（または0番アクション）が実行されオーバーレイ終了。
   - **パターンB (1〜4, 6〜9入力)**: いずれかの方向へ指をスライド → 閾値（24dp）突破時に方向に応じた振動（直交=軽、斜め=強）。指を離す（ACTION_UP）と1桁目確定。
   - **パターンC (5入力)**: 200ms未満でスライドせず指を離す → 「5」として1桁目確定（ダブルパルス振動）。
3. **1桁目確定後 (2ストローク目待機)**:
   - AppMenu等のプレビューに1桁目を表示。
   - 200ms待機タイマー開始。
   - **ケース1**: 200ms以内に次のタッチがなければ、その1桁で確定実行（例: `3` 番起動）。
   - **ケース2**: 200ms以内に再度タッチ → 2回目のフリック／タップで2桁目を判定し、指を離した瞬間に即座に2桁確定実行（例: `3` + `8` = `38` 番起動）。

---

## 5. 検証・テスト方針
- **単体テスト**: 角度計算 (`Math.atan2`) と8方向セクター分類ロジックの網羅的テスト。
- **タイマー制御テスト**: 200ms以内の連続入力 vs 200msタイムアウト時の確実な分岐検証。
- **実機操作感検証**: 親指の可動域における斜め方向（1, 3, 7, 9）の入力成功率とハプティクスフィードバックの明瞭さ。
