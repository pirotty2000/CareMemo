# UI層テスト仕様書 - DeleteOrRestorePerson (利用者復帰・完全抹消)

- **対象テストコード:**
    - `androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/settings/DeleteOrRestorePersonTest.kt`

## 1. 画面表示テスト (DeleteOrRestorePersonScreen)
**目的:** 復帰・抹消の各モードに応じてUIが適切に切り替わり、利用者の情報が正しく表示されることを検証する。

| ID | テスト項目 | 検証内容 |
|:---|:---|:---|
| CP-01 | 基本表示 (復帰モード) | タイトル「利用者の復帰」が表示され、TopBar が標準色（Primary）であること。 |
| CP-02 | 基本表示 (抹消モード) | タイトル「利用者の完全抹消」が表示され、TopBar および背景が警告色（Error）系であること。 |
| CP-03 | 警告文の表示 (抹消モード) | 抹消モード時のみ、画面上部に「二度と復元できません」といった注意喚起テキストが表示されること。 |
| CP-04 | 空状態の表示 | 終了した利用者がいない場合、専用の空状態（EmptyState）メッセージが表示されること。 |
| CP-05 | 利用者リスト表示 | アーカイブされた利用者の氏名、年齢、識別メモがリスト形式で正しく表示されていること。 |
| CP-06 | 全選択ボタンの制御 | 復帰モードでは「全選択/全解除」ボタンが表示され、抹消モードでは非表示（誤操作防止）であること。 |

## 2. 画面全体の挙動・結合テスト (DeleteOrRestorePersonScreen)
**目的:** 選択操作やモードに応じた最終確認、ViewModel との連動が正しく機能することを検証する。

| ID | テスト項目 | 検証内容 |
|:---|:---|:---|
| BH-01 | チェック選択操作 | 各項目のチェックボックスをタップして、ViewModel の選択状態（selectedIds）が更新されること。 |
| BH-02 | 全選択・全解除 | 復帰モードで「全選択」をタップした際、リスト全員にチェックが入り、「全解除」に切り替わること。 |
| BH-03 | 実行ボタンの表示制御 | 1名以上選択されている時のみ、下部に実行ボタン（「〜名を選択して復帰/抹消」）が表示されること。 |
| BH-04 | 復帰の実行 | 復帰モードでボタンをタップした際、ViewModel の `restoreSelectedPersons` が呼ばれること。 |
| BH-05 | 抹消の最終確認 | 抹消モードでボタンをタップした際、即座に実行されず最終確認ダイアログが表示されること。 |
| BH-06 | 抹消の実行 | 最終確認ダイアログで「抹消を実行する」をタップした際、ViewModel の `deleteSelectedPersons` が呼ばれること。 |
| BH-07 | 戻る操作 | 戻るボタンをタップした際、`onBack` コールバックが呼ばれること。 |

## 3. テスト用タグ (testTag) 定義
- `DeleteOrRestore_BackButton`: 戻るボタン
- `DeleteOrRestore_SelectAllButton`: 全選択/全解除ボタン
- `DeleteOrRestore_EmptyState`: 空状態表示
- `DeleteOrRestore_WarningBanner`: 抹消モード時の警告バナー
- `DeleteOrRestore_List`: 利用者リスト
- `DeleteOrRestore_Item_{id}`: 各利用者の項目
- `DeleteOrRestore_Checkbox_{id}`: 各利用者のチェックボックス
- `DeleteOrRestore_ActionButton`: 画面下部の実行ボタン（復帰/抹消）
- `DeleteOrRestore_ConfirmDialog`: 抹消最終確認ダイアログ

## 4. 実装状況
今回のUIテスト実装（`DeleteOrRestorePersonTest.kt`）において、定義されたすべての項目が実装され、検証されている。

| セクション | 項目 ID | ステータス | 備考 |
|:---|:---|:---:|:---|
| 1. 画面表示 | CP-01 〜 CP-06 | ✅ 実装済み | 復帰/抹消モード切替、警告表示、空状態、リスト表示、全選択制御の網羅。 |
| 2. 画面挙動・結合 | BH-01 〜 BH-07 | ✅ 実装済み | チェック操作、全選択、ボタン制御、復帰/抹消実行、最終確認、戻る操作の網羅。 |
