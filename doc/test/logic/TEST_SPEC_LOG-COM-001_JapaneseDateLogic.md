# テスト仕様書 - LOG-COM-001 JapaneseDateLogic

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/common/JapaneseDateLogicTest.kt`

## 1. 概要
西暦と和暦の相互変換、および日付の妥当性判定を行う。
失敗時は単なる `null` や `false` ではなく、原因を特定できる詳細な「事実」を返せることを検証する。

## 2. 西暦 ↔ 和暦 変換テスト (toJapaneseDate / toLocalDate)
**目的:** 相互変換の正確性を検証する。

| ID     | テスト項目   | 条件 (入力)           | 期待結果 (出力)              |
|:-------|:--------|:------------------|:-----------------------|
| CNV-01 | 令和への変換  | 2019/05/01        | `REIWA`, 1年            |
| CNV-02 | 平成への変換  | 1989/01/08        | `HEISEI`, 1年           |
| CNV-03 | 昭和への変換  | 1926/12/25        | `SHOWA`, 1年            |
| CNV-04 | 西暦への変換  | 1926/12/24        | `AD`, 1926年            |
| CNV-05 | 正常な日付復元 | `SHOWA`, 60, 1, 1 | 1985/01/01 (LocalDate) |

## 3. バリデーションテスト (validate)
**目的:** 入力された和暦日付が物理的・歴史的に妥当かを判定し、事実を返せることを検証する。

| ID     | テスト項目     | 条件 (入力)            | 期待結果 (Enum)         |
|:-------|:----------|:-------------------|:--------------------|
| VAL-01 | 正しい日付     | `REIWA`, 5, 10, 27 | `SUCCESS`           |
| VAL-02 | 存在しない日    | `SHOWA`, 60, 2, 30 | `INVALID_DAY`       |
| VAL-03 | 月の範囲外     | `SHOWA`, 60, 13, 1 | `INVALID_MONTH`     |
| VAL-04 | 元号の範囲外(前) | `REIWA`, 1, 4, 30  | `INVALID_ERA_RANGE` |
| VAL-05 | 年が0以下     | `REIWA`, 0, 1, 1   | `INVALID_YEAR`      |
| VAL-06 | アプリ制限(過古) | `AD`, 1899, 12, 31 | `OUT_OF_APP_RANGE`  |
| VAL-07 | 閏年の判定(正)  | `HEISEI`, 4, 2, 29 | `SUCCESS`           |
| VAL-08 | 閏年の判定(誤)  | `REIWA`, 5, 2, 29  | `INVALID_DAY`       |
