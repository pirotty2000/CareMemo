# コンテンツの自動スクロール調査結果レポート

## 1. 調査目的
`project_UI_GUIDELINES.md` の「5. コンテンツの自動スクロール」に基づき、以下の点を確認する。
- ダイアログ内で `AppDialogContent` が適切に使用され、スクロール管理が行われているか。
- 画面レイアウトにおいて `VerticalScrollIndicator` が適切に使用されているか。

## 2. 調査結果まとめ

| 画面 ID | 箇所 | 使用コンポーネント | ガイドライン遵守状況 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| **SCR-M-001** | 利用者一覧 (MainScreen) | (なし) | ○ 許容 (意図的) | 最上位画面のため、あえて `VerticalScrollIndicator` を使用しない設計。 |
| **SCR-M-002** | 利用者登録・編集 | `VerticalScrollIndicator` | ◎ 遵守 | 画面全体に対して適切に適用。 |
| **SCR-PH-*** | 健康管理詳細 | `VerticalScrollIndicator` | ◎ 遵守 | 履歴リスト、グラフエリア、入力フォームに適切に適用。 |
| **SCR-PC-*** | 所見メモ詳細 | `VerticalScrollIndicator` | ◎ 遵守 | 履歴リスト、詳細表示/編集パネルに適切に適用。 |
| **SCR-PC-*** | 写真プレビュー確認 | **AlertDialog** (直接使用) | × 違反 | `AppDialogContent` を介さず `AlertDialog` を使用。 |
| **SCR-PM-*** | 服薬管理詳細 | `VerticalScrollIndicator` | ◎ 遵守 | 履歴テーブルに適切に適用。 |
| **SCR-S-*** | 設定・管理 | `VerticalScrollIndicator` | ◎ 遵守 | 各設定画面で適切に適用。 |

## 3. 具体的な指摘事項と改善案

### SCR-M-001: 利用者一覧画面 (MainScreenContent.kt)
- **状況**: `LazyColumn` を使用しているが、`VerticalScrollIndicator` は配置されていない。
- **判断**: ユーザーの意図的な設計判断に基づき、現状を維持する（最上位画面としてのシンプルさを優先）。

### SCR-PC-*: 写真プレビュー確認ダイアログ (ConditionPhotoPreviewScreen.kt)
- **現状**: 写真の「削除」と「変更破棄」の確認に標準の `AlertDialog` が使用され、コンテンツを `Text` で直接指定している。
- **問題点**: コンテンツが `AppDialogContent` でラップされていないため、万が一テキストが長くなった場合にスクロール制御ができず、共通の視覚効果（インジケーター）も表示されない。
- **改善案**: `AppDeleteConfirmDialog` または `AppDialog` + `AppDialogContent` に差し替える（`AppDialog` 調査結果とも共通の課題）。

## 4. 共通コンポーネントの有効性
- `AppDialogContent.kt`: 内部で `VerticalScrollIndicator` を `isCompact = true` で使用しており、ダイアログ内でのスクロールバー表示が標準化されている。
- `AppInfoDialog`, `AppDeleteConfirmDialog`: いずれも内部で `AppDialogContent` を使用しており、ガイドラインを遵守している。

---
作成日: 2026/07/15
