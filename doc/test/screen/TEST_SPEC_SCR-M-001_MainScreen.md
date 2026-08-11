# テスト仕様書 - SCR-M-001 MainScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/main/MainScreenTest.kt`

## 1. 概要
利用者一覧画面（MainScreen）における表示（Rendering）および、ユーザー操作に伴う ViewModel への意図（Intent）伝達、ナビゲーションの実行を検証する。
ビジネスロジックやデータ整合性は ViewModel の単体テストで保証されているため、本テストでは UI 層の責務に特化する。

## 2. 表示テスト (Display)
**目的:** `PersonListUiState` の各状態に基づき、コンポーネントが正しく配置・描画されることを検証する。

| ID     | テスト項目     | 条件 (UiState)                  | 期待結果                              |
|:-------|:----------|:------------------------------|:----------------------------------|
| DSP-01 | ローディング表示  | `isLoading = true`            | 画面全体にローディングインジケータが表示されること         |
| DSP-02 | 利用者リスト描画  | `userList` に有効なデータがある         | 指定された件数分の `UserListItem` が表示されること |
| DSP-03 | 空状態の表示    | `userList` が空かつ検索クエリなし        | 「利用者が登録されていません」等のメッセージが表示されること    |
| DSP-04 | 検索結果なし表示  | `userList` が空かつ検索クエリあり        | 「該当する利用者はいません」等のメッセージが表示されること     |
| DSP-05 | 氏名伏せ字の適用  | `isNameMaskingEnabled = true` | リスト内の氏名が「○」等で伏せ字化されていること          |
| DSP-06 | カテゴリバッジ描画 | サマリーに記録あり (`hasCondition` 等)  | 該当するカテゴリのバッジ（アイコン）が表示されていること      |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目      | 操作                 | 期待結果 (呼び出される Intent)                             |
|:-------|:-----------|:-------------------|:-------------------------------------------------|
| ACT-01 | 検索入力       | 検索窓に文字を入力          | `setSearchQuery` が入力値とともに呼ばれること                  |
| ACT-02 | セクション選択    | 五十音索引（あ、か...）をタップ  | `setSelectedSection` が選択した文字で呼ばれること              |
| ACT-03 | クイックメニュー表示 | 利用者を長押し、またはバッジをタップ | `showQuickMenu` が該当する利用者で呼ばれること                  |
| ACT-04 | 緊急連絡先ロード   | メニューから「緊急連絡先」を選択   | `loadEmergencyContacts` が呼ばれること                  |
| ACT-05 | 利用終了の即時実行  | スワイプ等で「利用終了」を選択    | `logicalDeletePerson` が呼ばれ、Undo 用のスナックバーが表示されること |

## 4. ナビゲーション実行テスト (Navigation)
**目的:** ViewModel から発行された `PersonListViewEvent` を受け、実際に正しい目的地へ遷移することを検証する。

| ID     | テスト項目   | 発行される ViewEvent                         | 期待結果 (遷移先)                       |
|:-------|:--------|:----------------------------------------|:---------------------------------|
| NAV-01 | 詳細画面遷移  | `NavigateToDetail(id, category, query)` | 指定された ID、カテゴリ、検索語を伴って詳細画面へ遷移すること |
| NAV-02 | 利用者編集遷移 | `NavigateToEditPerson(id)`              | 該当利用者の編集画面へ遷移すること                |
| NAV-03 | 新規登録遷移  | `NavigateToAddPerson`                   | 新規登録モードで編集画面へ遷移すること              |
| NAV-04 | 設定画面遷移  | `NavigateToSettings`                    | 設定画面へ遷移すること                      |

## 5. テスト用タグ (testTag)
- `MainScreen_Loading`: ローディング表示
- `MainScreen_EmptyState`: データなし表示
- `MainScreen_UserList`: 利用者リストコンテナ
- `UserListItem_{id}`: 利用者カード（ID指定）
- `MainScreen_SearchBox`: 検索入力フィールド
- `MainScreen_KanaIndexBar`: 五十音索引バー
- `MainScreen_AddButton`: 新規登録 FAB
- `MainScreen_MenuButton`: ハンバーガーメニューボタン
- `QuickActionMenu`: クイックアクション用ダイアログ/シート
- `EmergencyContactSheet`: 緊急連絡先用ボトムシート
