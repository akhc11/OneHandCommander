# PLAN: MediaStore による端末内高速ファイルインデックス検索への移行

## 1. 概要・目的
現在、`AppMenu` のファイル検索は `java.io.File.listFiles()` を用いて主要フォルダ（Download, Pictures 等）の直下のみを走査・インメモリ保持しています。
本改修では、Android OS標準の純正インデックスシステムである **`MediaStore`（`MediaStore.Files`）** を用いた高速インデックス検索に移行します。

### メリット
- **端末全体の全階層ファイルを網羅**: 深い階層のPDF、オフィス書類、写真、音声、ZIPなども瞬時に検索可能。
- **高速性**: OSがバックグラウンドで事前構築しているSQLiteインデックスをクエリするため、数ミリ秒で結果を取得可能。
- **将来のOS互換性**: Android 10以降の Scoped Storage 制約に完全準拠し、Android 14/15/16以降でも永続的に動作が保証される。

---

## 2. Android公式仕様に基づく設計方針

### ① クエリ設計 (`MediaStore.Files` & `ContentUris`)
- `MediaStore.Files.getContentUri("external")` を対象にクエリ。
- **取得カラム**: `_ID`, `DISPLAY_NAME`, `SIZE`, `DATE_MODIFIED`, `MIME_TYPE`
- **非推奨APIの排除**: 非推奨（Deprecated）となった `DATA`（絶対パス）カラムに依存せず、`ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)` で安全な `content://` URI を生成。
- **並び順**: `DATE_MODIFIED DESC`（最新更新順）
- **初期表示**: 最近更新されたファイル（上位20〜30件をバックグラウンド取得し、メニューには上位4件を表示）
- **検索時**:
  - `DISPLAY_NAME LIKE ?` によるプレースホルダー付きDBクエリ、または最新キャッシュに対する高速インメモリフィルタリングを組み合わせて 0ms レスポンスを維持。

### ② パーミッション対応（Android 10〜14/15互換）
- `AndroidManifest.xml` に以下のパーミッションを定義:
  - `READ_EXTERNAL_STORAGE` (Android 12以下用)
  - `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (Android 13+用)
- 権限が一部または未許可の状態でもアプリがクラッシュしないよう、`try-catch` による安全なクエリと空リストの返却（Graceful Degradation）を徹底。

### ③ バックグラウンド非同期処理 (`Dispatchers.IO`)
- UIスレッド（メインスレッド）の完全保護。
- `ContentResolver.query()` はすべて `Dispatchers.IO` で実行し、結果をメインスレッドに反映。
- コルーチンのキャンセル制御（検索文字の入力ごとに直前の検索Jobを適切に管理）。

### ④ ファイルオープン・共有・操作の近代化 (`Uri` 統一)
- `MediaStore` から得られる `content://` URI を `FileInfo` モデルに保持。
- `UiHelper.openUri` / `UiHelper.shareUri` を追加し、`content://` URI を `Intent.FLAG_GRANT_READ_URI_PERMISSION` とともに `Intent.ACTION_VIEW` / `Intent.ACTION_SEND` に渡してスムーズに起動。

---

## 3. 変更対象ファイルと影響範囲

1. **`app/src/main/AndroidManifest.xml`**
   - メディア読み取りパーミッションの定義追加。
2. **`app/src/main/java/com/example/onehandcommander/utils/UiHelper.kt`**
   - `Uri` 直接指定によるファイルオープン・共有メソッドの追加。
3. **`app/src/main/java/com/example/onehandcommander/ui/overlays/AppMenu.kt`**
   - `FileInfo` を `Uri` ベースに改修。
   - `loadRecentFiles()` を `listFiles()` から `MediaStore` クエリへ変更。
   - `filterAll(query)` のファイル検索部分を `MediaStore` 連携に最適化。

---

## 4. UI/UXの変更点
- **見た目のUI変化**: なし（既存の美しいデザイン・カードレイアウト・4件表示を完全維持）。
- **機能面の進化**:
  - これまで拾えなかったフォルダ（サブフォルダ、各種アプリの保存先など）のファイルが「最近のファイル」および「検索」に即座に出現するようになる。
  - ファイルタップ時のアプリ起動（ビューアー起動や共有）が OS 標準の Content URI により確実に動作する。

---

## 5. 検証手順
1. `compile_applet` による Kotlin コンパイル・型整合性の検証。
2. `graphify-out/` ナレッジグラフの整合性確認。
3. Git による論理コミット。

