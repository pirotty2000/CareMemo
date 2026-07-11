# CareMemo プロジェクト構造定義書

このドキュメントでは、CareMemo プロジェクトのソースコード構造とその役割について説明します。仕様変更や不具合修正の際に対象ファイルを探すガイドとして利用してください。

# ドキュメントの構成

ドキュメントは3つのファイルで構成されています。

- project_structure.md (このファイル)：プロジェクト全体のパッケージ構造やファイル構成を定義
- project_UI_GUIDELINES.md：このプロジェクトのUI/UX 設計、および画面デザインの共通ルールを定義
- project_RULES.md：このプロジェクトのソースコードを修正・追加・レビューする際の行動規範と実装ルールを定義

設計変更や機能追加など、プロジェクトに影響を与える作業を行う際は、この3つのファイルを全て読み込んだ上で実装してください。
また、ドキュメントの加筆・修正の際は、3つのファイルの役割を十分理解し、同じ情報を複数のドキュメントが持つことのないよう細心の注意を払ってください。

# ドキュメント運用方針

- このドキュメントは CareMemo の設計上の唯一の基準（Single Source of Truth）とします。
- 設計変更や共通部品の追加・削除を行った場合は、実装と合わせて本ドキュメントも更新してください。
- 実装とドキュメントに差異が生じた場合は、ドキュメントを優先して見直し、必要に応じて実装またはドキュメントを修正してください。

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



# Repository - ViewModel - Screen 依存関係

各機能層におけるロジックの垂直方向の依存関係です。

| 分類       | 画面 (Screen)                        | ViewModel                                              | 主要Repository                                                                                                     |
|:---------|:-----------------------------------|:-------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------|
| 利用者一覧    | `MainScreen`<br>`PersonEditScreen` | `PersonListViewModel`<br>`PersonEditViewModel`         | `PersonRepository`<br>`DeleteOrRestorePersonRepository`<br>`PersonSummaryRepository`<br>`UserSettingsRepository` |
| (A) 健康記録 | `PersonHealthScreen`               | `PersonHealthViewModel`<br>`PersonDetailViewModel`     | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                                            |
| (B) 所見メモ | `PersonConditionScreen`            | `PersonConditionViewModel`<br>`PersonDetailViewModel`  | `ConditionRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                                         |
| (C) 服薬管理 | `PersonMedicationScreen`           | `PersonMedicationViewModel`<br>`PersonDetailViewModel` | `MedicationRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                                        |
| 健康一括入力   | `BatchInputScreen`                 | `BatchInputViewModel`                                  | `HealthRepository`<br>`PersonRepository`<br>`PersonSummaryRepository`                                            |
| 利用者管理    | `DeleteOrRestorePerson`            | `DeleteOrRestorePersonViewModel`                       | `DeleteOrRestorePersonRepository`                                                                                |
| アプリ設定    | `SettingsScreen`                   | `SettingsViewModel`                                    | `AppMaintenanceRepository`<br>`DeleteOrRestorePersonRepository`<br>`UserSettingsRepository`                      |
| 操作ログ     | `AuditLogScreen`                   | `SettingsViewModel`                                    | `AuditLogRepository`                                                                                             |
| 共通基盤     | (詳細画面全体)                           | `PersonDetailViewModel`                                | `PersonRepository`<br>`PersonSummaryRepository`                                                                  |

# Screen - Components 依存関係

各画面で使用されるUIコンポーネントの構成です。修正時の影響範囲の確認に利用してください。
※ 🔴**太字**は2つ以上の画面で共有されている部品です。変更時の影響範囲に注意してください。

