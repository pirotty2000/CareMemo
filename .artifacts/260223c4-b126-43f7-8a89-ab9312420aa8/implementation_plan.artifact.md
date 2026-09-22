# アラート・レポート機能の仕様検討と基盤実装

保存された健康データから異常値や急激な変化を抽出し、一覧表示する「アラート・レポート」機能を実装します。

## 目的
- 介護現場において見逃されがちなバイタルの異常値や、短期間での急激な体重減少などを自動的に検出し、早期対応を促す。
- `AppSpecifications` で定義された医学的・システム的な閾値を活用し、データに基づいた客観的な評価を提供する。

## Proposed Changes

### 1. ナビゲーションとUIの拡張

#### [MODIFY] [Destinations.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/navigation/Destinations.kt)
- `Destination` インターフェースに `AlertReport` オブジェクトを追加。

#### [MODIFY] [MainActivity.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/MainActivity.kt)
- `NavHost` に `Destination.AlertReport` のルートと Composable を追加。
- `AlertReportViewModel` の生成と注入。

#### [MODIFY] [MainScreenContent.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/main/MainScreenContent.kt)
- トップバーのメニューに「アラート・レポート」項目を追加。

### 2. ロジック層の実装

#### [NEW] `AlertLogic.kt`
- 異常判定のコアロジックを実装。
- `AppSpecifications` に基づく各項目の判定。
- 急激な体重減少（前回比 -3kg 以上）の判定ロジック。
- アラートの深刻度（Severity）の定義。

#### [NEW] `AlertReportLogic.kt`
- リポジトリからデータを取得し、`AlertLogic` を用いてアラートを抽出する機能。
- 全利用者または特定の利用者のデータをスキャンするエンジン。

### 3. データモデルの定義

#### [NEW] `AlertItem.kt`
- アラート一件を表すデータモデル。
- 項目：利用者情報、カテゴリ、日時、値、警告レベル、メッセージ。

### 4. ViewModel と UI の実装

#### [NEW] `AlertReportViewModel.kt`
- アラート一覧の状態管理。
- 非同期でのスキャン処理と、`Structural / Operation` 状態の管理。

#### [NEW] `AlertReportScreen.kt`
- アラート一覧の表示。
- 重大度に応じた色分け（赤：ALERT、黄：WARNING）。
- タップ時に当該利用者の詳細画面（健康記録等）へ遷移する機能。

## ユーザーレビューが必要な事項
- **アラート対象期間**: 直近何日間のデータをスキャン対象とするか？（デフォルトは直近30日程度を想定）
- **体重減少の基準**: 「3kg減少」の期間制限はあるか？（例：1ヶ月以内での減少か、単に前後比較か）
    - 今回は「時系列的に一つ前のデータとの比較」という指示に従います。

## Verification Plan

### Automated Tests
- `AlertLogicTest`: 境界値テスト、体重減少判定のユニットテスト。
- `AlertReportViewModelTest`: データ取得からアラート抽出、UI State 反映までのテスト。

### Manual Verification
- テストデータ（異常値、-3kgの体重データ）を投入し、レポート画面に正しく表示されることを確認。
- レポートから詳細画面への遷移が正しく機能することを確認。
