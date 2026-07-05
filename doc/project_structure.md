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
│   │   ├── base/       #  │   ├─ 共通基盤・システム部品
│   │   ├── main/       #  │   ├─ 利用者一覧固有部品
│   │   ├── common/     #  │   ├─ 詳細画面共通（履歴基盤・ヘッダー・入力部品など）
│   │   ├── health/     #  │   ├─ (A)健康記録固有（カテゴリ別表示・詳細ペイン）
│   │   ├── condition/  #  │   ├─ (B)所見メモ固有（詳細表示・編集・写真グリッド）
│   │   └── medication/ #  │   └─ (C)服薬管理固有（カレンダー・履歴テーブル・入力ダイアログ）
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
各カテゴリは、役割を分離した4つのファイルで構成されます。
- `*Screen.kt`: 画面のエントリポイント。WindowSize判定と各ViewModelの管理を行う。
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

| 分類       | 画面 (Screen)              | ViewModel                                              | 主要Repository                                                                         |
|:---------|:-------------------------|:-------------------------------------------------------|:-------------------------------------------------------------------------------------|
| 利用者一覧    | `MainScreen`             | `PersonListViewModel`                                  | `PersonRepository`<br>`ArchivedPersonRepository`<br>`PersonSummaryRepository`        |
| (A) 健康記録 | `PersonHealthScreen`     | `PersonHealthViewModel`<br>`PersonDetailViewModel`     | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                |
| (B) 所見メモ | `PersonConditionScreen`  | `PersonConditionViewModel`<br>`PersonDetailViewModel`  | `ConditionRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`             |
| (C) 服薬管理 | `PersonMedicationScreen` | `PersonMedicationViewModel`<br>`PersonDetailViewModel` | `MedicationRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`            |
| 健康一括入力   | `BatchInputScreen`       | `BatchInputViewModel`                                  | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                |
| 利用者管理    | `DeletedUserListScreen`  | `ArchivedPersonViewModel`                              | `ArchivedPersonRepository`                                                           |
| アプリ設定    | `SettingsScreen`         | `SettingsViewModel`                                    | `AppMaintenanceRepository`<br>`ArchivedPersonRepository`<br>`UserSettingsRepository` |
| 共通基盤     | (詳細画面全体)                 | `PersonDetailViewModel`                                | `PersonRepository`<br>`PersonSummaryRepository`                                      |

# Screen - Components 依存関係

各画面で使用されるUIコンポーネントの構成です。修正時の影響範囲の確認に利用してください。
※ 🔴**太字**は2つ以上の画面で共有されている部品です。変更時の影響範囲に注意してください。

