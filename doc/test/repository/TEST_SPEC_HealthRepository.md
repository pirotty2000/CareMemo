# テスト仕様書 - HealthRepository

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/data/repository/HealthRepositoryTest.kt`

## 1. 概要
健康記録に関連する 3 つのデータ系統（身長体重、バイタル、血糖値・HbA1c）の永続化管理および操作ログ記録の正確性を検証する。
各カテゴリの CRUD 操作に伴う監査ログの生成ルールと、一括保存時の挙動を対象とする。

## 2. 身長・体重操作テスト (HeightAndWeight)
**目的:** 身長・体重データの保存、削除、検索が DAO を介して正しく行われ、期待される監査ログが記録されることを検証する。

| ID    | テスト項目 | 検証内容                                     | 期待結果                                               |
|:------|:------|:-----------------------------------------|:---------------------------------------------------|
| HW-01 | 新規保存  | `insertHeightAndWeight` (isUpdate=false) | DAO の `insert` が呼ばれ、`actionType="INSERT"` のログが残ること |
| HW-02 | 更新保存  | `insertHeightAndWeight` (isUpdate=true)  | DAO の `insert` が呼ばれ、`actionType="UPDATE"` のログが残ること |
| HW-03 | 物理削除  | `deleteHeightAndWeight` の実行              | DAO の `delete` が呼ばれ、`actionType="DELETE"` のログが残ること |
| HW-04 | 時刻検索  | `findHeightAndWeightAtTime` の実行          | 指定された条件で DAO の `findAtTime` が呼ばれること                |

## 3. バイタル操作テスト (BpAndPulse)
**目的:** バイタルデータの保存、削除、検索が DAO を介して正しく行われ、期待される監査ログが記録されることを検証する。

| ID    | テスト項目 | 検証内容                                | 期待結果                                               |
|:------|:------|:------------------------------------|:---------------------------------------------------|
| VT-01 | 新規保存  | `insertBpAndPulse` (isUpdate=false) | DAO の `insert` が呼ばれ、`actionType="INSERT"` のログが残ること |
| VT-02 | 更新保存  | `insertBpAndPulse` (isUpdate=true)  | DAO の `insert` が呼ばれ、`actionType="UPDATE"` のログが残ること |
| VT-03 | 物理削除  | `deleteBpAndPulse` の実行              | DAO の `delete` が呼ばれ、`actionType="DELETE"` のログが残ること |

## 4. 血糖値・HbA1c操作テスト (GlucoseAndHbA1c)
**目的:** 血糖値データの保存、削除、検索が DAO を介して正しく行われ、期待される監査ログが記録されることを検証する。

| ID    | テスト項目 | 検証内容                                     | 期待結果                                               |
|:------|:------|:-----------------------------------------|:---------------------------------------------------|
| GL-01 | 新規保存  | `insertGlucoseAndHbA1c` (isUpdate=false) | DAO の `insert` が呼ばれ、`actionType="INSERT"` のログが残ること |
| GL-02 | 更新保存  | `insertGlucoseAndHbA1c` (isUpdate=true)  | DAO の `insert` が呼ばれ、`actionType="UPDATE"` のログが残ること |
| GL-03 | 物理削除  | `deleteGlucoseAndHbA1c` の実行              | DAO の `delete` が呼ばれ、`actionType="DELETE"` のログが残ること |

## 5. 一括保存テスト (Batch)
**目的:** 複数カテゴリのデータが一括で保存されることを検証する。

| ID     | テスト項目 | 条件                   | 期待結果                                     |
|:-------|:------|:---------------------|:-----------------------------------------|
| BAT-01 | 一括保存  | 異なるカテゴリのエンティティリストを渡す | 各カテゴリの `insert` メソッドが個別に呼ばれ、それぞれのログが残ること |
