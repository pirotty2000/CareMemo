# UI層テスト仕様書 - BatchInput (健康記録一括入力)

- **対象テストコード:**
    - `androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/health/BatchInputScreenTest.kt`
    - `test/java/jp/mydns/fujiwara/carememo/viewmodel/BatchInputViewModelTest.kt`

## 1. 画面表示テスト (BatchInputScreenContent)
**目的:** 一括入力画面の各項目が正しく表示されていることを検証する。

| ID | テスト項目 | 検証内容 |
|:---|:---|:---|
| CP-01 | 記録日時入力 | 年月日時分の入力フィールドが表示されていること。 |
| CP-02 | 身長・体重入力 | 身長と体重の入力フィールドが表示されていること。 |
| CP-03 | バイタル入力 | 血圧（最高・最低）、SpO2、脈拍、体温の入力フィールドが表示されていること。 |
| CP-04 | 血糖値入力 | 血糖値、HbA1cの入力フィールドが表示されていること。 |
| CP-05 | 保存・キャンセルボタン | 保存ボタンとキャンセルボタンが表示されていること。 |

## 2. 画面全体の挙動・結合テスト (BatchInputScreen)
**目的:** データの保存や重複チェックなどの挙動を検証する。

| ID | テスト項目 | 検証内容 |
|:---|:---|:---|
| BH-01 | データ保存の動作 | 数値を入力して保存ボタンを押した際、ViewModelの保存処理が呼ばれること。 |
| BH-02 | 入力バリデーション | 不正な日時が入力されている場合、保存ボタンが無効化されること。 |
| BH-03 | 保存成功時の演出 | 保存成功時に画面がフラッシュ（成功演出）し、スクロールがトップに戻ること。 |
| BH-04 | 重複ガード | 既存データと重複する項目がある場合、エラーダイアログに該当カテゴリが表示され、保存がブロックされること。 |

## 3. テスト用タグ (testTag) 定義
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

## 4. 実装状況
今回のUIテスト実装（`BatchInputScreenTest.kt`）において、定義されたすべての項目が実装され、検証されている。

| セクション | 項目 ID | ステータス | 備考 |
|:---|:---|:---:|:---|
| 1. 画面表示 | CP-01 〜 CP-05 | ✅ 実装済み | 各カテゴリの入力フィールド表示。 |
| 2. 画面挙動・結合 | BH-01 〜 BH-04 | ✅ 実装済み | 保存動作、バリデーション、成功演出、重複ガード。 |
