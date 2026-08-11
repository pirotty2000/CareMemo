# テスト仕様書 - SCR-PH-002 BatchInputScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/health/BatchInputScreenTest.kt`

## 1. 概要
健康記録の一括入力画面（BatchInputScreen）における UI 描画、入力フォームの挙動、および保存・キャンセルに伴う ViewModel 連携とナビゲーションを検証する。
バリデーションの論理的な判定や原子的な状態更新は ViewModel の単体テストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `BatchInputUiState` の各状態に基づき、入力フィールドやボタンが正しく表示されることを検証する。

| ID     | テスト項目        | 条件 (UiState)           | 期待結果                                      |
|:-------|:-------------|:-----------------------|:------------------------------------------|
| DSP-01 | 各カテゴリの入力項目表示 | 初期表示                   | 記録日時、身長・体重、バイタル、血糖値の全入力フィールドが表示されていること    |
| DSP-02 | 保存ボタンの活性制御   | `isValid = true/false` | ViewModel の判定に基づき、保存ボタンの活性状態が切り替わること      |
| DSP-03 | ローディング表示     | `isLoading = true`     | 保存ボタン内にインジケータが表示される、または読み込み中状態であることがわかること |
| DSP-04 | スクロール可能性     | 項目多数                   | 垂直スクロールが可能であり、最下部の保存・キャンセルボタンまでアクセスできること  |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの入力やタップ操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目   | 操作                | 期待結果 (UI の挙動)                                 |
|:-------|:--------|:------------------|:----------------------------------------------|
| ACT-01 | 数値入力の更新 | 各入力欄に数値を入力        | `updateHeight` 等、ViewModel の対応する更新メソッドが呼ばれること |
| ACT-02 | 日時変更    | 日時選択フィールドを操作      | `setRecordTime` が新しい日時とともに呼ばれること              |
| ACT-03 | 保存実行    | 保存ボタンをタップ         | `saveBatch()` が呼ばれること                         |
| ACT-04 | 編集キャンセル | 戻るボタンまたはキャンセルをタップ | 変更がある（`isChanged=true`）場合、破棄確認ダイアログが表示されること   |

## 4. ナビゲーション・副作用検証 (Navigation)
**目的:** ViewModel から発行された `BatchInputViewEvent` や `UiEvent` を受け、実際に正しい遷移やアクションが実行されることを検証する。

| ID     | テスト項目    | 発行されるイベント                 | 期待結果 (UI の挙動)                           |
|:-------|:---------|:--------------------------|:----------------------------------------|
| NAV-01 | 画面終了（戻る） | `NavigateBack`            | `navController.popBackStack()` が実行されること |
| EVT-01 | 保存成功時の演出 | `SaveSuccessEffects`      | スクロール位置が自動的にトップ（記録日時エリア）に戻ること           |
| EVT-02 | 重複エラー表示  | `ShowErrorDialogRes` (重複) | 重複したカテゴリ名を含むエラーダイアログが表示されること            |

## 5. テスト用タグ (testTag)
- `BatchInputScreen_BackButton`: 戻るボタン
- `BatchInputScreen_SaveButton`: 保存ボタン
- `BatchInputScreen_CancelButton`: キャンセルボタン
- `BatchInputScreen_DateTimeInput`: 記録日時入力エリア
- `BatchInputScreen_HeightField`: 身長入力フィールド
- `BatchInputScreen_WeightField`: 体重入力フィールド
- `BatchInputScreen_BpSystolicField`: 最高血圧入力フィールド
- `BatchInputScreen_BpDiastolicField`: 最低血圧入力フィールド
- `BatchInputScreen_SatField`: SpO2入力フィールド
- `BatchInputScreen_PulseField`: 脈拍入力フィールド
- `BatchInputScreen_TempField`: 体温入力フィールド
- `BatchInputScreen_GlucoseField`: 血糖値入力フィールド
- `BatchInputScreen_Hba1cField`: HbA1c入力フィールド
- `BatchInputScreen_InputScrollColumn`: 入力項目のスクロールコンテナ
- `BatchInputScreen_DiscardConfirmButton`: 破棄確認ダイアログの確定ボタン
- `BatchInputScreen_DiscardCancelButton`: 破棄確認ダイアログのキャンセルボタン
