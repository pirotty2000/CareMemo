# CareMemo プロジェクト構造定義書

このドキュメントでは、CareMemo プロジェクトのソースコード構造とその役割について説明します。仕様変更や不具合修正の際に対象ファイルを探すガイドとして利用してください。

# 設計思想

CareMemoでは画面をデータベーステーブル単位ではなく、「画面の振る舞い」で分類する。

- (A) 数値時系列
- (B) テキスト時系列
- (C) 服薬マトリックス

新しい機能追加時は、まず既存分類へ当てはまるかを検討し、当てはまらない場合のみ新しい分類を追加する。


# 共通基盤

以下は全画面で利用されるため、変更時は影響範囲を十分確認すること。

- PersonHeaderTitle
- CategorySelectorBar
- DateTimeInputFields
- PersonHistoryList


# 共通化の原則

- 同一仕様が今後も維持されるもののみ共通化する。
- 将来仕様が分かれそうなものは、重複を許容してもカテゴリ毎に保持する。
- 共通化はコード量削減ではなく、保守性向上を目的とする。

# パッケージ構造の概要

プロジェクトの主要なコードは `jp.mydns.fujiwara.carememo` パッケージ配下に配置されています。

```text
jp.mydns.fujiwara.carememo
├── ui/              # UIレイヤー（Jetpack Compose）
│   ├── screens/     # 利用者一覧・一括入力・設定・アーカイブのComposable
│   │   └─ detail/   # 利用者詳細画面（健康記録、所見、服薬）のComposable
│   │      └─sub/    # グラフ拡大、写真プレビュー等のサブ画面
│   ├── components/  # 再利用可能なUIコンポーネント（基盤部品とドメイン部品）
│   └── theme/       # アプリのテーマ設定（Color, Type, Shapeなど）
├── viewmodel/       # ビジネスロジックと状態管理（MVVM）
├── data/            # データレイヤー（Room Database）
│   └── repository/  # リポジトリ（データ操作とドメインロジックの隔離）
├── utils/           # ユーティリティ（日時操作、PDF生成、画像処理、ZIP圧縮）
├── MainActivity.kt  # アプリのエントリポイント、NavHostによる画面遷移定義
└── CareMemoApplication.kt # Application クラス、リポジトリのDI（依存注入）管理
```

# 詳細画面の設計原則 (A/B/C共通)

利用者詳細に紐づく主要3カテゴリ（健康記録・所見メモ・服薬管理）は、以下の原則に基づき構造が統一されています。

### 1. 構成ファイルの統一 (4ファイル構成)
各カテゴリは、役割を分離した4つのファイルで構成されます。
- `*Screen.kt`: 画面のエントリポイント。WindowSize判定と各ViewModelの管理を行う。
- `*ScreenPhone.kt`: スマートフォン用レイアウト。シングルペイン構成。
- `*ScreenTablet.kt`: タブレット用レイアウト。2ペイン構成。
- `*ScreenContent.kt`: Phone/Tabletで共有されるコアな表示・入力ロジックの定義。

### 2. ViewModel の二段構え (Dual-ViewModel)
すべての詳細画面 Composable は、以下の2つの ViewModel を併用します。
- **`PersonDetailViewModel`**: 詳細画面全体の共通フレームワーク（利用者基本情報の保持、カテゴリ切り替え、共通UIイベント等）を担当。
- **専門 ViewModel**: 各カテゴリ固有のデータ操作（CRUD）とビジネスロジックを担当。
  - (A) `PersonHealthViewModel`
  - (B) `PersonConditionViewModel`
  - (C) `PersonMedicationViewModel`

# Repository - ViewModel - Screen 依存関係

各機能層における垂直方向の依存関係の概要です。

