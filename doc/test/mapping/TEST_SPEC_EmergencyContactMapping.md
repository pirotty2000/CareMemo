# テスト仕様書 - EmergencyContactMapping

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/EmergencyContactMappingTest.kt`

## 1. 概要
緊急連絡先種別に対応するアイコンのマッピング、および表示用の電話番号整形ロジックを検証する。
※ `getLabel` は Composable 関数のため、単体テスト（JUnit）の対象外とする。

## 2. アイコンマッピングテスト (getIcon)
**目的:** 各種別識別子に対して、期待されるアイコン（ImageVector）が正しく返されることを検証する。

| ID     | テスト項目   | 条件 (入力文字列)          | 期待結果 (Icon)                           |
|:-------|:--------|:--------------------|:--------------------------------------|
| ICO-01 | 医師      | `"DOCTOR"`          | `Icons.Rounded.LocalHospital`         |
| ICO-02 | 訪問看護    | `"NURSING_STATION"` | `Icons.Rounded.MedicalServices`       |
| ICO-03 | 支援センター  | `"SUPPORT_CENTER"`  | `Icons.Rounded.AccountBalance`        |
| ICO-04 | ケアマネ/CW | `"CASE_WORKER"`     | `Icons.Rounded.AssignmentInd`         |
| ICO-05 | 家族      | `"FAMILY"`          | `Icons.Rounded.FamilyRestroom`        |
| ICO-06 | その他     | `"OTHER"`           | `Icons.Rounded.ContactPage`           |
| ICO-07 | 未定義     | `"UNKNOWN"`         | `Icons.Rounded.ContactPage` (Default) |

## 3. 電話番号整形テスト (formatPhoneNumber)
**目的:** 保存された数字のみの文字列から、表示に適したハイフン付き形式に正しく変換されることを検証する。

| ID     | テスト項目       | 条件 (入力)           | 期待結果 (出力)         | 備考           |
|:-------|:------------|:------------------|:------------------|:-------------|
| FMT-01 | 携帯電話 (11桁)  | `"09012345678"`   | `"090-1234-5678"` | 3-4-4 形式     |
| FMT-02 | 東京・市外局番2桁   | `"0312345678"`    | `"03-1234-5678"`  | 2-4-4 形式     |
| FMT-03 | 大阪・市外局番2桁   | `"0612345678"`    | `"06-1234-5678"`  | 2-4-4 形式     |
| FMT-04 | 一般市外局番 (3桁) | `"0481234567"`    | `"048-123-4567"`  | 3-3-4 形式     |
| FMT-05 | 記号混入の正規化    | `"090-1234-5678"` | `"090-1234-5678"` | 数字以外を除去して再整形 |
| FMT-06 | 特殊桁数（整形なし）  | `"12345"`         | `"12345"`         | そのまま返す       |
| FMT-07 | 空文字・null    | `""` / `null`     | `null`            |              |
