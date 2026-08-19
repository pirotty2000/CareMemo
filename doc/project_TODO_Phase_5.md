# CareMemo プロジェクト・課題管理 (Phase 5: 適合性調査)

本ドキュメントは、プロジェクト・ルールへの適合状況調査において発見された、改善すべき課題を記録します。

# 0. 課題一覧

本調査において発見された、修正・改善が必要な課題の一覧です。

| ID      | 対象コンポーネント                       | 課題内容                                                         |  優先度  | 分類        | 進捗 |
|:--------|:--------------------------------|:-------------------------------------------------------------|:-----:|:----------|:--:|
| ISS-001 | AppMaintenanceRepository        | 主要操作（インポート等）における監査ログの記録漏れ                                    | **高** | ルール違反     | ✅  |
| ISS-002 | AppMaintenanceRepository        | `DatabaseInconsistency` 生成時における UI リソース（R.string）への直接依存      |   中   | アーキテクチャ   | ✅  |
| ISS-003 | AppMaintenanceRepository        | `safeLaunch` 圏外でのチェックなしの例外スロー                                |   中   | エラーハンドリング | ✅  |
| ISS-004 | DeleteOrRestorePersonRepository | 物理削除実行時における紐付く画像ファイルの削除漏れ                                    | **高** | 仕様不備      | ✅  |
| ISS-005 | DeleteOrRestorePersonRepository | タイムスタンプ取得処理の不整合（System.currentTimeMillis の使用）                |   低   | 一貫性       | ✅  |
| ISS-006 | UserSettingsRepository          | 設定変更（生体認証、パスワード等）時における監査ログの記録漏れ                              |   中   | ルール違反     | ✅  |
| ISS-007 | UserSettingsRepository          | メモリ保持用変数（isLockBypassed）の配置場所の検討                             |   低   | 設計議論      | ⏳  |
| ISS-008 | PdfExportActionHandler          | `BaseUiStateViewModel` への直接依存（コンポーネントの ViewModel 依存禁止規約への抵触） |   中   | アーキテクチャ   | ✅  |

---

# 1. プロジェクト基盤

## 1.1. application / activity

| No. | 調査対象                | 状態 | 評価                        | 備考                                                                                  |
|:---:|:--------------------|:--:|:--------------------------|:------------------------------------------------------------------------------------|
|  1  | CareMemoApplication | ✅  | **適合 (Highly Compliant)** | サービスロケーターとして機能。リポジトリのシングルトン管理、DB早期初期化、ログ自動ローテーションを規約通りに実装。                          |
|  2  | MainActivity        | ✅  | **適合 (Highly Compliant)** | ナビゲーション（Type-safe）、セキュリティ（生体認証）、アダプティブUI（WindowSizeClass）を統括。ViewModel への手動 DI も適切。 |


## 1.2. data

| No. | 調査対象               | 状態 | 評価                        | 備考                                                                          |
|:---:|:-------------------|:--:|:--------------------------|:----------------------------------------------------------------------------|
|  1  | Entity             | ✅  | **適合 (Highly Compliant)** | Room O/R マッピングを担う不変データクラス群。UUID 主キー、論理削除フラグ、不変化のためのアノテーション適用など、規約を高度に遵守。    |
|  2  | Dao                | ✅  | **適合 (Highly Compliant)** | SQLite 操作の定義。Flow によるリアクティブな監視、論理削除/復元、一括バックアップ、整合性チェック用クエリ等を網羅。            |
|  3  | AppDatabase        | ✅  | **適合 (Highly Compliant)** | Room データベース基盤。SQLCipher によるフルディスク暗号化、シングルトンパターンの遵守、安全な初期化ロジックを実装。           |
|  4  | DatabaseKeyManager | ✅  | **適合 (Highly Compliant)** | DB暗号化鍵の安全な管理。Android Keystore と EncryptedSharedPreferences を活用し、機密情報の保護を徹底。 |
|  5  | AppSpecifications  | ✅  | **適合 (Highly Compliant)** | アプリ全体の仕様（閾値、制約、設定）の統一窓口。Facade Pattern により、各カテゴリの spec 定義を整理・カプセル化。         |
|  6  | Category           | ✅  | **適合**                    | 業務カテゴリの Enum 定義。Type-safe Navigation の目的地生成ロジック（toDestination）を内包。          |
|  7  | BackupDto          | ✅  | **適合**                    | バックアップ専用の転送オブジェクト。Entity との分離により、DB スキーマ変更に強い「交換形式」としての互換性を維持。              |
|  8  | Converters         | ✅  | **適合**                    | Room 用の型コンバータ（Instant ⇔ Long 等）。カスタム型の永続化を規約通りにサポート。                        |
|  9  | ThemeSetting       | ✅  | **適合**                    | 配色テーマ設定の Enum 定義。システム連動や独自パレットの選択肢を管理。                                      |

