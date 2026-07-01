# CareMemo プロジェクト構造定義書

このドキュメントでは、CareMemo プロジェクトのソースコード構造とその役割について説明します。仕様変更や不具合修正の際に対象ファイルを探すガイドとして利用してください。

# パッケージ構造の概要

プロジェクトの主要なコードは `jp.mydns.fujiwara.carememo` パッケージ配下に配置されています。

```text
jp.mydns.fujiwara.carememo
├── ui/              # UIレイヤー（Jetpack Compose）
│   ├── screens/     # 利用者一覧・削除画面・設定画面のComposable
│   │   └─ detail/   # 利用者詳細画面のComposable
│   │      └─sub/    # 利用者詳細画面から呼ばれるサブ画面のComposable
│   ├── components/  # 再利用可能な共通コンポーネント
│   └── theme/       # アプリのテーマ設定（Color, Type, Shapeなど）
├── viewmodel/       # ビジネスロジックと状態管理（MVVM）
├── data/            # データレイヤー（Room Database）
│   └── repository/  # リポジトリ（データ操作ロジック）
├── utils/           # ユーティリティ（共通処理、ヘルパー関数）
├── MainActivity.kt  # アプリのエントリポイント
└── CareMemoApplication.kt # Application クラス
```

# 各ディレクトリの詳細

## 1. `ui/screens/`
各機能の画面となるComposable関数が配置されています。画面遷移の単位となるファイルです。

### 1.1. 利用者一覧・共通入力：`ui/screens/`
- (Top) `MainScreen.kt`: 利用者一覧を表示するアプリのメイン画面。
- `BatchInputScreen.kt`: A系統（身長・体重、バイタル、血糖値）の数値を1画面でまとめて入力するための画面。連続入力に最適化されている。

### 1.2. 利用者詳細画面：`ui/screens/detail/`

#### 1.2.1. カテゴリ分類

5つのカテゴリ「身長・体重」「バイタル」「血糖値・HbA1c」「所見メモ」「服薬管理」がある。
データの特性上、5つのカテゴリは(A)(B)(C)の3つに分類される
  1. (A)「身長・体重」「バイタル」「血糖値・HbA1c」：ある利用者の、ある日時の、ある「数値」を記録・参照する。履歴一覧表示とグラフ表示を持つ。
  2. (B)「所見メモ」：ある利用者の、ある日時の、ある「情報(文字)」を記録・参照する。文字列検索ができる。写真を添付できる。
  3. (C)「服薬管理」：ある利用者の、ある日時に記録した、ある日時の「服薬状況（朝・昼・夕・寝る前など）」と、そのステータス（未服用、服薬介助、服用など）を記録・参照する。カレンダー形式での入力方式を採用。

#### 1.2.2. 画面遷移

- (Top) ←→ `BatchInputScreen` ：(A)の一括入力
- (Top) ←→ { (1) → (2) x (4) } ：(A)(B)スマホ向け
- (Top) ←→ { (1) → (3) x (4) } ：(A)(B)タブレット向け
  - (Top) ←→ { (1) ←→ (A)の[ (2) or (3) ] ←→ (sub-1) }：(A)のグラフ拡大画面
