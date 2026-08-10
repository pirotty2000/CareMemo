# テスト仕様書 - SCR-S-003 DeleteOrRestorePerson

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/settings/DeleteOrRestorePersonScreenTest.kt`

## 1. 概要
利用者の復帰・抹消画面（DeleteOrRestorePersonScreen）における UI 描画、対象者の選択操作、および ViewModel から発行されるイベントに基づく実行確認ダイアログの制御を検証する。
復帰・削除の論理的な整合性やバリデーション詳細は ViewModel の単体テストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `DeleteOrRestorePersonUiState` および現在の `mode` に基づき、リストやアクションボタンが正しく描画されることを検証する。

| ID     | テスト項目      | 条件 (UiState)         | 期待結果                                       |
|:-------|:-----------|:---------------------|:-------------------------------------------|
| DSP-01 | 利用終了者リスト描画 | 該当データあり              | アーカイブ状態の利用者が氏名（伏せ字対応）、生年月日、識別メモを伴って表示されること |
| DSP-02 | モード別配色反映   | `mode = DELETE`      | TopAppBar や背景色が注意喚起用の配色（赤系）に切り替わること        |
| DSP-03 | 実行ボタンの表示   | `selectedIds` が非空    | 選択件数を含んだ実行ボタン（復帰または抹消）がボトムバーに表示されること       |
| DSP-04 | 空状態の表示     | `archivedPersons` が空 | 「終了した利用者はいません」等のメッセージが表示されること              |
| DSP-05 | ローディング表示   | `isLoading = true`   | 読み込み中インジケータが表示されること                        |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの選択操作やボタンタップが、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目     | 操作                | 期待結果 (UI の挙動)                                        |
|:-------|:----------|:------------------|:-----------------------------------------------------|
| ACT-01 | 個別選択の切り替え | 項目のチェックボックスをタップ   | `toggleSelection(id)` が呼ばれ、チェック状態が更新されること            |
| ACT-02 | 全選択/解除    | アクションバーの「全選択」をタップ | `selectAll` / `clearSelection` が呼ばれ、全項目が選択状態になること    |
| ACT-03 | 実行確認の表示   | 実行ボタンをタップ         | `mode` に応じた確認ダイアログ（復帰確認または抹消警告）が表示されること              |
| ACT-04 | 実行の確定     | ダイアログで「実行」を選択     | ViewModel の実行メソッド（`restoreSelectedPersons` 等）が呼ばれること |

## 4. ナビゲーション・副作用検証 (Navigation)
**目的:** 戻る操作や処理完了時の遷移が正しく実行されることを検証する。

| ID     | テスト項目    | 操作・イベント   | 期待結果                                                          |
|:-------|:---------|:----------|:--------------------------------------------------------------|
| NAV-01 | 画面終了（戻る） | 戻るボタンをタップ | `navController.popBackStack()` が実行されること                       |
| NAV-02 | 更新通知の伝播  | 処理完了後に戻る  | `savedStateHandle` に `refresh_needed=true` がセットされ、親画面へ通知されること |

## 5. テスト用タグ (testTag)
- `DeleteOrRestore_BackButton`: 戻るボタン
- `DeleteOrRestore_List`: 対象者リストコンテナ
- `DeleteOrRestore_Item_{id}`: 各利用者の項目カード
- `DeleteOrRestore_Checkbox_{id}`: 各利用者のチェックボックス
- `DeleteOrRestore_ActionButton`: 復帰/抹消実行ボタン
- `DeleteOrRestore_SelectAllButton`: 全選択・解除ボタン
- `DeleteOrRestore_Loading`: 読み込み中表示
- `DeleteOrRestore_EmptyState`: データなし表示
- `DeleteOrRestore_ConfirmDialog`: 実行確認ダイアログ