## 1.3. data/spec

| No. | 調査対象                           | 状態 | 評価                        | 備考                                                               |
|:---:|:-------------------------------|:--:|:--------------------------|:-----------------------------------------------------------------|
|  1  | IdSpecifications               | ✅  | **適合**                    | システム共通の新規レコード識別子（__NEW__）を定義。                                    |
|  2  | HealthSpecifications           | ✅  | **適合 (Highly Compliant)** | 血圧、脈拍、SAT、体温、血糖、HbA1c、BMI、身長、体重の全閾値とグラフ描画設定を集約。医学的根拠に基づく閾値管理を徹底。 |
|  3  | ConstraintSpecifications       | ✅  | **適合**                    | 文字数制限、写真のサイズ制約、パスワードルール等を一括管理。                                   |
|  4  | MedicationSpecifications       | ✅  | **適合**                    | 服薬時間枠（4スロット）とステータスコードを定義。                                        |
|  5  | EmergencyContactSpecifications | ✅  | **適合**                    | 緊急連絡先のバリデーション制約と種別定数を定義。                                         |
|  6  | CalendarSpecifications         | ✅  | **適合 (Highly Compliant)** | 和暦（昭和・平成・令和）の改元日、オフセット、サポート範囲を定義。日付操作の基盤となる重要仕様。                 |
|  7  | SearchSpecifications           | ✅  | **適合**                    | 五十音インデックスのグループ定義を管理。                                             |
|  8  | SettingsSpecifications         | ✅  | **適合**                    | 設定画面の選択肢（ログ保持期間等）のバリエーションを定義。                                    |
|  9  | ExportSpecifications           | ✅  | **適合**                    | PDF 帳票のレイアウト、フォント、配色（印刷適性考慮）を精密に定義。                              |


## 2. Repository

### 2.1. 適合しているRepository

| No. | 調査対象                            | 状態 | 評価                        | 備考                                                                                                  |
|:---:|:--------------------------------|:--:|:--------------------------|:----------------------------------------------------------------------------------------------------|
|  2  | AppMaintenanceRepository        | ✅  | **適合 (Highly Compliant)** | データのバックアップ、復元、全消去、整合性修復を統括。`AuditLogRepository` 連携による自動ロギング、`InconsistencyType` によるアーキテクチャ適正化が完了済み。 |
|  3  | AuditLogRepository              | ✅  | **適合 (Highly Compliant)** | 責務が明確であり、`NonCancellable` を用いた堅牢な例外抑制により業務への非干渉が徹底されている。                                            |
|  4  | ConditionRepository             | ✅  | **適合 (Compliant)**        | DB 操作と物理ファイル操作（写真）の双方が適切にカプセル化され、監査ログ記録も徹底されている。                                                    |
|  5  | DeleteOrRestorePersonRepository | ✅  | **適合 (Highly Compliant)** | 利用者のアーカイブ・抹消を管理。物理削除時の関連画像ファイル自動消去、および `Instant` への時刻取得統一により、規約遵守とリソースリーク防止を両立。                     |
|  6  | EmergencyContactRepository      | ✅  | **適合 (Compliant)**        | `IdLogic` への依存排除が完了しており、子テーブル特有の `isUpdate` フラグ運用も規約通りである。                                          |
|  7  | HealthRepository                | ✅  | **適合 (Highly Compliant)** | 3系統のデータを扱うが責務分離が適切。すべての破壊的操作において監査ログが記録されている。                                                       |
|  8  | MedicationRepository            | ✅  | **適合 (Highly Compliant)** | プロジェクトの標準的な Repository 構造を忠実に守り、詳細な監査ログ記録が行われている。                                                   |
|  9  | PersonRepository                | ✅  | **適合 (Compliant)**        | 基本情報の CRUD と重複チェックを適切に提供。サマリー集計の分離も完了済み。                                                            |
| 10  | PersonSummaryRepository         | ✅  | **適合 (Highly Compliant)** | 複数テーブルの横断的な集計に特化。`combine` を用いたリアクティブな実装は効率的で、責務分離も完璧。                                              |

### 2.2. UserSettingsRepository (一部課題あり)