| 分類 | 画面 (Screen) | 主要コンポーネント (関数/ファイル名) | ViewModel | 主要Repository |
| :--- | :--- | :--- | :--- | :--- |
| 利用者一覧 | `MainScreen` | `CategoryBadges()`<br>`SearchBar()` <br> (CategoryBadges.kt) | `PersonListViewModel` | `PersonRepository`<br>`ArchivedPersonRepository`<br>`PersonSummaryRepository` |
| (A) 健康記録 | `PersonHealthScreen` | `HealthRecordDetailPane()`<br>`PersonHistoryList()` <br> (PersonHealthComponents.kt)<br>`HealthGraphView()` <br> (HealthGraphView.kt) | `PersonHealthViewModel`<br>`PersonDetailViewModel` | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| (B) 所見メモ | `PersonConditionScreen` | `ConditionDetailPane()`<br>`ObservationList()`<br>`SearchBox()` <br> (ConditionComponents.kt) | `PersonConditionViewModel`<br>`PersonDetailViewModel` | `ConditionRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| (C) 服薬管理 | `PersonMedicationScreen` | `CalendarGrid()`<br>`MedicationHistoryTable()`<br>`MedicationInputDialog()` <br> (MedicationComponents.kt) | `PersonMedicationViewModel`<br>`PersonDetailViewModel` | `MedicationRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| 健康一括入力 | `BatchInputScreen` | `DateTimeInputFields()` <br> (DateTimeInputFields.kt)<br>`VerticalScrollIndicator()` <br> (PersonHealthComponents.kt) | `BatchInputViewModel` | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| アーカイブ管理 | `DeletedUserListScreen` | `LazyColumn` (Compose公式)<br>`ListItem` (Compose公式) | `ArchivedPersonViewModel` | `ArchivedPersonRepository` |
| アプリ設定 | `SettingsScreen` | `SettingsSection()` <br> (共通UI部品無し/Screen内定義)<br>バックアップ・リストア処理 | `SettingsViewModel` | `AppMaintenanceRepository`<br>`ArchivedPersonRepository`<br>`UserSettingsRepository` |
| 共通基盤 | (詳細画面全体) | `PersonHeaderTitle()` <br> (PersonHeaderTitle.kt)<br>`CategorySelectorBar()` <br> (CategorySelectorBar.kt)<br>`PdfExportActionHandler()` <br> (PdfExportActionHandler.kt) | `PersonDetailViewModel` | `PersonRepository`<br>`PersonSummaryRepository` |

# ディレクトリ詳細

## 1. `ui/screens/detail/`
利用者の「個人ID」に紐づく詳細データを扱う画面群です。設計原則に基づき統一されたUI/UXを提供します。

- **(A) 健康記録**: 身長・体重、バイタル、血糖値を扱う。数値推移の可視化に特化。
- **(B) 所見メモ**: 自由記述のテキストと写真を扱う。全文検索機能を備える。
- **(C) 服薬管理**: カレンダー形式で日々の服薬状況（時間枠ごと）を管理。

### サブ画面：`ui/screens/detail/sub/`
- `GraphExpansionScreen.kt`: (A)のグラフ拡大。
- `ConditionPhotoFullScreen.kt`: (B)の写真全画面表示。
- `ConditionPhotoPreviewScreen.kt`: (B)の写真撮影後のキャプション入力。

## 2. `ui/components/`
### 2.1. 共通・基盤部品
- `PersonHeaderTitle.kt`: 利用者の氏名、年齢等を表示する全カテゴリ共通の最上部ヘッダー。
- `CategorySelectorBar.kt`: 詳細画面上部で(A)(B)(C)を切り替えるバー。
- `PersonHistoryList`: 履歴表示の基盤。日付グループ化、スワイプ削除等の基本機能を備える汎用部品。
- `DateTimeInputFields.kt`: `rememberDateTimeInputState` により、不連続な入力やブランキングを抑制する共通日時入力部品。

### 2.2. ドメイン特化部品
- `PersonHealthComponents.kt`: (A)専用の表示アイテム、詳細編集ペイン。
- `ConditionComponents.kt`: (B)専用の表示アイテム、詳細編集ペイン、検索ボックス。
- `MedicationComponents.kt`: (C)専用のカレンダー、履歴テーブル、入力ダイアログ。

## 3. `viewmodel/`
### 3.1. 基底クラス
- `BaseViewModel.kt`: UI通知（Snackbar/Dialog）や共通設定を保持。
- `PersonBaseViewModel.kt`: 利用者情報の保持・ロードを担う、詳細画面系 ViewModel の共通基底クラス。

# 実装上の重要指針（保守ガイド）

不具合修正や機能追加の際は、以下の指針を遵守してください。

### 1. UI状態の初期化とブランキング抑制
詳細画面や編集フォームにおいて、IDに基づいてデータを表示・編集する場合、状態変数の初期化に `LaunchedEffect` を使用しないでください。
- **推奨**: `val text = remember(id) { mutableStateOf(record?.field ?: "") }`
- **理由**: `LaunchedEffect` による初期化は非同期で行われるため、1フレーム目に空の値が表示され、2フレーム目に実際の値が表示される「ブランキング（チラつき）」の原因となります。

### 2. リストデータの安定化
ViewModel から取得したリストを Composable 内で加工（フィルタリング等）して子コンポーネントに渡す際は、`remember(records)` でリストインスタンスを安定化させてください。
- **理由**: 再コンポーズのたびに新しいリストインスタンスが生成されると、子コンポーネントの不要な再計算やスクロール位置のリセットを引き起こします。


# 実装ガイド

- 影響範囲を最小化すること。
- 既存UI/UXを維持すること。
- 共通部品は可能な限り変更しないこと。
- 新しいComposableは責務毎に追加すること。
- 不要なリファクタリングは行わないこと。

---
最終更新日: 2025/02/10
