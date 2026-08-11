# テスト仕様書 - ThemeDisplayMapper

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/ui/mapping/ThemeDisplayMapperTest.kt`

## 1. 概要
アプリのテーマ設定（システム、ライト、ダーク、およびカスタムカラー）に対応する、UI 表示用のラベルおよび説明文のリソース ID への変換ロジックを検証する。

## 2. ラベルマッピングテスト (getLabelRes)
**目的:** 各テーマ設定 Enum に対して、期待される日本語ラベルのリソース ID が正しく返されることを検証する。

| ID     | テスト項目      | 条件 (入力 Enum)    | 期待結果 (リソースID)                        |
|:-------|:-----------|:----------------|:-------------------------------------|
| LBL-01 | システム同期     | `SYSTEM`        | `R.string.theme_system_label`        |
| LBL-02 | ライト        | `LIGHT`         | `R.string.theme_light_label`         |
| LBL-03 | ダーク        | `DARK`          | `R.string.theme_dark_label`          |
| LBL-04 | ヒーリンググリーン  | `HEALING_GREEN` | `R.string.theme_healing_green_label` |
| LBL-05 | セリーンブルー    | `SERENE_BLUE`   | `R.string.theme_serene_blue_label`   |
| LBL-06 | ウォームアプリコット | `WARM_APRICOT`  | `R.string.theme_warm_apricot_label`  |
| LBL-07 | ミッドナイトネイビー | `MIDNIGHT_NAVY` | `R.string.theme_midnight_navy_label` |
| LBL-08 | クラシックサンド   | `CLASSIC_SAND`  | `R.string.theme_classic_sand_label`  |

## 3. 説明文マッピングテスト (getDescriptionRes)
**目的:** 各テーマ設定 Enum に対して、期待される説明文のリソース ID が正しく返されることを検証する。

| ID     | テスト項目      | 条件 (入力 Enum)    | 期待結果 (リソースID)                       |
|:-------|:-----------|:----------------|:------------------------------------|
| DSC-01 | システム同期     | `SYSTEM`        | `R.string.theme_system_desc`        |
| DSC-02 | ライト        | `LIGHT`         | `R.string.theme_light_desc`         |
| DSC-03 | ダーク        | `DARK`          | `R.string.theme_dark_desc`          |
| DSC-04 | ヒーリンググリーン  | `HEALING_GREEN` | `R.string.theme_healing_green_desc` |
| DSC-05 | セリーンブルー    | `SERENE_BLUE`   | `R.string.theme_serene_blue_desc`   |
| DSC-06 | ウォームアプリコット | `WARM_APRICOT`  | `R.string.theme_warm_apricot_desc`  |
| DSC-07 | ミッドナイトネイビー | `MIDNIGHT_NAVY` | `R.string.theme_midnight_navy_desc` |
| DSC-08 | クラシックサンド   | `CLASSIC_SAND`  | `R.string.theme_classic_sand_desc`  |