| 調査対象                   | 状態 | 評価            | 備考                                                                                    |
|:-----------------------|:--:|:--------------|:--------------------------------------------------------------------------------------|
| UserSettingsRepository | ⚠️ | **適合 (修正済み)** | 設定変更時の自動ロギングを実装済み。ただし、メモリ保持変数（`isLockBypassed`）の配置場所については設計議論の余地があり、ISS-007 として継続検討中。 |

## 3. ViewModel

| No. | 調査対象                                                                         | 状態 | 評価                        | 備考                                                                                                                 |
|:---:|:-----------------------------------------------------------------------------|:--:|:--------------------------|:-------------------------------------------------------------------------------------------------------------------|
|  1  | AuditLogViewModel                                                            | ✅  | **適合 (Highly Compliant)** | `BaseUiStateViewModel` に基づく標準的な実装。`ImmutableList` の徹底、`safeCollect` による非同期購読、`AuditLogLogic` へのロジック委譲、規約通りの遷移イベント。 |
|  2  | SettingsViewModel                                                            | ✅  | **適合 (Compliant)**        | 複数リポジトリを横断する保守操作を安全に統括。`ImportValidationResult` 等を用いた Logic レイヤーとの協調も適切。ViewModel としての構造は規約通り。                     |
|  3  | BatchInputViewModel                                                          | ✅  | **適合 (Highly Compliant)** | `PersonBaseUiStateViewModel` を継承し、複数カテゴリの一括入力状態を原子的に管理。`isChanged`, `isValid` の ViewModel 側判定など、重要ルールを高度に遵守。       |
|  4  | PersonEditViewModel                                                          | ✅  | **適合 (Highly Compliant)** | `IdLogic.isNew` を用いた新規/編集の自律判定、和暦コンポーネントの正規化など、UI 境界の責務を正確に果たしている。                                                 |
|  5  | PersonListViewModel                                                          | ✅  | **適合 (Highly Compliant)** | `ImmutableList` の徹底、複数ソースの統合フィルタリングフローが `safeCollect` により堅牢に実装されている。                                               |
|  6  | PersonHealthViewModel / PersonConditionViewModel / PersonMedicationViewModel | ✅  | **適合 (Highly Compliant)** | 詳細画面の3専門 ViewModel。利用者情報の自動ロードと履歴監視を `safeCollect` で実現。変更検知ロジックの ViewModel 集約、監査ログ構築など規約が徹底されている。                  |
|  7  | EmergencyContactEditViewModel                                                | ✅  | **適合 (Compliant)**        | `updateState` ヘルパーによる `isChanged`, `isValid` のプロパティ管理に修正済み。                                                        |
|  8  | DeleteOrRestorePersonViewModel                                               | ✅  | **適合 (Highly Compliant)** | 論理削除/復元/物理削除の複数モード管理を `ImmutableSet` と組み合わせて適切に実現。一括操作の Job制御も規約通り。                                                |
|  9  | UnassignedPhotoViewModel                                                     | ✅  | **適合 (Highly Compliant)** | 未割り当て写真の検出機能を、Logic 層との協調によりクリーンに実装。Android API 依存もなくテスト容易性が高い。                                                    |
| 10  | 基盤クラス (BaseUiStateViewModel / PersonBaseUiStateViewModel)                    | ✅  | **適合 (Highly Compliant)** | `safeLaunch`, `safeCollect`, `loadingStateProxy` など、アプリ全体の堅牢性を支える重要機能が標準化されている。                                    |

## 4. Logic

### 4.1. logic/common

| No. | 調査対象              | 状態 | 評価                        | 備考                                                                            |
|:---:|:------------------|:--:|:--------------------------|:------------------------------------------------------------------------------|
|  1  | IdLogic           | ✅  | **適合 (Highly Compliant)** | 新規ID判定の Single Source of Truth として機能。Android API への依存も皆無。                     |
|  2  | BirthEra          | ✅  | **適合**                    | 介護現場に必要な元号（昭和・平成・令和）を網羅した Pure Kotlin の Enum 定義。                              |
|  3  | PhoneLogic        | ✅  | **適合 (Highly Compliant)** | 複雑な日本の電話番号体系（0120/03等）に対応した整形ロジックを UI から完全に分離して保持。                            |
|  4  | HealthLogic       | ✅  | **適合 (Highly Compliant)** | BMI計算、異常値判定、入力バリデーションを集約。`AppSpecifications` の閾値に基づき厳密に実装されている。               |
|  5  | PersonLogic       | ✅  | **適合**                    | インポート時のデータクレンジング（生年月日正規化や重複回避）を担当。副作用のない純粋なデータ変換を徹底。                          |
|  6  | ConditionLogic    | ✅  | **適合**                    | 所見メモの検索フィルタリングや、ID比較を伴う厳密な重複チェック（自己更新の許容）を実装。                                 |
|  7  | MedicationLogic   | ✅  | **適合 (Highly Compliant)** | カレンダーの日付グリッド生成や、DBとの差分に基づく同期アクション (`SyncAction`) の判定など、複雑な業務ルールを純粋なロジックとして完結。 |
|  8  | JapaneseDateLogic | ✅  | **適合 (Highly Compliant)** | 改元日を考慮した和暦西暦変換と厳密な日付妥当性判定を提供。1900年〜のサポート範囲制限もドメインルールとして適用。                    |

