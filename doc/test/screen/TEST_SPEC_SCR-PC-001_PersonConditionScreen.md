# テスト仕様書 - SCR-PC-001 PersonConditionScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionScreenTest.kt`

## 1. 概要
所見メモ画面（PersonConditionScreen）における Adaptive UI（Phone/Tablet 分岐）の整合性、各共通コンポーネントの描画、および ViewModel 連携による状態遷移・ナビゲーションを検証する。
本画面は「画面エントランス」「Phone用レイアウト」「Tablet用レイアウト」「共通部品」の4層構造で実装されており、テストもそれぞれの役割に応じた検証を行う。

## 2. Adaptive Layout 検証 (Adaptive)
**目的:** デバイスの画面幅（WindowSizeClass）に応じて、適切なレイアウト構成が選択されることを検証する。

| ID     | テスト項目        | 条件 (WindowSize) | 期待結果                                      |
|:-------|:-------------|:----------------|:------------------------------------------|
| ADP-01 | Phone版レイアウト  | `Compact` (幅狭)  | 履歴リストと詳細（入力欄）が排他的に表示され、ボトムバー等で切り替え可能であること |
| ADP-02 | Tablet版レイアウト | `Expanded` (幅広) | 履歴リストと詳細（入力欄）が左右 2 カラムで **同時に** 表示されていること |

## 3. コンポーネント描画検証 (Components)
**目的:** `PersonConditionComponents.kt` に定義された各 Stateless 部品が、渡された State を正しく描画することを検証する。

| ID     | テスト項目    | 条件 (State) | 期待結果                             |
|:-------|:---------|:-----------|:---------------------------------|
| CPN-01 | 履歴アイテム描画 | 有効なレコードデータ | タイトル、和暦日時、著者が指定のフォーマットで表示されること   |
| CPN-02 | 写真サムネイル  | 写真データあり    | 写真枚数に応じたグリッドが表示され、クリック可能な状態であること |
| CPN-03 | 写真なし表示   | 写真データ 0 件  | 「写真がありません」等のプレースホルダーが表示されること     |
| CPN-04 | 検索バー状態   | クエリ入力あり    | 入力した文字が表示され、クリアボタン等が機能すること       |

## 4. 状態・インタラクション検証 (Interaction)
**目的:** ユーザーの操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目     | 操作           | 期待結果 (呼び出される Intent)                      |
|:-------|:----------|:-------------|:------------------------------------------|
| ACT-01 | 所見入力の更新   | 本文入力欄へのタイピング | `updateEditInput` が最新の入力値とともに呼ばれること       |
| ACT-02 | レコードの選択切替 | 履歴リストの項目をタップ | `setSelectedConditionId` が該当 ID で呼ばれること   |
| ACT-03 | 保存処理の実行   | 保存ボタンをタップ    | `saveCurrentEdit()` が ViewModel 側で実行されること |
| ACT-04 | 写真追加の開始   | 写真追加ボタンをタップ  | カメラ起動またはプレビュー遷移に繋がる処理が開始されること             |

## 5. ナビゲーション・副作用検証 (Navigation)
**目的:** ViewModel から発行される `ViewEvent` や `UiEvent` に応じて、正しい画面遷移や通知が実行されることを検証する。

| ID     | テスト項目     | 発行イベント                      | 期待結果 (UI の挙動)                           |
|:-------|:----------|:----------------------------|:----------------------------------------|
| NAV-01 | 写真プレビュー遷移 | `NavigateToPhotoPreview`    | URI を伴ってプレビュー画面へ遷移すること                  |
| NAV-02 | 写真拡大遷移    | `NavigateToPhotoFullScreen` | 指定 ID を伴ってフル画面表示へ遷移すること                 |
| NAV-03 | 戻る操作の実行   | 戻るボタンタップ                    | `navController.popBackStack()` が実行されること |
| EVT-01 | 保存成功通知    | `UiEvent.SaveSuccess`       | スナックバー等が表示され、入力状態が閲覧モードへ戻ること            |

## 6. テスト用タグ (testTag)
- `ConditionScreen_PhoneContent`: Phone用レイアウトコンテナ
- `ConditionScreen_TabletContent`: Tablet用レイアウトコンテナ
- `Condition_MemoInput`: 本文入力フィールド
- `Condition_SaveButton`: 保存ボタン
- `Condition_RecordList`: 履歴リストコンテナ
- `Condition_PhotoList`: 写真サムネイルリスト
- `UserListItem_{id}`: 履歴リスト内の各項目
