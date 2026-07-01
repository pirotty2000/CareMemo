# CareMemo プロジェクト構造定義書

このドキュメントでは、CareMemo プロジェクトのソースコード構造とその役割について説明します。仕様変更や不具合修正の際に対象ファイルを探すガイドとして利用してください。

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

# Repository - ViewModel - Screen 依存関係

各機能層における垂直方向の依存関係の概要です。

| 分類 | 画面 (Screen) | 主要コンポーネント (Components) | ViewModel | 主要Repository |
| :--- | :--- | :--- | :--- | :--- |
| 利用者一覧 | `MainScreen` | `CategoryBadges` | `PersonListViewModel` | `PersonRepository`<br>`ArchivedPersonRepository`<br>`PersonSummaryRepository` |
| (A) 健康記録 | `PersonHealthScreen` | `HealthRecordDetailPane`<br>`HealthGraphView`<br>`PersonHistoryList` | `PersonHealthViewModel` | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| (B) 所見メモ | `PersonConditionScreen` | `ConditionDetailPane`<br>`ObservationList`<br>`PersonHistoryList` | `PersonConditionViewModel` | `ConditionRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| (C) 服薬管理 | `PersonMedicationScreen` | `CalendarGrid`<br>`MedicationHistoryTable` | `PersonMedicationViewModel` | `MedicationRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| 健康一括入力 | `BatchInputScreen` | `CompactTextField`<br>`DateTimeInputFields` | `BatchInputViewModel` | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository` |
| アーカイブ管理 | `DeletedUserListScreen` | `PersonHistoryList` | `ArchivedPersonViewModel` | `ArchivedPersonRepository` |
| アプリ設定 | `SettingsScreen` | `PdfExportActionHandler` | `SettingsViewModel` | `AppMaintenanceRepository`<br>`ArchivedPersonRepository` |
| 共通基盤 | (詳細画面全体) | `PersonHeaderTitle`<br>`CategorySelectorBar` | `PersonDetailViewModel` | `PersonRepository`<br>`PersonSummaryRepository` |

# 各ディレクトリの詳細

## 1. `ui/screens/`
各機能の「画面」となるComposable関数が配置されています。Navigationによる遷移の単位です。

### 1.1. 利用者一覧・共通入力：`ui/screens/`
- `MainScreen.kt`: アプリの起点。アクティブな利用者の一覧表示、検索、新規登録を行う。
- `BatchInputScreen.kt`: (A)系統の数値を一括入力するための専用画面。連続入力に最適化。

### 1.2. 利用者詳細画面：`ui/screens/detail/`
利用者の「個人ID」に紐づく詳細データを扱う画面群です。デバイスサイズ（Phone/Tablet）に応じて最適なレイアウトを選択します。

#### 1.2.1. (A) 健康記録：`PersonHealthScreen*.kt`
身長・体重、バイタル、血糖値を扱う。数値データの推移（グラフ）と履歴リストを表示。
- `PersonHealthScreen.kt`: 入り口。WindowSize判定を行いPhone/Tabletへ分岐。
- `PersonHealthScreenPhone.kt`: スマホ向け（シングルペイン）。
- `PersonHealthScreenTablet.kt`: タブレット向け（2ペイン：リストと入力・グラフ）。
- `PersonHealthScreenContent.kt`: Phone/Tabletで共有される表示コンテンツ定義。

#### 1.2.2. (B) 所見メモ：`PersonConditionScreen*.kt`
自由記述のテキストと写真を扱う。全文検索機能を備える。
- `PersonConditionScreen.kt`: 入り口。
- `PersonConditionScreenPhone.kt` / `Tablet.kt` / `Content.kt`: レスポンシブ対応。

#### 1.2.3. (C) 服薬管理：`PersonMedicationScreen*.kt`
カレンダー形式で日々の服薬状況（朝・昼・夕・寝る前等）を管理。
- `PersonMedicationScreen.kt` / `Phone.kt` / `Tablet.kt` / `Content.kt`: レスポンシブ対応。

#### 1.2.4. サブ画面：`ui/screens/detail/sub/`
- `GraphExpansionScreen.kt`: (A)のグラフを横画面で拡大表示。
- `ConditionPhotoFullScreen.kt`: (B)の写真を全画面表示。
- `ConditionPhotoPreviewScreen.kt`: (B)の写真撮影後の確認・キャプション入力。

## 2. `ui/components/`
複数の画面で共有される部品です。「基盤部品」と「ドメイン特化部品」に分かれます。

### 2.1. 共通・基盤部品
- `PersonHistoryList`: 履歴表示の基盤。日付グループ化、スワイプ削除、選択状態の背景管理を行う。中身のComposableは呼び出し側から注入する。
- `CategorySelectorBar.kt`: 詳細画面上部で(A)(B)(C)を切り替えるバー。
- `CompactTextField.kt` / `DateTimeInputFields.kt`: 共通の入力部品。
- `PersonHeaderTitle.kt`: 利用者の基本情報を表示する共通ヘッダー。
- `PdfExportActionHandler.kt`: PDF生成・共有の共通ロジック部品。

### 2.2. ドメイン特化部品
- `PersonHealthComponents.kt`: (A)数値系の表示アイテム、詳細編集ペイン。
- `ConditionComponents.kt`: (B)所見メモの表示アイテム、詳細編集ペイン、検索ボックス。
- `MedicationComponents.kt`: (C)服薬カレンダー、履歴テーブル、入力ダイアログ。
- `LineChart.kt` / `HealthGraphView.kt`: (A)専用のグラフ描画部品。

## 3. `viewmodel/`
UIの状態（State）を保持し、Repositoryと通信します。

### 3.1. 基底クラス
- `BaseViewModel.kt`: UI通知（Snackbar/Dialog）や共通設定（伏せ字）を保持。
- `PersonBaseViewModel.kt`: 詳細画面の基盤。特定の利用者情報を保持・管理。

### 3.2. 専門ViewModel
- `PersonListViewModel.kt`: メイン画面の検索・フィルタリング・利用者管理。
- `PersonDetailViewModel.kt`: 詳細画面全体のフレームワーク（カテゴリ切り替え）を管理。
- `PersonHealthViewModel.kt` / `PersonConditionViewModel.kt` / `PersonMedicationViewModel.kt`: 各ドメイン専用のロジック。
- `ArchivedPersonViewModel.kt`: アーカイブされた利用者の管理。
- `SettingsViewModel.kt` / `BatchInputViewModel.kt`: 各設定・入力画面専用。

## 4. `data/`
データの永続化を担います。

### 4.1. リポジトリ (Data Access Layer)
`PersonRepository` を専門分野ごとに 4 分割し、保守性を高めています。
- `repository/PersonRepository.kt`: アクティブな利用者の基本情報を管理。
- `repository/ArchivedPersonRepository.kt`: 利用終了者の論理削除、復元、物理削除を管理。
- `repository/PersonSummaryRepository.kt`: 全カテゴリを横断した記録有無のサマリー集計。
- `repository/AppMaintenanceRepository.kt`: バックアップ、リストア、全消去等のシステム操作。
- `repository/HealthRepository.kt` / `ConditionRepository.kt` / `MedicationRepository.kt`: 各ドメインデータのCRUD。

## 5. `utils/`
特定のビジネスドメインに依存しない共通処理です。
- `DateTimeUtils.kt`: 日時表記の統一。
- `PdfExporter.kt`: PDFレポート生成。
- `ImageUtils.kt`: 写真のリサイズ・保存・削除。
- `ZipUtils.kt`: バックアップ用のパスワード付きZIP圧縮・展開。

---
最終更新日: 2025/02/09
