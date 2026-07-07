# CareMemo プロジェクト構造定義書

このドキュメントでは、CareMemo プロジェクトのソースコード構造とその役割について説明します。仕様変更や不具合修正の際に対象ファイルを探すガイドとして利用してください。

# ドキュメント運用方針

- このドキュメントは CareMemo の設計上の唯一の基準（Single Source of Truth）とする。
- 設計変更や共通部品の追加・削除を行った場合は、実装と合わせて本ドキュメントも更新すること。
- 実装とドキュメントに差異が生じた場合は、ドキュメントを優先して見直し、必要に応じて実装またはドキュメントを修正すること。


# 設計思想

CareMemoでは画面をデータベーステーブル単位ではなく、「画面の振る舞い」で分類する。

- (A) 数値時系列：血圧、体重、血糖値など、推移をグラフ化するもの。
- (B) テキスト時系列：所見メモ、写真など、出来事を記述するもの。
- (C) 服薬マトリックス：日付×時間枠（朝・昼・夕・寝る前）の状況を管理するもの。

新しい機能追加時は、まず既存分類へ当てはまるかを検討し、当てはまらない場合のみ新しい分類を追加する。


# 共通部品の扱い

本プロジェクトでは、複数の画面で共有される部品を「共通部品」として管理しています。
共通部品を変更する際は、影響範囲が広いため、必ず以下の手順で確認を行ってください。

