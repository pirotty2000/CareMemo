# Phase 5 課題解決プラン (AppMaintenanceRepository: ISS-001 ～ ISS-003)

`AppMaintenanceRepository` に関連する、監査ログ記録漏れ、UI リソース依存、および例外設計の課題を解決します。

## User Review Required

> [!IMPORTANT]
> `DatabaseInconsistency` クラスのプロパティ型が変更（`Int` -> `InconsistencyType`）されるため、これを参照している `SettingsViewModel` および `SettingsScreen` に影響があります。修正はマッピング層を介する形で行います。

## Proposed Changes

### 1. Data Layer (data)

#### [MODIFY] [Entity.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/Entity.kt)
- `InconsistencyType` Enum を追加。
    - `UNASSIGNED_HEIGHT_WEIGHT`, `UNASSIGNED_VITAL`, `UNASSIGNED_GLUCOSE`, `UNASSIGNED_CONDITION`, `UNASSIGNED_MEDICATION`, `UNASSIGNED_CONTACT`, `UNASSIGNED_PHOTO`
- `DatabaseInconsistency` の `descriptionResId: Int` を `type: InconsistencyType` に変更。

### 2. Repository Layer (data/repository)

#### [MODIFY] [AppMaintenanceRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/AppMaintenanceRepository.kt)
- **ISS-001 (監査ログ記録)**:
    - コンストラクタ引数を `auditLogDao: AuditLogDao` から `auditLogRepository: AuditLogRepository` に変更。
    - `exportData`, `importData`, `clearAllData`, `cleanInconsistencies`, `replaceAllData` の成功時に `auditLogRepository.log(...)` を呼び出す。
- **ISS-002 (UIリソース依存排除)**:
    - `scanInconsistencies` 内で `R.string` を直接渡すのをやめ、`InconsistencyType` Enum をセットするように変更。
- **ISS-003 (例外設計の改善)**:
    - `importData` 等で投げている `IOException` のメッセージから `context.getString` を排除し、定数または識別子（"FILE_READ_ERROR" 等）に変更。
    - これにより Repository 層から `Context` (および `R.string`) への依存を最小限に抑える（ファイル操作に必要な `Context` は維持）。

#### [MODIFY] [CareMemoApplication.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/CareMemoApplication.kt)
- `AppMaintenanceRepository` の初期化時に `auditLogRepository` を渡すように修正。

### 3. UI Mapping Layer (ui/mapping)

#### [NEW] [MaintenanceDisplayMapper.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/mapping/MaintenanceDisplayMapper.kt)
- `InconsistencyType` を対応する `R.string` リソース ID に変換するマッパーを追加。

### 4. UI Layer (ui/screens/settings)

#### [MODIFY] [SettingsScreen.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/settings/SettingsScreen.kt)
- 不整合レポート表示部分で、`inc.descriptionResId` の代わりに `MaintenanceDisplayMapper` を使用してラベルを表示するように修正。

---

## Verification Plan

### Automated Tests
- `AppMaintenanceRepository` の `scanInconsistencies` が正しい `InconsistencyType` を返すことを確認するユニットテスト（既存テストがあれば更新、なければ検討）。
- 成功ログが `AuditLogRepository` に記録されることを、モックを用いて確認する。

### Manual Verification
- 設定画面から「整合性チェック」を実行し、不整合が正しく表示されること。
- 「データ消去」「インポート」「エクスポート」を実行し、監査ログ画面に「SUCCESS」のログが記録されていること。
- インポート失敗時（ファイル破損等）に、適切なエラーダイアログが表示されること。
