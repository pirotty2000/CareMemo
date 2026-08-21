# プロジェクト TODO - Phase 6: 監査ログの強化とエクスポート機能

## 1. 目的
- 監査ログ（操作履歴）の透明性と利便性を高めるため、外部エクスポート機能を追加する。
- アプリ全域の例外処理を見直し、隠れたエラーを可視化することで保守性を向上させる。

## 2. 実装方針
### エクスポート機能
- **形式:** パスワード付き ZIP ファイル（既存のバックアップ形式に準拠）
- **内容物:** 
    - `audit_log.json`: 構造化されたデータ（再利用・自動解析用）
    - `audit_log.csv`: 表形式データ（Excel/人間による分析用）
- **配置:** 「設定」>「開発者用ツール」セクションに追加。
- **セキュリティ:** ZIP パスワードには設定済みのバックアップパスワードを使用する。

### 例外処理の再整理 (Error Logging)
- `try-catch` で握りつぶされている箇所を精査し、必要に応じて `AuditLog` に記録する。
- ログの肥大化を防ぐため、記録の要否を以下の基準で判断する。
    - **記録すべき:** データの不整合、ファイル入出力失敗、DBエラー、予期せぬロジック破綻。
    - **記録不要:** 仕様上の正常なガード（例：ナビゲーション引数の試行錯誤）、想定内のパース失敗。

## 3. 現状調査 (As-Is)
### 3.1 監査ログ記録の現状調査結果
アプリ全域の `try-catch` および既存の `auditLogRepository.log` 呼び出し箇所を調査した結果です。

- **Feature / Type / Result 列:** `☑` は各 Mapper に定義があり、UI 上で正しく日本語表示されることを示します。
- **Status:** 
    - `Logged`: 監査ログに記録されている（`safeLaunch` による自動記録含む）。
    - `Ignored`: 例外が発生しても無視されている、またはログに記録されていない。
    - `Logcat Only`: システムログ (Logcat) にのみ出力され、監査ログには残らない。
- **To-Be:**
    - `☑`: 「4.2 各ファイル修正方針」に基づき、改善・修正が必要な箇所。
- **分類 (Category):**
    - `VM`: ViewModel
    - `R`: Repository
    - `S`: Screen (UI)
    - `U`: Util
    - `L`: Logic

| ID | 分類 | ファイル名                       | Feature        | Type     | Result    | Status        | To-Be | 内容・備考                                       |
|:---|:--:|:----------------------------|:---------------|:---------|:----------|:--------------|:-----:|:--------------------------------------------|
| 1  | VM | `BaseUiStateViewModel`      | ☑ (各機能)        | ☑ ERROR  | ☑ (各エラー)  | Logged        |   ☑   | `safeLaunch`/`safeCollect` 内の未捕獲例外を自動記録。    |
| 2  | R  | `AppMaintenanceRepository`  | AppMaintenance | ☑ UPDATE | ☑ SUCCESS | Logged        |   ☑   | バックアップ・復元等の成功ログ。FeatureがMapper未定義。          |
| 3  | R  | `UserSettingsRepository`    | ☑ Settings     | ☑ UPDATE | ☑ SUCCESS | Logged        |       | 設定変更の成功ログ。                                  |
| 4  | R  | `AuditLogRepository`        | -              | -        | -         | **Ignored**   |   ☑   | ログ記録自体の失敗を `catch` で握りつぶしている。               |
| 5  | R  | `ConditionRepository`       | -              | -        | -         | **Ignored**   |   ☑   | 写真URIの削除失敗を `catch` で握りつぶしている。              |
| 6  | R  | `DeleteOrRestorePersonRepo` | -              | -        | -         | **Ignored**   |   ☑   | 物理削除時の写真ファイル削除失敗を `catch` で握りつぶしている。        |
| 7  |    | `CareMemoApplication`       | -              | -        | -         | Logcat Only   |   ☑   | 起動時の初期化エラー。                                 |
| 8  | U  | `PdfExporter`               | -              | -        | -         | Logcat/Logged |       | キャッシュ削除は Ignored。共有失敗は `safeLaunch` 経由で記録。  |
| 9  | U  | `ImageUtils`                | -              | -        | -         | **Ignored**   |       | ファイル操作、Exif解析等の失敗を広範に握りつぶしている。              |
| 10 | U  | `ZipUtils`                  | -              | -        | -         | (Logged)      |       | 例外を `IOException` で再送出。呼び出し元 ViewModel で記録。 |
| 11 | VM | `SettingsViewModel`         | ☑ Settings     | -        | -         | (Logged)      |       | インポート時のバリデーションエラー等を再送出して記録。                 |
| 12 | S  | `PersonConditionScreen`     | -              | -        | -         | **Ignored**   |   ☑   | カメラ起動失敗時にUI通知はするが、監査ログには記録されない。             |
| 13 | L  | `JapaneseDateLogic`         | -              | -        | -         | (N/A)         |       | 日付変換のガード（仕様通りの挙動として無視）。                     |
| 14 |    | `AppDatabase`               | -              | -        | -         | **Ignored**   |   ☑   | SQLCipherのパスワード不一致によるDB再作成（重要事象だが未記録）。      |
| 15 | S  | `MainScreenContent`         | -              | -        | -         | Logcat Only   |       | 更新履歴ファイルの読み込み失敗。                            |

