# テスト仕様書 - BirthEra

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/common/BirthEraTest.kt`

## 1. 元号定義テスト (Enum)
**目的:** アプリで使用する元号（西暦、昭和、平成、令和）が正しく定義されていることを検証する。

| ID     | テスト項目   | 検証内容                                               |
|:-------|:--------|:---------------------------------------------------|
| ERA-01 | 定義済みの元号 | `AD`, `SHOWA`, `HEISEI`, `REIWA` が Enum として存在すること。 |
