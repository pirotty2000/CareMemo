# テスト仕様書：SettingsLogic (TEST_SPEC_SettingsLogic.md)

## 1. 概要
アプリ設定画面における、監査ログのフィルタリング、バックアップファイルの検証、およびシステム制限（ストレージ容量）の判定ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `filterAuditLogs(logs, feature, result, ascending)`: 監査ログの絞り込みと並び替え
- `extractAvailableFeatures(logs)`: ログに含まれる機能名の重複なきリスト抽出
- `isValidZipHeader(header: ByteArray)`: Zipファイルののマジックナンバー判定
- `isVersionCompatible(backupVersion: Int, currentVersion: Int)`: バックアップの互換性判定

## 3. テストケース一覧

### 3.1. 監査ログフィルタリング (`filterAuditLogs`)
| ID | 入力状況 | 期待される結果 | 備考 |
| :--- | :--- | :--- | :--- |
| ST_LG_01 | フィルタなし | 全件 | フィルタなし |
| ST_LG_02 | 機能="Settings" | Settings のログのみ | 機能フィルタ |
| ST_LG_03 | 昇順=true | 古い順に並ぶ | 並び替え |

### 3.2. ファイル検証 (`isValidZipHeader`)
| ID | 入力 (ByteArray) | 期待値 | 備考 |
| :--- | :--- | :--- | :--- |
| ST_FL_01 | [0x50, 0x4B, 0x03, 0x04] | true | 正しい Zip ヘッダー |
| ST_FL_02 | [0x00, 0x00, 0x00, 0x00] | false | 不正なヘッダー |

### 3.3. バージョン互換性 (`isVersionCompatible`)
| ID | バックアップ版 | アプリ版 | 期待値 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| ST_VR_01 | 100 | 100 | true | 同じバージョン |
| ST_VR_02 | 90 | 100 | true | 古いバックアップ（OK） |
| ST_VR_03 | 110 | 100 | false | 新しいバックアップ（NG） |

---
最終更新日: 2026/07/13
