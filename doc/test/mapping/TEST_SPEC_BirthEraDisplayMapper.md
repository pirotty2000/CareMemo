# テスト仕様書 - BirthEraDisplayMapper

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/BirthEraDisplayMapperTest.kt`

## 1. 表示名マッピングテスト (BirthEraDisplayMapper)
**目的:** 各元号が、UI表示用の正しいリソース ID にマッピングされることを検証する。

| ID     | テスト項目    | 条件 (入力)           | 期待結果 (リソースID)                |
|:-------|:---------|:------------------|:-----------------------------|
| MAP-01 | 西暦のマッピング | `BirthEra.AD`     | `R.string.common_era_ad`     |
| MAP-02 | 昭和のマッピング | `BirthEra.SHOWA`  | `R.string.common_era_showa`  |
| MAP-03 | 平成のマッピング | `BirthEra.HEISEI` | `R.string.common_era_heisei` |
| MAP-04 | 令和のマッピング | `BirthEra.REIWA`  | `R.string.common_era_reiwa`  |
