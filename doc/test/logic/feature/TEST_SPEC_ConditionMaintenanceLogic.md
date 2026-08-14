# テスト仕様書 - ConditionMaintenanceLogic

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/ConditionMaintenanceLogicTest.kt`

## 1. 概要
所見メモに関連するデータの整合性維持ロジックの正確性を検証する。
特に、DBレコードと物理ストレージ上のファイルの不整合（未割り当て写真）を正しく検出し、適切な理由（Type）で分類できることを対象とする。

## 2. 未割り当て写真判定テスト (identifyUnassignedPhotos)
**目的:** DBレコードと物理ファイルのリストを突き合わせ、不整合の種類を正しく判定・ソートできることを検証する。

| ID      | テスト項目   | 条件 (入力データ)                          | 期待結果 (Type / 理由)                                                |
|:--------|:--------|:------------------------------------|:----------------------------------------------------------------|
| MAIN-01 | 正常系     | 全てのDBレコードが有効な所見IDを持ち、ファイルも存在する      | 未割り当てリストが空であること                                                 |
| MAIN-02 | 一時保存放置  | `conditionId` が空のDBレコードが存在する        | `TEMPORARY` (R.string.unassigned_photo_type_temporary)          |
| MAIN-03 | 親記録消失   | DBにある `conditionId` が所見一覧に存在しない     | `UNASSIGNED_RECORD` (R.string.unassigned_photo_type_unassigned) |
| MAIN-04 | 未登録ファイル | ストレージに `img_` で始まるファイルがあるが、DBに名前がない | `FILE_ONLY` (R.string.unassigned_photo_db_unregistered)         |
| MAIN-05 | ソート順    | 複数の不整合が混在する                         | `capturedAt` の降順（新しい順）で並んでいること                                  |
| MAIN-06 | サムネイル除外 | `thumb_` で始まるファイルのみが存在する            | 未割り当てとして検出されないこと（メイン画像を基準とするため）                                 |
