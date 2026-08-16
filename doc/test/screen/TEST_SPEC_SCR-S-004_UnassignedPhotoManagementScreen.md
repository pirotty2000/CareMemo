# テスト仕様書 - SCR-S-004 UnassignedPhotoManagementScreen

- **対象テストコード:**
    - `app/src/androidTest/java/jp/mydns/fujiwara/carememo/ui/screens/settings/UnassignedPhotoManagementScreenTest.kt`

## 1. 概要
未割り当て写真管理画面（UnassignedPhotoManagementScreen）における UI 描画、写真の削除操作に伴う ViewModel 連携、および戻る操作に伴う遷移制御を検証する。
不整合データのスキャンロジックや物理ファイルの削除処理は ViewModel/Logic 側のテストで保証されているため、本テストでは UI 層の振る舞いに特化する。

## 2. 表示テスト (Display)
**目的:** `UnassignedPhotoUiState` に基づき、未割り当て写真のリストやステータスが正しく描画されることを検証する。

| ID     | テスト項目        | 条件 (UiState)               | 期待結果                                 |
|:-------|:-------------|:---------------------------|:-------------------------------------|
| DSP-01 | 未割り当て写真リスト描画 | 有効なデータあり                   | 不整合が発生している写真がグリッド形式で一覧表示されること        |
| DSP-02 | 写真種別の表示      | `UnassignedPhotoInfo.type` | 「一時ファイル」や「レコードなし」等の不整合理由が日本語で表示されること |
| DSP-03 | 空状態の表示       | `unassignedPhotos` が空      | 「未割り当て写真はありません」等のメッセージが表示されること       |
| DSP-04 | ローディング表示     | `isLoading = true`         | 読み込み中インジケータが表示されること                  |

## 3. 操作・インタラクションテスト (Interaction)
**目的:** ユーザーの削除操作が、ViewModel の適切なメソッド呼び出し（Intent 伝達）に繋がることを検証する。

| ID     | テスト項目   | 操作             | 期待結果 (UI の挙動)                                   |
|:-------|:--------|:---------------|:------------------------------------------------|
| ACT-01 | 削除確認の表示 | 項目内の削除アイコンをタップ | 削除確認ダイアログが表示されること                               |
| ACT-02 | 削除実行の確定 | ダイアログで「削除」を選択  | ViewModel の `deletePhoto()` が該当する写真情報とともに呼ばれること |

## 4. ナビゲーション・副作用検証 (Navigation)
**目的:** 戻るボタン操作が正しく実行されることを検証する。

| ID     | テスト項目    | 操作        | 期待結果                                                       |
|:-------|:---------|:----------|:-----------------------------------------------------------|
| NAV-01 | 画面終了（戻る） | 戻るボタンをタップ | `navController.popBackStack()` または ViewModel の戻る処理が実行されること |

## 5. テスト用タグ (testTag)
- `UnassignedPhoto_BackButton`: 戻るボタン
- `UnassignedPhoto_Grid`: 写真リストコンテナ
- `UnassignedPhoto_Item_{fileName}`: 各写真項目
- `UnassignedPhoto_DeleteButton`: 項目内の削除ボタン
- `UnassignedPhoto_Loading`: 読み込み中表示
- `UnassignedPhoto_EmptyState`: データなし表示