### 3.2 課題と改善方針
- **FeatureNameMapper の不足:** `AppMaintenance` など、リポジトリ層で直接定義されている機能名がマッパーに存在せず、UIで「不明」扱いになる可能性がある。
- **ファイル操作の黙殺:** `ImageUtils` や `DeleteOrRestorePersonRepository` での写真削除失敗が記録されておらず、ストレージが予期せず肥大化する原因の特定が困難。
- **DBの再作成事象:** `AppDatabase` でのDB削除・再作成は「データの喪失」という重大事象だが、現在はログに残っていない。
- **カメラ起動失敗:** デバイス依存のトラブルとして記録しておく価値がある。

## 4. 改善方針(To-Be)

### 4.1 全体方針
#### 4.1.2 監査ログの網羅性向上 (全体方針)
- **隠れたエラーの可視化:** `Ignored` と判定された箇所（特にファイル操作やDB再作成）について、ユーザーへの通知は不要であっても監査ログには `resultType = "IO_ERROR"` や `resultType = "DB_ERROR"` として記録を残す。
- **マッパーの拡充:** リポジトリ層で定義されている `AppMaintenance` などの内部機能名を `FeatureNameMapper` に追加し、UI上で「不明」とならないようにする。
- **重大イベントの捕捉:** SQLCipherの不一致によるDB再作成など、データ喪失の可能性があるイベントは `CareMemoApplication` または `AppDatabase` レベルで確実に記録する。

#### 4.1.2 分析効率の改善
- **ハイブリッド・エクスポート:** 既存の `ZipUtils` を活用し、JSON（システム用）と CSV（分析用）を同梱したパスワード付き ZIP を生成する。
- **CSVフォーマットの標準化:** RFC 4180 に準拠し、Excel で文字化けせず開けるよう BOM 付き UTF-8 または適切なエンコーディングを採用する。

### 4.2 各ファイル修正方針

#### 4.2.1 BaseUiStateViewModel (ID: 1)
- **現状:** `safeLaunch` / `safeCollect` により、子 ViewModel で発生した未捕捉例外は自動的に監査ログへ `ERROR` として記録される仕組みが整っている。マッパーも `☑` であり、現状でも高い網羅性がある。
- **検討結果:** 
    - 基本的に修正不要だが、`featureName` の取得失敗時のガード（"Unknown" フォールバック）が実装ミスを隠蔽している。
- **改善:** 
    - `featureName` が未定義のまま `safeLaunch` が呼ばれた場合、監査ログの `resultType` に `GUARD_SKIPPED` を記録するようにし、分析時に以下の判別を可能にする。
        - `featureName: "Unknown"`, `resultType: "ERROR"` ➔ **本物のエラー**（機能名の取得は成功したが、記録後に問題が発生）
        - `featureName: "Unknown"`, `resultType: "GUARD_SKIPPED"` ➔ **開発者の実装ミス**（ViewModel での機能名定義忘れ）

#### 4.2.2 AppMaintenanceRepository (ID: 2)
- **現状:** バックアップ、復元、全消去などの主要操作について `auditLogRepository.log` が正しく呼ばれており、監査ログに記録されている。
- **検討結果:** 
    - `FeatureName` として使用されている `"AppMaintenance"` が `FeatureNameMapper.kt` に定義されていないため、UI上では機能名が日本語表示されない。
    - 操作種別 (`UPDATE`, `DELETE`) および結果 (`SUCCESS`) はマッパーに定義済みである。
- **改善:** `FeatureNameMapper.kt` に `"AppMaintenance"` の定義を追加する。

#### 4.2.3 UserSettingsRepository (ID: 3)
- **現状:** 各種設定（伏せ字、テーマ、生体認証など）の変更成功時に `auditLogRepository.log` が呼ばれている。
- **検討結果:** 
    - `FeatureName`, `ActionType`, `ResultType` のすべてが既存のマッパーに定義されており、UI 上で日本語表示が可能である。
    - `try-catch` ブロック（テーマ設定の列挙型パース失敗時のガード）も仕様に基づいたフォールバックであり、ノイズとなるため記録不要。
