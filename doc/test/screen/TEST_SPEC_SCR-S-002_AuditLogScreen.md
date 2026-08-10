# テスト仕様書 - SCR-S-002 AuditLogScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/settings/AuditLogScreenTest.kt`

## 1. 概要
監査ログ閲覧画面（AuditLogScreen）における UI 描画、フィルタリングおよびソート操作の連動、および戻る操作に伴う ViewModel 連携を検証する。
ログの集計ロジックやフィルタリングの論理的な判定は ViewModel 側のテストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `AuditLogUiState` の各状態に基づき、ログリストやフィルタチップが正しく描画されることを検証する。

| ID     | テスト項目      | 条件 (UiState)          | 期待結果                                        |
|:-------|:-----------|:----------------------|:--------------------------------------------|
| DSP-01 | ログリスト描画    | `filteredLogs` にデータあり | 取得されたログがカード形式で一覧表示されること                     |
| DSP-02 | 空状態の表示     | `filteredLogs` が空     | フィルタの有無に応じた「ログはありません」等のメッセージが表示されること        |
| DSP-03 | ローディング表示   | `isLoading = true`    | 画面中央にプログレスバーが表示されること                        |
| DSP-04 | 表示ラベルの日本語化 | 各種コード値                | 「利用終了」「成功」「健康記録」などの正しい日本語ラベルに変換されて表示されていること |
| DSP-05 | フィルタチップ表示  | 有効なフィルタ候補あり           | 機能別、結果別のフィルタ選択肢（Chip）が表示されていること             |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーのフィルタ操作やソート切り替えが、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目    | 操作            | 期待結果 (呼び出される Intent)               |
|:-------|:---------|:--------------|:-----------------------------------|
| ACT-01 | 機能フィルタ選択 | 機能フィルタから項目を選択 | `setFeatureFilter` が選択した機能名で呼ばれること |
| ACT-02 | 結果フィルタ選択 | 結果フィルタから項目を選択 | `setResultFilter` が選択した結果種別で呼ばれること |
| ACT-03 | ソート順切り替え | ソートアイコンをタップ   | `toggleSortOrder()` が呼ばれること        |
| ACT-04 | フィルタ解除   | クリアボタンをタップ    | `clearFilters()` が呼ばれること           |

## 4. ナビゲーション・副作用検証 (Navigation)
**目的:** 戻るボタン操作が正しく実行されることを検証する。

| ID     | テスト項目    | 操作        | 期待結果                                                       |
|:-------|:---------|:----------|:-----------------------------------------------------------|
| NAV-01 | 画面終了（戻る） | 戻るボタンをタップ | `navController.popBackStack()` または ViewModel の戻る処理が実行されること |

## 5. テスト用タグ (testTag)
- `AuditLogScreen_BackButton`: 戻るボタン
- `AuditLog_LogList`: ログリストコンテナ
- `AuditLog_FilterChips`: フィルタ選択エリア
- `AuditLog_ResultFilter`: 結果絞り込み Chip
- `AuditLog_FeatureFilter`: 機能絞り込み Chip
- `AuditLog_SortToggle`: ソート切り替えボタン
- `AuditLog_FilterClear`: フィルタクリアボタン
- `AuditLog_Loading`: 読み込み中表示
- `AuditLog_EmptyState`: データなし表示
- `AuditLogItem_{id}`: 各ログ項目カード
