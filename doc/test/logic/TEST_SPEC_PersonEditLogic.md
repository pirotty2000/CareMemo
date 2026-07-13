# テスト仕様書：PersonEditLogic (TEST_SPEC_PersonEditLogic.md)

## 1. 概要
`PersonEditViewModel` から抽出された、利用者編集画面固有のバリデーション、変更検知、および Entity 生成ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `isChanged(state: PersonEditUiState, initial: Person?)`: 初期状態からの変更有無を判定
- `isValid(state: PersonEditUiState)`: 保存可能な入力状態か（必須項目・日付妥当性）を判定
- `createPerson(state: PersonEditUiState, initial: Person?)`: UI状態から `Person` Entity を構築

## 3. テストケース一覧

### 3.1. 変更検知 (`isChanged`)

| ID | 入力 (UiState) | 入力 (initial Person) | 期待値 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| CH_01 | 空の状態 | null (新規) | false | 初期値のまま |
| CH_02 | 苗字に入力あり | null (新規) | true | 新規：変更あり |
| CH_03 | 変更なし | Person A | false | 既存：変更なし |
| CH_04 | 苗字のみ変更 | Person A | true | 既存：変更あり |
| CH_05 | 元号のみ変更 | Person A | true | 既存：誕生日（元号）変更 |
| CH_06 | メモのみ変更 | Person A | true | 既存：メモ変更 |

### 3.2. バリデーション (`isValid`)

| ID | 入力 (UiState) | 期待値 | 備考 |
| :--- | :--- | :--- | :--- |
| VL_01 | 全項目正しく入力 | true | 正常 |
| VL_02 | 苗字が空 | false | 必須項目欠落 |
| VL_03 | 名前が空 | false | 必須項目欠落 |
| VL_04 | 年が空 | false | 日付不完全 |
| VL_05 | 不正な日付 (2/30) | false | `JapaneseDateLogic.isValid` に準じる |

### 3.3. Entity 生成 (`createPerson`)

| ID | 入力 (UiState) | 入力 (initial) | 期待値 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| CP_01 | 正常入力 | null | ID=0 の新規 Person | trim() が適用されていること |
| CP_02 | 正常入力 | Person(ID=10) | ID=10 の更新 Person | initial の ID が維持されていること |
| CP_03 | 不正な日付 | null | null | 日付が不正なら Entity 生成不可 |

---
最終更新日: 2026/07/13