| 分類                   | 画面 (Screen)                                                              | 使用コンポーネント (ファイル名)                                                                                                                                                                                                                                                                                                                                                                                                                      |
|:---------------------|:-------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1. 利用者一覧および(A)(B)(C) | (全主要画面)                                                                  | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/AppDialog.kt`**：共通ダイアログ基盤（ボタン・コンテンツ・スクロール制御）<br>🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/AppTextField.kt`**：共通の入力フィールド（標準）<br>🔴**`base/AppCompactTextField.kt`**：入力欄の微調整用コンポーネント                                                                             |
| 2. 利用者一覧             | `MainScreen`<br>`*Content.kt`<br>`PersonEditScreen.kt`                   | `main/CategoryBadges.kt`：記録状況を示すカテゴリバッジ<br>`main/MainComponents.kt`：利用者一覧共通部品（UserListItem 等）<br>`main/KanaIndexBar.kt`：五十音インデックスバー<br>🔴**`base/SearchBox.kt`**：共通検索バー<br>`main/BirthdayInputFields.kt`：生年月日入力部品                                                                                                                                                                                                                      |
| 3. (A)(B)(C)共通       | (詳細3画面全体)                                                                | 🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助<br>🔴**`base/AppDeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ<br>🔴**`common/CategorySelectorBar.kt`**：(A)(B)(C)の切り替えバー<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤<br>🔴**`common/PdfExportActionHandler.kt`**：PDF出力のアクション管理<br>🔴**`common/PdfSettingsDialog.kt`**：PDF出力設定ダイアログ |
| 4. (A) 健康記録          | `PersonHealthScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`     | `health/PersonHealthComponents.kt`：(A)専用の表示・編集・詳細パネル・詳細項目(DetailItem)<br>`health/HealthGraphView.kt`：(A)専用グラフ表示<br>`health/LineChart.kt`：グラフ描画エンジン<br>`health/HealthChartHelper.kt`：グラフ用データ変換                                                                                                                                                                                                                                          |
| 5. (B) 所見メモ          | `PersonConditionScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt`  | 🔴**`base/SearchBox.kt`**：共通検索バー<br>`condition/PersonConditionComponents.kt`：(B)専用の表示・編集・写真グリッド                                                                                                                                                                                                                                                                                                                                        |
| 6. (C) 服薬管理          | `PersonMedicationScreen`<br>`*Phone.kt`<br>`*Tablet.kt`<br>`*Content.kt` | `medication/PersonMedicationComponents.kt`：(C)専用カレンダー・履歴テーブル・入力ダイアログ                                                                                                                                                                                                                                                                                                                                                                   |
| 7. (A)の一括入力          | `BatchInputScreen`                                                       | 🔴**`base/LoadingScreen.kt`**：共通のローディング表示<br>🔴**`common/DateTimeInputFields.kt`**：共通の日時入力<br>🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                       |
| 8. 利用者管理             | `DeleteOrRestorePerson`                                                  | 🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                                                                           |
| 9. アプリ設定             | `SettingsScreen`                                                         | 🔴**`base/AppDeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ<br>🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                                                             |
| 10. 操作ログ             | `AuditLogScreen`                                                         | 🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理<br>🔴**`base/EmptyState.kt`**：共通の「データなし」表示<br>🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                                                                                                                                                                                                                                                                                     |

# Components - Screen 逆引きリファレンス

コンポーネント側から見た、各画面への使用状況マトリックスです。<br><br>
※ **注意**: 本セクションは「Screen - Components 依存関係」と同じ情報を視点（行・列）を変えて表現したものです。<br>
※ **注意**: 一方の表を修正した際は、必ずもう一方も更新して矛盾が起きないようにしてください。
<br>

