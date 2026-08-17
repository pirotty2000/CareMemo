# テスト仕様書 - SCR-PH-001 PersonHealthScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/health/PersonHealthScreenTest.kt`

## 1. 概要
健康記録画面（PersonHealthScreen）における Adaptive UI（Phone/Tablet 分岐）の整合性、各共通コンポーネント（履歴・グラフ・入力）の描画、および ViewModel 連携による状態遷移・ナビゲーションを検証する。
本画面は「画面エントランス」「Phone用レイアウト」「Tablet用レイアウト」「共通コンテンツ」の4層構造で実装されており、テストもそれぞれの役割に応じた検証を行う。

## 2. Adaptive Layout 検証 (Adaptive)
**目的:** デバイスの画面幅（WindowSizeClass）に応じて、適切なレイアウト構成が選択されることを検証する。

| ID     | テスト項目        | 条件 (WindowSize) | 期待結果                                           |
|:-------|:-------------|:----------------|:-----------------------------------------------|
| ADP-01 | Phone版レイアウト  | `Compact` (幅狭)  | 履歴・グラフ・入力が切り替え式で表示され、プロパティに応じたタブが選択されていること     |
| ADP-02 | Tablet版レイアウト | `Expanded` (幅広) | 履歴リストと詳細（グラフまたは入力）が左右 2 カラムで **同時に** 表示されていること |

## 3. コンポーネント描画検証 (Components)
**目的:** `PersonHealthScreenContent.kt` および関連部品が、渡された State を正しく描画することを検証する。

| ID     | テスト項目    | 条件 (State)                     | 期待結果                                 |
|:-------|:---------|:-------------------------------|:-------------------------------------|
| CPN-01 | 履歴リスト描画  | 有効なレコードデータ                     | 日付、計測値（身長、体重、血圧等）がカテゴリに応じた形式で表示されること |
| CPN-02 | グラフエリア描画 | `preferredShowHistory = false` | 計測値の推移グラフが表示されていること                  |
| CPN-03 | 空状態の表示   | レコード 0 件                       | 「記録がありません」等のメッセージが表示されること            |
| CPN-04 | 入力フォーム表示 | `selectedRecordId` が非 null     | カテゴリに応じた入力フィールド（数値入力等）が表示されること       |

## 4. 状態・インタラクション検証 (Interaction)
**目的:** ユーザーの操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目    | 操作              | 期待結果 (呼び出される Intent)                           |
|:-------|:---------|:----------------|:-----------------------------------------------|
| ACT-01 | カテゴリ切り替え | カテゴリバーのタブをタップ   | `navigateToCategory` (DetailViewModel) が呼ばれること |
| ACT-02 | 表示モード変更  | 履歴/グラフのスイッチをタップ | `updatePreferredShowHistory` が呼ばれること           |
| ACT-03 | レコード選択   | 履歴リストの項目をタップ    | `setSelectedRecordId` が該当 ID で呼ばれること           |
| ACT-04 | 保存処理の実行  | 保存ボタンをタップ       | `saveCurrentEdit()` が ViewModel 側で実行されること      |

## 5. ナビゲーション・副作用検証 (Navigation)
**目的:** ViewModel から発行される `ViewEvent` や `UiEvent` に応じて、正しい画面遷移や通知が実行されることを検証する。

| ID     | テスト項目   | 発行イベント                     | 期待結果 (UI の挙動)                                              |
|:-------|:--------|:---------------------------|:-----------------------------------------------------------|
| NAV-01 | グラフ拡大遷移 | `NavigateToGraphExpansion` | 指定されたカテゴリと期間を伴って拡大画面へ遷移すること                                |
| NAV-02 | 戻る操作の実行 | 戻るボタンタップ                   | `navController.popBackStack()` または ViewModel の戻る処理が実行されること |
| EVT-01 | 保存成功通知  | `UiEvent.SaveSuccess`      | 選択状態がリセット（一覧表示へ戻る）されること                                    |

## 6. セキュリティ検証 (Security)
**目的:** 重要操作時に適切な保護がかかることを検証する。

| ID     | テスト項目      | 操作                      | 期待結果                                       |
|:-------|:-----------|:------------------------|:-------------------------------------------|
| SEC-01 | PDF出力時の再認証 | PDF設定ダイアログで「PDFを作成」をタップ | `onRequireAuthentication` が適切なメッセージで呼ばれること |

## 7. テスト用タグ (testTag)
- `HealthScreen_PhoneContent`: Phone用レイアウトコンテナ
- `HealthScreen_TabletContent`: Tablet用レイアウトコンテナ
- `HealthScreen_HistoryList`: 履歴リストコンテナ
- `HealthScreen_GraphArea`: グラフ表示エリア
- `HealthScreen_InputForm`: 入力フォームエリア
- `HealthScreen_HistoryGraphSwitch`: 履歴/グラフ切り替えスイッチ
- `CategorySelectorBar`: カテゴリ選択バー
- `HealthScreen_AddButton`: 新規登録 FAB
- `HealthScreen_BackButton`: 戻るボタン
- `HealthScreen_PdfButton`: PDF出力ボタン
