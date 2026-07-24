# テスト仕様書 - SCR-PC-001 PersonConditionScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionScreenTest_1_Common.kt`
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionScreenTest_2_Component.kt`
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionScreenTest_3_Behavior.kt`
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionScreenTest_4_Logic.kt`
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/PersonConditionViewModelTest.kt`

## 0. 二段構え ViewModel (Dual-ViewModel) の役割
本画面は、共通フレームワークと専門ロジックを分離して管理します。

- **PersonDetailUiStateViewModel**: 利用者基本情報の保持、カテゴリ切り替え、共通UIイベントを担当。
- **PersonConditionViewModel**: 所見メモ固有のデータ操作（CRUD）とビジネスロジックを担当。

## 1. 共通コンポーネントテスト (Header / CategoryBar)
**目的:** 全詳細画面で共通となる上部エリアの表示と基本操作を検証する。

| ID     | テスト項目         | 検証内容                                                                          |
|:-------|:--------------|:------------------------------------------------------------------------------|
| COM-01 | ヘッダー：戻るボタン    | 戻るアイコンが表示され、タップ時に `onBack` が呼ばれること。                                           |
| COM-02 | ヘッダー：利用者情報    | 利用者の氏名（全角スペース区切り）と年齢が正しく表示されていること。                                            |
| COM-03 | ヘッダー：PDF出力ボタン | PDF出力アイコンが表示され、タップ時に動作すること。                                                   |
| COM-04 | カテゴリ選択バー      | タップ操作により、**利用者IDを保持したまま** `SCR-PH-001` (健康) および `SCR-PM-001` (服薬) へ正しく遷移すること。 |

## 2. コンポーネント単体テスト (PersonConditionScreenContent)
**目的:** 所見記録固有のレイアウト表示を検証する。

| ID    | テスト項目         | 検証内容                                          |
|:------|:--------------|:----------------------------------------------|
| CP-01 | スマホ版：日付選択表示   | 現在選択されている日付（和暦・曜日付き）が表示されていること。               |
| CP-02 | スマホ版：メモ入力エリア  | テキスト入力欄が表示され、既存のメモが反映されていること。                 |
| CP-03 | スマホ版：写真リスト表示  | 添付されている写真がサムネイル形式で一覧表示されること。                  |
| CP-04 | スマホ版：写真なし状態   | 写真が1枚もない時、「写真がありません」と表示されること。                 |
| CP-05 | タブレット版：2カラム表示 | 履歴リストと詳細内容が同時に表示されていること。                      |
| CP-06 | メモ入力のスクロール    | 非常に長いメモが入力された際、スクロールがスムーズに行え、最下部まで表示・編集できること。 |

## 3. 画面全体の挙動・結合テスト (PersonConditionScreen)
**目的:** 保存、カメラ起動などの挙動を検証する。

| ID    | テスト項目        | 検証内容                                                                    |
|:------|:-------------|:------------------------------------------------------------------------|
| BH-01 | メモの保存        | 保存ボタンを押した際、ViewModel経由でRepositoryの監査ログ用引数が正しく渡され、保存が完了すること。             |
| BH-02 | 写真撮影への遷移     | 写真追加ボタン押下によりカメラが起動し、撮影成功後に `SCR-PC-002` へ正しく遷移すること。                     |
| BH-03 | 日時重複時の保存ガード  | 既存データと同一日時の入力を保存しようとした際、エラーダイアログが表示され、保存が中断されること。                       |
| BH-04 | 写真フル画面への遷移   | 写真の閲覧操作により、ID を保持して `SCR-PC-003` へ正しく遷移すること。                            |
| BH-05 | 詳細画面からの戻り    | `SCR-PC-002` や `SCR-PC-003` から戻った際、利用者IDや入力中のメモ内容が維持されていること。            |
| BH-06 | 撮影キャンセル時の挙動  | カメラアプリから写真なしで戻った際、エラーが発生せず、元の状態が維持されていること。                              |
| BH-07 | プレビュー保存後の反映  | `SCR-PC-002` で保存して戻った際、リストに新しい写真が反映されること。                               |
| BH-08 | PDF出力設定      | PDF出力設定ダイアログにおいて、所見特有の項目（写真の有無等）が正しく表示・機能すること。                          |
| BH-09 | 撮影失敗時の通知     | カメラアプリ側で異常が発生し `success=false` で戻った際、ログが出力され、必要に応じてユーザーに通知されること。        |
| BH-10 | 入力不備のバリデーション | 内容が空、記録者が空、または文字数制限(1000文字)超過の状態で保存しようとした際、具体的な不備内容を示すエラーダイアログが表示されること。 |

