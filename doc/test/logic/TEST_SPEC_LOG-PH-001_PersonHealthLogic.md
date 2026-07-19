# テスト仕様書 - LOG-PH-001 PersonHealthLogic

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/PersonHealthLogicTest.kt`
- **関連するロジック仕様書:**
    - [LOG-PH-002 HealthLogic (共通計算ルール)](TEST_SPEC_LOG-PH-002_HealthLogic.md)

## 0. UI 状態の構造 (PersonHealthUiState)
**集約されたプロパティ:**
- 利用者コンテキスト: `personId`
- 画面固有の状態: `records`
- 派生・制御状態:
    - `isLoading`: ロード中フラグ

## 1. 新規判定テスト (isNew)
**目的:** エンティティが新規登録用（IDが0）か更新用かを正しく判定できることを検証する。

| ID     | テスト項目  | 条件 (入力)                   | 期待結果 (戻り値) |
|:-------|:-------|:--------------------------|:----------:|
| NEW-01 | 新規レコード | ID = 0 のレコード              |   `true`   |
| NEW-02 | 更新レコード | ID = 100 のレコード            |  `false`   |
| NEW-03 | 型不一致   | `HistoryRecord` 以外のオブジェクト |  `false`   |

## 2. 重複判定テスト (validateDuplicate)
**目的:** 既存データとの時間重複を正しく判定し、適切な「事実」を返せることを検証する。

| ID     | テスト項目    | 条件 (入力)                    | 期待結果 (Enum)      |
|:-------|:---------|:---------------------------|:-----------------|
| DUP-01 | 重複なし（新規） | 既存データなし (null)             | `SUCCESS`        |
| DUP-02 | 重複あり（新規） | 同じ時間の別レコードが既に存在            | `DUPLICATE_TIME` |
| DUP-03 | 重複なし（更新） | 同じ時間のレコードが存在するが、自分自身(ID一致) | `SUCCESS`        |
| DUP-04 | 重複あり（更新） | 同じ時間のレコードが存在し、自分以外(ID不一致)  | `DUPLICATE_TIME` |

## 3. 数値妥当性テスト (validateValues)
**目的:** 各カテゴリの数値が業務ルール（AppThresholds）の範囲内にあるか判定し、事実を返せることを検証する。

| ID     | テスト項目      | 条件 (入力)                     | 期待結果 (Enum)     |
|:-------|:-----------|:----------------------------|:----------------|
| VAL-01 | 正常な値（身長体重） | 範囲内の身長・体重                   | `SUCCESS`       |
| VAL-02 | 異常な値（身長）   | `AppThresholds` の範囲外（過大・過小） | `INVALID_VALUE` |
| VAL-03 | 正常な値（バイタル） | 範囲内の血圧・脈拍                   | `SUCCESS`       |
| VAL-04 | 正常な値（血糖値）  | 範囲内の血糖値・HbA1c               | `SUCCESS`       |
| VAL-05 | 必須入力漏れ     | 記録日時が null                  | `INVALID_TIME`  |