| 分類                  | 画面 (Screen)                                                              | 使用コンポーネント (ファイル名)                                                                                                                                                                                                                                                                                                           |
|:--------------------|:-------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 利用者一覧および(A)(B)(C)共通 | (全画面)                                                                    | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/InfoDialog.kt`**：共通の通知・エラーダイアログ                                                                                                                                     |
| 利用者一覧               | `MainScreen`                                                             | 🔴**`main/CategoryBadges.kt`**：記録状況を示すカテゴリバッジ<br>🔴**`main/MainComponents.kt`**：利用者一覧共通部品（UserListItem, UserEditDialog, CategorySelectionSheet）<br>`main/CompactTextField.kt`：入力欄の微調整用コンポーネント<br>`main/KanaIndexBar.kt`：五十音インデックスバー<br>🔴**`base/SearchBox.kt`**：共通検索バー                                                     |
| (A)(B)(C)共通         | (詳細3画面全体)                                                                | 🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助<br>🔴**`common/CategorySelectorBar.kt`**：(A)(B)(C)の切り替えバー<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー<br>🔴**`common/PdfExportActionHandler.kt`**：PDF出力のアクション管理<br>🔴**`common/PdfSettingsDialog.kt`**：PDF出力設定ダイアログ |
| (A) 健康記録            | `PersonHealthScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`     | 🔴**`base/DeleteConfirmDialog.kt`**：破壊的なアクションの警告ダイアログ<br>🔴**`common/CommonDetailComponents.kt`**：詳細画面の共通小部品（DetailItemなど）<br>🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤（PersonHistoryListなど）<br>`health/PersonHealthComponents.kt`：(A)専用の表示・編集・詳細パネル<br>`health/HealthGraphView.kt`：(A)専用グラフ表示                         |
| (B) 所見メモ            | `PersonConditionScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`  | 🔴**`base/DeleteConfirmDialog.kt`**：破壊的なアクションの警告ダイアログ<br>🔴**`base/SearchBox.kt`**：共通検索バー<br>🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤（PersonHistoryListなど）<br>`condition/PersonConditionComponents.kt`：(B)専用の表示・編集・詳細パネル                                                                                              |
| (C) 服薬管理            | `PersonMedicationScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt` | 🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助<br>`medication/PersonMedicationComponents.kt`：(C)専用のカレンダー・履歴テーブル・入力ダイアログ                                                                                                                                                                                                  |
| (A)の一括入力            | `BatchInputScreen`                                                       | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー                                                                      |
| 利用者管理               | `DeletedUserListScreen`                                                  | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                          |
| アプリ設定               | `SettingsScreen`                                                         | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/DeleteConfirmDialog.kt`**：破壊的なアクションの警告ダイアログ<br>🔴**`base/InfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                               |

# Components - Screen 逆引きリファレンス

コンポーネント側から見た、各画面への使用状況マトリックスです。<br><br>
※ **注意**: 本セクションは「Screen - Components 依存関係」と同じ情報を視点（行・列）を変えて表現したものです。<br>
※ **注意**: 一方の表を修正した際は、必ずもう一方も更新して矛盾が起きないようにしてください。
<br>

| コンポーネント (ファイル名)                            | 一覧 | (A)健康 | (B)所見 | (C)服薬 | (A)一括 | ｱｰｶｲﾌﾞ | 設定 |
|:-------------------------------------------|:--:|:-----:|:-----:|:-----:|:-----:|:------:|:--:|
| **【base/ 共通基盤】**                           |    |       |       |       |       |        |    |
| 🔴**`base/AppTopAppBarColors.kt`**         | ✓  |   ✓   |   ✓   |   ✓   |   ✓   |   ✓    | ✓  |
| 🔴**`base/LoadingScreen.kt`**              | ✓  |   ✓   |   ✓   |   ✓   |   ✓   |        |    |
| 🔴**`base/EmptyState.kt`**                 | ✓  |   ✓   |   ✓   |   ✓   |       |   ✓    |    |
| 🔴**`base/InfoDialog.kt`**                 | ✓  |   ✓   |   ✓   |   ✓   |       |        | ✓  |
| 🔴**`base/SearchBox.kt`**                  | ✓  |       |   ✓   |       |       |        |    |
| 🔴**`base/VerticalScrollIndicator.kt`**    |    |   ✓   |   ✓   |   ✓   |   ✓   |   ✓    | ✓  |
| 🔴**`base/DeleteConfirmDialog.kt`**        |    |   ✓   |   ✓   |       |       |        | ✓  |
| `base/BirthdayInputFields.kt`              | ✓  |       |       |       |       |        |    |
| **【main/ 一覧固有】**                           |    |       |       |       |       |        |    |
| `main/KanaIndexBar.kt`                     | ✓  |       |       |       |       |        |    |
| 🔴**`main/CategoryBadges.kt`**             | ✓  |   ✓   |   ✓   |   ✓   |       |        |    |
| 🔴**`main/MainComponents.kt`**             | ✓  |       |       |       |       |        |    |
| `main/CompactTextField.kt`                 | ✓  |       |       |       |       |        |    |
| **【common/ 詳細共通】**                         |    |       |       |       |       |        |    |
| 🔴**`common/CategorySelectorBar.kt`**      |    |   ✓   |   ✓   |   ✓   |       |        |    |
| 🔴**`common/CommonDetailComponents.kt`**   |    |   ✓   |       |       |       |        |    |
| 🔴**`common/DateTimeInputFields.kt`**      |    |   ✓   |   ✓   |   ✓   |   ✓   |        |    |
| 🔴**`common/HistoryComponents.kt`**        |    |   ✓   |   ✓   |       |       |        |    |
| 🔴**`common/PersonHeaderTitle.kt`**        |    |   ✓   |   ✓   |   ✓   |   ✓   |        |    |
| 🔴**`common/PdfExportActionHandler.kt`**   |    |   ✓   |   ✓   |   ✓   |       |        |    |
| 🔴**`common/PdfSettingsDialog.kt`**        |    |   ✓   |   ✓   |   ✓   |       |        |    |
| **【各ドメイン固有】**                              |    |       |       |       |       |        |    |
| `health/PersonHealthComponents.kt`         |    |   ✓   |       |       |       |        |    |
| `health/HealthGraphView.kt`                |    |   ✓   |       |       |       |        |    |
| `condition/PersonConditionComponents.kt`   |    |       |   ✓   |       |       |        |    |
| `medication/PersonMedicationComponents.kt` |    |       |       |   ✓   |       |        |    |


# 実装上の重要指針（保守ガイド）

不具合修正や機能追加の際は、以下の指針を遵守してください。

### 1. 基本方針
- **影響範囲の最小化**: 依頼された修正内容以外について、設計変更やファイル整理を行わない。
- **既存設計とUI/UXの尊重**: 既存の設計思想、フォルダ構成、責務分割、および既存のUI/UXを維持する。既存実装と異なる設計を採用する場合は、修正前に理由・メリット・影響範囲を説明し、承認を得ること。
- **不要なリファクタリングの禁止**: 「利用箇所が少ない」「将来のため」「フォルダ構成のみ」「命名変更のみ」といった理由での修正は行わない。改善案がある場合は、コードを直接修正せず「改善提案」として提示すること。

### 2. コーディング・UI規約
- **Material3 準拠**: 標準的な扱いに極力準拠する。必要な場面で準拠されている実装から逸脱する場合は、その旨を明記して修正の是非の判断を求めること。
- **Modifier の扱い**: `Modifier` は常に Composable 関数の最初のオプション引数とする。
- **Preview の必須化**: 各 Composable ファイルには、主要な状態を確認できる Preview を必ず 1 つ以上含める。
- **UIイベント通知**: トーストやダイアログの表示は、ViewModel の `UiEventFlow` 経由で行う。
- **Composable の追加**: 新しい Composable は責務毎に追加し、新規実装より既存実装の再利用を優先する。

### 3. 状態管理・パフォーマンス
- **UI状態の初期化とブランキング抑制**: IDに基づいてデータを表示・編集する場合、状態変数の初期化に `LaunchedEffect` を使用しない。
  - **推奨**: `val text = remember(id) { mutableStateOf(record?.field ?: "") }`
  - **理由**: `LaunchedEffect` による初期化は非同期で行われるため、1フレーム目に空の値が表示され、2フレーム目に実際の値が表示される「ブランキング（チラつき）」の原因となるため。
- **リストデータの安定化**: ViewModel から取得したリストを Composable 内で加工（フィルタリング等）して子コンポーネントに渡す際は、`remember(records)` でリストインスタンスを安定化させる。
  - **キーの指定**: データ更新時に正しく再計算されるよう、`remember` のキーには Flow 等から取得した最新のリストインスタンスを指定すること。加工後のリストは不変（Immutable）な状態を維持すること。
  - **理由**: 再コンポーズのたびに新しいリストインスタンスが生成されると、子コンポーネントの不要な再計算やスクロール位置のリセットを引き起こすため。
- **利用者コンテキストの確実な切り替え**: 異なる利用者間で画面を遷移する際、古いデータが残存するのを防ぐため、以下の実装を徹底すること。
  - **即時クリア**: `loadPerson(id)` の開始時に `_currentPerson.value = null` をセットし、古いデータを明示的に消去する。
  - **ジョブの排他制御**: データのロードを行うコルーチンは `Job` 変数で管理し、新しいロードを開始する前に必ず `cancel()` すること。Flow の監視を行う場合は、`collectLatest` や `flatMapLatest` を活用し、古いリクエストが自動的にキャンセルされる構成を推奨する。
  - **個別状態のリセット**: 専門 ViewModel では `loadPerson` をオーバーライドし、検索クエリや選択中の日付などの「利用者固有の状態変数」を初期値に戻すこと。
  - **ナビゲーションの注意**: 利用者が切り替わる遷移では、`NavHost` の `restoreState = true` を使用しない。

### 4. 共通化・共通部品の判断基準
- **共通化の原則**: 
  - 同一仕様が今後も維持されるもののみ共通化する。
  - 将来仕様が分かれそうなものは、重複を許容してもカテゴリ毎に保持する。
  - 共通化はコード量削減ではなく、保守性向上を目的とする。
- **共通部品の利用と変更**:
  - 新しい Composable を作成する前に、既存の共通部品で実現できないか必ず確認する。優先順位は「1. 既存をそのまま利用 ＞ 2. 軽微に拡張 ＞ 3. 新規追加」とする。
  - 共通部品は可能な限り変更しない。変更時は影響範囲（🔴マークが付いている部品か、どの画面で使用されているか）を必ず確認すること。
- **推奨・非推奨の例**:
  - **推奨**: LoadingScreen, EmptyState, Dialog, 共通入力部品, 共通ボタン列、履歴リスト基盤など。
  - **非推奨**: 画面固有の入力フォーム, カテゴリ固有の表示ロジック, 将来仕様が分岐する可能性が高いUIなど。

### 5. 配色とプライバシー（マスキング）
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

### 6. 修正報告
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
最終更新日: 2026/07/04
