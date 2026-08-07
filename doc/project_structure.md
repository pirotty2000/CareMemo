# CareMemo プロジェクト構造定義書

このドキュメントでは、CareMemo プロジェクトのソースコード構造とその役割について説明します。仕様変更や不具合修正の際に対象ファイルを探すガイドとして利用してください。

---

# ドキュメントの構成

ドキュメントは3つのファイルで構成されています。

- project_structure.md (このファイル)：プロジェクト全体のパッケージ構造やファイル構成を定義
- project_UI_GUIDELINES.md：このプロジェクトのUI/UX 設計、および画面デザインの共通ルールを定義
- project_RULES.md：このプロジェクトのソースコードを修正・追加・レビューする際の行動規範と実装ルールを定義

設計変更や機能追加など、プロジェクトに影響を与える作業を行う際は、この3つのファイルを全て読み込んだ上で実装してください。
また、ドキュメントの加筆・修正の際は、3つのファイルの役割を十分理解し、同じ情報を複数のドキュメントが持つことのないよう細心の注意を払ってください。

---

# ドキュメント運用方針

- このドキュメントは CareMemo の設計上の唯一の基準（Single Source of Truth）とします。
- 設計変更や共通部品の追加・削除を行った場合は、実装と合わせて本ドキュメントも更新してください。
- 実装とドキュメントに差異が生じた場合は、ドキュメントを優先して見直し、必要に応じて実装またはドキュメントを修正してください。

---

# パッケージ構造の概要

プロジェクトの主要なコードは `jp.mydns.fujiwara.carememo` パッケージ配下に配置されています。

```text
jp.mydns.fujiwara.carememo
├── ui/                 # UIレイヤー（Jetpack Compose）
│   ├── navigation/     #  ├─ ナビゲーション定義（Type-safe Destinations）
│   ├── screens/        #  ├─ 各画面のComposable（機能階層で分類）
│   │   ├── main/       #  │   ├─ 利用者一覧画面
│   │   ├── health/     #  │   ├─ (A)健康記録・一括入力・グラフ拡大
│   │   ├── condition/  #  │   ├─ (B)所見メモ・写真関連
│   │   ├── medication/ #  │   ├─ (C)服薬管理
│   │   └── settings/   #  │   └─ アプリ設定・利用者管理
│   ├── components/     #  ├─ 再利用可能なUIコンポーネント（階層管理）
│   │   ├── base/       #  │   ├─ 【汎用基盤】ドメイン非依存（例: AppTextField, LoadingScreen）
│   │   │               #  │   │  ※ アプリの業務知識を知らない純粋なUI部品
│   │   ├── common/     #  │   ├─ 【ドメイン共通】ドメイン依存かつ複数画面（例: PersonHeader, DateTimeInput）
│   │   │               #  │   │  ※ 利用者情報や介護記録のルールを共有する部品
│   │   ├── main/       #  │   ├─ 【固有】利用者一覧画面専用（例: UserListItem, BirthdayInputFields）
│   │   ├── health/     #  │   ├─ 【固有】(A)健康記録専用
│   │   ├── condition/  #  │   ├─ 【固有】(B)所見メモ専用
│   │   └── medication/ #  │   └─ 【固有】(C)服薬管理専用
│   ├── mapping/        #  ├─ 表示用マッピング（監査ログ等の識別子を日本語に変換）
│   └── theme/          #  └─ アプリのテーマ設定（Color, Type, Shapeなど）
├── viewmodel/          # UI状態の管理と実行制御（safeLaunch / safeCollect による通知・ロード管理）
├── logic/              # ドメインロジック（計算・判定・Entity変換等の純粋なロジック）
│   ├── common/         #  ├─ アプリ全体で再利用可能な計算・変換ロジック
│   └── feature/        #  └─ 特定の画面・ViewModelに密結合した判定・加工ロジック
├── data/               # データレイヤー（Room Database / Repository / AppSpecifications）
│   ├── repository/     #  ├─ リポジトリ（純粋なデータCRUD操作に特化。業務ロジックを持たない）
│   └── spec/           #  └─ アプリの仕様定義（ドメイン別に分割された定数・バリデーション規則）
├── utils/              # ユーティリティ（日時操作、PDF生成、画像処理、ZIP圧縮）
├── MainActivity.kt     # アプリのエントリポイント、NavHostによる画面遷移定義
└── CareMemoApplication.kt # Application クラス、リポジトリのDI（依存注入）管理
```

---

# Entity 一覧

- `data/Entity.kt` に定義されているデータベースのテーブル構造です。
- 全てのエンティティは UUID による主キー管理を行い、将来のサーバー同期に対応しています。

