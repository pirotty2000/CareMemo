# テスト仕様書：SettingsLogic (TEST_SPEC_SettingsLogic.md)

## 1. 概要
アプリ設定画面における、バックアップファイルの検証、およびシステム制限（ストレージ容量）の判定ロジックの正確性を検証する。

## 2. テスト対象メソッド

- `isValidZipHeader(header: ByteArray)`: Zipファイルのマジックナンバー判定
- `isVersionCompatible(backupVersion: Int, currentVersion: Int)`: バックアップの互換性判定
- `hasAvailableSpace(dir: File, requiredBytes: Long)`: 空き容量判定

## 3. テストケース一覧

### 3.1. ファイル検証 (`isValidZipHeader`)
| ID | 入力 (ByteArray) | 期待値 | 備考 |
| :--- | :--- | :--- | :--- |
| ST_FL_01 | [0x50, 0x4B, 0x03, 0x04] | true | 正しい Zip ヘッダー |
| ST_FL_02 | [0x00, 0x00, 0x00, 0x00] | false | 不正なヘッダー |

### 3.2. バージョン互換性 (`isVersionCompatible`)
| ID | バックアップ版 | アプリ版 | 期待値 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| ST_VR_01 | 100 | 100 | true | 同じバージョン |
| ST_VR_02 | 90 | 100 | true | 古いバックアップ（OK） |
| ST_VR_03 | 110 | 100 | false | 新しいバックアップ（NG） |

### 3.3. 空き容量判定 (`hasAvailableSpace`)
| ID | 入力条件 | 期待値 | 備考 |
| :--- | :--- | :--- | :--- |
| ST_SP_01 | 十分な空きがある | true | 正常 |
| ST_SP_02 | 空きが不足している | false | 容量不足 |

---
最終更新日: 2026/07/13
