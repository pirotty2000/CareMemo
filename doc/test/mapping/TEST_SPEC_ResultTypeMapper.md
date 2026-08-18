# テスト仕様書 - ResultTypeMapper

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/ResultTypeMapperTest.kt`

## 1. 概要
監査ログ等で使用される操作結果識別子（String）から、UI 表示用の名称（リソース ID）への変換ロジックを検証する。

## 2. マッピングテスト (toResultLabelRes)
**目的:** 各結果識別子文字列が、期待される日本語名称のリソース ID に正しくマッピングされることを検証する。

| ID     | テスト項目     | 条件 (入力文字列)           | 期待結果 (リソースID)                            |
|:-------|:----------|:---------------------|:-----------------------------------------|
| MAP-01 | 成功        | `"SUCCESS"`          | `R.string.audit_result_success`          |
| MAP-02 | DBエラー     | `"DB_ERROR"`         | `R.string.audit_result_db_error`         |
| MAP-03 | 入出力エラー    | `"IO_ERROR"`         | `R.string.audit_result_io_error`         |
| MAP-04 | 形式エラー     | `"FORMAT_ERROR"`     | `R.string.audit_result_format_error`     |
| MAP-05 | 入力妥当性エラー  | `"VALIDATION_ERROR"` | `R.string.audit_result_validation_error` |
| MAP-06 | その他エラー    | `"OTHER_ERROR"`      | `R.string.audit_result_other_error`      |
| MAP-07 | 不明なエラー    | `"UNKNOWN"`          | `R.string.audit_result_unknown`          |
| MAP-08 | セキュリティエラー | `"SECURITY_ERROR"`   | `R.string.audit_result_security_error`   |
| MAP-09 | 外部エラー     | `"EXTERNAL_ERROR"`   | `R.string.audit_result_external_error`   |
| MAP-10 | ガード回避     | `"GUARD_SKIPPED"`    | `R.string.audit_result_guard_skipped`    |
| MAP-11 | 未定義文字列    | `"UNDEFINED"`        | `R.string.audit_result_unknown`          |
