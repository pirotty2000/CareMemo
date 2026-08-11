# テスト仕様書 - FeatureNameMapper

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/FeatureNameMapperTest.kt`

## 1. 概要
監査ログ等で使用される機能識別子（String）から、UI 表示用の名称（リソース ID）への変換ロジックを検証する。

## 2. マッピングテスト (toFeatureLabelRes)
**目的:** 各機能識別子文字列が、期待される日本語名称のリソース ID に正しくマッピングされることを検証する。

| ID     | テスト項目     | 条件 (入力文字列)                         | 期待結果 (リソースID)                                 |
|:-------|:----------|:-----------------------------------|:----------------------------------------------|
| MAP-01 | 利用者一覧     | `"PersonList"`                     | `R.string.audit_feature_person_list`          |
| MAP-02 | 利用者編集     | `"PersonEdit"`                     | `R.string.audit_feature_person_edit`          |
| MAP-03 | 利用終了/復帰管理 | `"DeleteOrRestorePerson"`          | `R.string.audit_feature_person_archive`       |
| MAP-04 | 詳細共通基盤    | `"PersonBase"`                     | `R.string.audit_feature_person_base`          |
| MAP-05 | 健康記録画面    | `"PersonHealth"`                   | `R.string.audit_feature_health`               |
| MAP-06 | 一括入力      | `"BatchInput"`                     | `R.string.audit_feature_batch_input`          |
| MAP-07 | 詳細：身長体重   | `"PersonDetail/HEIGHT_AND_WEIGHT"` | `R.string.audit_feature_detail_height_weight` |
| MAP-08 | 詳細：バイタル   | `"PersonDetail/BP_AND_PULSE"`      | `R.string.audit_feature_detail_vital`         |
| MAP-09 | 詳細：血糖値    | `"PersonDetail/GLUCOSE_AND_HBA1C"` | `R.string.audit_feature_detail_glucose`       |
| MAP-10 | 所見メモ画面    | `"PersonCondition"`                | `R.string.audit_feature_condition`            |
| MAP-11 | 詳細：所見     | `"PersonDetail/CONDITION"`         | `R.string.audit_feature_detail_condition`     |
| MAP-12 | 服薬管理画面    | `"PersonMedication"`               | `R.string.audit_feature_medication`           |
| MAP-13 | 詳細：服薬     | `"PersonDetail/MEDICATION"`        | `R.string.audit_feature_detail_medication`    |
| MAP-14 | アプリ設定     | `"Settings"`                       | `R.string.audit_feature_settings`             |
| MAP-15 | 詳細：基本情報   | `"PersonDetail/Base"`              | `R.string.audit_feature_detail_base`          |
| MAP-16 | 未定義       | `"UNKNOWN"`                        | `0`                                           |