### 4.2. logic/feature

| No. | 調査対象                       | 状態 | 評価     | 備考                                                                     |
|:---:|:---------------------------|:--:|:-------|:-----------------------------------------------------------------------|
|  1  | AuditLogLogic              | ✅  | **適合** | ログのフィルタリングとソートを担当。標準Listを返し、不変化は ViewModel に委譲する設計を遵守。                 |
|  2  | SecurityLogic              | ✅  | **適合** | 各種フラグから最終的なセキュリティステータス（LOCKED/UNLOCKED等）を決定する純粋な判定ロジック。                |
|  3  | SettingsLogic              | ✅  | **適合** | Zip形式検証やバージョン互換性チェックを担当。副作用を排除し「事実の判定」に専念。                             |
|  4  | BatchInputLogic            | ✅  | **適合** | 複数カテゴリを跨ぐバリデーションと Entity 生成を統括。`HealthCategoryProcessor` を介した拡張性の高い設計。 |
|  5  | PersonEditLogic            | ✅  | **適合** | 変更検知、バリデーション、Entity構築を集約。UI 状態とドメインモデルの境界を適切に管理。                       |
|  6  | PersonListLogic            | ✅  | **適合** | 五十音判定、統合フィルタリング、UI用State（年齢・和暦誕生日等）への変換を担当。                            |
|  7  | PersonHealthLogic          | ✅  | **適合** | 健康記録のバリデーションと Entity 構築。各種 Processor と Registry を活用したクリーンな構造。          |
|  8  | PersonConditionLogic       | ✅  | **適合** | 所見メモのバリデーションとレコード構築。未割り当て写真情報の定義（UnassignedPhotoInfo）も保持。              |
|  9  | EmergencyContactLogic      | ✅  | **適合** | 緊急連絡先のバリデーションとデータクレンジング（ハイフン除去等）を徹底。                                   |
| 10  | PersonMedicationLogic      | ✅  | **適合** | 服薬履歴の日付別グルーピングを担当。UI（カレンダー）の描画効率を考慮したデータ構造を提供。                         |
| 11  | ConditionMaintenanceLogic  | ✅  | **適合** | DBと物理ファイルの不整合を検出・分類する高度なメンテナンスロジックを Pure Kotlin で実現。                   |
| 12  | DeleteOrRestorePersonLogic | ✅  | **適合** | 一括操作時の選択状態管理とバリデーションを担当。                                               |
| 13  | HeightWeightProcessor      | ✅  | **適合** | 身長・体重カテゴリ特有のバリデーションと Entity 生成をカプセル化。                                  |
| 14  | VitalProcessor             | ✅  | **適合** | バイタルカテゴリ特有のロジックを担当。`HealthLogic` と適切に連携。                               |
| 15  | GlucoseProcessor           | ✅  | **適合** | 血糖値カテゴリ特有のロジックを担当。                                                     |
| 16  | HealthCategoryProcessor    | ✅  | **適合** | 健康記録の各カテゴリ処理を抽象化するインターフェース定義。                                          |
| 17  | HealthProcessorRegistry    | ✅  | **適合** | プロセッサの登録と取得を管理。Open-Closed Principle に基づく設計。                           |
| 18  | PersonDetailLogic          | ✅  | **適合** | 現状は UiState と ViewEvent の定義のみだが、責務の所在は明確。                              |

## 5. UI

### 5.1. components

#### 5.1.1. ui/components/base

