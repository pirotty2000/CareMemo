# テスト仕様書 - SCR-PH-002 BatchInputScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/health/BatchInputScreenTest.kt`
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/BatchInputViewModelTest.kt`

## 1. コンポーネント単体テスト (BatchInputScreenContent)
**目的:** 一括入力画面の各項目が正しく表示されていることを検証する。

| ID    | テスト項目       | 検証内容                                            |
|:------|:------------|:------------------------------------------------|
| CP-01 | 記録日時入力      | 年月日時分の入力フィールドが表示されていること。                        |
| CP-02 | 身長・体重入力     | 身長と体重の入力フィールドが表示されていること。                        |
| CP-03 | バイタル入力      | 血圧（最高・最低）、SpO2、脈拍、体温の入力フィールドが表示されていること。         |
| CP-04 | 血糖値入力       | 血糖値、HbA1cの入力フィールドが表示されていること。                    |
| CP-05 | 保存・キャンセルボタン | 保存ボタンとキャンセルボタンが表示されていること。                       |
| CP-06 | スクロール操作の視認性 | 入力項目が多い際、スクロールがスムーズに行え、最下部の項目まで確実にアクセス・入力できること。 |

## 2. 画面全体の挙動・結合テスト (BatchInputScreen)
**目的:** データの保存や重複チェックなどの挙動を検証する。

| ID    | テスト項目       | 検証内容                                                                     |
|:------|:------------|:-------------------------------------------------------------------------|
| BH-01 | データ保存の動作    | 数値を入力して保存ボタンを押した際、ViewModelの保存処理が呼ばれること。                                 |
| BH-02 | 入力バリデーション   | 全てが未入力、または不正な値が入力されている場合、保存ボタンが無効化されること。                                 |
| BH-03 | 保存成功時の演出    | 保存成功時に画面がフラッシュ（成功演出）し、スクロールがトップに戻ること。                                    |
| BH-04 | 重複ガード       | 既存データと重複する項目がある場合、エラーダイアログに該当カテゴリが表示され、保存がブロックされること。                     |
| BH-05 | 連続入力の維持     | 保存完了後、**記録日時は保持され**、その他の入力フィールドがクリアされた状態で画面が維持されること。                     |
| BH-06 | キャンセル時の破棄確認 | 変更がある状態（数値入力または**記録日時の変更**）で「戻る」または「キャンセル」を操作した際、入力内容破棄の確認ダイアログが表示されること。 |
| BH-07 | 保存後の破棄確認抑止  | 保存成功直後（変更がない状態）で「戻る」を操作した際、ダイアログを表示せずに画面が閉じること。                          |

## 3. ロジック・安全性テスト (BatchInputViewModel)
**目的:** 例外発生時も保存中フラグが適切に解除され、画面がフリーズしないことを検証する。

| ID    | テスト項目        | 検証内容                                                                            |
|:------|:-------------|:--------------------------------------------------------------------------------|
| LG-01 | 一括保存失敗時の安全性  | 保存中に例外が発生した際、`uiState.isLoading` が `false` になり、エラー通知が行われること。                    |
| LG-02 | バリデーション結果の翻訳 | `BatchInputLogic.validate` の結果（事実）が、適切な `AppValidationException`（UI通知）に翻訳されること。 |
| LG-03 | 重複カテゴリの識別    | 重複が発生したカテゴリ名がエラーメッセージ（引数）として正しく組み立てられていること。                                     |
| LG-04 | 状態の原子性       | 入力値や日時の更新と同時に、`isValid` や `isChanged` が同一の `UiState` 更新サイクルで反映されること。            |

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
- `BatchInputScreen_DiscardConfirmButton`: 破棄確認ダイアログの確定（破棄）ボタン
- `BatchInputScreen_DiscardCancelButton`: 破棄確認ダイアログのキャンセル（編集継続）ボタン

## 5. 実装状況
| セクション        | 項目 ID         | ステータス  | 備考                    |
|:-------------|:--------------|:------:|:----------------------|
| 1. コンポーネント単体 | CP-01 〜 CP-06 | ✅ 実装済み | スクロール視認性を追加。          |
| 2. 画面挙動・結合   | BH-01 〜 BH-07 | ✅ 実装済み | 日時変更時の破棄確認を追加。        |
| 3. ロジック・安全性  | LG-01 〜 LG-03 | ✅ 実装済み | ViewModelユニットテストで検証済。 |
