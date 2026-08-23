# 実装計画・仕様書: 常駐オーバーレイ描画の Shape Drawable XML 排除 & Canvas 直接描画 / Paint キャッシュ化

## 1. 概要・目的
常駐オーバーレイ（タッチパッド背景・枠線描画など）から XML Shape Drawable（`bg_wireframe.xml`）のパース処理を完全排除し、Kotlin コードによるゼロ・アロケーション直接描画（`TouchpadWireframeDrawable`）へリファクタリングする。
外観・デザイン・機能・透過度設定を100%維持しながら、I/Oとリフレクションを排除し、滑らかな追従性と省メモリ性を実現する。

---

## 2. 変更対象と実装内容

### ① `TouchpadWireframeDrawable.kt` の新設
- 高速なアンチエイリアス対応 `Paint`（背景塗りつぶし・枠線ストローク）を初期化・キャッシュ。
- `drawRoundRect` を用いて、半透明ダーク背景（`#CC121212`）とシアン/ターコイズ枠線（`#26C6DA`、角丸12dp、線幅2dp）を直接レンダリング。
- ガベージコレクション（GC）を引き起こすオブジェクト生成を `draw()` 内部で一切行わない。

### ② `Touchpad.kt` のリファクタリング
- `ContextCompat.getDrawable(..., R.drawable.bg_wireframe)` によるファイル読み込みを廃止。
- `TouchpadWireframeDrawable` インスタンスを直接背景にセット。

### ③ 不要リソースの削除
- `app/src/main/res/drawable/bg_wireframe.xml`

---

## 3. 保証事項
- 既存のサイバー調・半透明ターコイズ枠線デザインを完全維持
- 設定画面での透明度・サイズ変更への即時追従
- 仮想カーソルやタッチ入力の高速な追従性
