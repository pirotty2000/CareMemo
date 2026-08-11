# テスト仕様書 - ActionTypeMapper

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/ActionTypeMapperTest.kt`

## 1. 概要
監査ログ等で使用される操作種別識別子（String）から、UI 表示用の名称（リソース ID）への変換ロジックを検証する。

## 2. マッピングテスト (toActionLabelRes)
**目的:** 各識別子文字列が、期待される日本語ラベルのリソース ID に正しくマッピングされることを検証する。

| ID     | テスト項目      | 条件 (入力文字列)             | 期待結果 (リソースID)                            |
|:-------|:-----------|:-----------------------|:-----------------------------------------|
| MAP-01 | 新規登録       | `"INSERT"`             | `R.string.audit_action_insert`           |
| MAP-02 | 更新         | `"UPDATE"`             | `R.string.audit_action_update`           |
| MAP-03 | 物理削除       | `"DELETE"`             | `R.string.audit_action_delete`           |
| MAP-04 | 利用終了（論理削除） | `"LOGICAL_DELETE"`     | `R.string.audit_action_logical_delete`   |
| MAP-05 | 復帰         | `"RESTORE"`            | `R.string.audit_action_restore`          |
| MAP-06 | 完全抹消       | `"PERMANENT_DELETE"`   | `R.string.audit_action_permanent_delete` |
| MAP-07 | 一括抹消       | `"CLEAR_ALL_ARCHIVED"` | `R.string.audit_action_clear_all`        |
| MAP-08 | 未定義        | `"UNKNOWN"`            | `0`                                      |