## 4. ロジック・安全性テスト (PersonConditionViewModel)
**目的:** 例外発生時もローディング状態が適切に解除され、画面がフリーズしないことを検証する。

| ID    | テスト項目         | 検証内容                                                                                                                                                      |
|:------|:--------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------|
| LG-01 | データロード失敗時の安全性 | 履歴データや写真データの取得中に例外が発生した際、`uiState.isLoading` が `false` になり、エラー通知が行われること。                                                                                  |
| LG-02 | 保存失敗時の安全性     | 所見メモや写真の保存中に例外が発生した際、`uiState.isProcessing` が `false` になり、エラーダイアログが表示されること。                                                                               |
| LG-03 | 検索と連動した原子性    | `searchQuery` を更新した際、同一の `UiState` 更新サイクルで `filteredRecords` が正しくフィルタリングされること。                                                                            |
| LG-04 | 写真データの連動      | `selectedConditionId` を変更した際、自動的にその ID に紐づく写真リストの購読が開始されること。                                                                                              |
| LG-04 | 写真保存失敗時の安全性   | 写真の最適化・保存中に例外が発生した際、監査ログに **`IO_ERROR`** が記録され、`isProcessing` が解除されること。                                                                                   |
| LG-05 | 写真削除失敗時の安全性   | 写真の削除中に例外が発生した際、監査ログに **`IO_ERROR`** が記録され、`isProcessing` が解除されること。                                                                                       |
| LG-06 | 撮影準備失敗時の安全性   | 撮影・選択のエラー通知時、`errorMessage` がセットされ、監査ログにエラーが記録されること。                                                                                                      |
| LG-07 | バリデーション結果の翻訳  | `PersonConditionLogic.validate` が返した「事実（Enum）」を ViewModel 内で UI 通知用の `AppValidationException` に翻訳し、`safeLaunch` を介して監査ログに `VALIDATION_ERROR` が正しく記録されること。 |

## 5. ViewModel 内部ロジックテスト (PersonConditionViewModel)
**目的:** UIを伴わない純粋なデータ処理・状態遷移の正しさを検証する。

| ID     | テスト項目     | 検証内容                                                        |
|:-------|:----------|:------------------------------------------------------------|
| VML-01 | 検索フィルタリング | 検索クエリの入力により、`filteredRecords` がタイトルや内容で正しく絞り込まれること。         |
| VML-02 | 写真の自動ロード  | `selectedConditionId` が更新された際、対応する写真リストがリポジトリから自動的に取得されること。 |

## 6. テスト用タグ (testTag) 定義
- `ConditionScreen_BackButton`: 戻るボタン
- `PersonHeader_Title`: 利用者氏名・年齢エリア
- `ConditionScreen_PdfButton`: PDF出力ボタン
- `CategorySelectorBar`: カテゴリ選択バー
- `Condition_MemoInput`: メモ入力フィールド
- `Condition_SaveButton`: 保存ボタン
- `Condition_PhotoList`: 写真リスト
- `Condition_AddPhotoButton`: 写真追加ボタン
- `AppLoadingIndicator`: ローディング画面 (LoadingScreen)

## 7. 実装状況
| セクション        | 項目 ID           | ステータス  | 備考                                     |
|:-------------|:----------------|:------:|:---------------------------------------|
| 1. 共通コンポーネント | COM-01 〜 COM-04 | ✅ 実装済み | COM-02: 全角スペース区切りに対応。                  |
| 2. コンポーネント単体 | CP-01 〜 CP-06   | ✅ 実装済み | CP-01: 時刻だけでなく和暦日付表示を追加。               |
| 3. 画面挙動・結合   | BH-01 〜 BH-09   | ✅ 実装済み | BH-09: 撮影失敗ハンドリングを ViewModel/UI 両面で強化。 |
| 4. ロジック・安全性  | LG-01 〜 LG-06   | ✅ 実装済み | LG-06: notifyPhotoError によるログ記録を実装。    |
| 5. 内部ロジック    | VML-01 〜 VML-02 | ✅ 実装済み | ViewModelTest にて検証済み。                  |