| コンポーネント (ファイル名)                                                         | 一覧 | (A)健康 | (B)所見 | (C)服薬 | (A)一括 | 管理 | 設定 | ログ |
|:------------------------------------------------------------------------|:--:|:-----:|:-----:|:-----:|:-----:|:--:|:--:|:--:|
| **【共通部品 (複数画面で使用)】**                                                    |    |       |       |       |       |    |    |    |
| **＜ドメイン非依存＞**                                                           |    |       |       |       |       |    |    |    |
| **（入力フィールド）**                                                           |    |       |       |       |       |    |    |    |
| 　　🔴**`base/AppTextField.kt`**：共通の入力フィールド（標準）                           | ✓  |   ✓   |   ✓   |   ✓   |   ✓   |    | ✓  |    |
| 　　🔴**`base/AppCompactTextField.kt`**：入力欄の微調整用コンポーネント                   | ✓  |   ✓   |   ✓   |   ✓   |   ✓   |    |    |    |
| **（共通ダイアログ）**                                                           |    |       |       |       |       |    |    |    |
| 　　🔴**`base/AppDialog.kt`**：共通ダイアログ基盤（ボタン・コンテンツ・スクロール制御）                | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  | ✓  |    |
| 　　🔴**`base/AppInfoDialog.kt`**：共通の通知・エラーダイアログ                          | ✓  |   ✓   |   ✓   |   ✓   |   ✓   | ✓  | ✓  |    |
| 　　🔴**`base/AppDeleteConfirmDialog.kt`**：破壊的な操作の警告ダイアログ                 |    |   ✓   |   ✓   |   ✓   |       |    | ✓  |    |
| **（その他）**                                                               |    |       |       |       |       |    |    |    |
| 　　🔴**`base/AppTopAppBarColors.kt`**：TopAppBar の配色管理                    | ✓  |   ✓   |   ✓   |   ✓   |   ✓   | ✓  | ✓  | ✓  |
| 　　🔴**`base/LoadingScreen.kt`**：共通のローディング表示                             | ✓  |   ✓   |   ✓   |   ✓   |   ✓   |    |    |    |
| 　　🔴**`base/EmptyState.kt`**：共通の「データなし」表示                               | ✓  |   ✓   |   ✓   |   ✓   |       | ✓  |    | ✓  |
| 　　🔴**`base/SearchBox.kt`**：共通検索バー                                      | ✓  |       |   ✓   |       |       |    |    |    |
| 　　🔴**`base/VerticalScrollIndicator.kt`**：垂直スクロール補助                     |    |   ✓   |   ✓   |   ✓   |   ✓   | ✓  | ✓  | ✓  |
| **＜ドメイン依存＞**                                                            |    |       |       |       |       |    |    |    |
| 　　🔴**`common/CategorySelectorBar.kt`**：(A)(B)(C)の切り替えバー                |    |   ✓   |   ✓   |   ✓   |       |    |    |    |
| 　　🔴**`common/DateTimeInputFields.kt`**：共通の日時入力                         |    |   ✓   |   ✓   |   ✓   |   ✓   |    |    |    |
| 　　🔴**`common/HistoryComponents.kt`**：共通の履歴リスト基盤                        |    |   ✓   |   ✓   |   ✓   |       |    |    |    |
| 　　🔴**`common/PdfExportActionHandler.kt`**：PDF出力のアクション管理                |    |   ✓   |   ✓   |   ✓   |       |    |    |    |
| 　　🔴**`common/PdfSettingsDialog.kt`**：PDF出力設定ダイアログ                      |    |   ✓   |   ✓   |   ✓   |       |    |    |    |
| 　　🔴**`common/PersonHeaderTitle.kt`**：利用者情報ヘッダー                         |    |   ✓   |   ✓   |   ✓   |   ✓   |    |    |    |
| **【個別部品 (特定ドメイン/画面)】**                                                  |    |       |       |       |       |    |    |    |
| **＜利用者一覧：MainScreen＞**                                                  |    |       |       |       |       |    |    |    |
| 　　`main/BirthdayInputFields.kt`：生年月日入力部品                                | ✓  |       |       |       |       |    |    |    |
| 　　`main/CategoryBadges.kt`：記録状況を示すカテゴリバッジ                               | ✓  |       |       |       |       |    |    |    |
| 　　`main/KanaIndexBar.kt`：五十音インデックスバー                                    | ✓  |       |       |       |       |    |    |    |
| 　　`main/MainComponents.kt`：利用者一覧共通部品（UserListItem 等）                    | ✓  |       |       |       |       |    |    |    |
| **＜(A)健康記録：PersonHealthScreen＞**                                        |    |       |       |       |       |    |    |    |
| 　　`health/HealthGraphView.kt`：(A)専用グラフ表示                                |    |   ✓   |       |       |       |    |    |    |
| 　　`health/LineChart.kt`：グラフ描画エンジン                                       |    |   ✓   |       |       |       |    |    |    |
| 　　`health/HealthChartHelper.kt`：グラフ用データ変換                               |    |   ✓   |       |       |       |    |    |    |
| 　　`health/PersonHealthComponents.kt`：(A)専用の表示・編集・詳細パネル・詳細項目(DetailItem) |    |   ✓   |       |       |       |    |    |    |
| **＜(B)所見メモ：PersonConditionScreen＞**                                     |    |       |       |       |       |    |    |    |
| 　　`condition/PersonConditionComponents.kt`：(B)専用の表示・編集・写真グリッド           |    |       |   ✓   |       |       |    |    |    |
| **＜(C)服薬管理：PersonMedicationScreen＞**                                    |    |       |       |       |       |    |    |    |
| 　　`medication/PersonMedicationComponents.kt`：(C)専用カレンダー・履歴テーブル・入力ダイアログ  |    |       |       |   ✓   |       |    |    |    |




最終更新日: 2026/07/08