| エンティティ名            | テーブル名                   | 概要                                        |
|:-------------------|:------------------------|:------------------------------------------|
| `Person`           | `person_db`             | **利用者基本情報**: 氏名、ふりがな、生年月日、および論理削除状態を管理。   |
| `HeightAndWeight`  | `height_and_weight_db`  | **身体計測**: 利用者の身長・体重の記録。                   |
| `BpAndPulse`       | `bp_and_pulse_db`       | **バイタル**: 血圧（最高・最低）、脈拍、酸素飽和度（SAT）、体温の記録。  |
| `GlucoseAndHbA1c`  | `glucose_and_hba1c_db`  | **血糖・検査値**: 血糖値および HbA1c の記録。             |
| `ConditionAtVisit` | `condition_at_visit_db` | **所見メモ**: 訪問時の様子や特記事項のテキスト記録。             |
| `ConditionPhoto`   | `condition_photo_db`    | **所見写真**: 所見メモに添付された写真のファイル名とメタ情報。        |
| `MedicationRecord` | `medication_record_db`  | **服薬記録**: 日付および時間枠ごとの服薬実施ステータス。           |
| `EmergencyContact` | `emergency_contact_db`  | **緊急連絡先**: 利用者に紐付く主治医、家族、事業所等の連絡先情報。      |
| `AuditLog`         | `audit_log_db`          | **操作ログ**: アプリ内で行われたデータ操作（作成・更新・削除等）の監査証跡。 |

---

# AppSpecifications 一覧

- `data/spec` 配下には、アプリ全体のビジネスルール、閾値、バリデーション制約などの「定数」が集約されています。
- ロジック（判定）はここには含めず、純粋な数値や定義のみを管理します。

| ファイル名                               | 役割・主な定義内容                                                              |
|:------------------------------------|:-----------------------------------------------------------------------|
| `CalendarSpecifications.kt`         | **和暦・日付の制約**: サポートする最小日付(1900年)、各元号(昭和・平成・令和)の開始日やオフセット値。              |
| `ConstraintSpecifications.kt`       | **入力・システム制約**: 氏名・メモの最大文字数、写真の最大枚数(3枚)・サイズ、パスワード桁数などの物理的制約。            |
| `EmergencyContactSpecifications.kt` | **緊急連絡先の種別・優先度**: 施設名・担当者名・電話番号の最大文字数、表示順序のデフォルト値、および連絡先種別の定義。         |
| `HealthSpecifications.kt`           | **健康データの閾値**: 血圧・脈拍・SAT・体温・血糖値・HbA1c・BMIの異常判定閾値、およびグラフ描画の刻み幅や表示範囲。     |
| `IdSpecifications.kt`               | **ID管理の定義**: 新規レコードであることを示すシステム共通の識別子（"NEW"など）の定義。                     |
| `MedicationSpecifications.kt`       | **服薬管理の定義**: 服薬時間枠(朝・昼・夕・寝る前)のインデックスとラベル、服薬ステータスコード(0:未、1:介助、2:服用)の定義。 |
| `PdfExportSpecifications.kt`        | **出力仕様**: PDF生成時のA4レイアウト(余白・行間)、フォントサイズ、配色(RGB)、テーブル列幅の定義。             |
| `SearchSpecifications.kt`           | **検索・表示インデックス**: 利用者一覧で使用する五十音インデックスのグループ定義(全、あ、か...他)。                |
| `SettingsSpecifications.kt`         | **設定の選択肢**: アプリ再ロック時間(即時〜30分)や、監査ログの保持期間(1週間〜1年)の選択肢リスト。               |

---
# CareMemo 画面一覧

