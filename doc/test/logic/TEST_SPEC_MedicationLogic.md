# テスト仕様書：MedicationLogic (TEST_SPEC_MedicationLogic.md)

## 1. 概要
服薬管理（カテゴリC）における、カレンダーの日付計算、時間枠（スロット）の管理、服用ステータスの判定ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `getCalendarDays(yearMonth: YearMonth)`: カレンダー表示用の日付リスト（前後の空セル含む）の生成
- `toTimeSlot(index: Int)`: 数値から `MedicationTimeSlot` Enum への変換
- `toStatus(code: Int?)`: 数値から `MedicationStatus` Enum への変換
- `determineSyncActions(current: List<MedicationRecord>, input: List<MedicationRecord?>)`: 保存時の追加・削除アクションの判定

## 3. テストケース一覧

### 3.1. カレンダー生成 (`getCalendarDays`)
| ID | 入力 (YearMonth) | 期待値 (リストの長さ) | 期待値 (最初の有効な日のインデックス) | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| ML_CL_01 | 2023-11 (水曜開始) | 33 | 3 | 11/1は水曜(3)なので前に3つ空セル |
| ML_CL_02 | 2024-02 (うるう年) | 30 | 4 | 2/1は木曜(4)、29日まで |
| ML_CL_03 | 2023-10 (日曜開始) | 31 | 0 | 10/1は日曜(0)なので空セルなし |

### 3.2. Enum 変換 (`toTimeSlot / toStatus`)
| ID | 入力値 | 期待値 (Enum) | 備考 |
| :--- | :--- | :--- | :--- |
| ML_EN_01 | slot: 0 | MORNING | 朝 |
| ML_EN_02 | slot: 3 | BEDTIME | 寝る前 |
| ML_EN_03 | status: 2 | TAKEN | 服用済 |
| ML_EN_04 | status: 0 | NONE | 未服用 |

### 3.3. 同期アクション判定 (`determineSyncActions`)
| ID | 入力 (現在) | 入力 (画面入力) | 期待値 (Action) | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| ML_SY_01 | [朝:服用済] | [朝:服用済, 昼:介助] | 昼を INSERT | 1件追加 |
| ML_SY_02 | [朝:服用済] | [朝:null] | 朝を DELETE | 1件削除 |
| ML_SY_03 | [朝:服用済] | [朝:未服用] | 朝を INSERT | ステータス更新 |

---
最終更新日: 2026/07/13
