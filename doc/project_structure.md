# CareMemo プロジェクト構造定義書

このドキュメントでは、CareMemo プロジェクトのソースコード構造とその役割について説明します。仕様変更や不具合修正の際に対対象ファイルを探すガイドとして利用してください。

# 設計思想

CareMemoでは画面をデータベーステーブル単位ではなく、「画面の振る舞い」で分類する。

- (A) 数値時系列
- (B) テキスト時系列
- (C) 服薬マトリックス

新しい機能追加時は、まず既存分類へ当てはまるかを検討し、当てはまらない場合のみ新しい分類を追加する。


# 共通基盤

以下は全画面で共通利用される基盤部品であるため、変更時は影響範囲を十分に確認すること。

- `PersonHeaderTitle.kt`: 利用者情報ヘッダー
- `CategorySelectorBar.kt`: カテゴリ切替バー
- `LoadingScreen.kt`: 共通ローディング表示
- `SearchBox.kt`: 共通検索バー
- `InfoDialog.kt`: 共通通知ダイアログ
- `DeleteConfirmDialog.kt`: 共通削除確認ダイアログ
- `AppTopAppBarColors.kt`: トップバー共通配色設定
- `DateTimeInputFields.kt`: 共通日時入力
- `BirthdayInputFields.kt`: 共通生年月日入力（和暦対応）
- `KanaIndexBar.kt`: 五十音インデックス
- `EmptyState.kt`: 共通空状態表示
- `VerticalScrollIndicator.kt`: 垂直スクロール補助


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

各機能層における垂直方向の依存関係の概要です。コンポーネントを修正する際は、右記の「使用コンポーネント」欄を参照して影響範囲を確認してください。

| 分類       | 画面 (Screen)              | 使用コンポーネント (ファイル名)                                                                                                                                                                                                                                                                                                                   | ViewModel                                              | 主要Repository                                                                         |
|:---------|:-------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------|:-------------------------------------------------------------------------------------|
| 利用者一覧    | `MainScreen`             | `SearchBox.kt`<br>`KanaIndexBar.kt`<br>`CategoryBadges.kt`<br>`BirthdayInputFields.kt`<br>`LoadingScreen.kt`<br>`EmptyState.kt`<br>`InfoDialog.kt`<br>`AppTopAppBarColors.kt`<br>`CompactTextField.kt`                                                                                                                              | `PersonListViewModel`                                  | `PersonRepository`<br>`ArchivedPersonRepository`<br>`PersonSummaryRepository`        |
| (A) 健康記録 | `PersonHealthScreen`     | `PersonHeaderTitle.kt`<br>`CategorySelectorBar.kt`<br>`PdfExportActionHandler.kt`<br>`LoadingScreen.kt`<br>`EmptyState.kt`<br>`AppTopAppBarColors.kt`<br>`----------`<br>`PersonHealthComponents.kt`<br>`----------`<br>`VerticalScrollIndicator.kt`<br>`HealthGraphView.kt`<br>`InfoDialog.kt`<br>`DeleteConfirmDialog.kt`                           | `PersonHealthViewModel`<br>`PersonDetailViewModel`     | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                |
| (B) 所見メモ | `PersonConditionScreen`  | `PersonHeaderTitle.kt`<br>`CategorySelectorBar.kt`<br>`PdfExportActionHandler.kt`<br>`LoadingScreen.kt`<br>`EmptyState.kt`<br>`AppTopAppBarColors.kt`<br>`----------`<br>`ConditionComponents.kt`<br>`----------`<br>`SearchBox.kt`<br>`InfoDialog.kt`<br>`DeleteConfirmDialog.kt`                                                                                  | `PersonConditionViewModel`<br>`PersonDetailViewModel`  | `ConditionRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`             |
| (C) 服薬管理 | `PersonMedicationScreen` | `PersonHeaderTitle.kt`<br>`CategorySelectorBar.kt`<br>`PdfExportActionHandler.kt`<br>`LoadingScreen.kt`<br>`EmptyState.kt`<br>`AppTopAppBarColors.kt`<br>`----------`<br>`MedicationComponents.kt`<br>`----------`<br>`VerticalScrollIndicator.kt`<br>`DateTimeInputFields.kt`                                                                                      | `PersonMedicationViewModel`<br>`PersonDetailViewModel` | `MedicationRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`            |
| 健康一括入力   | `BatchInputScreen`       | `PersonHeaderTitle.kt`<br>`DateTimeInputFields.kt`<br>`LoadingScreen.kt`<br>`VerticalScrollIndicator.kt`<br>`AppTopAppBarColors.kt`                                                                                                                                                                                                 | `BatchInputViewModel`                                  | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                |
| アーカイブ管理  | `DeletedUserListScreen`  | `EmptyState.kt`<br>`VerticalScrollIndicator.kt`<br>`AppTopAppBarColors.kt`                                                                                                                                                                                                                                                          | `ArchivedPersonViewModel`                              | `ArchivedPersonRepository`                                                           |
| アプリ設定    | `SettingsScreen`         | `InfoDialog.kt`<br>`DeleteConfirmDialog.kt`<br>`VerticalScrollIndicator.kt`<br>`AppTopAppBarColors.kt`                                                                                                                                                                                                                              | `SettingsViewModel`                                    | `AppMaintenanceRepository`<br>`ArchivedPersonRepository`<br>`UserSettingsRepository` |
| 共通基盤     | (詳細画面全体)                 | `PersonHeaderTitle.kt`<br>`CategorySelectorBar.kt`<br>`PdfExportActionHandler.kt`<br>`LoadingScreen.kt`<br>`SearchBox.kt`<br>`InfoDialog.kt`<br>`DeleteConfirmDialog.kt`<br>`AppTopAppBarColors.kt`<br>`DateTimeInputFields.kt`<br>`BirthdayInputFields.kt`<br>`KanaIndexBar.kt`<br>`EmptyState.kt`<br>`VerticalScrollIndicator.kt` | `PersonDetailViewModel`                                | `PersonRepository`<br>`PersonSummaryRepository`                                      |

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
- `LoadingScreen.kt`: プロジェクト共通のローディング表示UI。
- `SearchBox.kt`: `OutlinedTextField` をベースとしたプロジェクト共通検索バー。
- `InfoDialog.kt`: タイトルとメッセージを表示して閉じるだけの共通通知ダイアログ。
- `DeleteConfirmDialog.kt`: 破壊的アクションを警告する共通削除確認ダイアログ。
- `AppTopAppBarColors.kt`: プロジェクト標準の TopAppBar 配色を一括管理する設定。
- `DateTimeInputFields.kt`: `rememberDateTimeInputState` により日時を扱う共通入力部品。
- `BirthdayInputFields.kt`: 和暦選択とバリデーションを備えた生年月日専用入力部品。
- `KanaIndexBar.kt`: 五十音による絞り込みを行う水平スクロールバー。
- `EmptyState.kt`: 記録なし等の「空状態」をアイコン付きで表示する共通コンポーネント。
- `VerticalScrollIndicator.kt`: 画面が縦に長い場合にのみ出現するスクロール補助（`ScrollState` および `LazyListState` の両方に対応）。

