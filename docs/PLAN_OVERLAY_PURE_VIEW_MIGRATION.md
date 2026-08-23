# 実装計画・仕様書: 常駐オーバーレイの Pure View 化と XML インフレート撤廃

## 1. 概要・目的
常駐オーバーレイ（`FloatingButton`, `Touchpad`, 仮想カーソル）の生成処理から `LayoutInflater.inflate` による XML パース処理を完全に撤廃し、Kotlin コードによる Pure Custom View / 直接生成構造へリファクタリングする。
既存の操作性・機能・外観は100%維持したまま、メモリ割り当て（GC）の抑制と描画追従速度の向上を実現する。

---

## 2. 変更対象と実装内容

### ① `FloatingButton.kt`
- `layout_float_button.xml` のインフレートを廃止。
- `View(context)` を直接生成し、`FloatRingDrawable` を直接背景としてセット。
- 階層ネストを完全ゼロ化（単一 View を WindowManager へ配置）。

### ② `Touchpad.kt` (タッチパッド本体 & 仮想カーソル)
- `layout_touchpad.xml` および `layout_cursor.xml` のインフレートを廃止。
- **タッチパッド本体**: `FrameLayout(context)` をコード上で直接生成し、背景・サイズ・パディング・タッチリスナーを設定。
- **仮想カーソル**: `View(context)` をコード上で直接生成し、外枠リング・中心点を描画する `CursorDrawable` または直接背景設定により軽量化。

### ③ 不要リソースの削除
- `app/src/main/res/layout/layout_float_button.xml`
- `app/src/main/res/layout/layout_touchpad.xml`
- `app/src/main/res/layout/layout_cursor.xml`

---

## 3. 保証事項
- タップ・ドラッグ・ロングプレス判定の仕様維持
- 設定画面（サイズ・透明度・位置・速度）の変更即時反映
- メモリリークのない安全な WindowManager ライフサイクル管理
