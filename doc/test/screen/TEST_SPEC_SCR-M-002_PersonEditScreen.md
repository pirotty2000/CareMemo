# テスト仕様書 - SCR-M-002 PersonEditScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/main/PersonEditScreenTest.kt`

## 1. 概要
利用者の登録・編集画面（PersonEditScreen）における UI 描画、入力フォームの動作、および ViewModel から発行されるイベントに基づく遷移・ダイアログ制御を検証する。
入力バリデーションの論理的な判定や重複チェックの詳細は ViewModel の単体テストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `PersonEditUiState` の各状態に基づき、入力フィールドやボタンが正しく表示されることを検証する。

| ID     | テスト項目      | 条件 (UiState)                  | 期待結果                                   |
|:-------|:-----------|:------------------------------|:---------------------------------------|
| DSP-01 | 初期表示（新規）   | `isNew = true`, フィールド空        | 全ての入力欄が空で表示され、保存ボタンが非活性であること           |
| DSP-02 | 初期表示（編集）   | `isNew = false`, データあり        | リポジトリからロードされた氏名、生年月日等が各フィールドに反映されていること |
| DSP-03 | ローディング表示   | `isLoading = true`            | 画面全体にローディングインジケータが表示されること              |
| DSP-04 | 保存ボタンの活性制御 | `isSaveEnabled = true/false`  | ViewModel の判定に基づき、保存ボタンの活性・非活性が切り替わること |
| DSP-05 | 氏名伏せ字の適用   | `isNameMaskingEnabled = true` | 編集画面であってもヘッダー等の表示が伏せ字設定に従うこと（※実装依存）    |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの入力やタップ操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目   | 操作                | 期待結果 (呼び出される Intent)                                |
|:-------|:--------|:------------------|:----------------------------------------------------|
| ACT-01 | 氏名入力    | 姓・名フィールドに文字入力     | `updateLastName` / `updateFirstName` が入力値とともに呼ばれること |
| ACT-02 | 元号選択    | 元号セレクタをタップして変更    | `updateEra` が選択した元号で呼ばれること                          |
| ACT-03 | 保存実行    | 有効な状態で保存ボタンをタップ   | `save()` が呼ばれること                                    |
| ACT-04 | 編集キャンセル | 戻るボタンまたはキャンセルをタップ | 変更がある場合は破棄確認ダイアログが表示されること                           |

## 4. ナビゲーション・イベント実行テスト (Navigation & Side Effects)
**目的:** ViewModel から発行された `PersonEditViewEvent` や `UiEvent` を受け、実際に正しい遷移やダイアログ表示が行われることを検証する。

| ID     | テスト項目    | 発行されるイベント                 | 期待結果 (UI の挙動)                                 |
|:-------|:---------|:--------------------------|:----------------------------------------------|
| NAV-01 | 画面終了（戻る） | `NavigateBack(result)`    | `navController.popBackStack()` が呼ばれ、結果が返されること |
| EVT-01 | 重複警告表示   | `ShowErrorDialogRes` (重複) | 重複警告ダイアログが表示されること                             |
| EVT-02 | 警告からの復帰  | ダイアログで「編集を続ける」を選択         | ダイアログが閉じ、特定の入力欄（識別メモ等）にフォーカスが移動すること           |

## 5. テスト用タグ (testTag)
- `PersonEdit_Loading`: ローディング表示
- `PersonEdit_LastName`: 姓入力フィールド
- `PersonEdit_FirstName`: 名入力フィールド
- `PersonEdit_LastNameKana`: せい入力フィールド
- `PersonEdit_FirstNameKana`: めい入力フィールド
- `PersonEdit_Memo`: 識別メモ入力フィールド
- `PersonEdit_EraSelector`: 和暦・西暦セレクタ
- `PersonEdit_BirthYear`: 年入力フィールド
- `PersonEdit_BirthMonth`: 月入力フィールド
- `PersonEdit_BirthDay`: 日入力フィールド
- `PersonEdit_SaveButton`: 保存ボタン
- `PersonEdit_CancelButton`: キャンセルボタン
- `PersonEdit_DiscardConfirmDialog`: 変更破棄確認ダイアログ
- `PersonEdit_DuplicateDialog`: 重複警告ダイアログ
