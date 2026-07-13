# テスト仕様書：HealthLogic (TEST_SPEC_HealthLogic.md)

## 1. 概要
`AppThresholds` から抽出された、健康記録（A系統）に関する判定ロジック・バリデーションの正確性を検証する。
本クラスは Pure Kotlin で実装され、リソース ID や Android フレームワークに依存しない。

## 2. テスト対象項目

- **判定ロジック (Evaluators)**
    - `evaluateBMI(bmi: Double)`: BMI値に基づくステータスとアラートレベルの判定
    - `evaluateVital(...)`: 血圧、脈拍、SAT、体温に基づくアラートレベルの判定
    - `evaluateGlucose(glucose: Int?)`: 血糖値に基づく判定
    - `evaluateHbA1c(hba1c: Double?)`: HbA1cに基づく判定
- **バリデーション (Validators)**
    - `isValidHeightAndWeight(height, weight)`: 入力形式と必須チェック
    - `isValidBpAndPulse(...)`: バイタル入力の妥当性
    - `isValidGlucoseAndHbA1c(...)`: 血糖値入力の妥当性
- **計算 (Calculators)**
    - `calculateBMI(height, weight)`: BMIの算出（0除算防止含む）

## 3. テストケース一覧

### 3.1. BMI判定 (`evaluateBMI`)
| ID | 入力 (bmi) | 期待値 (Status) | 期待値 (AlertLevel) | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| HL_BMI_01 | 18.4 | UNDERWEIGHT | INFO | 低体重 |
| HL_BMI_02 | 22.0 | NORMAL | NORMAL | 普通体重 |
| HL_BMI_03 | 25.0 | OBESITY_1 | WARNING | 肥満(1度) |
| HL_BMI_04 | 40.0 | OBESITY_4 | ALERT | 肥満(4度) |

### 3.2. バイタル判定 (`evaluateVital`)
| ID | 入力 (sys, dia, sat, pulse, temp) | 期待値 (AlertLevel) | 備考 |
| :--- | :--- | :--- | :--- |
| HL_VTL_01 | 120, 80, 98, 70, 36.5 | NORMAL | 全て正常 |
| HL_VTL_02 | 140, 90, 98, 70, 36.5 | ALERT | 高血圧 |
| HL_VTL_03 | 120, 80, 90, 70, 36.5 | ALERT | 低SAT |
| HL_VTL_04 | 120, 80, 98, 70, 37.5 | ALERT | 発熱 |
| HL_VTL_05 | 95, 55, 98, 45, 35.0 | WARNING | 低血圧・徐脈・低体温（WARNINGが優先） |

---
最終更新日: 2026/07/13
