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
├── ui/                    # UIレイヤー（Jetpack Compose）
│   ├── navigation/        #  ├─ ナビゲーション定義（Type-safe Destinations）
│   ├── screens/           #  ├─ 各画面のComposable（機能カテゴリ別に4ファイル構成ルールを遵守）
│   │   ├── main/          #  │   ├─ 利用者一覧、登録・編集、緊急連絡先
│   │   ├── health/        #  │   ├─ (A)健康記録・一括入力・グラフ拡大
│   │   ├── condition/     #  │   ├─ (B)所見メモ・写真関連
│   │   ├── medication/    #  │   ├─ (C)服薬管理
│   │   └── settings/      #  │   └─ アプリ設定、操作ログ、アーカイブ管理、未割り当て写真
│   ├── components/        #  ├─ 再利用可能なUIコンポーネント（階層管理）
│   │   ├── base/          #  │   ├─ 【汎用基盤】ドメイン非依存（例: AppTextField, LoadingScreen）
│   │   ├── common/        #  │   ├─ 【ドメイン共通】ドメイン依存かつ複数画面（例: PersonHeaderTitle）
│   │   ├── main/          #  │   ├─ 【固有】利用者一覧・編集専用
│   │   ├── health/        #  │   ├─ 【固有】(A)健康記録専用（例: HealthGraphView）
│   │   ├── condition/     #  │   ├─ 【固有】(B)所見メモ専用
│   │   └── medication/    #  │   └─ 【固有】(C)服薬管理専用
│   ├── mapping/           #  ├─ 表示用マッピング（ドメイン識別子を日本語リソースIDや色に変換）
│   ├── preview/           #  ├─ プレビュー用基盤（MockData, PreviewStates）
│   ├── theme/             #  ├─ アプリのテーマ設定（Color, Type, カスタムパレット、セマンティック配色）
│   └── utils/             #  └─ UIユーティリティ（PhoneNumberVisualTransformation 等）
├── viewmodel/             # UI状態管理と実行制御（BaseUiStateViewModel を基盤とする MVI 構造）
├── logic/                 # ドメインロジック（Android API 排除、Pure Kotlin での計算・判定）
│   ├── common/            #  ├─ アプリ全体で再利用可能な共通計算ロジック
│   └── feature/           #  └─ 特定画面に密結合したロジック（UiState / ViewEvent 定義を内包）
├── data/                  # データレイヤー
│   ├── repository/        #  ├─ リポジトリ（データの永続化と監査ログ記録に専念）
│   ├── spec/              #  ├─ アプリの仕様定義（閾値、制約、定数群）
│   ├── Entity.kt          #  ├─ Room エンティティ定義
│   ├── Dao.kt             #  ├─ Room DAO インターフェース
│   ├── AppDatabase.kt     #  ├─ Room + SQLCipher データベース基盤
│   ├── SecuritySession.kt #  ├─ 揮発性セッション状態（自動ロックバイパス等）
│   └── BackupDto.kt       #  └─ バックアップ用データ転送オブジェクト
├── utils/                 # ユーティリティ（DateTime, Image, PDF, ZIP 等の重量処理）
├── MainActivity.kt        # アプリのエントリポイント、セキュリティ統括、NavHost
└── CareMemoApplication.kt # Application クラス、サービスロケーター（Repo / Session 管理）
```

---

# Entity 一覧

- `data/Entity.kt` に定義されているデータベースのテーブル構造です。

| エンティティ名            | テーブル名                   | 概要                                      |
|:-------------------|:------------------------|:----------------------------------------|
| `Person`           | `person_db`             | **利用者基本情報**: 氏名、ふりがな、生年月日、論理削除状態、更新日時。  |
| `HeightAndWeight`  | `height_and_weight_db`  | **身体計測**: 利用者の身長・体重の記録（分単位の一意制約）。       |
| `BpAndPulse`       | `bp_and_pulse_db`       | **バイタル**: 血圧（最高・最低）、脈拍、SAT、体温の記録。       |
| `GlucoseAndHbA1c`  | `glucose_and_hba1c_db`  | **血糖・検査値**: 血糖値および HbA1c の記録。           |
| `ConditionAtVisit` | `condition_at_visit_db` | **所見メモ**: 訪問時の様子や特記事項のテキスト記録（記録者名保持）。   |
| `ConditionPhoto`   | `condition_photo_db`    | **所見写真**: 添付写真のファイル名、サムネイル、撮影日時、キャプション。 |
| `MedicationRecord` | `medication_record_db`  | **服薬記録**: 服用対象日、時間枠（4種）、服用ステータスの記録。     |
| `EmergencyContact` | `emergency_contact_db`  | **緊急連絡先**: 種別、施設名、担当者名、電話番号、優先順位。       |
| `AuditLog`         | `audit_log_db`          | **操作ログ**: 機能名、操作種別、影響ID、実行結果、詳細メッセージ。   |

---

# AppSpecifications 一覧

- `data/spec` 配下および `AppSpecifications.kt` による定数管理構造です。

| ファイル名                               | 役割・主な定義内容                                            |
|:------------------------------------|:-----------------------------------------------------|
| `CalendarSpecifications.kt`         | **和暦仕様**: サポート最小日(1900/1/1)、各元号(昭和・平成・令和)の開始日とオフセット。 |
| `ConstraintSpecifications.kt`       | **物理制約**: 文字数上限、写真枚数(3枚)・サイズ、パスワード長、デベロッパーモード閾値。     |
| `EmergencyContactSpecifications.kt` | **連絡先仕様**: 連絡先種別の定数定義（DOCTOR, FAMILY 等）とデフォルト優先度。    |
| `HealthSpecifications.kt`           | **健康閾値**: 血圧・血糖等の異常判定基準、グラフのY軸刻み幅、描画範囲。              |
| `IdSpecifications.kt`               | **ID仕様**: 新規レコード識別子（"__NEW__"）の定義。                   |
| `MedicationSpecifications.kt`       | **服薬仕様**: 4つの時間枠ラベル、服薬ステータスコード（0:未, 1:介助, 2:服用）。     |
| `PdfExportSpecifications.kt`        | **帳票仕様**: A4レイアウト、印刷用配色、テーブル列幅、フォントサイズ。              |
| `SearchSpecifications.kt`           | **検索仕様**: 五十音インデックスのグループ定義（全、あ...わ、他）。               |
| `SettingsSpecifications.kt`         | **設定選択肢**: 監査ログ保持期間のバリエーション定義。                       |

---

# CareMemo 画面一覧

| 画面ID       | 分類         | 画面名                             | 実装ファイル                                        | 備考                         |
|------------|------------|---------------------------------|-----------------------------------------------|----------------------------|
| SCR-M-001  | Main       | MainScreen                      | `main/MainScreen.kt`                          | 利用者一覧、検索、メニュー              |
| SCR-M-002  | Main       | PersonEditScreen                | `main/PersonEditScreen.kt`                    | 利用者情報の登録・修正                |
| SCR-M-003  | Main       | EmergencyContactListScreen      | `main/EmergencyContactListScreen.kt`          | 緊急連絡先の一覧・削除確認              |
| SCR-M-004  | Main       | EmergencyContactEditScreen      | `main/EmergencyContactEditScreen.kt`          | 連絡先情報の入力、種別選択              |
| SCR-PH-001 | Health     | PersonHealthScreen              | `health/PersonHealthScreen.kt`                | 健康記録（Phone/Tablet/Content） |
| SCR-PH-002 | Health     | BatchInputScreen                | `health/BatchInputScreen.kt`                  | 巡回時等の複数カテゴリ一括入力            |
| SCR-PH-003 | Health     | GraphExpansionScreen            | `health/GraphExpansionScreen.kt`              | グラフの全画面・ランドスケープ表示          |
| SCR-PC-001 | Condition  | PersonConditionScreen           | `condition/PersonConditionScreen.kt`          | 所見メモ（Phone/Tablet/Content） |
| SCR-PC-002 | Condition  | ConditionPhotoPreviewScreen     | `condition/ConditionPhotoPreviewScreen.kt`    | 写真撮影直後の確認・キャプション編集         |
| SCR-PC-003 | Condition  | ConditionPhotoFullScreen        | `condition/ConditionPhotoFullScreen.kt`       | 添付写真の拡大閲覧、カルーセル表示          |
| SCR-PM-001 | Medication | PersonMedicationScreen          | `medication/PersonMedicationScreen.kt`        | 服薬管理（Phone/Tablet/Content） |
| SCR-S-001  | Settings   | SettingsScreen                  | `settings/SettingsScreen.kt`                  | アプリ設定、バックアップ、保守ツール         |
| SCR-S-002  | Settings   | AuditLogScreen                  | `settings/AuditLogScreen.kt`                  | 操作履歴のフィルタ・ソート・詳細参照         |
| SCR-S-003  | Settings   | DeleteOrRestorePerson           | `settings/DeleteOrRestorePerson.kt`           | 利用終了者の復帰・物理削除管理            |
| SCR-S-004  | Settings   | UnassignedPhotoManagementScreen | `settings/UnassignedPhotoManagementScreen.kt` | 孤立した画像ファイルの検出・整理           |

---

# ViewModel 一覧

```text
ViewModel (androidx.lifecycle.ViewModel)
└── BaseUiStateViewModel<S, E> (基盤：UI状態、UIイベント、ロード制御、エラー通知)
    ├── PersonListViewModel (利用者一覧・フィルタリング)
    ├── PersonEditViewModel (利用者登録・変更検知)
    ├── EmergencyContactEditViewModel (緊急連絡先 CRUD)
    ├── SettingsViewModel (アプリ設定、バックアップ、整合性チェック)
    ├── AuditLogViewModel (監査ログ参照)
    ├── DeleteOrRestorePersonViewModel (アーカイブ利用者操作)
    ├── UnassignedPhotoViewModel (孤立写真管理)
    │
    └── PersonBaseUiStateViewModel<S, E> (基盤：利用者コンテキストの自動ロード・同期)
        ├── PersonDetailUiStateViewModel (詳細共通：ヘッダー、カテゴリ遷移)
        ├── PersonHealthViewModel (専門：健康データ管理、PDF連携)
        ├── PersonConditionViewModel (専門：所見メモ、写真連携)
        ├── PersonMedicationViewModel (専門：服薬同期、カレンダー管理)
        └── BatchInputViewModel (専門：複数カテゴリ同時入力管理)
