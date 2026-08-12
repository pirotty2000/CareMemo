# テスト仕様書 - HealthCategoryProcessors

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/HealthCategoryProcessorsTest.kt`

## 1. 概要
健康記録の各カテゴリ（身長体重、バイタル、血糖値）固有の処理を担当するプロセッサ群の単体テスト。各プロセッサがインターフェース `HealthCategoryProcessor` に則り、バリデーション、Entity生成、重複チェックのためのデータ検索を正しく行えることを検証する。

## 2. 身長・体重プロセッサ (HeightWeightProcessor)
| ID    | テスト項目       | 条件 (入力)               | 期待結果 (戻り値)                      |
|:------|:-------------|:------------------------|:---------------------------------|
| HW-01 | 空判定 (isEmpty) | 全て空                     | `true`                           |
| HW-02 | 空判定 (isEmpty) | 身長のみ入力                  | `false`                          |
| HW-03 | バリデーション      | 正常値 (170cm, 60kg)       | `SUCCESS`                        |
| HW-04 | バリデーション      | 範囲外 (500kg)            | `OUT_OF_RANGE`                   |
| HW-05 | Entity生成     | 正常入力                    | `HeightAndWeight` オブジェクト (値一致) |
| HW-06 | 既存検索       | リポジトリがデータを返す             | 非 null                           |

## 3. バイタルプロセッサ (VitalProcessor)
| ID    | テスト項目       | 条件 (入力)               | 期待結果 (戻り値)                      |
|:------|:-------------|:------------------------|:---------------------------------|
| VT-01 | 空判定 (isEmpty) | 全て空                     | `true`                           |
| VT-02 | 空判定 (isEmpty) | 血圧のみ入力                  | `false`                          |
| VT-03 | バリデーション      | 正常値 (120/80, 36.5度)    | `SUCCESS`                        |
| VT-04 | バリデーション      | 形式不正 ("abc")           | `INVALID_FORMAT`                 |
| VT-05 | Entity生成     | 正常入力                    | `BpAndPulse` オブジェクト (値一致)      |

## 4. 血糖値プロセッサ (GlucoseProcessor)
| ID    | テスト項目       | 条件 (入力)               | 期待結果 (戻り値)                      |
|:------|:-------------|:------------------------|:---------------------------------|
| GL-01 | 空判定 (isEmpty) | 全て空                     | `true`                           |
| GL-02 | バリデーション      | 正常値 (100mg/dL, 5.5%)   | `SUCCESS`                        |
| GL-03 | Entity生成     | 正常入力                    | `GlucoseAndHbA1c` オブジェクト (値一致) |

## 5. 共通メソッドテスト (個別編集画面用)
| ID    | テスト項目           | 検証内容                    | 期待結果                           |
|:------|:-----------------|:------------------------|:-------------------------------|
| CM-01 | validateFromMap  | 文字列マップからのバリデーション        | `HealthInputValidationResult` が一致 |
| CM-02 | createEntityFromValues | 型変換済みマップからの Entity 生成 | 指定した ID, 値を持つ Entity が生成されること |