| No. | 調査対象                    | 状態 | 評価                        | 備考                                                                       |
|:---:|:------------------------|:--:|:--------------------------|:-------------------------------------------------------------------------|
|  1  | AppDialog               | ✅  | **適合**                    | ダイアログの基本コンテナ。セマンティクスに応じたボタン色分けや、スクロール可能なコンテンツ領域を提供。                      |
|  2  | SearchBox               | ✅  | **適合**                    | アプリ共通の検索バー。`AppTextField` をラップし、デザインと挙動の一貫性を保証。                          |
|  3  | EmptyState              | ✅  | **適合**                    | データ不在時のプレースホルダー。中央配置とアイコン表示をカプセル化。                                       |
|  4  | AppTextField            | ✅  | **適合 (Highly Compliant)** | `TextFieldValue` による精密なカーソル制御、入力フィルタ、自動スクロール（BringIntoView）を内蔵した高機能入力基盤。 |
|  5  | AppInfoDialog           | ✅  | **適合**                    | 通知専用のシンプルなダイアログ。`AppDialog` の適切なラッパーとして機能。                               |
|  6  | LoadingScreen           | ✅  | **適合**                    | 待機画面の標準化。テストタグの付与により自動テスト容易性も確保。                                         |
|  7  | AppTopAppBarColors      | ✅  | **適合**                    | `TopAppBar` の配色を一元管理。テーマに連動したブランドカラーを確実に適用。                              |
|  8  | AppCompactTextField     | ✅  | **適合**                    | 省スペースな数値入力等に特化。内部で `AppTextField` と同等の制御ロジックを維持。                         |
|  9  | AppDeleteConfirmDialog  | ✅  | **適合**                    | 破壊的操作の確認用。`AppDialogActionType.DELETE` による警告色の適用を標準化。                    |
| 10  | VerticalScrollIndicator | ✅  | **適合**                    | `ScrollState`/`LazyListState` 双方に対応した高度な位置表示インジケーターを Pure Compose で実装。   |

#### 5.1.2. ui/components/common

| No. | 調査対象                            | 状態 | 評価                        | 備考                                                                                |
|:---:|:--------------------------------|:--:|:--------------------------|:----------------------------------------------------------------------------------|
|  1  | HistoryComponents               | ✅  | **適合**                    | 時系列リストの共通基盤。`stickyHeader` による日付区切りや `SwipeToDismissBox` による削除操作をカプセル化。           |
|  2  | PdfSettingsDialog               | ✅  | **適合**                    | PDF出力設定用の共通ダイアログ。`AppDialog` をベースとし、期間選択やパスワード設定の UI を提供。                         |
|  3  | PersonHeaderTitle               | ✅  | **適合**                    | 利用者の基本情報を TopAppBar 等に表示するためのタイトル部品。伏せ字対応も内包。                                     |
|  4  | CategorySelectorBar             | ✅  | **適合**                    | 詳細画面のカテゴリ切り替えナビゲーション。データ存在有無のバッジ表示（ボーダー強調）に対応。                                    |
|  5  | DateTimeInputFields             | ✅  | **適合**                    | 年月日時分の分割入力フィールド。Stateful 版と Stateless 版の両方を提供し、柔軟な利用が可能。                          |
|  6  | PdfExportActionHandler          | ✅  | **適合 (Highly Compliant)** | 完全に Stateless な設計へリファクタリング済み。ViewModel への依存を排除し、実行ロジックを Screen 層へ委譲することで規約を高度に遵守。 |
|  7  | HistoryPreviewParameterProvider | ✅  | **適合**                    | プレビュー用のモックデータ提供クラス。開発効率の向上に寄与。                                                    |

#### 5.1.3. ui/components/condition

| No. | 調査対象                      | 状態 | 評価                        | 備考                                                                           |
|:---:|:--------------------------|:--:|:--------------------------|:-----------------------------------------------------------------------------|
|  1  | PersonConditionComponents | ✅  | **適合 (Highly Compliant)** | 履歴リスト、詳細/編集パネル、写真管理、未割り当て写真救済の全パーツを網羅。規約（Stateless, ImmutableList使用等）を完璧に遵守。 |

#### 5.1.4. ui/components/health

| No. | 調査対象                   | 状態 | 評価                        | 備考                                                   |
|:---:|:-----------------------|:--:|:--------------------------|:-----------------------------------------------------|
|  1  | LineChart              | ✅  | **適合**                    | Canvas による低レイヤー描画エンジン。ピンチズーム・ツールチップ等、高度なインタラクションを実現。 |
|  2  | HealthGraphView        | ✅  | **適合**                    | カテゴリ別の複数グラフ表示コンテナ。X軸の同期や目安情報の表示機能を統合。                |
|  3  | HealthChartHelper      | ✅  | **適合**                    | 判定基準に基づくグラフ設定・配色生成ユーティリティ。描画と業務ロジックの橋渡しを担当。          |
|  4  | PersonHealthComponents | ✅  | **適合 (Highly Compliant)** | 身長体重・バイタル・血糖の各詳細表示および編集フォーム。カテゴリに応じた動的なUI切り替えを適切に実装。 |

