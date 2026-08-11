# テスト仕様書 - ConditionRepository

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/data/repository/ConditionRepositoryTest.kt`

## 1. 概要
所見メモ（カテゴリB）および所見写真に関するデータアクセス、永続化、および操作ログ記録の正確性を検証する。
特に、保存・削除に伴う監査ログの生成ルールと、写真データの紐付け管理（迷子写真の救済等）を対象とする。

## 2. 所見メモ操作テスト (ConditionAtVisit)
**目的:** 所見メモの保存・削除が DAO を介して正しく行われ、期待される監査ログが記録されることを検証する。

| ID     | テスト項目 | 検証内容                                      | 期待結果                                               |
|:-------|:------|:------------------------------------------|:---------------------------------------------------|
| MEM-01 | 新規保存  | `insertConditionAtVisit` (isUpdate=false) | DAO の `insert` が呼ばれ、`actionType="INSERT"` のログが残ること |
| MEM-02 | 更新保存  | `insertConditionAtVisit` (isUpdate=true)  | DAO の `insert` が呼ばれ、`actionType="UPDATE"` のログが残ること |
| MEM-03 | 物理削除  | `deleteConditionAtVisit` の実行              | DAO の `delete` が呼ばれ、`actionType="DELETE"` のログが残ること |
| MEM-04 | 時刻検索  | `findConditionAtTime` の実行                 | 指定された条件で DAO の `findAtTime` が呼ばれること                |

## 3. 写真メタデータ操作テスト (ConditionPhoto)
**目的:** 写真の保存、削除、および複雑な紐付け操作（リンク、再登録）が正しく行われることを検証する。

| ID     | テスト項目    | 検証内容                           | 期待結果                                              |
|:-------|:---------|:-------------------------------|:--------------------------------------------------|
| PHT-01 | 写真保存     | `insertConditionPhoto` の実行     | DAO の `insert` が呼ばれ、`condition_photo_db` のログが残ること |
| PHT-02 | 一時保存の紐付け | `linkTemporaryPhotosToRecord`  | DAO の一括更新が呼ばれ、紐付け成功のログが残ること                       |
| PHT-03 | 写真の再紐付け  | `reattachPhotoToRecord` (迷子救済) | DAO の `updateConditionId` が呼ばれ、再紐付けのログが残ること       |
| PHT-04 | ファイル救済登録 | `adoptFileAsPhoto` (未登録ファイル救済) | 物理情報から `ConditionPhoto` が構築・保存され、ログが残ること          |
| PHT-05 | 写真削除     | `deleteConditionPhotoById`     | DAO の `deleteById` が呼ばれ、削除のログが残ること                |

## 4. データ取得・検索テスト (Query)
**目的:** 各種条件によるデータ取得が DAO と正しく連携しているかを検証する。

| ID     | テスト項目   | 検証内容                             | 期待結果                               |
|:-------|:--------|:---------------------------------|:-----------------------------------|
| QRY-01 | 利用者別所見  | `getConditionAtVisitByPersonId`  | 指定された利用者 ID で DAO の取得 Flow が返されること |
| QRY-02 | キーワード検索 | `getPersonIdsByConditionKeyword` | DAO のキーワード検索 Flow が返されること          |
| QRY-03 | 全写真取得   | `getAllPhotosByPersonIdFlow`     | 利用者に紐付く全写真の Flow が返されること           |
