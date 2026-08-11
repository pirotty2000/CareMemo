# テスト仕様書 - SCR-S-001 SettingsScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/settings/SettingsScreenTest.kt`

## 1. 概要
アプリ設定画面（SettingsScreen）における UI 描画、各種設定（伏せ字、生体認証、テーマ等）の操作連動、メンテナンス操作（エクスポート・インポート等）の実行制御、および ViewModel から発行されるイベントに基づく遷移・ダイアログ制御を検証する。
ビジネスロジックや統計データの計算は ViewModel の単体テストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `SettingsUiState` の各状態に基づき、設定項目や現在の設定値が正しく描画されることを検証する。

| ID     | テスト項目   | 条件 (UiState)                    | 期待結果                                   |
|:-------|:--------|:--------------------------------|:---------------------------------------|
| DSP-01 | 基本レイアウト | 初期表示                            | 「設定・管理」のタイトルと戻るボタンが表示されていること           |
| DSP-02 | 設定値の反映  | 伏せ字=true, テーマ=SYSTEM            | 各設定項目のラベルやスイッチ、選択状態が現在の状態を反映していること     |
| DSP-03 | 統計情報の表示 | ログ件数, 終了利用者数                    | 監査ログ数や利用終了者数がラベル内に正しく表示されていること         |
| DSP-04 | 開発者モード  | `isDeveloperModeEnabled = true` | 隠しメニュー（監査ログ、整合性チェック等）が表示されること          |
| DSP-05 | 処理中表示   | `isProcessing = true`           | エクスポート等の実行中にプログレスバーまたは専用のロード画面が表示されること |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの入力やタップ操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目     | 操作             | 期待結果 (呼び出される Intent)                                                  |
|:-------|:----------|:---------------|:----------------------------------------------------------------------|
| ACT-01 | 伏せ字設定変更   | スイッチをタップ       | `userSettingsRepository` または ViewModel の更新メソッドが呼ばれること                 |
| ACT-02 | 開発者モード有効化 | バージョン情報を連続タップ  | `handleVersionClick` が呼ばれ、規定回数で `isDeveloperModeEnabled` が true になること |
| ACT-03 | エクスポート開始  | 「バックアップ実行」をタップ | `exportLauncher`（システムピッカー）が起動すること                                     |
| ACT-04 | 整合性チェック実行 | 「整合性チェック」をタップ  | `checkIntegrity()` が呼ばれ、結果が表示されること                                    |

## 4. ナビゲーション・副作用検証 (Navigation)
**目的:** ViewModel から発行された `SettingsViewEvent` や `UiEvent` を受け、実際に正しい遷移やダイアログ表示が行われることを検証する。

| ID     | テスト項目     | 発行されるイベント                     | 期待結果 (UI の挙動)                           |
|:-------|:----------|:------------------------------|:----------------------------------------|
| NAV-01 | 監査ログ遷移    | `NavigateToAuditLog`          | 監査ログ画面へ遷移すること                           |
| NAV-02 | アーカイブ管理遷移 | `NavigateToArchiveManagement` | 利用終了者の復帰・抹消画面へ遷移すること                    |
| NAV-03 | 画面終了（戻る）  | `NavigateBack`                | `navController.popBackStack()` が実行されること |
| EVT-01 | パスワード要求   | `RequestImportPassword`       | インポート用のパスワード入力ダイアログが表示されること             |

## 5. テスト用タグ (testTag)
- `SettingsScreen_BackButton`: 戻るボタン
- `Settings_MaskingRow`: 氏名伏せ字設定行
- `Settings_AuditLogButton`: 監査ログ遷移ボタン
- `Settings_BackupButton`: バックアップ実行ボタン
- `Settings_IntegrityCheckButton`: 整合性チェックボタン
- `Settings_VersionClickable`: バージョン情報タップ領域
- `Settings_Loading`: 処理中インジケータ
