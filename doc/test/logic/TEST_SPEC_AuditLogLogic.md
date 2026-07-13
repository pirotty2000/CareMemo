# テスト仕様書：AuditLogLogic (TEST_SPEC_AuditLogLogic.md)

## 1. 概要
操作ログ（監査ログ）画面における、表示データの絞り込み、並べ替え、およびフィルター選択肢の抽出ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `filterAuditLogs(logs, feature, result, ascending)`: 監査ログの絞り込みと並び替え
- `extractAvailableFeatures(logs)`: ログに含まれる機能名の重複なきリスト抽出
- `extractAvailableResults(logs)`: ログに含まれる実行結果の重複なきリスト抽出

## 3. テストケース一覧

### 3.1. 監査ログフィルタリング (`filterAuditLogs`)
| ID | 入力状況 | 期待される結果 | 備考 |
| :--- | :--- | :--- | :--- |
| AL_LG_01 | フィルタなし | 全件（降順がデフォルト） | 初期表示状態 |
| AL_LG_02 | 機能="Settings" | Settings のログのみ | 機能フィルタ |
| AL_LG_03 | 結果="SUCCESS" | SUCCESS のログのみ | 結果フィルタ |
| AL_LG_04 | 機能="Settings", 結果="SUCCESS" | 両方に合致するログのみ | 複合フィルタ |
| AL_LG_05 | 昇順=true | IDまたは時刻の昇順（古い順） | 並び替え |
| AL_LG_06 | 該当なしの条件 | 空リスト | フィルタ結果ゼロ |

### 3.2. 選択肢抽出 (`extractAvailableFeatures` / `extractAvailableResults`)
| ID | 入力 | 期待値 | 備考 |
| :--- | :--- | :--- | :--- |
| AL_EX_01 | 複数機能が混在するログ | 重複なし・ソート済みの機能リスト | 機能選択肢 |
| AL_EX_02 | 複数結果が混在するログ | 重複なし・ソート済みの結果リスト | 結果選択肢 |

---
最終更新日: 2026/07/13
