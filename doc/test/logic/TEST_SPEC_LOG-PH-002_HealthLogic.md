# テスト仕様書 - LOG-PH-002 HealthLogic

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/common/HealthLogicTest.kt`
- **関連するロジック仕様書:**
    - [LOG-PH-001 PersonHealthLogic (画面保存フロー)](TEST_SPEC_LOG-PH-001_PersonHealthLogic.md)

## 1. BMI判定テスト (evaluateBMI)
**目的:** BMI値に基づき、正しい肥満度区分とアラートレベルが判定されることを検証する。

| ID     | テスト項目  | 条件 (入力: BMI) | 期待結果 (Status) | 期待結果 (AlertLevel) |
|:-------|:-------|:-------------|:--------------|:------------------|
| BMI-01 | 低体重    | 18.4         | `UNDERWEIGHT` | `INFO`            |
| BMI-02 | 普通体重   | 22.0         | `NORMAL`      | `NORMAL`          |
| BMI-03 | 肥満(1度) | 25.0         | `OBESITY_1`   | `WARNING`         |
| BMI-04 | 肥満(4度) | 40.0         | `OBESITY_4`   | `ALERT`           |

## 2. バイタル判定テスト (evaluateVital)
**目的:** 各バイタル項目に対し、基準値に基づき正しい事実とアラートレベルが判定されることを検証する。

| ID     | テスト項目       | 条件 (入力: sys, dia, sat, pulse, temp) | 期待結果 (最悪 AlertLevel) |
|:-------|:------------|:------------------------------------|:---------------------|
| VTL-01 | 全て正常        | 120, 80, 98, 70, 36.5               | `NORMAL`             |
| VTL-02 | 高血圧（収縮期）    | 140, 80, 98, 70, 36.5               | `ALERT`              |
| VTL-03 | 低酸素（SAT）    | 120, 80, 90, 70, 36.5               | `ALERT`              |
| VTL-04 | 発熱（体温）      | 120, 80, 98, 70, 37.5               | `ALERT`              |
| VTL-05 | 複合異常（注意レベル） | 95, 55, 98, 45, 35.0                | `WARNING`            |

## 3. 血糖値・HbA1c判定テスト (evaluateGlucose / evaluateHbA1c)
**目的:** 血糖値および HbA1c の値に基づき、正しい区分とアラートレベルが判定されることを検証する。

| ID     | テスト項目      | 条件 (入力) | 期待結果 (Status) | 期待結果 (AlertLevel) |
|:-------|:-----------|:--------|:--------------|:------------------|
| GLC-01 | 血糖値：正常     | 90      | `NORMAL`      | `NORMAL`          |
| GLC-02 | 血糖値：注意     | 100     | `WARNING`     | `WARNING`         |
| GLC-03 | 血糖値：高血糖    | 126     | `HIGH`        | `ALERT`           |
| GLC-04 | 血糖値：低血糖    | 69      | `LOW`         | `ALERT`           |
| HBA-01 | HbA1c：正常   | 5.5     | `NORMAL`      | `NORMAL`          |
| HBA-02 | HbA1c：注意   | 6.0     | `WARNING`     | `WARNING`         |
| HBA-03 | HbA1c：糖尿病型 | 6.5     | `DIABETES`    | `ALERT`           |

## 4. 計算ロジックテスト (calculateBMI)
**目的:** 身長と体重から BMI が正しく計算されること、および異常値（0除算等）が適切に処理されることを検証する。

| ID     | テスト項目   | 条件 (入力: height, weight) | 期待結果 (戻り値) |
|:-------|:--------|:------------------------|:----------:|
| CAL-01 | 正常な計算   | 170.0, 60.0             |  20.76...  |
| CAL-02 | 身長が0    | 0.0, 60.0               |    0.0     |
| CAL-03 | 身長がnull | null, 60.0              |    0.0     |

## 5. 入力バリデーションテスト (validateInput)
**目的:** UIからの文字列入力が、形式・範囲ともに正しいかを「事実」として判定できることを検証する。

| ID     | テスト項目   | 検証内容                          | 期待結果 (Enum)      |
|:-------|:--------|:------------------------------|:-----------------|
| VLD-01 | 正しい入力   | 範囲内の適切な数値入力                   | `SUCCESS`        |
| VLD-02 | 未入力     | 空文字                           | `EMPTY`          |
| VLD-03 | 形式不正    | 数値以外の文字、または小数点位置異常            | `INVALID_FORMAT` |
| VLD-04 | 範囲外（過大） | `AppThresholds.MAX_...` を超える値 | `OUT_OF_RANGE`   |
| VLD-05 | 範囲外（過小） | `AppThresholds.MIN_...` を下回る値 | `OUT_OF_RANGE`   |
