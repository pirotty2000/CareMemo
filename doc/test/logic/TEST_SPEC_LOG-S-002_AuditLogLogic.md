# テスト仕様書 - LOG-S-002 AuditLogLogic

- **対象テストコード:** `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/AuditLogLogicTest.kt`

## 1. 概要
操作ログ（監査ログ）画面における、表示データの絞り込み、並べ替え、およびフィルター選択肢の抽出ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `filterAndSortLogs(logs, feature, result, ascending)`: 監査ログの絞り込みと並び替え
- `extractAvailableFeatures(logs)`: ログに含まれる機能名の重複なきリスト抽出
- `extractAvailableResults(logs)`: ログに含まれる実行結果の重複なきリスト抽出

## 3. テストケース一覧

### 3.1. 監査ログフィルタリング (`filterAndSortLogs`)
| ID    | 入力状況                        | 期待される結果         | 備考       |
|:------|:----------------------------|:----------------|:---------|
| LG-01 | フィルタなし                      | 全件（降順がデフォルト）    | 初期表示状態   |
| LG-02 | 機能="Settings"               | Settings のログのみ  | 機能フィルタ   |
| LG-03 | 結果="SUCCESS"                | SUCCESS のログのみ   | 結果フィルタ   |
| LG-04 | 機能="Settings", 結果="SUCCESS" | 両方に合致するログのみ     | 複合フィルタ   |
| LG-05 | 昇順=true                     | IDまたは時刻の昇順（古い順） | 並び替え     |
| LG-06 | 該当なしの条件                     | 空リスト            | フィルタ結果ゼロ |

### 3.2. 選択肢抽出 (`extractAvailableFeatures` / `extractAvailableResults`)
| ID    | 入力          | 期待値              | 備考    |
|:------|:------------|:-----------------|:------|
| EX-01 | 複数機能が混在するログ | 重複なし・ソート済みの機能リスト | 機能選択肢 |
| EX-02 | 複数結果が混在するログ | 重複なし・ソート済みの結果リスト | 結果選択肢 |

---
最終更新日: 2026/07/13