| 画面ID       | 分類         | 画面名                           | 実装ID          | 実装ファイル                                        | 備考          |
|------------|------------|-------------------------------|---------------|-----------------------------------------------|-------------|
| SCR-M-001  | Main       | MainScreen                    | -             | `main/MainScreen.kt`                          | トップ画面       |
| SCR-M-002  | Main       | PersonEditScreen              | -             | `main/PersonEditScreen.kt`                    | 利用者登録・編集    |
| SCR-M-003  | Main       | EmergencyContactListScreen    | -             | `main/EmergencyContactListScreen.kt`          | 緊急連絡先管理     |
| SCR-M-004  | Main       | EmergencyContactEditScreen    | -             | `main/EmergencyContactEditScreen.kt`          | 緊急連絡先登録・編集  |
| SCR-PH-001 | Health     | PersonHealthScreen            | -             | `health/PersonHealthScreen.kt`                | 健康記録        |
|            |            |                               | SCR-PH-001-PH | `health/PersonHealthScreenPhone.kt`           | Phone実装     |
|            |            |                               | SCR-PH-001-TB | `health/PersonHealthScreenTablet.kt`          | Tablet実装    |
|            |            |                               | SCR-PH-001-CT | `health/PersonHealthScreenContent.kt`         | 共通Content実装 |
| SCR-PH-002 | Health     | BatchInputScreen              | -             | `health/BatchInputScreen.kt`                  | 健康記録一括入力    |
| SCR-PH-003 | Health     | GraphExpansionScreen          | -             | `health/GraphExpansionScreen.kt`              | グラフ拡大表示     |
| SCR-PC-001 | Condition  | PersonConditionScreen         | -             | `condition/PersonConditionScreen.kt`          | 所見メモ        |
|            |            |                               | SCR-PC-001-PH | `condition/PersonConditionScreenPhone.kt`     | Phone実装     |
|            |            |                               | SCR-PC-001-TB | `condition/PersonConditionScreenTablet.kt`    | Tablet実装    |
|            |            |                               | SCR-PC-001-CT | `condition/PersonConditionScreenContent.kt`   | 共通Content実装 |
| SCR-PC-002 | Condition  | ConditionPhotoPreviewScreen   | -             | `condition/ConditionPhotoPreviewScreen.kt`    | 写真プレビュー     |
| SCR-PC-003 | Condition  | ConditionPhotoFullScreen      | -             | `condition/ConditionPhotoFullScreen.kt`       | 写真全画面表示     |
| SCR-PM-001 | Medication | PersonMedicationScreen        | -             | `medication/PersonMedicationScreen.kt`        | 服薬管理        |
|            |            |                               | SCR-PM-001-PH | `medication/PersonMedicationScreenPhone.kt`   | Phone実装     |
|            |            |                               | SCR-PM-001-TB | `medication/PersonMedicationScreenTablet.kt`  | Tablet実装    |
|            |            |                               | SCR-PM-001-CT | `medication/PersonMedicationScreenContent.kt` | 共通Content実装 |
| SCR-S-001  | Settings   | SettingsScreen                | -             | `settings/SettingsScreen.kt`                  | 設定          |
| SCR-S-002  | Settings   | AuditLogScreen                | -             | `settings/AuditLogScreen.kt`                  | 監査ログ        |
| SCR-S-003  | Settings   | DeleteOrRestorePerson         | -             | `settings/DeleteOrRestorePerson.kt`           | 終了利用者管理     |
| SCR-S-004  | Settings   | OrphanedPhotoManagementScreen | -             | `settings/OrphanedPhotoManagementScreen.kt`   | 迷子写真の確認     |

---
# ViewModel一覧

```text
ViewModel (androidx.lifecycle.ViewModel)
└── BaseUiStateViewModel<S, E> (型安全なUI状態・イベント管理、SavedStateHandle 対応)
    ├── PersonListViewModel (利用者一覧)
    ├── PersonEditViewModel (利用者登録・編集)
    ├── EmergencyContactEditViewModel (緊急連絡先管理)
    ├── SettingsViewModel (設定・保守)
    ├── AuditLogViewModel (操作ログ参照)
    ├── DeleteOrRestorePersonViewModel (利用修了者管理)
    ├── OrphanedPhotoViewModel (迷子写真確認)
    │
    └── PersonBaseUiStateViewModel<S, E> (利用者コンテキストの自動同期基盤)
        ├── PersonDetailUiStateViewModel (詳細画面共通: ヘッダー、カテゴリ管理)
        ├── PersonHealthViewModel (専門: 健康記録)
        ├── PersonConditionViewModel (専門: 所見メモ)
        ├── PersonMedicationViewModel (専門: 服薬管理)
        └── BatchInputViewModel (専門: 一括入力)

[特徴]
・1画面1UiState(S): 画面の状態を一つのデータクラスで集約し、原子的に更新。
・不変コレクション (ImmutableList): UiState 内のリスト型には `ImmutableList` を使用し、Compose の再コンポーズ最適化（Stability）を強制。
・型安全イベント(E): 画面遷移等の副作用を ViewEvent として定義し、一元管理。
・自律初期化: SavedStateHandle 経由で引数を取得し、プロセス死からの復旧に対応。
```


---


# Repository - ViewModel - Screen 依存関係

- 各機能層におけるロジックの垂直方向の依存関係です。
- 実装上の原則（基盤機能の利用ルール等）については、`project_RULES.md` の「4. エラーハンドリングと実行制御」を参照してください。


