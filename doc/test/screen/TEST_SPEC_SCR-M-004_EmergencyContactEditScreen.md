# テスト仕様書 - SCR-M-004 EmergencyContactEditScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/main/EmergencyContactEditScreenTest.kt`

## 1. 概要
緊急連絡先の登録・編集画面（EmergencyContactEditScreen）における UI 描画、入力フォームの挙動、および ViewModel から発行されるイベントに基づく遷移・ダイアログ制御を検証する。
バリデーションの論理的な判定や正規化ロジックは ViewModel の単体テストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `EmergencyContactUiState` に基づき、入力フィールドやボタンが正しく表示・制御されることを検証する。

| ID     | テスト項目      | 条件 (UiState)              | 期待結果                                                 |
|:-------|:-----------|:--------------------------|:-----------------------------------------------------|
| DSP-01 | 初期表示（新規）   | `isNew = true`, フィールド空    | 全ての入力欄が空で表示され、保存ボタンが非活性であること                         |
| DSP-02 | 初期表示（編集）   | `isEditing = true`, データあり | 指定した連絡先の施設名、電話番号等が各フィールドに反映されていること                   |
| DSP-03 | 保存ボタンの活性制御 | `isValid = true/false`    | ViewModel の判定に基づき、保存ボタンの活性状態が切り替わること                 |
| DSP-04 | 電話番号の整形表示  | 非フォーカス状態                  | `PhoneNumberVisualTransformation` により、ハイフン付きで表示されること |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの入力やタップ操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目     | 操作                | 期待結果 (UI の挙動)                                |
|:-------|:----------|:------------------|:---------------------------------------------|
| ACT-01 | 各項目の入力更新  | 施設名や電話番号を入力       | `onUpdateContact` (ViewModel) が入力値とともに呼ばれること |
| ACT-02 | 電話番号フォーカス | 電話番号欄をタップ         | ハイフンが消え、生入力（数字のみ）が可能な状態になること                 |
| ACT-03 | 保存実行      | 保存ボタンをタップ         | `onSaveClick` が呼ばれること                        |
| ACT-04 | 編集キャンセル   | 戻るボタンまたはキャンセルをタップ | 変更がある（`isChanged=true`）場合、破棄確認ダイアログが表示されること  |

## 4. ナビゲーション・イベント実行テスト (Navigation & Side Effects)
**目的:** ViewModel から発行された `EmergencyContactViewEvent` を受け、実際に画面遷移が実行されることを検証する。

| ID     | テスト項目      | 発行される ViewEvent | 期待結果 (UI の挙動)                                  |
|:-------|:-----------|:----------------|:-----------------------------------------------|
| NAV-01 | 画面終了（保存成功） | `SaveSuccess`   | `navController.popBackStack()` が実行され、前の画面に戻ること |
| NAV-02 | 画面終了（戻る）   | `NavigateBack`  | `navController.popBackStack()` が実行されること        |

## 5. テスト用タグ (testTag)
- `EmergencyContact_SaveButton`: 保存ボタン
- `EmergencyContact_CancelButton`: キャンセルボタン
- `EmergencyContact_TypeDropdown`: 種別選択プルダウン
- `EmergencyContact_FacilityField`: 施設名入力欄
- `EmergencyContact_PersonField`: 担当者名入力欄
- `EmergencyContact_PhoneField`: 電話番号入力欄
- `EmergencyContact_PriorityField`: 表示順序入力欄
- `EmergencyContact_DiscardDialog`: 破棄確認ダイアログ
