# テスト仕様書 - LOG-COM-002 BirthEra

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/common/BirthEraTest.kt`
- **対象表示マッパー:**
    - `jp.mydns.fujiwara.carememo.ui.mapping.BirthEraDisplayMapper`

## 1. 元号定義テスト (Enum)
**目的:** アプリで使用する元号（西暦、昭和、平成、令和）が正しく定義されていることを検証する。

| ID     | テスト項目   | 検証内容                                               |
|:-------|:--------|:---------------------------------------------------|
| ERA-01 | 定義済みの元号 | `AD`, `SHOWA`, `HEISEI`, `REIWA` が Enum として存在すること。 |

## 2. 表示名マッピングテスト (BirthEraDisplayMapper)
**目的:** 各元号が、UI表示用の正しいリソース ID にマッピングされることを検証する。

| ID     | テスト項目    | 条件 (入力)           | 期待結果 (リソースID)                |
|:-------|:---------|:------------------|:-----------------------------|
| MAP-01 | 西暦のマッピング | `BirthEra.AD`     | `R.string.common_era_ad`     |
| MAP-02 | 昭和のマッピング | `BirthEra.SHOWA`  | `R.string.common_era_showa`  |
| MAP-03 | 平成のマッピング | `BirthEra.HEISEI` | `R.string.common_era_heisei` |
| MAP-04 | 令和のマッピング | `BirthEra.REIWA`  | `R.string.common_era_reiwa`  |
