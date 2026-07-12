# UI層テスト仕様書 - PersonMedication (服薬管理)

- **対象テストコード:**
    - `androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/medication/PersonMedicationScreenTest.kt`
    - `test/java/jp/mydns/fujiwara/carememo/viewmodel/PersonMedicationViewModelTest.kt`

## 1. 詳細画面共通コンポーネント (Header / CategoryBar)
**目的:** 全詳細画面で共通となる上部エリアの表示と基本操作を検証する。

| ID | テスト項目 | 検証内容 |
|:---|:---|:---|
| COM-01 | ヘッダー：戻るボタン | 戻るアイコンが表示され、タップ時に `onBack` が呼ばれること。 |
| COM-02 | ヘッダー：利用者情報 | 利用者の氏名（例: 山田 太郎）と年齢が正しく表示されていること。 |
| COM-03 | ヘッダー：PDF出力ボタン | PDF出力アイコンが表示され、タップ時に設定ダイアログまたはエラー（空時）が表示されること。 |
| COM-04 | カテゴリ選択バー | 現在のカテゴリが選択状態で表示され、他カテゴリタップ時に `onNavigateToCategory` が呼ばれること。 |

## 2. 個別コンポーネント単体テスト (PersonMedicationScreenContent)
**目的:** 服薬管理画面固有のレイアウト表示を検証する。

| ID | テスト項目 | 検証内容 |
|:---|:---|:---|
| CP-01 | ローディング表示 | `isLoading = true` の時、LoadingScreenが表示されること。 |
| CP-02 | スマホ版：デフォルト表示 | カレンダーが表示され、履歴テーブルが表示されていないこと。 |
| CP-03 | スマホ版：履歴モード切り替え | セグメントボタンで「履歴」を選択時、カレンダーが消え、履歴テーブルが表示されること。 |
| CP-04 | スマホ版：履歴空状態 | 履歴データが0件の時、履歴テーブル内に「記録がありません」と表示されること。 |
| CP-05 | タブレット版：2カラム表示 | カレンダーと履歴テーブルが同時に表示されていること。 |
| CP-06 | 月間ナビゲーション表示 | 選択された年月（例: 2026(令和8)年07月）が正しく表示されていること。 |

## 3. 画面全体の挙動・結合テスト (PersonMedicationScreen)
**目的:** ユーザーの操作が正しくViewModelに伝わり、UIが適切に反応することを検証する。

| ID | テスト項目 | 検証内容 |
|:---|:---|:---|
| BH-01 | 前月/次月ボタンの動作 | ナビゲーションボタン押下時、ViewModelの `previousMonth()` / `nextMonth()` が呼ばれること。 |
| BH-02 | ダイアログの表示 | カレンダーの特定の日付（例: 10日）をタップした際、その日付の入力ダイアログが表示されること。 |
| BH-03 | ダイアログの保存 | ダイアログでステータスを選択し保存した際、ViewModelの `syncMedicationDay()` が呼ばれること。 |

## 4. テスト用タグ (testTag) 定義
- `Medication_BackButton`: 戻るボタン
- `PersonHeader_NameAndAge`: 利用者氏名・年齢
- `Medication_PdfButton`: PDF出力ボタン
- `CategorySelectorBar`: カテゴリ選択バー
- `Medication_Loading`: ローディング画面
- `Medication_Calendar`: カレンダーグリッド
- `Medication_HistoryTable`: 履歴テーブル
- `Medication_MonthPrev` / `Medication_MonthNext`: 月移動ボタン
- `Medication_MonthText`: 現在の年月表示
- `Medication_Dialog_Save` / `Medication_Dialog_Cancel`: ダイアログボタン

## 5. 実装状況
今回のUIテスト実装（`PersonMedicationScreenTest.kt`）において、定義されたすべての項目が実装され、検証されている。

| セクション | 項目 ID | ステータス | 備考 |
|:---|:---|:---:|:---|
| 1. 共通コンポーネント | COM-01 〜 COM-04 | ✅ 実装済み | ヘッダーおよびカテゴリ選択の動作。 |
| 2. コンポーネント単体 | CP-01 〜 CP-06 | ✅ 実装済み | カレンダー・履歴の切替、年月表示。 |
| 3. 画面挙動・結合 | BH-01 〜 BH-03 | ✅ 実装済み | 前後月移動、ダイアログ表示と保存動作。 |
