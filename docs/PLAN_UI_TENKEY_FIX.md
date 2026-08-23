# PLAN: ファイル表示の簡素化（ファイル名最大化）およびテンキー入力精度の改善

## 1. 概要・課題の整理

### 課題①: 最近のファイル表示の幅不足
- 現在の `item_file_list.xml` にてファイルサイズ（例: 2.4 MB）や更新日時（例: 08/23 12:30）を表示しているため、横幅が圧迫され、肝心のファイル名が切り詰められてしまっている。
- **改善方針**: サイズ・日時の表示（`file_info`）を完全に削除し、行の横幅全体をファイル名専用（`file_name`）として割り当てる。

### 課題②: テンキー入力（左上/左下の認識ズレと5の入力不具合）
- **「5が入力できない」根本原因**:
  - `MotionEvent.ACTION_DOWN` 時に 300ms（または200ms）の長押しタイマー（`longPressZeroRunnable`）が発動し、中央タップ（5）をしようとして指が少しでも画面に触れたままになると「0（検索）」が誤爆・即確定してしまっていた。
  - また、タップした際の微小な指のブレ（20dp以下）の許容値と、タップ判定（`ACTION_UP` 時の即時判定）が競合していた。
- **「左上が1だったのに左下が1になっている」原因と対応**:
  - 電卓配列（PCテンキー: 左下1, 2, 3 / 左上7, 8, 9）と、電話配列（スマホ: 左上1, 2, 3 / 左下7, 8, 9）の混同、あるいはジェスチャーテンキーの角度判定とHUD表示の不整合。
  - 現在のジェスチャー判定は「左上=-135°(1) / 上=-90°(2) / 右上=-45°(3) / 左下=135°(7) / 下=90°(8) / 右下=45°(9)」となっていますが、長押し0の暴発や誤判定により意図しない入力が発生していた。
  - 長押し0の閾値を調整（または5タップを最優先にし、明示的な下フリック/長押しへの分離）し、方向判定のしきい値とタップ判定を完全に安定化させる。

---

## 2. 具体的な改修方針

### ① ファイル一覧表示の最適化 (`item_file_list.xml`, `AppMenu.kt`)
- `item_file_list.xml` から `file_info`（TextView）を削除 / 非表示化。
- `file_name` の幅を `match_parent`（または `weight=1` で全幅）に拡大し、フォントサイズ・パディングを調整してファイル名を最大限読みやすくする。
- `RecentFilesListAdapter` のバインド処理をファイル名のみに簡素化。

### ② テンキー入力判定の根本修正 (`Tenkey.kt`, `Constants.kt`)
- **「5（中心タップ）」の確実な入力**:
  - `ACTION_DOWN` 時に即「0」判定が走るのを防ぐため、長押し判定時間を適切な値（500ms以上、または長押しを廃止しタップ=5、フリック=1〜4, 6〜9、0は明示的な操作に分離）に調整。
  - `distance < flickThresholdPx`（指の移動が少ない）で `ACTION_UP` された場合は、**確実に「5」として即座に受け付け**。
- **方向マッピングの完全検証とHUD同期**:
  - 電話配列（左上=1, 上=2, 右上=3, 左=4, 中心=5, 右=6, 左下=7, 下=8, 右下=9）の角度判定をより明確にし、斜め・直交の角度範囲を均等（45°刻み）にクリーン化。
  - HUD（`GestureTenkeyHudDrawable.kt`）の描画位置と、判定ロジックの完全一致を保証。

---

## 3. 変更対象ファイル
1. `app/src/main/res/layout/item_file_list.xml`
2. `app/src/main/java/com/example/onehandcommander/ui/overlays/AppMenu.kt`
3. `app/src/main/java/com/example/onehandcommander/ui/overlays/Tenkey.kt`
4. `app/src/main/java/com/example/onehandcommander/ui/drawables/GestureTenkeyHudDrawable.kt`
5. `app/src/main/java/com/example/onehandcommander/utils/Constants.kt`
