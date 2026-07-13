# テスト仕様書：PersonListLogic (TEST_SPEC_PersonListLogic.md)

## 1. 概要
利用者一覧画面における、五十音インデックスの判定、リストのフィルタリング、および UI 状態への変換ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `getSection(furigana: String)`: ふりがなから所属する五十音行（あ、か、さ...）を判定
- `filterPersons(allPersons: List<Person>, section: String, matchedIds: Set<Int>?)`: 条件に基づく利用者の絞り込み
- `createPersonUiState(person: Person, isMasking: Boolean, summary: PersonCategorySummary)`: 表示用データの構築

## 3. テストケース一覧

### 3.1. 五十音判定 (`getSection`)
| ID | 入力 (ふりがな) | 期待値 (行) | 備考 |
| :--- | :--- | :--- | :--- |
| PL_SC_01 | "あべ" | "あ" | あ行 |
| PL_SC_02 | "がもう" | "か" | 濁音（か行） |
| PL_SC_03 | "だて" | "た" | 濁音（た行） |
| PL_SC_04 | "っだ" | "た" | 促音開始（た行） |
| PL_SC_05 | "123" | "他" | 記号・数値 |
| PL_SC_06 | "" | "他" | 空文字 |

### 3.2. リストフィルタリング (`filterPersons`)
| ID | セクション選択 | 検索ヒットID | 期待される結果 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| PL_FL_01 | "全" | null | 全件 | フィルタなし |
| PL_FL_02 | "か" | null | 「か行」の利用者のみ | 五十音フィルタ |
| PL_FL_03 | "全" | [1, 2] | ID が 1, 2 の利用者のみ | 検索フィルタ優先 |
| PL_FL_04 | "あ" | [1] | ID が 1 の利用者のみ | 重複条件 |

### 3.3. UI 状態変換 (`createPersonUiState`)
| ID | 入力 (Person) | 伏せ字 | 期待値 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| PL_UI_01 | 1950年生まれ | false | 年齢が正確であること | 年齢計算 |
| PL_UI_02 | 山田太郎 | true | "山○\u3000太○" であること | 氏名伏せ字 |
| PL_UI_03 | ヤマダタロウ | true | "ヤ○○\u3000タ○○" であること | カナ伏せ字 |

---
最終更新日: 2026/07/13
