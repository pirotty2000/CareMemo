# テスト仕様書 - HealthDisplayMapper

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/HealthDisplayMapperTest.kt`

## 1. 概要
健康記録の判定結果（Enum）から、表示用のリソース ID や警告レベルへの変換ロジックを検証する。
本クラスは「表示内容の決定」に専念し、色の決定は行わない設計に基づいている。

## 2. ラベルマッピングテスト (get...Label)
**目的:** 各カテゴリ（BMI、バイタル、血糖値、HbA1c）のステータス Enum が、正しい日本語ラベルのリソース ID にマッピングされることを検証する。

| ID     | テスト項目    | 検証内容                        | 期待結果 (リソースID)                |
|:-------|:---------|:----------------------------|:-----------------------------|
| LBL-01 | BMIラベル   | 各 `BmiStatus` に対するマッピング     | `R.string.bmi_label_...`     |
| LBL-02 | バイタルラベル  | 各 `VitalStatus` に対するマッピング   | `R.string.vital_label_...`   |
| LBL-03 | 血糖値ラベル   | 各 `GlucoseStatus` に対するマッピング | `R.string.glucose_label_...` |
| LBL-04 | HbA1cラベル | 各 `HbA1cStatus` に対するマッピング   | `R.string.hba1c_label_...`   |
| LBL-05 | null 考慮  | ステータスが `null` の場合           | `null` が返ること                 |

## 3. インジケーター・配色テスト (getVitalIndicatorLevel / getPdfBgColor)
**目的:** UI 上のアラートレベル判定や、PDF 出力時の背景色設定が正しく行われることを検証する。

| ID     | テスト項目      | 条件 (入力)                    | 期待結果                     |
|:-------|:-----------|:---------------------------|:-------------------------|
| IND-01 | バイタル点灯     | `isActive = true`          | `HealthAlertLevel.ALERT` |
| IND-02 | バイタル消灯     | `isActive = false`         | `HealthAlertLevel.NONE`  |
| PDF-01 | PDF背景色(警告) | `HealthAlertLevel.WARNING` | 指定のグレースケール値              |
| PDF-02 | PDF背景色(異常) | `HealthAlertLevel.ALERT`   | 指定の濃いグレースケール値            |

## 4. グラフ境界線テスト (get...GraphLimits)
**目的:** グラフ表示に使用する境界線（閾値）のリストが、`AppSpecifications` の定義通りに生成されることを検証する。
※ `context` を使用するため、単体テストではモックを使用する。

| ID     | テスト項目  | 検証内容       | 期待結果         |
|:-------|:-------|:-----------|:-------------|
| LMT-01 | BMI境界線 | 上限・下限の 2 本 | 適切なラベルと値のリスト |
| LMT-02 | 脈拍境界線  | 上限・下限の 2 本 | 適切なラベルと値のリスト |
| LMT-03 | SAT境界線 | 下限の 1 本    | 適切なラベルと値のリスト |
