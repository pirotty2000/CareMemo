# テスト仕様書 - LOG-PH-003 BatchInputLogic

- **対象テストコード:** `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/BatchInputLogicTest.kt`

## 1. 概要
`BatchInputViewModel` から抽出された、健康記録の一括入力に関するドメインロジックの正確性を検証する。
複数のカテゴリ（身長体重、バイタル、血糖値）を横断する保存判定や、Entity 変換ロジックを対象とする。

## 2. UI 状態の構造 (BatchInputUiState)
**集約されたプロパティ:**
- 利用者コンテキスト: `personId`, `currentPersonName`, `personSummary`
- 健康記録入力項目: `height`, `weight`, `bpSystolic`, `bpDiastolic`, `sat`, `pulse`, `bodyTemperature`, `glucose`, `hba1c`
- 日時情報: `recordTime`, `initialRecordTime` (変更検知用基準)
- 派生・制御状態:
    - `isLoading`: 保存中フラグ
    - `isValid`: 保存可能フラグ（入力変更時に即座に更新される）
    - `isChanged`: 変更ありフラグ（入力または日時の変更時に即座に更新される）
    - `isNameMaskingEnabled`: 氏名伏せ字設定

## 3. テスト対象メソッド

- `validate(state: BatchInputUiState)`: 入力内容の妥当性を判定し、詳細な「事実」を返す
- `isValid(state: BatchInputUiState)`: 保存可能な入力状態か（簡易判定・UI用）
- `isChanged(state: BatchInputUiState)`: 初期状態（表示時または保存直後）から変更されているか
- `getEffectiveCategories(state: BatchInputUiState)`: 有効な入力がある（保存対象となる）カテゴリのリストを抽出
- `createEntities(personId: Int, time: Instant, state: BatchInputUiState)`: UI状態から保存対象となる Entity のリストを生成

## 3. テストケース一覧

### 3.1. バリデーション (`validate` / `isValid`)
| ID    | 入力状況             | validate期待値 (事実) | isValid期待値 | 備考                  |
|:------|:-----------------|:-----------------|:-----------|:--------------------|
| VL_01 | 全項目が空            | EMPTY_ALL        | false      | 保存対象なし              |
| VL_02 | 体重のみ正常入力         | SUCCESS          | true       | 一部のカテゴリが有効なら全体として成功 |
| VL_03 | 不正な値（120.x）が含まれる | INVALID_VALUE    | false      | 一つでも形式不正があれば保存不可とする |
| VL_04 | 範囲外の値（体温50度）     | INVALID_VALUE    | false      | 業務ルール違反             |

### 3.2. カテゴリ抽出 (`getEffectiveCategories`)
| ID    | 入力状況     | 期待値 (カテゴリリスト)          | 備考 |
|:------|:---------|:-----------------------|:---|
| EX_01 | 体重と血圧を入力 | [HEIGHT_WEIGHT, VITAL] |    |
| EX_02 | 血糖値のみ入力  | [GLUCOSE]              |    |
| EX_03 | 全項目空     | []                     |    |

### 3.3. Entity 生成 (`createEntities`)
| ID    | 入力状況     | 期待値 (生成されるリスト)                | 備考              |
|:------|:---------|:------------------------------|:----------------|
| CP_01 | 体重と血圧を入力 | [HeightAndWeight, BpAndPulse] |                 |
| CP_02 | 血糖値のみ入力  | [GlucoseAndHbA1c]             |                 |
| CP_03 | 不正な値を含む  | IllegalArgumentException      | バリデーション済みを前提とする |

## 4. 変更検知テスト (isChanged)
| ID     | 入力状況      | 期待値   | 備考   |
|:-------|:----------|:------|:-----|
| CHG_01 | 初期状態（表示時） | false | 変更なし |
| CHG_02 | 数値の入力あり   | true  | 変更あり |
| CHG_03 | 記録日時の変更あり | true  | 変更あり |