| 分類       | 画面 (Screen)                                                  | ViewModel                                                                         | 主要Logic                                                       | 主要Repository                                                                                                                             |
|:---------|:-------------------------------------------------------------|:----------------------------------------------------------------------------------|:--------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------|
| 利用者一覧    | `MainScreen`<br>`PersonEditScreen`                           | `PersonListViewModel` **(B)**<br>`PersonEditViewModel` **(B)**                    | `PersonListLogic`<br>`PersonEditLogic`<br>`JapaneseDateLogic` | `PersonRepository`<br>`DeleteOrRestorePersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository` |
| (A) 健康記録 | `PersonHealthScreen`                                         | `PersonHealthViewModel` **(PB)**<br>+ `PersonDetailUiStateViewModel` **(PB)**     | `PersonHealthLogic`<br>`HealthLogic`<br>`IdLogic`             | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`                |
| (B) 所見メモ | `PersonConditionScreen`                                      | `PersonConditionViewModel` **(PB)**<br>+ `PersonDetailUiStateViewModel` **(PB)**  | `PersonConditionLogic`<br>`ConditionLogic`<br>`IdLogic`       | `ConditionRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`             |
| (C) 服薬管理 | `PersonMedicationScreen`                                     | `PersonMedicationViewModel` **(PB)**<br>+ `PersonDetailUiStateViewModel` **(PB)** | `PersonMedicationLogic`<br>`MedicationLogic`                  | `MedicationRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`            |
| 緊急連絡先管理  | `EmergencyContactListScreen`<br>`EmergencyContactEditScreen` | `EmergencyContactEditViewModel` **(B)**                                           | `EmergencyContactLogic`<br>`PhoneNumberVisualTransformation`  | `EmergencyContactRepository`<br>`PersonRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`                                   |
| 健康一括入力   | `BatchInputScreen`                                           | `BatchInputViewModel` **(PB)**                                                    | `BatchInputLogic`<br>`HealthLogic`                            | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`                |
| 利用者管理    | `DeleteOrRestorePerson`                                      | `DeleteOrRestorePersonViewModel` **(B)**                                          | `DeleteOrRestorePersonLogic`                                  | `DeleteOrRestorePersonRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`                                                    |
| アプリ設定    | `SettingsScreen`                                             | `SettingsViewModel` **(B)**                                                       | `SettingsLogic`                                               | `AppMaintenanceRepository`<br>`DeleteOrRestorePersonRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`                      |
| 操作ログ     | `AuditLogScreen`                                             | `AuditLogViewModel` **(B)**                                                       | `AuditLogLogic`                                               | `AuditLogRepository`<br>`UserSettingsRepository`                                                                                         |
| 迷子写真確認   | `OrphanedPhotoManagementScreen`                              | `OrphanedPhotoViewModel` **(B)**                                                  | `ConditionMaintenanceLogic`                                   | `ConditionRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`                                                                |
| 共通基盤     | (詳細画面全体)                                                     | `PersonDetailUiStateViewModel` **(PB)**                                           | -                                                             | `PersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository`<br>`AuditLogRepository`                                      |

<br>
※ **(B)**: `BaseUiStateViewModel` 継承（基本UI状態・イベント管理）<br>
※ **(PB)**: `PersonBaseUiStateViewModel` 継承（利用者コンテキスト管理）

---

# Logic - ドメインロジックと計算ルール

- ViewModel から「Android フレームワークやライフサイクルに依存しない純粋な計算・判定・変換」を分離したレイヤーです。
- リスト形式の計算結果を返す際は、原則として `ImmutableList` を使用し、ViewModel や UI 層での不要な変換処理を排除します。
- 実装の原則については、`project_RULES.md` の「3.5. Logic レイヤーによる責務の分離」を参照してください。

## **Logic 一覧**

### **ドメイン共通ロジック (logic/common)**

- アプリ全体、または複数の画面で再利用される計算・判定・Enum 定義です。

| ファイル名                  | 役割・主な内容                                               |
|:-----------------------|:------------------------------------------------------|
| `BirthEra.kt`          | 元号（昭和・平成・令和・西暦）の Enum 定義とリソース紐付け。                     |
| `IdLogic.kt`           | システム共通の ID 判定（新規レコード ID 等）ロジック。                       |
| `JapaneseDateLogic.kt` | 西暦 ↔ 和暦の相互変換、和暦の妥当性チェック、日付文字列の正規化。                    |
| `HealthLogic.kt`       | BMI 計算、バイタル・血糖値・HbA1c の異常判定ルール、入力妥当性チェック。             |
| `MedicationLogic.kt`   | カレンダーの日付リスト生成（空セル挿入）、同期アクション（保存・削除）の判定、およびデータのクレンジング。 |
| `ConditionLogic.kt`    | 所見メモの検索フィルタリング、重複判定ロジック。                              |