#### 5.1.5. ui/components/main

| No. | 調査対象                | 状態 | 評価                        | 備考                                                                  |
|:---:|:--------------------|:--:|:--------------------------|:--------------------------------------------------------------------|
|  1  | KanaIndexBar        | ✅  | **適合**                    | 水平スクロール式の五十音インデックス。選択状態の自動追従スクロールにも対応。                              |
|  2  | CategoryBadges      | ✅  | **適合**                    | 各カテゴリの記録有無を示す漢字一文字バッジ。アクティブ時の色分けとアクセシビリティ対応が良好。                     |
|  3  | MainComponents      | ✅  | **適合 (Highly Compliant)** | 利用者カード（UserListItem）および機能選択シート。誕生日通知などの条件付き装飾が適切に Stateless 化されている。 |
|  4  | QuickActionMenu     | ✅  | **適合**                    | バッジタップ時のコンテキストメニュー。緊急連絡先等へのクイックアクセスを提供。                             |
|  5  | BirthdayInputFields | ✅  | **適合**                    | 和暦・西暦対応の生年月日入力。`BirthdayInputState` による状態管理とバリデーション。                |

#### 5.1.6. ui/components/medication

| No. | 調査対象                       | 状態 | 評価                        | 備考                                                               |
|:---:|:---------------------------|:--:|:--------------------------|:-----------------------------------------------------------------|
|  1  | PersonMedicationComponents | ✅  | **適合 (Highly Compliant)** | カレンダーグリッド、月間履歴テーブル、4スロット対応の入力ダイアログを網羅。複雑な同期更新も Stateless な構成で維持。 |

### 5.2. mapping

| No. | 調査対象                    | 状態 | 評価     | 備考                                                 |
|:---:|:------------------------|:--:|:-------|:---------------------------------------------------|
|  1  | ActionTypeMapper        | ✅  | **適合** | 監査ログの操作種別（String）を和名リソースIDに変換する拡張プロパティ。            |
|  2  | ResultTypeMapper        | ✅  | **適合** | 監査ログの実行結果（String）を和名リソースIDに変換する拡張プロパティ。            |
|  3  | FeatureNameMapper       | ✅  | **適合** | 内部機能名（featureName）を表示用リソースIDにマッピング。                |
|  4  | ThemeDisplayMapper      | ✅  | **適合** | テーマ設定に応じたラベルおよび説明文の解決を担当。                          |
|  5  | HealthDisplayMapper     | ✅  | **適合** | 健康記録の判定結果（Enum）をリソースIDや警告レベルに翻訳。グラフ境界線やPDF配色管理も内包。 |
|  6  | BirthEraDisplayMapper   | ✅  | **適合** | 元号（BirthEra）の表示名称解決を担当。                            |
|  7  | EmergencyContactMapping | ✅  | **適合** | 緊急連絡先種別の名称・アイコン解決、および電話番号整形ロジックを提供。                |
|  8  | MedicationDisplayMapper | ✅  | **適合** | 服薬ステータスに対応する記号、カラー、ラベルを統合管理。                       |

### 5.3. navigation

| No. | 調査対象         | 状態 | 評価                        | 備考                                                                         |
|:---:|:-------------|:--:|:--------------------------|:---------------------------------------------------------------------------|
|  1  | Destinations | ✅  | **適合 (Highly Compliant)** | Type-safe Navigation に基づく目的地定義。Kotlin Serialization を活用し、引数および戻り値の型安全性を保証。 |

### 5.4. screens

#### 5.4.1. ui/screens/condition

| No. | 調査対象                                    | 状態 | 評価     | 備考                                                       |
|:---:|:----------------------------------------|:--:|:-------|:---------------------------------------------------------|
|  1  | PersonConditionScreen                   | ✅  | **適合** | 4ファイル構成の最上位。マルチレイアウト制御、イベントハンドリング、OS連携（カメラ・ギャラリー）を適切に統括。 |
|  2  | PersonConditionScreenPhone              | ✅  | **適合** | スマホ向けレイアウト。Scaffold 統合とシングルペイン制御を規約通りに実装。                |
|  3  | PersonConditionScreenTablet             | ✅  | **適合** | タブレット向けレイアウト。2ペイン構成による大画面最適化を実現。                         |
|  4  | PersonConditionScreenContent            | ✅  | **適合** | 共通レイアウト基盤。1カラム/2カラムの動的切り替えと、各パーツの統合を担当。                  |
|  5  | ConditionPhotoPreviewScreen             | ✅  | **適合** | 写真撮影後の確認画面。キャプション編集と変更保護（BackHandler）が適切に実装されている。        |
|  6  | ConditionPhotoFullScreen                | ✅  | **適合** | 全画面写真閲覧。ズーム・パン・カルーセル等の高度な UI 制御を完結。                      |
|  7  | PersonConditionPreviewParameterProvider | ✅  | **適合** | 多様な表示状態（空、ロード中、詳細等）のプレースホルダー供給。                          |

