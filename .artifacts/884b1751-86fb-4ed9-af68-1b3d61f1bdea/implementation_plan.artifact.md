# 監査ログの強化とエクスポート機能の実装プラン (Phase 6)

監査ログ（操作履歴）の透明性と利便性を高めるため、外部エクスポート機能（JSON/CSV同梱ZIP）の追加と、アプリ全域の例外ハンドリングの改善を行います。

## ユーザーレビューが必要な項目
- **エクスポート配置:** 「設定 > 開発者用ツール」内に「監査ログをエクスポート」を追加します。
- **セキュリティ:** ZIPパスワードは既存のバックアップパスワード（設定済みであれば）を流用します。
- **緊急ログファイル:** DB破損時に作成される `audit_emergency.log` は、エクスポート時に自動的に ZIP へ同梱されます。

## 提案される変更点

### 1. 基盤・データアクセス層
- **[MODIFY] [Dao.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/Dao.kt):** `AuditLogDao` に `getAllLogsRaw(): List<AuditLog>` を追加。
- **[MODIFY] [AuditLogRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/AuditLogRepository.kt):**
    - ログ記録失敗時の `Log.e` 強化。
    - `SystemEmergencyLogger`（ファイル出力）の実装と呼び出し。
- **[MODIFY] [AppDatabase.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/AppDatabase.kt):** パスワード不一致によるDB削除・再作成時のログ記録を追加。

### 2. ビジネスロジック・ユーティリティ
- **[NEW] `AuditLogExportLogic.kt`:**
    - `AuditLog` リストから CSV 文字列への変換ロジック（BOM付き UTF-8, クォート処理）。
    - JSON 変換ロジック。
- **[MODIFY] [AppMaintenanceRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/AppMaintenanceRepository.kt):**
    - `exportAuditLogs(uri, password)` メソッドを実装。
    - 一時ディレクトリに JSON/CSV/緊急ログを作成し `ZipUtils` で固める。
- **[MODIFY] [FeatureNameMapper.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/mapping/FeatureNameMapper.kt):** `"AppMaintenance"`, `"System"` を追加。

### 3. ViewModel・UI層
- **[MODIFY] [BaseUiStateViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/BaseUiStateViewModel.kt):** `featureName` 取得失敗時に `GUARD_SKIPPED` を記録するよう改善。
- **[MODIFY] [SettingsViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/SettingsViewModel.kt):** 監査ログエクスポートのコマンド処理を追加。
- **[MODIFY] [SettingsScreen.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/settings/SettingsScreen.kt):** 開発者セクションにエクスポートボタンを追加。

### 4. 例外記録の追加 (ID 5, 6, 7, 12)
- **[MODIFY] [ConditionRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/ConditionRepository.kt):** 写真削除失敗を記録。
- **[MODIFY] [DeleteOrRestorePersonRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/DeleteOrRestorePersonRepository.kt):** 抹消時のファイル削除失敗を記録。
- **[MODIFY] [CareMemoApplication.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/CareMemoApplication.kt):** 起動時エラーの記録と、古い緊急ログファイルの自動削除。
- **[MODIFY] [PersonConditionScreen.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionScreen.kt):** カメラ起動失敗の記録。

## 検証計画

### 自動テスト (JUnit/Instrumented Test)
- `AuditLogExportLogicTest`: CSV 変換におけるカンマ・改行のクォート処理が正しいか検証。
- `AuditLogRepositoryTest`: 記録失敗時に緊急ログファイルが作成されるか検証。

### 手動検証
1.  **エクスポート確認:**
    - 設定から「監査ログをエクスポート」を実行。
    - 保存された ZIP を PC で解凍し、Excel で CSV が文字化けせず開けるか、JSON が正しい構造かを確認。
2.  **エラー記録確認:**
    - 意図的にカメラ権限を外す等してエラーを発生させ、エクスポートしたログに `EXTERNAL_ERROR` 等が残っているか確認。
3.  **緊急ログ確認:**
    - 擬似的にDBをロックさせてログ記録を失敗させ、エクスポート ZIP 内に `audit_emergency.log` が含まれるか確認。