### **機能固有ロジック (logic/feature)**
- 特定の画面や ViewModel の状態管理（UiState）に密結合したロジックです。
- **※ 各 Logic クラスのファイルには、その画面の `UiState` および `ViewEvent` の定義も集約されています。**

| ファイル名                           | 役割・主な内容                                                  |
|:--------------------------------|:---------------------------------------------------------|
| `PersonListLogic.kt`            | 利用者一覧の五十音判定、フィルタリング、UI状態（伏せ字・年齢計算）への変換。                  |
| `PersonEditLogic.kt`            | 利用者編集画面における変更検知（`isChanged`）、保存可否判定（`isValid`）、Entity生成。 |
| `PersonDetailLogic.kt`          | 利用者詳細（A/B/C共通）のUI状態定義、カテゴリ管理、共通イベントの定義。                  |
| `PersonHealthLogic.kt`          | 健康記録画面における新規・更新判定、および重複チェックロジック。                         |
| `PersonConditionLogic.kt`       | 所見メモ画面における UI 状態定義、変更検知、バリデーション、Entity生成。                |
| `PersonMedicationLogic.kt`      | 服薬管理画面における履歴の日付別グルーピング、UiStateへの変換。                      |
| `BatchInputLogic.kt`            | 一括入力画面における保存データの仕分け、複数カテゴリ横断のバリデーション。                    |
| `DeleteOrRestorePersonLogic.kt` | 利用者管理（復帰・抹消）画面の表示状態定義。                                   |
| `SettingsLogic.kt`              | ZIP検証、バージョン互換性、空き容量チェック、開発者モード有効化判定。                     |
| `AuditLogLogic.kt`              | 監査ログのフィルタリング、並び替え、選択肢の抽出。                                |
| `ConditionMaintenanceLogic.kt`  | データベースと物理ファイルの照合、迷子写真の分類。                                |

### **表示用マッピングロジック (ui/mapping)**
- 判定結果（Enum）を日本語のラベルやテーマに合わせた色へ変換するロジックです。
- **※ これらは Android リソース（R.string）や UI 資源（Color）に依存します。**

| ファイル名                        | 役割・主な内容                                   |
|:-----------------------------|:------------------------------------------|
| `HealthDisplayMapper.kt`     | 健康状態の Enum（AlertLevel 等）を日本語ラベル、色、説明文へ変換。 |
| `BirthEraDisplayMapper.kt`   | 元号（昭和・平成・令和・西暦）の Enum を日本語ラベルへ変換。         |
| `MedicationDisplayMapper.kt` | 服薬状況の Enum を記号（○/△/×）、時間枠ラベル、色へ変換。        |
| `ConditionDisplayMapper.kt`  | 所見メモの写真枚数ラベルなどの表示文字列を生成。                  |
| `EmergencyContactMapping.kt` | 緊急連絡先の種別（Enum）を日本語ラベルやアイコンへ変換。            |
| `FeatureNameMapper.kt`       | 監査ログ用の機能識別子を日本語名称へ変換。                     |
| `ActionTypeMapper.kt`        | 監査ログ用の操作種別（INSERT等）を日本語名称へ変換。             |
| `ResultTypeMapper.kt`        | 監査ログ用の実行結果（SUCCESS等）を日本語名称へ変換。            |

---

# Screen - Components 依存関係

- 各画面で使用されるUIコンポーネントの構成です。修正時の影響範囲の確認に利用してください。
- ※ 🔴**太字**は2つ以上の画面で共有されている部品です。変更時の影響範囲に注意してください。

