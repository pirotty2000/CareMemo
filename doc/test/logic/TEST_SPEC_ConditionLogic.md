# テスト仕様書：ConditionLogic (TEST_SPEC_ConditionLogic.md)

## 1. 概要
所見メモ（カテゴリB）における、検索フィルタリングや保存時の重複判定ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `filterRecords(records: List<ConditionAtVisit>, query: String)`: 文字列による検索フィルタリング
- `isDuplicate(current: ConditionAtVisit, existing: ConditionAtVisit?)`: 保存時の重複判定

## 3. テストケース一覧

### 3.1. 検索フィルタリング (`filterRecords`)
| ID | 入力 (クエリ) | 期待されるヒット数 | 備考 |
| :--- | :--- | :--- | :--- |
| CL_FL_01 | 空文字 | 全件 | フィルタなし |
| CL_FL_02 | "発熱" | タイトルまたは本文に含む件数 | 基本検索 |
| CL_FL_03 | "ABCD" | 0 | ヒットなし |
| CL_FL_04 | "TEST" (大文字) | "test" を含む件数 | 大文字小文字を区別しない |

### 3.2. 重複判定 (`isDuplicate`)
| ID | 入力 (新規) | 入力 (既存) | 期待値 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| CL_DP_01 | ID=0 (新規) | null | false | 重複なし |
| CL_DP_02 | ID=0 (新規) | ID=10 | true | 同じ時間の別レコードが存在 |
| CL_DP_03 | ID=10 (更新) | ID=10 | false | 自分自身との一致は重複でない |
| CL_DP_04 | ID=10 (更新) | ID=20 | true | 日時変更の結果、他と重複 |

---
最終更新日: 2026/07/13
