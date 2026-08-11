# テスト仕様書 - SCR-PC-002 ConditionPhotoPreviewScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/condition/ConditionPhotoPreviewScreenTest.kt`

## 1. 概要
写真プレビュー画面（ConditionPhotoPreviewScreen）における UI 描画、キャプション入力の挙動、および保存・削除・キャンセルに伴う ViewModel 連携とナビゲーションを検証する。
写真の最適化保存ロジック（ImageUtils等）は ViewModel 側のテストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `UiState` および渡された URI に基づき、プレビュー画像や入力欄が正しく描画されることを検証する。

| ID     | テスト項目      | 条件 (UiState)           | 期待結果                                   |
|:-------|:-----------|:-----------------------|:---------------------------------------|
| DSP-01 | 画像プレビュー表示  | `previewUri` が有効       | 撮影した（または選択した）画像が画面中央にプレビュー表示されること      |
| DSP-02 | キャプション初期値  | `previewUri` が有効       | 画像の撮影日時に基づくデフォルトのキャプションが入力欄にセットされていること |
| DSP-03 | ローディング表示   | `isProcessing = true`  | 保存処理中を示すプログレスバーが表示され、入力・ボタンが制限されること    |
| DSP-04 | エラーメッセージ表示 | `errorMessage` が非 null | 読み込み失敗等のエラーメッセージが画面上に表示されること           |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの入力やタップ操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目    | 操作        | 期待結果 (UI の挙動)                                    |
|:-------|:---------|:----------|:-------------------------------------------------|
| ACT-01 | キャプション編集 | 入力欄に文字入力  | 入力した内容がテキストフィールドに反映され、変更あり状態になること                |
| ACT-02 | 保存ボタンタップ | 保存をタップ    | `processAndSavePhoto` (ViewModel) が呼ばれ、画面が終了すること |
| ACT-03 | 削除ボタンタップ | 削除をタップ    | 削除確認ダイアログが表示されること                                |
| ACT-04 | 戻る・キャンセル | 戻るボタンをタップ | 変更がある場合は破棄確認ダイアログが表示され、ない場合は即座に戻ること              |

## 4. ナビゲーション・副作用検証 (Navigation)
**目的:** ViewModel のイベントやダイアログの確定に応じて、正しい遷移が実行されることを検証する。

| ID     | テスト項目    | 操作・イベント    | 期待結果 (UI の挙動)                                         |
|:-------|:---------|:-----------|:------------------------------------------------------|
| NAV-01 | 画面終了（保存） | 保存実行時      | `navController.popBackStack()` が実行されること               |
| NAV-02 | 画面終了（削除） | 削除ダイアログで確定 | 実際の削除（※ViewModel）は行わず、プレビューを破棄して `popBackStack` されること |

## 5. テスト用タグ (testTag)
- `PhotoPreview_Image`: プレビュー画像
- `PhotoPreview_CaptionInput`: キャプション入力欄
- `PhotoPreview_SaveButton`: 保存ボタン
- `PhotoPreview_DeleteButton`: 削除ボタン
- `PhotoPreview_Loading`: 保存中インジケータ
- `PhotoPreview_DiscardDialog`: 破棄確認ダイアログ