#### 5.4.2. ui/screens/health

| No. | 調査対象                                 | 状態 | 評価                        | 備考                                                                  |
|:---:|:-------------------------------------|:--:|:--------------------------|:--------------------------------------------------------------------|
|  1  | PersonHealthScreen                   | ✅  | **適合**                    | 健康記録画面の最上位 Screen。Phone/Tablet の出し分けと、各種ダイアログ、遷移イベントの統合管理を担当。       |
|  2  | PersonHealthScreenPhone              | ✅  | **適合**                    | スマホ向けレイアウト。履歴 ↔ グラフのモード切り替え（SegmentedButton）を含むシングルペイン構成。           |
|  3  | PersonHealthScreenTablet             | ✅  | **適合**                    | タブレット向けレイアウト。履歴とグラフ/詳細を常時並列表示する 2 ペイン構成を規約通りに実装。                    |
|  4  | PersonHealthScreenContent            | ✅  | **適合**                    | 健康記録の共通レイアウト基盤。マルチレイアウト対応と、履歴リスト・詳細パネル・グラフの統合を担当。                   |
|  5  | BatchInputScreen                     | ✅  | **適合 (Highly Compliant)** | 全健康カテゴリの一括入力画面。`isChanged` による破棄保護、`isValid` による保存制御、IME回避などを高度に実装。 |
|  6  | GraphExpansionScreen                 | ✅  | **適合**                    | グラフの全画面表示。ランドスケープ固定（DisposableEffect）や初期スクロール、アニメーションによる強調を実装。      |
|  7  | PersonHealthPreviewParameterProvider | ✅  | **適合**                    | プレビュー用のモックデータ提供プロバイダー。                                              |

#### 5.4.3. ui/screens/main

| No. | 調査対象                       | 状態 | 評価                        | 備考                                                                                |
|:---:|:---------------------------|:--:|:--------------------------|:----------------------------------------------------------------------------------|
|  1  | MainScreen                 | ✅  | **適合**                    | アプリの顔となる利用者一覧の制御層。ボトムシート、ダイアログ、スナックバーの連動、および Type-safe な画面遷移を統括。                  |
|  2  | MainScreenContent          | ✅  | **適合**                    | 利用者一覧の表示層。検索・五十音フィルタ・リスト描画を分離。`change_history.log` の読み込み処理も内包。                    |
|  3  | PersonEditScreen           | ✅  | **適合 (Highly Compliant)** | 利用者登録・編集。`BackHandler` による保護に加え、`BirthdayInputSection` での ViewModel/UI 直結パターンを採用。 |
|  4  | EmergencyContactListScreen | ✅  | **適合**                    | 緊急連絡先一覧。Stateless な構成と適切な削除確認フローを維持。                                              |
|  5  | EmergencyContactEditScreen | ✅  | **適合**                    | 緊急連絡先編集。種別選択ドロップダウンや `PhoneNumberVisualTransformation` を活用。                       |

#### 5.4.4. ui/screens/medication

| No. | 調査対象                          | 状態 | 評価     | 備考                                                  |
|:---:|:------------------------------|:--:|:-------|:----------------------------------------------------|
|  1  | PersonMedicationScreen        | ✅  | **適合** | 服薬管理画面の最上位 Screen。4ファイル構成を遵守し、カテゴリ同期や入力ダイアログの制御を担当。 |
|  2  | PersonMedicationScreenPhone   | ✅  | **適合** | スマホ向けレイアウト。カレンダー ↔ 履歴のモード切り替え。                      |
|  3  | PersonMedicationScreenTablet  | ✅  | **適合** | タブレット向けレイアウト。大画面を活かしたカレンダーと履歴の並列表示。                 |
|  4  | PersonMedicationScreenContent | ✅  | **適合** | 共通レイアウト基盤. 月間ナビゲーション（前月/次月）と各表示モードの切り替えを統括。         |

#### 5.4.5. ui/screens/settings