### 2.2. ドメイン特化部品
- `PersonHealthComponents.kt`: (A)専用の表示アイテム、詳細編集ペイン、および履歴リスト基盤 (`PersonHistoryList`)。
- `ConditionComponents.kt`: (B)専用の表示アイテム、詳細編集ペイン。
- `MedicationComponents.kt`: (C)専用のカレンダー、履歴テーブル、入力ダイアログ。

# 実装上の重要指針（保守ガイド）

不具合修正や機能追加の際は、以下の指針を遵守してください。

### 1. UI状態の初期化とブランキング抑制
詳細画面や編集フォームにおいて、IDに基づいてデータを表示・編集する場合、状態変数の初期化に `LaunchedEffect` を使用しないでください。
- **推奨**: `val text = remember(id) { mutableStateOf(record?.field ?: "") }`
- **理由**: `LaunchedEffect` による初期化は非同期で行われるため、1フレーム目に空の値が表示され、2フレーム目に実際の値が表示される「ブランキング（チラつき）」の原因となります。

### 2. リストデータの安定化
ViewModel から取得したリストを Composable 内で加工（フィルタリング等）して子コンポーネントに渡す際は、`remember(records)` でリストインスタンスを安定化させてください。
- **理由**: 再コンポーズのたびに新しいリストインスタンスが生成されると、子コンポーネントの不要な再計算やスクロール位置のリセットを引き起こします。

### 3. 利用者コンテキストの確実な切り替え
異なる利用者間で画面を遷移する際、前の利用者のデータが残存したり、非同期処理のタイミングで上書きされたりするのを防ぐため、以下の実装を徹底してください。
- **即時クリア**: `loadPerson(id)` の開始時に `_currentPerson.value = null` をセットし、古いデータを明示的に「忘れる」こと。
- **ジョブの排他制御**: データのロード（Roomの監視等）を行うコルーチンは `Job` 変数で管理し、新しいロードを開始する前に必ず `cancel()` すること。
- **個別状態のリセット**: 専門 ViewModel では `loadPerson` をオーバーライドし、検索クエリや選択中の日付などの「利用者固有の状態変数」を初期値に戻すこと。
- **ナビゲーションの注意**: 利用者が切り替わる遷移（一覧 → 詳細、カテゴリ間遷移）では、`NavHost` の `restoreState = true` を使用しないこと（ナビゲーションシステムによる不適切な状態復元を防ぐため）。


# 実装ガイド

- 影響範囲を最小化すること。
- 既存UI/UXを維持すること。
- 共通部品は可能な限り変更しないこと。
- 新しいComposableは責務毎に追加すること。
- 不要なリファクタリングは行わないこと。
- Material3の標準的な扱いに極力準拠すること。必要な場面で準拠されている実装から逸脱する場合は、その旨を明示して修正の是非の判断を求めること。

---
最終更新日: 2025/02/11