- (Top) ←→ { (5) → (6) x (8) } ：(B)スマホ向け
- (Top) ←→ { (5) → (7) x (8) } ：(B)タブレット向け
  - (Top) ←→ { (7) ←→ [ (sub-2) or (sub-3] } ：(B)の写真機能用画面
- (Top) ←→ { (9) → (10) x (12) } ：(C)スマホ向け
- (Top) ←→ { (9) → (11) x (12) } ：(C)タブレット向け

#### 1.2.3. (A)カテゴリ「身長・体重」「バイタル」「血糖値・HbA1c」

##### 1.2.3.1. (A)メイン画面：`ui/screens/detail/UnifiedRecordScreen*.kt`
- (1) `UnifiedRecordScreen.kt`:(A)数値系カテゴリの入り口となるスクリーンで、WindowsSize判定を行い、Phone/Tabletに分岐。
- (2) `UnifiedRecordScreenPhone.kt`：(A)スマホ向けスクリーン。
- (3) `UnifiedRecordScreenTablet.kt`：(A)タブレット向けスクリーン。
- (4) `UnifiedRecordScreenContent.kt`：(A)数値系カテゴリのコンテントで、Phone/Tablet各々が使用。

##### 1.2.3.2. (A)サブ画面：`ui/screens/detail/sub/`
- (sub-1) `GraphExpansionScreen.kt`: (A)バイタルデータ等のグラフを拡大して詳細に確認・操作するための画面。
 
#### 1.2.4. (B)カテゴリ「所見メモ」

##### 1.2.4.1. (B)メイン画面：`ui/screens/detail/ConditionDetailScreen*.kt`
- (5) `ConditionDetailScreen.kt`：(B)文字列カテゴリの入り口となるスクリーンで、WindowsSize判定を行い、Phone/Tabletに分岐。
- (6) `ConditionDetailScreenPhone.kt`：(B)のスマホ向けスクリーン
- (7) `ConditionDetailScreenTablet.kt`：(B)のタブレット向けスクリーン
- (8) `ConditionDetailScreenContent.kt`：(B)文字列系のコンテントで、Phone/Tablet各々が使用。
 
##### 1.2.4.2. (B)サブ画面：`ui/screens/detail/sub`

- (sub-2) `ConditionPhotoFullScreen.kt`: (B)「所見メモ」に添付された写真を全画面モードで表示する画面。
- (sub-3) `ConditionPhotoPreviewScreen.kt`: (B)「所見メモ」の写真撮影時のプレビュー、または保存前の写真確認を行うための画面。

#### 1.2.5. (C)カテゴリ「服薬管理」

##### 1.2.5.1. (C)メイン画面：`ui/screens/detail/MedicationScreen*.kt`
- (9) `MedicationScreen.kt`: (C)入り口となるスクリーンで、WindowsSize判定を行い、Phone/Tabletに分岐。
- (10) `MedicationScreenPhone.kt`：(C)スマホ向けスクリーン
- (11) `MedicationScreenTablet.kt`：(C)タブレット向けスクリーン
- (12) `MedicationScreenContent.kt`：(C)のコンテントでPhone/Tablet各々が使用

### 1.3. 設定・管理画面：`ui/screens/`
- `SettingsScreen.kt`: 「設定・管理」画面。伏せ字文字設定、セキュリティ設定、データのバックアップ/リストア等を行う画面。
- `DeletedUserListScreen.kt`: 削除された利用者の一覧を表示し、復元または完全に削除するための管理画面。その機能は「管理・設定」画面から呼び出される。

## 2. `ui/components/`
複数の画面で共有される、あるいは画面を構成する小さなUI部品が配置されています。

### 2.1. 共通部品
- `CategorySelectorBar.kt`: 「バイタル」「所見メモ」「服薬」などのカテゴリを切り替えるための選択バー。
- `CompactTextField.kt`: 数値入力や短いテキスト入力に適した、省スペースなカスタムテキストフィールド。
- `DateTimeInputFields.kt`: 日時入力（日付選択・時刻選択）用のカスタムフィールド。
- `PersonHeaderTitle.kt`: 画面上部に表示される利用者の名前や基本情報を表示するヘッダー部品。
- `PdfSettingsDialog.kt`: PDF出力時の出力期間や項目を設定するためのダイアログUI。
- `PdfExportActionHandler.kt`: 各画面で共通のPDF出力処理（ダイアログ表示〜PDF生成〜共有）を制御する非表示のロジックコンポーネント。
### 2.2. 利用者一覧画面の部品
- `CategoryBadges.kt`: 各カテゴリの状態やラベルを視覚的に表示するバッジコンポーネント。
### 2.3. 利用者詳細画面
#### 2.3.1. 共通基盤および (A) カテゴリ部品
- `UnifiedRecordComponents.kt`：(A)(B)系統共通の履歴リスト基盤、および (A)数値系カテゴリ専用の表示・編集ペイン。
- `LineChart.kt`: バイタルデータ等の履歴を折れ線グラフで表示するための汎用コンポーネント。
- `HealthGraphView.kt`: `LineChart` を利用し、健康管理データに特化したグラフ表示用コンポーネント。
- `HealthChartHelper.kt`: グラフ表示のためのデータ変換や計算を行うヘルパー。
#### 2.3.2. (B) カテゴリ「所見メモ」専用部品
- `ConditionComponents.kt`: 「所見メモ」カテゴリ専用の表示・編集ペイン、検索ボックス、および写真グリッド表示。
#### 2.3.3. (C) カテゴリ「服薬管理」
- `MedicationComponents.kt`：カレンダーグリッド、服薬履歴テーブル、入力ダイアログ等の専用コンポーネント。


## 3. `viewmodel/`
UIの状態（State）を保持し、`data` レイヤーとの仲介を行います。
#### 3.1. 基底クラス
- `BaseViewModel.kt`: すべての ViewModel の基底クラス。UI通知（Snackbar/Dialog）の送信や、アプリ全体の共通設定（伏せ字、記録者名）の保持を担当。
- `PersonBaseViewModel.kt`: 特定の利用者に紐づく ViewModel の共通基底クラス。利用者IDの管理、基本情報の取得、カテゴリサマリーの取得を担当。
#### 3.2. 利用者一覧
- `PersonListViewModel.kt`: 利用者一覧画面（メイン画面）のロジック。利用者の追加・削除・復元、一覧の取得を管理。
#### 3.3. 利用者詳細
##### 3.3.1. 画面フレーム管理 (共通)
- `PersonDetailViewModel.kt`: 利用者詳細画面のナビゲーション（カテゴリ切り替え）および、共通状態の管理を担当。具体的なデータ操作ロジックは持たない。
##### 3.3.2. (A) カテゴリ「身長・体重」「バイタル」「血糖値・HbA1c」
- `HealthRecordViewModel.kt`: 数値系カテゴリのデータ取得・保存・削除を担当。カテゴリ選択に応じた履歴データの動的取得や保存処理を行う。
- `BatchInputViewModel.kt`: A系統の一括入力画面専用の ViewModel。複数カテゴリの同時保存（トランザクション）と、連続入力のための「日時引き継ぎ・入力リセット」ロジックを担当。
##### 3.3.3. (B) カテゴリ「所見メモ」
- `PersonConditionViewModel.kt`: 「所見メモ」カテゴリのデータ取得・保存・削除および検索を担当。付随する写真処理（撮影・リサイズ・保存・削除）や写真有無のマップ管理を行う。
##### 3.3.4. (C) カテゴリ「服薬管理」
- `MedicationViewModel.kt`: 服薬管理画面のロジック。カレンダー形式での月別データ取得、服薬状況の更新・削除を管理。
#### 3.4. 設定・管理
- `SettingsViewModel.kt`: 設定画面のロジック。パスワード設定、テーマ変更、データのバックアップ/リストアを管理。


## 4. `data/`
データの永続化と取得を担当します。Roomを使用したローカルDB管理と、外部へのデータ提供インターフェースを含みます。
### 4.1. データベース関連 
- `AppDatabase.kt`: Roomデータベースの定義。
- `Entity.kt`: データベースのテーブル定義（利用者の基本情報や各種履歴データ、写真情報など）および、共通インターフェース HistoryRecord の定義。
- `Dao.kt`: データベースアクセスのためのインターフェース。
- `DatabaseKeyManager.kt`: データベースの暗号化キー管理（SQLCipher関連）。SQLCipher によるデータベースの暗号化キー管理。アプリロックに関連するセキュリティの根幹。
- `Converters.kt`: Roomデータベースの日付と、Instant型の、相互変換クラス(@Suppress("unused"))。
### 4.2. データベース - リポジトリ - ViewModel - UIのためのリポジトリ
- `repository/PersonRepository.kt`: (共通) 利用者情報およびアプリ全体のデータ管理（バックアップ・復元等）を担当するリポジトリ。
- `repository/UserSettingsRepository.kt`: (共通) アプリの設定（伏せ字設定、テーマ、ロック設定など）を管理するリポジトリ。
- `repository/HealthRepository.kt`: (A系統) 健康記録（身長体重、バイタル、血糖値）のデータ管理を担当するリポジトリ。「一括入力」（トランザクション処理）でも使用。
- `repository/ConditionRepository.kt`: (B系統) 所見メモおよび写真のデータ管理を担当するリポジトリ。
- `repository/MedicationRepository.kt`: (C系統) 服薬管理のデータ管理を担当するリポジトリ。
- `Category.kt`: (A)(B)(C)固有の処理の判定・分岐を助けるクラス。各カテゴリの表示名や、グラフ・検索・写真の有無といった振る舞いを定義する列挙型クラス。
### 4.3. ViewModelのための閾値情報
- `HealthThresholds.kt`: バイタルサインの閾値などの定義。
### 4.4. テーマ設定に必要なデータ
- `ThemeSetting.kt`：アプリのテーマ設定を定義する列挙型

## 5. `utils/`
特定のドメインに依存しない共通処理を記述します。
- `DateTimeUtils.kt`: あらゆる場面で「日付」の表記を統一するための、日時操作のヘルパー。
- `PdfExporter.kt`: PDF生成処理。
- `ZipUtils.kt`: バックアップ・リストア用のZIP圧縮・展開。
- `ImageUtils.kt`: 写真撮影・保存関連の処理。

---
最終更新日: 2026/06/30
