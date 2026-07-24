# テスト仕様書 - SCR-PH-002 BatchInputScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/health/BatchInputScreenTest.kt`
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/BatchInputViewModelTest.kt`

## 1. コンポーネント単体テスト (BatchInputScreenContent)
**目的:** 一括入力画面の各項目が正しく表示されていることを検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| CP-01 | 記録日時入力 | 年月日時分の入力フィールドが表示されていること。 |
| CP-02 | 身長・体重入力 | 身長と体重の入力フィールドが表示されていること。 |
| CP-03 | バイタル入力 | 血圧（最高・最低）、SpO2、脈拍、体温の入力フィールドが表示されていること。 |
| CP-04 | 血糖値入力 | 血糖値、HbA1cの入力フィールドが表示されていること。 |
| CP-05 | 保存・キャンセルボタン | 保存ボタンとキャンセルボタンが表示されていること。 |

## 2. 画面全体の挙動・結合テスト (BatchInputScreen)
**目的:** データの保存や重複チェックなどの挙動を検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| BH-01 | データ保存の動作 | 数値を入力して保存ボタンを押した際、ViewModelの保存処理が呼ばれること。 |
| BH-02 | 入力バリデーション | 不正な日時が入力されている場合、保存ボタンが無効化されること。 |
| BH-03 | 保存成功時の演出 | 保存成功時に画面がフラッシュ（成功演出）し、スクロールがトップに戻ること。 |
| BH-04 | 重複ガード | 既存データと重複する項目がある場合、エラーダイアログに該当カテゴリが表示され、保存がブロックされること。 |

## 3. ロジック・安全性テスト (BatchInputViewModel)
**目的:** 例外発生時も保存中フラグが適切に解除され、画面がフリーズしないことを検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| LG-01 | 一括保存失敗時の安全性 | 保存中に例外が発生した際、`isSaving` が `false` になり、エラーダイアログが表示され、監査ログに "ERROR" が記録されること。 |

## 4. テスト用タグ (testTag) 定義
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

## 5. 実装状況
| セクション | 項目 ID | ステータス | 備考 |
| :--- | :--- | :---: | :--- |
| 1. コンポーネント単体 | CP-01 〜 CP-05 | ✅ 実装済み | 各カテゴリの入力フィールド表示。 |
| 2. 画面挙動・結合 | BH-01 〜 BH-04 | ✅ 実装済み | 保存動作、バリデーション、成功演出、重複ガード。 |
| 3. ロジック・安全性 | LG-01 | ✅ 実装済み | ViewModelユニットテストで検証済。 |
