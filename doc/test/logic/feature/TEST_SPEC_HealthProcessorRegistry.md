# テスト仕様書 - HealthProcessorRegistry

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/HealthProcessorRegistryTest.kt`

## 1. 概要
各健康記録カテゴリに対応するプロセッサを一元管理するレジストリの正確性を検証する。正しいプロセッサが取得できること、および未サポートのカテゴリに対して適切に振る舞うことを対象とする。

## 2. プロセッサ取得テスト
| ID     | テスト項目                   | 条件 (引数)                      | 期待結果                             |
|:-------|:-------------------------|:-------------------------------|:-----------------------------------|
| REG-01 | 全プロセッサ取得 (getAll)      | なし                             | 3つのプロセッサがリストで返されること              |
| REG-02 | 一括用カテゴリで取得 (getByCategory) | `HEIGHT_WEIGHT`                | `HeightWeightProcessor` が返されること   |
| REG-03 | 汎用カテゴリで取得 (getByGeneralCategory) | `Category.BP_AND_PULSE`        | `VitalProcessor` が返されること          |
| REG-04 | 未サポートカテゴリ (getByCategory) | 未定義の Enum 値                    | `IllegalArgumentException`         |
| REG-05 | 該当なし (getByGeneralCategory) | `Category.CONDITION_AT_VISIT` | `null` が返されること                  |
