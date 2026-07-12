# テスト仕様書 - PersonMedication (服薬管理)

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/medication/PersonMedicationScreenTest.kt`
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/PersonMedicationViewModelTest.kt`

## 1. 共通コンポーネントテスト (Header / CategoryBar)
**目的:** 全詳細画面で共通となる上部エリアの表示と基本操作を検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| COM-01 | ヘッダー：戻るボタン | 戻るアイコンが表示され、タップ時に `onBack` が呼ばれること。 |
| COM-02 | ヘッダー：利用者情報 | 利用者の氏名と年齢が正しく表示されていること。 |
| COM-03 | ヘッダー：PDF出力ボタン | PDF出力アイコンが表示され、タップ時に動作すること。 |
| COM-04 | カテゴリ選択バー | 現在のカテゴリが選択状態で表示され、他カテゴリタップ時に遷移すること。 |

## 2. コンポーネント単体テスト (PersonMedicationScreenContent)
**目的:** 服薬管理画面固有のレイアウト表示を検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| CP-01 | ローディング表示 | `isLoading = true` の時、LoadingScreenが表示されること。 |
| CP-02 | スマホ版：デフォルト表示 | カレンダーが表示され、履歴テーブルが表示されていないこと。 |
| CP-03 | スマホ版：履歴モード切り替え | セグメントボタンで「履歴」を選択時、履歴テーブルが表示されること。 |
| CP-04 | スマホ版：履歴空状態 | 履歴データが0件の時、「記録がありません」と表示されること。 |
| CP-05 | タブレット版：2カラム表示 | カレンダーと履歴テーブルが同時に表示されていること。 |
| CP-06 | 月間ナビゲーション表示 | 選択された年月（和暦付き）が正しく表示されていること。 |

## 3. 画面全体の挙動・結合テスト (PersonMedicationScreen)
**目的:** ユーザーの操作が正しくViewModelに伝わり、UIが適切に反応することを検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| BH-01 | 前月/次月ボタンの動作 | ボタン押下時、ViewModelの `previousMonth()` / `nextMonth()` が呼ばれること。 |
| BH-02 | ダイアログの表示 | カレンダーの特定の日付をタップした際、入力ダイアログが表示されること。 |
| BH-03 | ダイアログの保存 | ダイアログで保存した際、ViewModelの `syncMedicationDay()` が呼ばれること。 |

## 4. ロジック・安全性テスト (PersonMedicationViewModel)
**目的:** 例外発生時もローディング状態が適切に解除され、画面がフリーズしないことを検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| LG-01 | データ取得失敗時の安全性 | 月間データ取得中（Flow）に例外が発生した際、`isLoading` が `false` になり、監査ログに記録されること。 |
| LG-02 | 同期失敗時の安全性 | データの同期中に例外が発生した際、`isLoading` が `false` になり、監査ログに記録されること。 |

## 5. テスト用タグ (testTag) 定義
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

## 6. 実装状況
| セクション | 項目 ID | ステータス | 備考 |
| :--- | :--- | :---: | :--- |
| 1. 共通コンポーネント | COM-01 〜 COM-04 | ✅ 実装済み | ヘッダーおよびカテゴリ選択。 |
| 2. コンポーネント単体 | CP-01 〜 CP-06 | ✅ 実装済み | カレンダー・履歴の表示切替。 |
| 3. 画面全体の挙動 | BH-01 〜 BH-03 | ✅ 実装済み | 月移動、ダイアログ保存。 |
| 4. ロジック・安全性 | LG-01 〜 LG-02 | ✅ 実装済み | 例外発生時のローディング解除検証済。 |
