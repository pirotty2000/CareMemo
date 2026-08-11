# テスト仕様書 - MedicationDisplayMapper

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/MedicationDisplayMapperTest.kt`

## 1. 概要
服薬管理のステータスや時間枠（スロット）に対応する表示記号やリソース ID への変換ロジックを検証する。
※ `getStatusColor` は `@Composable` 関数（テーマに依存）のため、単体テスト（JUnit）の対象外とする。

## 2. 記号マッピングテスト (getStatusSymbol)
**目的:** 各服薬ステータス（服用・介助・未服用）が、カレンダーや履歴で表示される正しい記号にマッピングされることを検証する。

| ID     | テスト項目 | 条件 (入力 Enum)              | 期待結果 (記号) |
|:-------|:------|:--------------------------|:----------|
| SYM-01 | 服用済み  | `MedicationStatus.TAKEN`  | `"○"`     |
| SYM-02 | 服薬介助  | `MedicationStatus.ASSIST` | `"△"`     |
| SYM-03 | 未服用   | `MedicationStatus.NONE`   | `"×"`     |
| SYM-04 | データなし | `null`                    | `"－"`     |

## 3. 時間枠ラベルテスト (getTimeSlotLabelRes)
**目的:** 各時間枠（朝・昼・夕・寝る前）に対応する日本語ラベルのリソース ID が正しく返されることを検証する。

| ID     | テスト項目   | 条件 (入力)                    | 期待結果 (リソースID)                 |
|:-------|:--------|:---------------------------|:------------------------------|
| LBL-01 | 朝       | `MORNING`, `isShort=false` | `R.string.slot_morning`       |
| LBL-02 | 昼       | `LUNCH`, `isShort=false`   | `R.string.slot_lunch`         |
| LBL-03 | 夕       | `DINNER`, `isShort=false`  | `R.string.slot_dinner`        |
| LBL-04 | 寝る前     | `BEDTIME`, `isShort=false` | `R.string.slot_bedtime`       |
| LBL-05 | 寝る前(短縮) | `BEDTIME`, `isShort=true`  | `R.string.slot_bedtime_short` |