- **改善:** 現状維持。修正の必要なし。

#### 4.2.4 AuditLogRepository (ID: 4)
- **現状:** ログ記録自体の失敗を `catch` で握りつぶしている。
- **検討結果:** 
    - ログDBの破損や容量不足時に発生しうるが、同じDBにエラーを記録しようとすると再帰的なクラッシュを招く恐れがある。
- **改善:** 
    - `catch` 時に詳細な `Log.e` を出力するように修正。
    - **緊急回避策の実装:** DBを介さない `SystemEmergencyLogger`（ファイル出力型）を新設。`filesDir/audit_emergency.log` に追記保存する。
    - **エクスポート連携:** 監査ログのエクスポート時に、このファイルが存在すれば ZIP に同梱し、DB破損時の原因究明を可能にする。
    - **消去タイミング:** 
        - 手動: 設定画面の「操作ログを消去」実行時に、DBログと同時にファイルを物理削除する。
        - 自動: 起動時のログローテーション（ID 7）において、30日以上経過した緊急ログファイルを自動削除する。

#### 4.2.5 ConditionRepository (ID: 5)
- **現状:** 写真URIの削除失敗（`contentResolver.delete`）を `catch` で無視している。
- **検討結果:** 
    - 物理ファイルの削除漏れに直結し、将来的な未割り当て写真の増大を招くため、発生を把握すべき。
- **改善:** `auditLogRepository.log` を追加し、`actionType = "DELETE"`, `resultType = "IO_ERROR"` として記録する。

#### 4.2.6 DeleteOrRestorePersonRepository (ID: 6)
- **現状:** 物理削除時の写真ファイル削除失敗を `catch` で無視している。
- **検討結果:** 
    - アーカイブの完全抹消プロセスにおける失敗であり、ストレージ管理上、重要度が高い。
- **改善:** `auditLogRepository.log` を追加し、`actionType = "PERMANENT_DELETE"`, `resultType = "IO_ERROR"` として記録する。

#### 4.2.7 CareMemoApplication (ID: 7)
- **現状:** 起動時のログローテーション処理等のエラーが `Logcat` のみに出力されている。
- **検討結果:** 
    - 起動時の問題はアプリ全体の健全性に影響する。DBが利用可能な状態であれば監査ログに残すべき。
- **改善:** 
    - `auditLogRepository.log` を使用し、`featureName = "System"`, `actionType = "INFO"`, `resultType = "IO_ERROR"` 等で記録する。
    - **追加タスク:** 起動時のローテーション処理において、古くなった「緊急ログファイル（ID 4）」の自動削除ロジックを実装する。

#### 4.2.8 PdfExporter (ID: 8)
- **現状:** キャッシュファイル（古いPDF）の削除失敗は無視されている。
- **検討結果:** 
    - 一時ファイルの清掃失敗であり、次回の起動時や別タイミングで解消される可能性が高く、重要度は低い。
- **改善:** 現状維持。

#### 4.2.9 ImageUtils (ID: 9)
- **現状:** ファイル操作、Exif解析、ビットマップ処理等の失敗を広範に `catch` で無視している。
- **検討結果:** 
    - ユーティリティレベルですべて記録するとログが肥大化する。
- **改善:** ユーティリティ内では記録せず、呼び出し元のリポジトリ（ID: 5, 6 等）側で、重要な操作（保存・削除）の成否に応じて記録する方針とする。

#### 4.2.10 ZipUtils (ID: 10)
- **現状:** 例外を `IOException` で再送出しており、呼び出し元の `SettingsViewModel` が `safeLaunch` で補足・記録している。
- **検討結果:** 
    - 基盤の例外ハンドリングが正しく機能しており、ユーザーにもエラーが通知されるため適切である。
- **改善:** 現状維持。

#### 4.2.11 SettingsViewModel (ID: 11)
- **現状:** インポート時のバリデーションやバージョンチェックのエラーを再送出して記録している。
- **検討結果:** 
    - 設定画面の機能名やエラー種別もマッパーに対応済みであり、問題ない。
- **改善:** 現状維持。

#### 4.2.12 PersonConditionScreen (ID: 12)
- **現状:** カメラの起動失敗時に UI でトースト通知はするが、監査ログには記録されない。
- **検討結果:** 
    - デバイス環境依存（権限や他アプリの干渉）のトラブルであり、サポート時の証跡として価値がある。
- **改善:** `ViewModel` を介して `auditLogRepository.log` を呼び出し、`resultType = "EXTERNAL_ERROR"` として記録する。