```

---

# Logic - ドメインロジック

ViewModel から計算、判定、変換、およびバリデーションの純粋なアルゴリズムを分離したレイヤーです。

### **ドメイン共通ロジック (logic/common)**
| ファイル名                  | 役割・内容                                 |
|:-----------------------|:--------------------------------------|
| `IdLogic.kt`           | 新規レコード ID 判定の Single Source of Truth。 |
| `JapaneseDateLogic.kt` | 和暦西暦変換、日付妥当性チェック、改元日考慮。               |
| `PhoneLogic.kt`        | 日本の電話番号体系に基づくハイフン挿入位置の算出。             |
| `HealthLogic.kt`       | BMI計算、バイタル・血糖の医学的根拠に基づく異常値判定。         |
| `MedicationLogic.kt`   | カレンダーグリッド生成、DB 差分に基づく同期アクション判定。       |
| `ConditionLogic.kt`    | 所見メモの検索フィルタリング、ID 比較を伴う重複チェック。        |
| `PersonLogic.kt`       | 利用者情報のクレンジング、重複回避用識別子の生成。             |
| `BirthEra.kt`          | 介護現場で扱う元号（昭和・平成・令和）の Enum 定義。         |

### **機能固有ロジック (logic/feature)**
- 各ファイルには、対応する ViewModel の `UiState` および `ViewEvent` の定義が集約されています。
- プロセッサ（`HealthCategoryProcessor` 等）を用いた、拡張性の高いバリデーション・Entity構築ロジックを保持します。

| ファイル名                           | 役割・主な内容                           |
|:--------------------------------|:----------------------------------|
| `PersonListLogic.kt`            | 利用者一覧の五十音判定、フィルタリング、UiState への変換。 |
| `PersonEditLogic.kt`            | 利用者情報の変更検知、保存可否判定、Entity 生成。      |
| `PersonHealthLogic.kt`          | 健康記録のバリデーション、履歴データの管理。            |
| `PersonConditionLogic.kt`       | 所見メモのバリデーション、不整合情報の管理。            |
| `PersonMedicationLogic.kt`      | 服薬履歴の日付別グルーピング。                   |
| `BatchInputLogic.kt`            | 複数カテゴリの一括保存、横断バリデーション。            |
| `DeleteOrRestorePersonLogic.kt` | 利用者アーカイブ管理画面の状態定義。                |
| `SecurityLogic.kt`              | アプリロック要否判定、セキュリティ状態の決定。           |
| `SettingsLogic.kt`              | バックアップ ZIP 検証、バージョン互換性判定。         |
| `AuditLogLogic.kt`              | 操作ログの動的フィルタリングとソート。               |
| `ConditionMaintenanceLogic.kt`  | DB と物理ファイルの照合、孤立写真の検出・分類。         |
| `HealthCategoryProcessor.kt`    | 各健康カテゴリ処理の抽象インターフェース。             |
| `HeightWeightProcessor.kt`      | 身長体重カテゴリ固有のロジック実装。                |
| `VitalProcessor.kt`             | バイタルカテゴリ固有のロジック実装。                |
| `GlucoseProcessor.kt`           | 血糖値カテゴリ固有のロジック実装。                 |
| `HealthProcessorRegistry.kt`    | カテゴリ別プロセッサの集中管理レジストリ。             |
| `PersonDetailLogic.kt`          | 詳細画面共通（ヘッダー・カテゴリ管理）の状態定義。         |

### **表示用マッピング (ui/mapping)**
- ドメイン識別子を表示用リソース ID（R.string）やセマンティックカラーへ変換するレイヤーです。

| ファイル名                        | 役割・主な内容                      |
|:-----------------------------|:-----------------------------|
| `HealthDisplayMapper.kt`     | 健康状態（判定結果）に応じたラベル、色、グラフ配色。   |
| `MedicationDisplayMapper.kt` | 服薬状況に応じた記号（○/△/×）、カラー、ラベル。   |
| `EmergencyContactMapping.kt` | 連絡先種別の名称・アイコン解決、電話番号整形。      |
| `BirthEraDisplayMapper.kt`   | 元号 Enum の表示用リソース ID 解決。      |
| `ThemeDisplayMapper.kt`      | 配色テーマ設定の名称、説明文のリソース ID 解決。   |
| `FeatureNameMapper.kt`       | 監査ログ用の機能名の日本語変換。             |
| `ActionTypeMapper.kt`        | 監査ログ用の操作種別（INSERT 等）の日本語変換。  |
| `ResultTypeMapper.kt`        | 監査ログ用の実行結果（SUCCESS 等）の日本語変換。 |

---

# 基盤ユーティリティ (utils)

Android API を利用する重量級の共通処理をカプセル化しています。

| ユーティリティ名           | 役割                                      |
|:-------------------|:----------------------------------------|
| `DateTimeUtils.kt` | 日付操作、和暦フォーマット、年齢計算、誕生日の UTC 正規化。        |
| `ImageUtils.kt`    | 画像のリサイズ、回転補正、Exif 情報の自動除去、サムネイル生成。      |
| `PdfExporter.kt`   | Canvas 描画による A4 帳票生成、パスワード保護、外部共有連携。    |
| `ZipUtils.kt`      | `Zip4j` を使用したパスワード付きデータバックアップ（AES 暗号化）。 |

---

# 画面遷移一覧（NAV）

- 画面遷移の設計と定義です。
- 遷移の基本ルールについては、`project_RULES.md` の「9. 画面遷移 (NAV) ルール」を参照してください。

## 画面遷移定義

| NAV ID                 | 遷移元画面ID    | 遷移先画面ID    | 操作                                                 | 期待結果                                        |
|:-----------------------|:-----------|:-----------|:---------------------------------------------------|:--------------------------------------------|
| **[Main: 利用者管理]**      |            |            |                                                    |                                             |
| **NAV-M-001**          | SCR-M-001  | SCR-M-002  | 利用者一覧でFABをタップ。                                     | 利用者登録画面が表示される。                              |
| **NAV-M-002**          | SCR-M-001  | SCR-M-002  | 利用者一覧で鉛筆アイコンをタップし、「利用者情報を編集」を選択。                   | 利用者編集画面が表示される。                              |
| **NAV-M-003**          | SCR-M-002  | SCR-M-001  | 登録・編集画面で「保存」に成功。                                   | 利用者一覧画面に戻り、情報が更新される。                        |
| **NAV-M-004**          | SCR-M-001  | SCR-PH-002 | 利用者を選択し、メニューの「健康記録の一括入力」をタップ。                      | 健康記録一括入力画面が表示される。                           |
| **NAV-M-005**          | SCR-PH-002 | SCR-M-001  | 一括入力画面で「保存」に成功。                                    | 利用者一覧画面に戻る。                                 |
| **NAV-M-006**          | SCR-M-001  | SCR-M-003  | 利用者一覧で鉛筆アイコンをタップし、「連絡先の管理・編集」を選択。                  | 緊急連絡先管理画面が表示される。                            |
| **NAV-M-007**          | SCR-M-003  | SCR-M-004  | 緊急連絡先管理画面でFABをタップ。                                 | 緊急連絡先の登録画面が表示される。                           |
| **NAV-M-008**          | SCR-M-004  | SCR-M-003  | 緊急連絡先の登録で「保存」に成功。                                  | 管理画面に戻り、情報が更新される。                           |
| **[Health: 健康記録]**     |            |            |                                                    |                                             |
| **NAV-PH-001**         | SCR-M-001  | SCR-PH-001 | 利用者をタップし、メニューの「身長・体重」「バイタル」「血糖値・HbA1c」のいずれかをタップ。   | 健康管理画面（各カテゴリ）が表示される。                        |
| **NAV-PH-002**         | SCR-PH-001 | SCR-PH-003 | グラフ表示エリア、またはグラフ拡大アイコンをタップ。                         | グラフ拡大表示画面が表示される。                            |
| **[Condition: 所見メモ]**  |            |            |                                                    |                                             |
| **NAV-PC-001**         | SCR-M-001  | SCR-PC-001 | 利用者を選択し、メニューの「所見メモ」をタップ。                           | 所見メモ画面が表示される。                               |
| **NAV-PC-002**         | SCR-PC-001 | SCR-PC-002 | 所見メモの一つをタップしたときに表示される「記録の詳細」画面でカメラアイコンをタップし、写真を撮影。 | 写真プレビュー画面が表示される。                            |
| **NAV-PC-003**         | SCR-PC-002 | SCR-PC-001 | プレビュー画面で「保存」に成功。                                   | 記録の詳細画面に戻り、写真が保存される(サムネイルが追加される)。           |
| **NAV-PC-004**         | SCR-PC-001 | SCR-PC-003 | 保存済みの写真サムネイルをタップ。                                  | 写真全画面表示画面が表示される。                            |
| **[Medication: 服薬管理]** |            |            |                                                    |                                             |
| **NAV-PM-001**         | SCR-M-001  | SCR-PM-001 | 利用者を選択し、メニューの「服薬管理」をタップ。                           | 服薬管理画面が表示される。                               |
| **[Settings: 設定・ログ]**  |            |            |                                                    |                                             |
| **NAV-S-001**          | SCR-M-001  | SCR-S-001  | ハンバーガーメニューから「設定」をタップ。                              | 設定画面が表示される。                                 |
| **NAV-S-002**          | SCR-S-001  | SCR-S-002  | 設定画面で隠しメニューの「操作ログを参照」をタップ.                         | 監査ログ画面が表示される。                               |
| **NAV-S-003**          | SCR-S-001  | SCR-S-003  | 設定画面で「利用終了者の復帰」または「利用修了者の完全抹消」をタップ.                | タップされたメニューに応じて利用者管理（復帰・完全抹消のいずれか）画面が表示される。  |
| **NAV-S-004**          | SCR-S-001  | SCR-S-004  | 設定画面で「未割り当て写真の確認」をタップ.                             | 未割り当て写真確認画面が表示される。                          |
| **[Common: 詳細画面間遷移]**  |            |            |                                                    |                                             |
| **NAV-COM-001**        | SCR-PH-001 | SCR-PC-001 | 詳細画面のカテゴリバーで「所見メモ」をタップ.                            | 所見メモ画面に切り替わる（スタックは積まない）。                    |
| **NAV-COM-002**        | SCR-PC-001 | SCR-PM-001 | 詳細画面のカテゴリバーで「服薬確認」をタップ.                            | 服薬管理画面に切り替わる（スタックは積まない）。                    |
| **NAV-COM-003**        | SCR-PM-001 | SCR-PH-001 | 詳細画面のカテゴリバーで「身長・体重」「バイタル」「血糖値・HbA1c」のいずれかをタップ.     | タップされたカテゴリに応じ、そのカテゴリの履歴表示に切り替わる（スタックは積まない）。 |

---

最終更新日: 2026/08/21
