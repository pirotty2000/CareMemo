# テスト仕様書：BatchInputLogic (TEST_SPEC_BatchInputLogic.md)

## 1. 概要
`BatchInputViewModel` から抽出された、健康記録の一括入力に関するドメインロジックの正確性を検証する。
複数のカテゴリ（身長体重、バイタル、血糖値）を横断する保存判定や、Entity 変換ロジックを対象とする。

## 2. テスト対象メソッド

- `isValid(state: BatchInputUiState)`: いずれかのカテゴリが保存可能な入力を持っているか判定
- `createEntities(personId: Int, time: Instant, state: BatchInputUiState)`: UI状態から保存対象となる Entity のリストを生成
- `getEffectiveCategories(state: BatchInputUiState)`: 入力がある（保存対象となる）カテゴリのリストを取得

## 3. テストケース一覧

### 3.1. バリデーション (`isValid`)
| ID | 入力状況 | 期待値 | 備考 |
| :--- | :--- | :--- | :--- |
| BI_VL_01 | 全項目が空 | false | 保存対象なし |
| BI_VL_02 | 身長のみ入力（体重空） | false | 体重必須ルールに抵触 |
| BI_VL_03 | 体重のみ入力 | true | 身長体重カテゴリが有効 |
| BI_VL_04 | 血圧(上)のみ入力 | true | バイタルカテゴリが有効 |
| BI_VL_05 | 形式不正な入力（120.5.1） | false | 形式エラー時は無効 |

### 3.2. Entity 生成 (`createEntities`)
| ID | 入力状況 | 期待値 (生成されるリスト) | 備考 |
| :--- | :--- | :--- | :--- |
| BI_CP_01 | 体重と血圧を入力 | [HeightAndWeight, BpAndPulse] | 2つのEntityが生成される |
| BI_CP_02 | 血糖値のみ入力 | [GlucoseAndHbA1c] | 1つのEntityが生成される |
| BI_CP_03 | 全カテゴリ入力 | [HeightAndWeight, BpAndPulse, GlucoseAndHbA1c] | 3つのEntityが生成される |

---
最終更新日: 2026/07/13