| 分類                   | 画面 (Screen)                                                              | 使用コンポーネント (ファイル名)                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|:---------------------|:-------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1. 利用者一覧および(A)(B)(C) | (全主要画面)                                                                  | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/AppDialog.kt`**：共通ダイアログ基盤（ボタン・コンテンツ・スクロール制御）<br>🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/AppTextField.kt`**：共通の入力フィールド（標準）<br>🔴**`base/AppCompactTextField.kt`**：入力欄の微調整用コンポーネント                                                                                                                                |
| 2. 利用者一覧             | `MainScreen`<br>`*Content.kt`<br>`PersonEditScreen.kt`                   | `main/CategoryBadges.kt`：記録状況を示すカテゴリバッジ<br>`main/MainComponents.kt`：利用者一覧共通部品（UserListItem 等）<br>`main/KanaIndexBar.kt`：五十音インデックスバー<br>`main/QuickActionMenu.kt`：バッジタップ時のクイックメニュー<br>🔴**`base/SearchBox.kt`**：共通検索バー<br>`main/BirthdayInputFields.kt`：生年月日入力部品(PersonEditScreen専用)                                                                                                                                                                                                       |
| 3. (A)(B)(C)共通       | (詳細3画面全体)                                                                | 🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助<br>🔴**`base/AppDeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ<br>🔴**`common/CategorySelectorBar.kt`**：(A)(B)(C)の切り替えバー<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/PdfExportActionHandler.kt`**：PDF出力のアクション管理<br>🔴**`common/PdfSettingsDialog.kt`**：PDF出力設定ダイアログ                                                                                                      |
| 4. (A) 健康記録          | `PersonHealthScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`     | `health/PersonHealthComponents.kt`：(A)専用の表示・編集・詳細パネル・詳細項目(DetailItem)<br>`health/HealthGraphView.kt`：(A)専用グラフ表示<br>`health/LineChart.kt`：グラフ描画 engine<br>`health/HealthChartHelper.kt`：グラフ用データ変換<br>🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤                                                                                                                                                                                                                                        |
| 5. (B) 所見メモ          | `PersonConditionScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`  | 🔴**`base/SearchBox.kt`**：共通検索バー<br>`condition/PersonConditionComponents.kt`：(B)専用の表示・編集・写真グリッド<br>🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤                                                                                                                                                                                                                                                                                                                                         |
| 6. (C) 服薬管理          | `PersonMedicationScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt` | `medication/PersonMedicationComponents.kt`：(C)専用カレンダー・履歴テーブル・入力ダイアログ                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 7. (A)の一括入力          | `BatchInputScreen`                                                       | 🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                                                                          |
| 8. 利用者管理             | `DeleteOrRestorePerson`                                                  | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                                                                        |
| 9. アプリ設定             | `SettingsScreen`                                                         | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/AppDeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ<br>🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                                                          |
| 10. 操作ログ             | `AuditLogScreen`                                                         | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                                                                                                                        |
| 11. 迷子写真確認           | `OrphanedPhotoManagementScreen`                                          | 🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/LoadingScreen.kt`**：共通のローディング表示                                                                                                                                                                                                                                                                                                                                                                                                      |
| 12. 緊急連絡先            | `EmergencyContact*Screen.kt`                                             | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/AppTextField.kt`**：共通の入力フィールド（標準）<br>🔴**`base/AppCompactTextField.kt`**：入力欄の微調整用コンポーネント<br>🔴**`base/AppDialog.kt`**：共通ダイアログ基盤<br>🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/SearchBox.kt`**：共通検索バー<br>🔴**`base/AppDeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助 |

---

# Components - Screen 逆引きリファレンス

- コンポーネント側から見た、各画面への使用状況マトリックスです。
- ※ **注意**: 本セクションは「Screen - Components 依存関係」と同じ情報を視点（行・列）を変えて表現したものです。
- ※ **注意**: 一方の表を修正した際は、必ずもう一方も更新して矛盾が起きないようにしてください。