| No. | 調査対象                                      | 状態 | 評価                        | 備考                                                       |
|:---:|:------------------------------------------|:--:|:--------------------------|:---------------------------------------------------------|
|  1  | SettingsScreen                            | ✅  | **適合**                    | 設定画面の全体制御。多数のダイアログ、SAFによるエクスポート/インポート、生体認証連携など複雑な副作用を管理。 |
|  2  | SettingsScreenContent                     | ✅  | **適合**                    | 設定項目のセクション別表示。開発者向けツールの条件付き表示も適切に実装。                     |
|  3  | AuditLogScreen                            | ✅  | **適合**                    | 監査ログ一覧。フィルタリングやソート操作を ViewModel へ適切にバイパス。                |
|  4  | DeleteOrRestorePersonScreen               | ✅  | **適合 (Highly Compliant)** | アーカイブ管理。DELETE モード時の強力な警告表示（配色・バナー）と多段階確認による安全性を確保。      |
|  5  | UnassignedPhotoManagementScreen / Content | ✅  | **適合**                    | 未割り当て写真管理。孤立ファイルの検出・削除フローを保守機能としてクリーンに実装。                |

### 5.5. ui/theme

| No. | 調査対象             | 状態 | 評価     | 備考                                                                                         |
|:---:|:-----------------|:--:|:-------|:-------------------------------------------------------------------------------------------|
|  1  | Theme            | ✅  | **適合** | `CareMemoTheme` 最上位コンポーネント。Material 3 準拠、動的カラー、および 5 種のカスタムテーマ（Healing Green 等）の動的切り替えを統括。 |
|  2  | Color            | ✅  | **適合** | 基本カラーリソースの定義。各テーマパレットのソースとなる 16 進数定数を保持。                                                   |
|  3  | Type             | ✅  | **適合** | Material 3 Typography の定義。アプリ全体でのフォント・サイズの一貫性を保証。                                          |
|  4  | AuditLogColor    | ✅  | **適合** | 監査ログ用のセマンティックカラー。操作種別（INSERT/DELETE）や成否に応じた直感的な配色を提供。                                      |
|  5  | HealthAlertColor | ✅  | **適合** | 健康記録のアラートレベル（ALERT/WARNING/INFO）に応じた表示色およびグラフハイライト色の解決を担当。                                 |

### 5.6. ui/utils

| No. | 調査対象                            | 状態 | 評価                        | 備考                                                                                                  |
|:---:|:--------------------------------|:--:|:--------------------------|:----------------------------------------------------------------------------------------------------|
|  1  | PhoneNumberVisualTransformation | ✅  | **適合 (Highly Compliant)** | Compose の `VisualTransformation` を用いた電話番号の動的整形。ハイフン位置の算出を `PhoneLogic` へ委譲しており、UI とロジックの分離が徹底されている。 |

## 5.7. preview

| No. | 調査対象          | 状態 | 評価     | 備考                                                                               |
|:---:|:--------------|:--:|:-------|:---------------------------------------------------------------------------------|
|  1  | MockData      | ✅  | **適合** | プレビュー・テスト用の集中ダミーデータソース。`persistentListOf` 等を用いた不変化データの提供が規約通り。                   |
|  2  | PreviewStates | ✅  | **適合** | `PreviewParameterProvider` 等で使用するためのプレビュー用 UiState 集約定義。各画面の表示バリエーション管理を容易にしている。 |

## 6. utils

| No. | 調査対象          | 状態 | 評価                        | 備考                                                                                  |
|:---:|:--------------|:--:|:--------------------------|:------------------------------------------------------------------------------------|
|  1  | ZipUtils      | ✅  | **適合**                    | `Zip4j` を用いたパスワード付き圧縮・解凍。`Dispatchers.IO` の使用、非同期進捗監視、パスワード検証ロジックが堅牢に実装されている。       |
|  2  | ImageUtils    | ✅  | **適合 (Highly Compliant)** | 画像のリサイズ、回転補正、Exif除去、サムネイル生成を統括。`recycle()` によるメモリ解放や `Dispatchers.IO` での実行が徹底されている。 |
|  3  | PdfExporter   | ✅  | **適合**                    | `PdfDocument` と `PDFBox` を併用した帳票生成。Canvas による精密なレイアウト、改ページ制御、パスワード保護、共有インテント連携を完結。  |
|  4  | DateTimeUtils | ✅  | **適合 (Highly Compliant)** | 和暦変換、年齢計算、誕生日の UTC 正規化を担当。ケア業務特有の表記ルールとデータ整合性のための厳密な日付操作を両立。                        |


---
最終更新日: 2026/08/19
