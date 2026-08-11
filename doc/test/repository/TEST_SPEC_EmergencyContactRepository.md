# テスト仕様書 - EmergencyContactRepository

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/data/repository/EmergencyContactRepositoryTest.kt`

## 1. 概要
利用者に紐付く「緊急連絡先」のデータアクセス、永続化、および操作ログ記録の正確性を検証する。
特に、新規登録時の ID 発行ルールと、保存・削除に伴う監査ログの生成（詳細情報の付記）を対象とする。

## 2. 連絡先操作テスト (CRUD)
**目的:** 連絡先の保存（新規・更新）および削除が DAO を介して正しく行われ、期待される監査ログが記録されることを検証する。

| ID     | テスト項目       | 検証内容                           | 期待結果                                                    |
|:-------|:------------|:-------------------------------|:--------------------------------------------------------|
| CUR-01 | 新規登録 (IDなし) | `insertContact` を ID="NEW" で実行 | 新しい UUID が発行され、DAO の `insert` が呼ばれ、ログに `"INSERT"` が残ること |
| CUR-02 | 新規登録 (IDあり) | `insertContact` を既存 ID で実行     | 渡した ID が維持され、DAO の `insert` が呼ばれ、ログに `"INSERT"` が残ること   |
| CUR-03 | 情報更新        | `updateContact` の実行            | DAO の `update` が呼ばれ、ログに `"UPDATE"` が残り、詳細に施設名が含まれること    |
| CUR-04 | 物理削除        | `deleteContact` の実行            | DAO の `delete` が呼ばれ、ログに `"DELETE"` が残ること                |

## 3. データ取得テスト (Query)
**目的:** 条件に応じた連絡先情報の取得が DAO と正しく連携しているかを検証する。

| ID     | テスト項目    | 検証内容                    | 期待結果                              |
|:-------|:---------|:------------------------|:----------------------------------|
| GET-01 | 利用者別一覧取得 | `getContactsByPersonId` | 指定した利用者 ID で DAO の取得 Flow が返されること |
| GET-02 | ID指定取得   | `getContactById`        | 指定した ID で DAO の取得メソッドが呼ばれること      |