| コンポーネント (ファイル名)                                                         | 一覧 | (A)健康 | (B)所見 | (C)服薬 | (A)一括 | 連絡 | 管理 | 設定 | ログ |
|:------------------------------------------------------------------------|:--:|:-----:|:-----:|:-----:|:-----:|:--:|:--:|:--:|:--:|
| **【共通部品 (複数画面で使用)】**                                                    |    |       |       |       |       |    |    |    |    |
| **＜ドメイン非依存＞**                                                           |    |       |       |       |       |    |    |    |    |
| **（入力フィールド）**                                                           |    |       |       |       |       |    |    |    |    |
| 　　🔴**`base/AppTextField.kt`**：共通の入力フィールド（標準）                           | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  |    |    |    |
| 　　🔴**`base/AppCompactTextField.kt`**：入力欄の微調整用コンポーネント                   | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  |    |    |    |
| **（共通ダイアログ）**                                                           |    |       |       |       |       |    |    |    |    |
| 　　🔴**`base/AppDialog.kt`**：共通ダイアログ基盤（ボタン・コンテンツ・スクロール制御）                | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  |    |    |    |
| 　　🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ                          | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  | ✓  | ✓  |    |
| 　　🔴**`base/AppDeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ                 |    |   ✓   |   ✓   |   ✓   |       | ✓  |    | ✓  |    |
| **（その他）**                                                               |    |       |       |       |       |    |    |    |    |
| 　　🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理                    | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  | ✓  | ✓  | ✓  |
| 　　🔴**`base/LoadingScreen.kt`**：共通のローディング表示                             | ✓  |   ✓   |   ✓   |   ✓   |   ✓   | ✓  |    |    |    |
| 　　🔴**`base/EmptyState.kt`**：共通の「データなし」表示                               | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  | ✓  |    | ✓  |
| 　　🔴**`base/SearchBox.kt`**：共通検索バー                                      | ✓  |       |   ✓   |       |       | ✓  |    |    |    |
| 　　🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                     |    |   ✓   |   ✓   |   ✓   |   ✓   | ✓  | ✓  | ✓  | ✓  |
| **＜ドメイン依存＞**                                                            |    |       |       |       |       |    |    |    |    |
| 　　🔴**`common/CategorySelectorBar.kt`**：(A)(B)(C)の切り替えバー                |    |   ✓   |   ✓   |   ✓   |       |    |    |    |    |
| 　　🔴**`common/DateTimeInputFields.kt`**：共通の日時入力                         |    |   ✓   |   ✓   |   ✓   |   ✓   |    |    |    |    |
| 　　🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤                        |    |   ✓   |   ✓   |       |       |    |    |    |    |
| 　　🔴**`common/PdfExportActionHandler.kt`**：PDF出力のアクション管理                |    |   ✓   |   ✓   |   ✓   |       |    |    |    |    |
| 　　🔴**`common/PdfSettingsDialog.kt`**：PDF出力設定ダイアログ                      |    |   ✓   |   ✓   |   ✓   |       |    |    |    |    |
| 　　🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー                         |    |   ✓   |   ✓   |   ✓   |   ✓   |    |    |    |    |
| **【個別部品 (特定ドメイン/画面)】**                                                  |    |       |       |       |       |    |    |    |    |
| **＜利用者一覧：MainScreen＞**                                                  |    |       |       |       |       |    |    |    |    |
| 　　`main/BirthdayInputFields.kt`：生年月日入力部品(PersonEditScreen専用)            | ✓  |       |       |       |       |    |    |    |    |
| 　　`main/CategoryBadges.kt`：記録状況を示すカテゴリバッジ                               | ✓  |       |       |       |       |    |    |    |    |
| 　　`main/KanaIndexBar.kt`：五十音インデックスバー                                    | ✓  |       |       |       |       |    |    |    |    |
| 　　`main/MainComponents.kt`：利用者一覧共通部品（UserListItem 等）                    | ✓  |       |       |       |       |    |    |    |    |
| 　　`main/QuickActionMenu.kt`：バッジタップ時のクイックメニュー                            | ✓  |       |       |       |       |    |    |    |    |
| **＜(A)健康記録：PersonHealthScreen＞**                                        |    |       |       |       |       |    |    |    |    |
| 　　`health/HealthGraphView.kt`：(A)専用グラフ表示                                |    |   ✓   |       |       |       |    |    |    |    |
| 　　`health/LineChart.kt`：グラフ描画エンジン                                       |    |   ✓   |       |       |       |    |    |    |    |
| 　　`health/HealthChartHelper.kt`：グラフ用データ変換                               |    |   ✓   |       |       |       |    |    |    |    |
| 　　`health/PersonHealthComponents.kt`：(A)専用の表示・編集・詳細パネル・詳細項目(DetailItem) |    |   ✓   |       |       |       |    |    |    |    |
| **＜(B)所見メモ：PersonConditionScreen＞**                                     |    |       |       |       |       |    |    |    |    |
| 　　`condition/PersonConditionComponents.kt`：(B)専用の表示・編集・写真グリッド           |    |       |   ✓   |       |       |    |    |    |    |
| **＜(C)服薬管理：PersonMedicationScreen＞**                                    |    |       |       |       |       |    |    |    |    |
| 　　`medication/PersonMedicationComponents.kt`：(C)専用カレンダー・履歴テーブル・入力ダイアログ  |    |       |       |   ✓   |       |    |    |    |    |

---

# 画面遷移一覧（NAV）

- 画面遷移の設計と定義です。
- 遷移の基本ルールについては、`project_RULES.md` の「9. 画面遷移 (NAV) ルール」を参照してください。

## 画面遷移定義

| NAV ID                 | 遷移元画面ID    | 遷移先画面ID    | 操作                                | 期待結果                     |
|:-----------------------|:-----------|:-----------|:----------------------------------|:-------------------------|
| **[Main: 利用者管理]**      |            |            |                                   |                          |
| **NAV-M-001**          | SCR-M-001  | SCR-M-002  | 利用者一覧で「新規登録」をタップする。               | 利用者登録画面が表示される。           |
| **NAV-M-002**          | SCR-M-001  | SCR-M-002  | 利用者一覧で利用者を長押しし、「基本情報の編集」をタップする。   | 利用者編集画面が表示される。           |
| **NAV-M-003**          | SCR-M-002  | SCR-M-001  | 登録・編集画面で「保存」をタップし、成功する。           | 利用者一覧画面に戻り、情報が更新される。     |
| **NAV-M-004**          | SCR-M-001  | SCR-PH-002 | 利用者を選択し、表示されたメニューの「健康一括入力」をタップする。 | 健康記録一括入力画面が表示される。        |
| **NAV-M-005**          | SCR-PH-002 | SCR-M-001  | 一括入力画面で「保存」をタップし、成功する。            | 利用者一覧画面に戻る。              |
| **NAV-M-006**          | SCR-M-001  | SCR-M-003  | 利用者一覧で利用者を長押しし、「緊急連絡先の管理」をタップする。  | 緊急連絡先管理画面が表示される。         |
| **NAV-M-007**          | SCR-M-003  | SCR-M-004  | 緊急連絡先管理画面で「追加」をタップ、または既存項目を編集する。  | 緊急連絡先登録・編集画面が表示される。      |
| **NAV-M-008**          | SCR-M-004  | SCR-M-003  | 登録・編集画面で「保存」をタップし、成功する。           | 管理画面に戻り、情報が更新される。        |
| **[Health: 健康記録]**     |            |            |                                   |                          |
| **NAV-PH-001**         | SCR-M-001  | SCR-PH-001 | 利用者を選択し、表示されたメニューの「身長体重」をタップする。   | 健康管理画面（身長体重）が表示される。      |
| **NAV-PH-002**         | SCR-PH-001 | SCR-PH-003 | グラフ表示エリア、またはグラフ拡大アイコンをタップする。      | グラフ拡大表示画面が表示される。         |
| **[Condition: 所見メモ]**  |            |            |                                   |                          |
| **NAV-PC-001**         | SCR-M-001  | SCR-PC-001 | 利用者を選択し、表示されたメニューの「所見メモ」をタップする。   | 所見メモ画面が表示される。            |
| **NAV-PC-002**         | SCR-PC-001 | SCR-PC-002 | 記録の編集パネルでカメラアイコンをタップし、写真を撮影する。    | 写真プレビュー画面が表示される。         |
| **NAV-PC-003**         | SCR-PC-002 | SCR-PC-001 | プレビュー画面で「保存する」をタップし、成功する。         | 所見メモ画面に戻り、写真が保存される。      |
| **NAV-PC-004**         | SCR-PC-001 | SCR-PC-003 | 保存済みの写真サムネイルをタップする。               | 写真全画面表示画面が表示される。         |
| **[Medication: 服薬管理]** |            |            |                                   |                          |
| **NAV-PM-001**         | SCR-M-001  | SCR-PM-001 | 利用者を選択し、表示されたメニューの「服薬管理」をタップする。   | 服薬管理画面が表示される。            |
| **[Settings: 設定・ログ]**  |            |            |                                   |                          |
| **NAV-S-001**          | SCR-M-001  | SCR-S-001  | ドロワー（ハンバーガーメニュー）を開き、「設定」をタップする。   | 設定画面が表示される。              |
| **NAV-S-002**          | SCR-S-001  | SCR-S-002  | 設定画面で「操作ログの参照」をタップする。             | 監査ログ画面が表示される。            |
| **NAV-S-003**          | SCR-S-001  | SCR-S-003  | 設定画面で「利用終了者の管理」をタップする。            | 利用者管理（復帰・抹消）画面が表示される。    |
| **NAV-S-004**          | SCR-S-001  | SCR-S-004  | 設定画面で「迷子写真の確認」をタップする。             | 迷子写真確認画面が表示される。          |
| **[Common: 詳細画面間遷移]**  |            |            |                                   |                          |
| **NAV-COM-001**        | SCR-PH-001 | SCR-PC-001 | 詳細画面のカテゴリバーで「所見」をタップする。           | 所見メモ画面に切り替わる（スタックは積まない）。 |
| **NAV-COM-002**        | SCR-PC-001 | SCR-PM-001 | 詳細画面のカテゴリバーで「服薬」をタップする。           | 服薬管理画面に切り替わる（スタックは積まない）。 |
| **NAV-COM-003**        | SCR-PM-001 | SCR-PH-001 | 詳細画面のカテゴリバーで「健康」をタップする。           | 健康管理画面に切り替わる（スタックは積まない）。 |

---

最終更新日: 2026/08/05
