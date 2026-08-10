# テスト仕様書 - MedicationRepository

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/data/repository/MedicationRepositoryTest.kt`

## 1. 概要
利用者の「服薬管理」に関するデータの永続化管理および操作ログ記録の正確性を検証する。
服薬状況の保存・更新・削除に伴う監査ログの生成ルールと、月単位のデータ取得が DAO と正しく連携していることを対象とする。

## 2. 服薬記録操作テスト (CRUD)
**目的:** 服薬記録の保存および削除が DAO を介して正しく行われ、期待される詳細情報を含む監査ログが記録されることを検証する。

| ID     | テスト項目 | 検証内容                                      | 期待結果                                                             |
|:-------|:------|:------------------------------------------|:-----------------------------------------------------------------|
| MED-01 | 新規登録  | `insertMedicationRecord` (isUpdate=false) | DAO の `insert` が呼ばれ、`actionType="INSERT"` のログ（日付、枠、ステータス含む）が残ること |
| MED-02 | 内容更新  | `insertMedicationRecord` (isUpdate=true)  | DAO の `insert` が呼ばれ、`actionType="UPDATE"` のログが残ること               |
| MED-03 | 物理削除  | `deleteMedicationRecord` の実行              | DAO の `delete` が呼ばれ、`actionType="DELETE"` のログが残ること               |

## 3. データ取得テスト (Query)
**目的:** 指定された条件（利用者、年月）によるデータ取得が DAO と正しく連携しているかを検証する。

| ID     | テスト項目  | 検証内容                                    | 期待結果                                            |
|:-------|:-------|:----------------------------------------|:------------------------------------------------|
| GET-01 | 全履歴取得  | `getMedicationRecords` の Flow 購読        | 指定した利用者 ID で DAO の取得 Flow が返されること               |
| GET-02 | 月間履歴取得 | `getMedicationRecordsByMonth` の Flow 購読 | 指定した利用者 ID と年月（"yyyy-MM"）で DAO の取得 Flow が返されること |