1. **影響範囲の特定**: [Components - Screen 逆引きリファレンス](#components---screen-逆引きリファレンス) を参照し、🔴マークが付いている部品か、どの画面で使用されているかを確認すること。
2. **動作確認**: チェックがついているすべての画面において、表示崩れやデグレードが発生していないか確認すること。


# パッケージ構造の概要

プロジェクトの主要なコードは `jp.mydns.fujiwara.carememo` パッケージ配下に配置されています。

```text
jp.mydns.fujiwara.carememo
├── ui/                 # UIレイヤー（Jetpack Compose）
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
│   └── theme/          #  └─ アプリのテーマ設定（Color, Type, Shapeなど）
├── viewmodel/          # ビジネスロジックと状態管理（MVVM）
├── data/               # データレイヤー（Room Database / Repository / AppThresholds）
│   └── repository/     #  └─ リポジトリ（データ操作とドメインロジックの隔離）
├── utils/              # ユーティリティ（日時操作、PDF生成、画像処理、ZIP圧縮）
├── MainActivity.kt     # アプリのエントリポイント、NavHostによる画面遷移定義
└── CareMemoApplication.kt # Application クラス、リポジトリのDI（依存注入）管理
```

# 詳細画面の設計原則 (A/B/C共通)

利用者詳細に紐づく主要3カテゴリ（健康記録・所見メモ・服薬管理）は、以下の原則に基づき構造が統一されています。

### 1. 構成ファイルの統一 (4ファイル構成)
各カテゴリは、原則として役割を分離した4つのファイルで構成されます（MainScreen等、現時点でマルチペインが不要な画面は Tablet/Phone を分けない場合がありますが、Content の分離は共通ルールとします）。
- `*Screen.kt`: 画面のエントリポイント。WindowSize判定と各ViewModelの管理、および**画面遷移（遷移ロジック）**を担当。
- `*ScreenPhone.kt`: スマートフォン用レイアウト。シングルペイン構成。
- `*ScreenTablet.kt`: タブレット用レイアウト。2ペイン構成。
- `*ScreenContent.kt`: Phone/Tabletで共有されるコアな表示・入力ロジックの定義（Stateless）。

### 2. ViewModel の二段構え (Dual-ViewModel)
すべての詳細画面 Composable は、以下の2つの ViewModel を併用します。
- **`PersonDetailViewModel`**: 詳細画面全体の共通フレームワーク（利用者基本情報の保持、カテゴリ切り替え、共通UIイベント等）を担当。
- **専門 ViewModel**: 各カテゴリ固有のデータ操作（CRUD）とビジネスロジックを担当。
  - (A) `PersonHealthViewModel`
  - (B) `PersonConditionViewModel`
  - (C) `PersonMedicationViewModel`
- **ViewModel間の連携**: `PersonDetailViewModel` が保持する `personId` を、Screen(Composable) 経由で専門 ViewModel の `loadPerson(id)` へ伝達し、利用者コンテキストの同期とデータロードを開始します。

# Repository - ViewModel - Screen 依存関係

各機能層におけるロジックの垂直方向の依存関係です。

| 分類       | 画面 (Screen)              | ViewModel                                              | 主要Repository                                                                                |
|:---------|:-------------------------|:-------------------------------------------------------|:--------------------------------------------------------------------------------------------|
| 利用者一覧    | `MainScreen`<br>`PersonEditScreen` | `PersonListViewModel`<br>`PersonEditViewModel` | `PersonRepository`<br>`DeleteOrRestorePersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository` |
| (A) 健康記録 | `PersonHealthScreen`     | `PersonHealthViewModel`<br>`PersonDetailViewModel`     | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                       |
| (B) 所見メモ | `PersonConditionScreen`  | `PersonConditionViewModel`<br>`PersonDetailViewModel`  | `ConditionRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                    |
| (C) 服薬管理 | `PersonMedicationScreen` | `PersonMedicationViewModel`<br>`PersonDetailViewModel` | `MedicationRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                   |
| 健康一括入力   | `BatchInputScreen`       | `BatchInputViewModel`                                  | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                       |
| 利用者管理    | `DeleteOrRestorePerson`  | `DeleteOrRestorePersonViewModel`                       | `DeleteOrRestorePersonRepository`                                                           |
| アプリ設定    | `SettingsScreen`         | `SettingsViewModel`                                    | `AppMaintenanceRepository`<br>`DeleteOrRestorePersonRepository`<br>`UserSettingsRepository` |
| 共通基盤     | (詳細画面全体)                 | `PersonDetailViewModel`                                | `PersonRepository`<br>`PersonSummaryRepository`                                             |

# Screen - Components 依存関係

各画面で使用されるUIコンポーネントの構成です。修正時の影響範囲の確認に利用してください。
※ 🔴**太字**は2つ以上の画面で共有されている部品です。変更時の影響範囲に注意してください。

| 分類                   | 画面 (Screen)                                                              | 使用コンポーネント (ファイル名)                                                                                                                                                                                                                                                                                                                             |
|:---------------------|:-------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1. 利用者一覧および(A)(B)(C) | (全主要画面)                                                                  | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/AppDialog.kt`**：共通ダイアログ基盤（ボタン・コンテンツ・スクロール制御）<br>🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/InfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/AppTextField.kt`**：共通の入力フィールド（標準）<br>🔴**`base/AppCompactTextField.kt`**：入力欄の微調整用コンポーネント                                                                                                   |
| 2. 利用者一覧             | `MainScreen`<br>`*Content.kt`<br>`PersonEditScreen.kt` | `main/CategoryBadges.kt`：記録状況を示すカテゴリバッジ<br>`main/MainComponents.kt`：利用者一覧共通部品（UserListItem 等）<br>`main/KanaIndexBar.kt`：五十音インデックスバー<br>🔴**`base/SearchBox.kt`**：共通検索バー<br>`main/BirthdayInputFields.kt`：生年月日入力部品                                                                                                                             |
| 3. (A)(B)(C)共通       | (詳細3画面全体)                                                                | 🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助<br>🔴**`base/DeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ<br>🔴**`common/CategorySelectorBar.kt`**：(A)(B)(C)の切り替えバー<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤<br>🔴**`common/PdfExportActionHandler.kt`**：PDF出力のアクション管理<br>🔴**`common/PdfSettingsDialog.kt`**：PDF出力設定ダイアログ                   |
| 4. (A) 健康記録          | `PersonHealthScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`     | `health/PersonHealthComponents.kt`：(A)専用の表示・編集・詳細パネル・詳細項目(DetailItem)<br>`health/HealthGraphView.kt`：(A)専用グラフ表示<br>`health/LineChart.kt`：グラフ描画エンジン<br>`health/HealthChartHelper.kt`：グラフ用データ変換 |
| 5. (B) 所見メモ          | `PersonConditionScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`  | 🔴**`base/SearchBox.kt`**：共通検索バー<br>`condition/PersonConditionComponents.kt`：(B)専用の表示・編集・写真グリッド                                                                                                                                        |
| 6. (C) 服薬管理          | `PersonMedicationScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt` | `medication/PersonMedicationComponents.kt`：(C)専用カレンダー・履歴テーブル・入力ダイアログ                                                                                                                                                                                                                                                                          |
| 7. (A)の一括入力          | `BatchInputScreen`                                                       | 🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                              |
| 8. 利用者管理             | `DeleteOrRestorePerson`                                                  | 🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/InfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                     |
| 9. アプリ設定             | `SettingsScreen`                                                         | 🔴**`base/DeleteConfirmDialog.kt`**：破壊的操作の警告ダイアログ<br>🔴**`base/InfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                           |

# Components - Screen 逆引きリファレンス

コンポーネント側から見た、各画面への使用状況マトリックスです。<br><br>
※ **注意**: 本セクションは「Screen - Components 依存関係」と同じ情報を視点（行・列）を変えて表現したものです。<br>
※ **注意**: 一方の表を修正した際は、必ずもう一方も更新して矛盾が起きないようにしてください。
<br>

| コンポーネント (ファイル名) | 一覧 | (A)健康 | (B)所見 | (C)服薬 | (A)一括 | 管理 | 設定 |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **【共通部品 (複数画面で使用)】** | | | | | | | |
| 🔴**`base/AppCompactTextField.kt`**：入力欄の微調整用コンポーネント | ✓ | ✓ | ✓ | ✓ | ✓ | | |
| 🔴**`base/AppDialog.kt`**：共通ダイアログ基盤（ボタン・コンテンツ・スクロール制御） | ✓ | ✓ | ✓ | ✓ | | ✓ | ✓ |
| 🔴**`base/AppTextField.kt`**：共通の入力フィールド（標準） | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ |
| 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 🔴**`base/DeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ | | ✓ | ✓ | ✓ | | | ✓ |
| 🔴**`base/EmptyState.kt`**：共通の「データなし」表示 | ✓ | ✓ | ✓ | ✓ | | ✓ | |
| 🔴**`base/InfoDialog.kt`**：共通の通知・エラーダイアログ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 🔴**`base/LoadingScreen.kt`**：共通のローディング表示 | ✓ | ✓ | ✓ | ✓ | ✓ | | |
| 🔴**`base/SearchBox.kt`**：共通検索バー | ✓ | | ✓ | | | | |
| 🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助 | | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 🔴**`common/CategorySelectorBar.kt`**：(A)(B)(C)の切り替えバー | | ✓ | ✓ | ✓ | | | |
| 🔴**`common/DateTimeInputFields.kt`**：共通の日時入力 | | ✓ | ✓ | ✓ | ✓ | | |
| 🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤 | | ✓ | ✓ | ✓ | | | |
| 🔴**`common/PdfExportActionHandler.kt`**：PDF出力のアクション管理 | | ✓ | ✓ | ✓ | | | |
| 🔴**`common/PdfSettingsDialog.kt`**：PDF出力設定ダイアログ | | ✓ | ✓ | ✓ | | | |
| 🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー | | ✓ | ✓ | ✓ | ✓ | | |
| **【個別部品 (特定ドメイン/画面)】** | | | | | | | |
| `condition/PersonConditionComponents.kt`：(B)専用の表示・編集・写真グリッド | | | ✓ | | | | |
| `health/HealthChartHelper.kt`：グラフ用データ変換 | | ✓ | | | | | |
| `health/HealthGraphView.kt`：(A)専用グラフ表示 | | ✓ | | | | | |
| `health/LineChart.kt`：グラフ描画エンジン | | ✓ | | | | | |
| `health/PersonHealthComponents.kt`：(A)専用の表示・編集・詳細パネル・詳細項目(DetailItem) | | ✓ | | | | | |
| `main/BirthdayInputFields.kt`：生年月日入力部品 | ✓ | | | | | | |
| `main/CategoryBadges.kt`：記録状況を示すカテゴリバッジ | ✓ | | | | | | |
| `main/KanaIndexBar.kt`：五十音インデックスバー | ✓ | | | | | | |
| `main/MainComponents.kt`：利用者一覧共通部品（UserListItem 等） | ✓ | | | | | | |
| `medication/PersonMedicationComponents.kt`：(C)専用カレンダー・履歴テーブル・入力ダイアログ | | | | ✓ | | | |


# 実装上の重要指針（保守ガイド）

不具合修正や機能追加の際は、以下の指針を遵守してください。

## 1．入力コンポーネントの実装ガイド（AppTextField）

アプリ内での数値・テキスト入力には、標準の `OutlinedTextField` を直接使用せず、必ずプロジェクト共通基盤である `AppTextField`（またはそれをラップしたコンポーネント）を使用して、入力体験（UX）を統一してください。

### 基本ポリシー：編集体験の統一
CareMemo では、以下の挙動を「アプリ標準ルール」として定義しています。
*   **タップ時のカーソル制御（最重要）**: 入力項目をタップ（フォーカス取得）した際、既存の値を保持したまま**カーソルを自動的に末尾に移動**させます。
*   **自動フォーカス移動**: 以下の条件下で、手動操作なしに次の入力項目へフォーカスをリレーします。
    *   **最大桁数（maxLength）到達**: 年月日などの固定桁数入力時。
    *   **IMEアクション（Next）**: キーボードの「次へ」押下時。
*   **入力属性（AppTextFieldType）による自動設定**: `type` 引数を指定するだけで、以下の設定が自動適用されます。
    *   **利用可能なタイプ**: `TEXT`, `INTEGER`, `DECIMAL`, `PASSWORD`, `EMAIL`, `PHONE`
    *   **キーボード**: タイプに適したモード（数値、日本語、英数等）が自動選択されます。
    *   **入力制限**: 数値タイプの場合、数字以外の入力が自動的に弾かれます。
    *   **IMEアクション**: 文脈に応じた最適なボタン（次へ、完了等）が設定されます。

### コンポーネントの使い分け
見た目（サイズ感）に応じて選択してください。どちらも内部で `AppTextField` のロジックを継承しています。

| コンポーネント名 | 用途 | 配置場所 |
| :--- | :--- | :--- |
| **`AppTextField`** | 標準的な入力項目（名前、メモ、記録者など） | `base/AppTextField.kt` |
| **`AppCompactTextField`** | 省スペースが必要な数値入力（日時、バイタル等） | `base/AppCompactTextField.kt` |

### 実装上のルール
*   **FocusRequester の原則禁止**: 
    画面上の並び順に従った標準的な入力遷移（例：年→月→日）を実現するために、**呼び出し側で `FocusRequester` を手動管理しないでください。** `AppTextField` の自動フォーカス移動機能に任せることで、配線コードのないクリーンな宣言的 UI を維持してください。
*   **例外的な挙動**: 
    検索窓など、タップ時に値をクリアした方が利便性が高い特殊なケースに限り、個別に挙動を上書きすることを許可します。その場合は、ソースコード内にその理由を明記してください。

## 2，ダイアログの標準化ガイド（AppDialog）

アプリ内での意思決定を求めるダイアログには、標準の `AlertDialog` を直接使用せず、必ずプロジェクト共通基盤である `AppDialog`（およびその関連コンポーネント）を使用して、ユーザーの視線誘導と操作の意図を明確にしてください。

### 基本ポリシー：アクションの視覚的プライオリティ
CareMemo では、誤操作を防止しつつ操作を加速させるため、以下の「ボタン配置と視覚効果のルール」を定義しています。

*   **ポジティブアクション（確定・実行）**:
    *   **配置**: ダイアログの**右下**に配置します。
    *   **スタイル**: 常に**塗りつぶしボタン**（`AppDialogConfirmButton`）を使用し、最も目立つようにします。
    *   **色分け（AppDialogActionType）**: 操作の性質に応じて、以下の3色を使い分けます。
        *   **SAVE (Primary)**: データの保存、登録、情報の確定。テーマの主色を使用し、画面全体のトーンと統一する。
        *   **DELETE (Error)**: データの削除、情報の破棄、完全抹消などの破壊的操作。警告を示す赤系を使用する。
        *   **ACTION (Tertiary)**: PDF出力、外部送信、同期処理の実行など、新しい価値を生む処理。Primary とは異なるアクセント色を使用する。
*   **ネガティブアクション（キャンセル・戻る）**:
    *   **配置**: ダイアログの**左下**（ポジティブアクションの左）に配置します。
    *   **スタイル**: 常に**文字のみのボタン**（`AppDialogDismissButton`）を使用し、視覚的な重みを下げます。

### コンテンツの自動スクロール
*   **AppDialogContent の使用**:
    ダイアログ内のメッセージや入力項目は、必ず `AppDialogContent` でラップしてください。これにより、コンテンツが画面高さに収まらない場合に、自動的にスクロールバー（`VerticalScrollIndicator`）が表示され、かつタイトルとボタンエリアが固定された正しいレイアウトが維持されます。

## 3．基本方針
- **影響範囲の最小化**: 依頼された修正内容以外について、設計変更やファイル整理を行わない。
- **既存設計とUI/UXの尊重**: 既存の設計思想、フォルダ構成、責務分割、および既存のUI/UXを維持する。既存実装と異なる設計を採用する場合は、修正前に理由・メリット・影響範囲を説明し、承認を得ること。
- **不要なリファクタリングの禁止**: 「利用箇所が少ない」「将来のため」「フォルダ構成のみ」「命名変更のみ」といった理由での修正は行わない。改善案がある場合は、コードを直接修正せず「改善提案」として提示すること。

## 4．コーディング・UI規約
- **Material3 準拠**: 標準的な扱いに極力準拠する。必要な場面で準拠されている実装から逸脱する場合は、その旨を明記して修正の是非の判断を求めること。
- **Modifier の扱い**: `Modifier` は常に Composable 関数の最初のオプション引数とする。
- **Preview の必須化**: 各 Composable ファイルには、主要な状態を確認できる Preview を必ず 1 つ以上含める。
- **UIイベント通知**: トーストやダイアログの表示は、ViewModel の `UiEventFlow` 経由で行う。
- **Composable の追加**: 新しい Composable は責務毎に追加し、新規実装より既存実装の再利用を優先する。

## 5．状態管理・パフォーマンス・データ保護
- **UI状態の初期化とブランキング抑制**: IDに基づいてデータを表示・編集する場合、状態変数の初期化に `LaunchedEffect` を使用しない。
  - **推奨**: `val text = remember(id) { mutableStateOf(record?.field ?: "") }`
  - **理由**: `LaunchedEffect` による初期化は非同期で行われるため、1フレーム目に空の値が表示され、2フレーム目に実際の値が表示される「ブランキング（チラつき）」の原因となるため。
- **入力データの保護（破棄確認の必須化）**:
  - **原則**: 文字列入力（名前、メモ等）を伴う「新規作成」および「編集」画面・パネルにおいては、未保存の変更がある状態で「戻る」操作（システム戻るボタン、画面上のキャンセル/戻るボタン）を行った際、必ず**破棄確認ダイアログ**を表示すること。
  - **実装**: `BackHandler` および `derivedStateOf` による変更検知（`isChanged`）を組み合わせて実装する。
  - **例外**: 「服薬管理」のカレンダー入力など、誤操作のリスクが低い選択主体の入力、またはデータが極めて軽量な場合は、この限りではない。
- **リストデータの安定化**: ViewModel から取得したリストを Composable 内で加工（フィルタリング等）して子コンポーネントに渡す際は、`remember(records)` でリストインスタンスを安定化させる。
  - **キーの指定**: データ更新時に正しく再計算されるよう、`remember` のキーには Flow 等から取得した最新のリストインスタンスを指定すること。加工後のリストは不変（Immutable）な状態を維持すること。
  - **理由**: 再コンポーズのたびに新しいリストインスタンスが生成されると、子コンポーネントの不要な再計算やスクロール位置のリセットを引き起こすため。
- **利用者コンテキストの確実な切り替え**: 異なる利用者間で画面を遷移する際、古いデータが残存するのを防ぐため、以下の実装を徹底すること。
  - **即時クリア**: `loadPerson(id)` の開始時に `_currentPerson.value = null` をセットし、古いデータを明示的に消去する。
  - **ジョブの排他制御**: データのロードを行うコルーチンは `Job` 変数で管理し、新しいロードを開始する前に必ず `cancel()` すること。Flow の監視を行う場合は、`collectLatest` や `flatMapLatest` を活用し、古いリクエストが自動的にキャンセルされる構成を推奨する。
  - **個別状態のリセット**: 専門 ViewModel では `loadPerson` をオーバーライドし、検索クエリや選択中の日付などの「利用者固有の状態変数」を初期値に戻すこと。
  - **ナビゲーションの注意**: 利用者が切り替わる遷移では、`NavHost` の `restoreState = true` を使用しない。

## 6．共通化・共通部品の判断基準
- **共通化の原則**: 
  - 同一仕様が今後も維持されるもののみ共通化する。
  - 将来仕様が分かれそうなものは、重複を許容してもカテゴリ毎に保持する。
  - 共通化はコード量削減ではなく、保守性向上を目的とする。
- **配置場所の判断フロー**:
  1. **その部品は他のアプリ（例：家計簿アプリ等）でもそのまま使えますか？**
     - YES → `base` へ（ドメイン非依存）
     - NO → 2へ
  2. **その部品は「利用者」「介護記録」などの業務知識を知っていますか？**
     - YES（かつ複数画面で使う） → `common` へ（ドメイン共通）
     - YES（かつ特定の画面でしか使わない） → `main`, `health` 等の各フォルダへ（画面固有）
- **共通部品の利用と変更**:
  - 新しい Composable を作成する前に、既存の共通部品で実現できないか必ず確認する。優先順位は「1. 既存をそのまま利用 ＞ 2. 軽微に拡張 ＞ 3. 新規追加」とする。
  - 共通部品は可能な限り変更しない。変更時は影響範囲（🔴マークが付いている部品か、どの画面で使用されているか）を必ず確認すること。
- **推奨・非推奨の例**:
  - **推奨**: LoadingScreen, EmptyState, Dialog, 共通入力部品, 共通ボタン列、履歴リスト基盤など。
  - **非推奨**: 画面固有の入力フォーム, カテゴリ固有の表示ロジック, 将来仕様が分岐する可能性が高いUIなど。

## 7．配色とプライバシー（マスキング）
- **配色セマンティクスの遵守**: 固定色（`Color.Red`等）の直接指定を避け、`MaterialTheme.colorScheme` のセマンティックカラー（`primary`, `error`等）を優先する。
- **状態の視覚的区別（3段階ルール）**:
  - **正常**: `onSurfaceVariant`（グレー系）を使用し、情報をミュート（控えめに表示）することで重要なシグナルの埋没を防ぐ。
  - **注意/予備群**: オレンジ系（ライトモード：`#E65100` / ダークモード：`#FFB74D`）を明示的に使用する。`tertiary` カラーが `primary` と色調が近く判別しにくい場合の代替手段とする。
  - **異常/警告**: `error`（赤系）を使用し、即座に注意が必要な状態であることを示す。
- **一貫したマスキングロジック**: 
  - 氏名等の伏せ字処理は `Person` クラスの一元化されたロジック（`getMaskedName`, `getMaskedFurigana`）を必ず利用し、画面ごとに個別実装しない。
  - **漢字氏名**: 交互にマスク（例：山○　太○）。
  - **カナ氏名**: 2文字目以降をすべてマスク（例：ヤ○○　タ○○）。
- **PDFの機密性保持**: PDF出力は外部共有を前提とするため、アプリの設定（伏せ字ON/OFF）に関わらず、**常にマスキングを適用した状態で出力**すること。

## 8．修正報告
修正完了時は以下を報告すること。
- 修正ファイル
- 共通部品への影響有無
- 動作確認が必要な画面
- 今回見つかった改善候補（修正は行わない）


# ソースファイルのコメント・テンプレート

## ui/screens

```
Screen :

【画面名】：

【役割】：

【主な機能】：

【遷移】：

【使用するViewModel】：

【使用するComponents】：

【備考】：

```

## ui/components

```
Component：

【役割】：

【主な機能】：

【想定する利用場所】：

【このコンポーネントでは行わないこと】：

【公開composable】：

```


---
最終更新日: 2026/07/06