#### 4.2.13 JapaneseDateLogic (ID: 13)
- **現状:** 日付変換の妥当性チェックで例外を `catch` し、`null` 等を返している。
- **検討結果:** 
    - 不正入力を検知するための「正常なロジックの一部」であり、エラーとして記録すべきではない。
- **改善:** 現状維持。

#### 4.2.14 AppDatabase (ID: 14)
- **現状:** SQLCipher のパスワード不一致時に既存DBを削除して再作成する処理が未記録。
- **検討結果:** 
    - 臨床データが物理的に抹消される最も重大なイベントである。原因を特定するため必ず記録すべき。
- **改善:** `auditLogRepository.log` を追加。`featureName = "AppMaintenance"`, `actionType = "PERMANENT_DELETE"`, `details = "DB Recreated due to password mismatch"` を記録する。

#### 4.2.15 MainScreenContent (ID: 15)
- **現状:** 更新履歴（assets 内のファイル）の読み取り失敗が `Logcat` のみ。
- **検討結果:** 
    - アプリの主要機能ではなく、リソースの問題であるため、Logcat で十分である。
- **改善:** 現状維持。

### 4.3 監査ログ・マトリックス (To-Be)
明示的に `auditLogRepository.log` を呼び出す箇所の網羅表です。（`BaseUiStateViewModel` による自動エラー記録は除く）
`☑` は今回の Phase 6 で新規追加または改善される項目です。

| FeatureName             | ActionType              | 記録場所 (ファイル名)                | ResultType (主なもの)     | To-Be |
|:------------------------|:------------------------|:----------------------------|:----------------------|:-----:|
| `PersonList`            | INSERT, UPDATE, DELETE  | `PersonRepository`          | SUCCESS               |       |
| `PersonEdit`            | INSERT, UPDATE          | `PersonRepository`          | SUCCESS               |       |
| `DeleteOrRestorePerson` | LOGICAL_DELETE, RESTORE | `DeleteOrRestorePersonRepo` | SUCCESS               |       |
| `DeleteOrRestorePerson` | PERMANENT_DELETE        | `DeleteOrRestorePersonRepo` | SUCCESS, **IO_ERROR** |   ☑   |
| `PersonHealth`          | INSERT, UPDATE, DELETE  | `HealthRepository`          | SUCCESS               |       |
| `PersonCondition`       | INSERT, UPDATE, DELETE  | `ConditionRepository`       | SUCCESS, **IO_ERROR** |   ☑   |
| `PersonCondition`       | INFO (Camera Error)     | `PersonConditionScreen`     | **EXTERNAL_ERROR**    |   ☑   |
| `PersonMedication`      | INSERT, UPDATE, DELETE  | `MedicationRepository`      | SUCCESS               |       |
| `MedicalContact`        | INSERT, UPDATE, DELETE  | `EmergencyContactRepo`      | SUCCESS               |       |
| `Settings`              | UPDATE                  | `UserSettingsRepository`    | SUCCESS               |       |
| `AuditLog`              | DELETE                  | `AuditLogRepository`        | SUCCESS               |       |
| `AuditLog`              | ERROR (Rec failure)     | `AuditLogRepository`        | **IO_ERROR (File)**   |   ☑   |
| `AppMaintenance`        | UPDATE (Export/Import)  | `AppMaintenanceRepo`        | SUCCESS               |       |
| `AppMaintenance`        | DELETE (Clear)          | `AppMaintenanceRepo`        | SUCCESS               |       |
| `AppMaintenance`        | PERMANENT_DELETE        | `AppDatabase`               | **DB_ERROR**          |   ☑   |
| `System` (New)          | INFO (Init/Rotation)    | `CareMemoApplication`       | SUCCESS, **IO_ERROR** |   ☑   |


## 5. TODO リスト
### フェーズ 6-1: エクスポート機能の実装
- [x] `AuditLogDao` に全件取得クエリ (`getAllLogsRaw`) を追加
- [x] `AuditLog` から CSV 文字列への変換ロジックの実装（カンマ・改行・クォート対応）
- [x] `AppMaintenanceRepository` に監査ログエクスポート処理を実装
- [x] `SettingsViewModel` / `SettingsScreen` に UI と連携を追加

### フェーズ 6-2: 例外処理の調査・修正
- [x] `try-catch` 箇所の全件精査と記録要否の判断
- [x] 必要な箇所への `auditLogRepository.log(...)` の挿入
- [x] ログレベル（resultType）の整理と分析用ドキュメントの更新

### フェーズ 6-3: 検証
- [x] パスワード付き ZIP が正しく生成され、PC (Excel/VS Code) で開けることを確認
- [x] 意図的に発生させたエラーが監査ログに記録されることを確認
