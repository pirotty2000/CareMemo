# テスト仕様書 - UnassignedPhotoViewModel

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/UnassignedPhotoViewModelTest.kt`

## 1. 概要
未割り当て写真管理画面（SCR-S-004）における UI 状態（UiState）の管理、不整合データのスキャン、写真の削除操作、および画面遷移イベントの正確性を検証する。

## 2. 初期化・データロードテスト (Initialization)
**目的:** 画面起動時に正しく不整合データのスキャンが開始され、結果が反映されることを検証する。

| ID | テスト項目 | 検証内容 | 期待結果 |
|:---|:---|:---|:---|
| INI-01 | 自動スキャン成功 | `UnassignedPhotoViewModel` のインスタンス化 | 内部で `loadUnassignedPhotos` が呼ばれ、スキャン結果が `unassignedPhotos` に反映されること |
| INI-02 | ロード中状態の管理 | データ取得開始から完了まで | `isLoading` が適切に true -> false と遷移すること |

## 3. 写真操作テスト (Actions)
**目的:** 写真の削除操作がリポジトリおよびファイルシステムと正しく連携し、リストが更新されることを検証する。

| ID | テスト項目 | 条件 (操作) | 期待結果 |
|:---|:---|:---|:---|
| ACT-01 | 写真削除実行 | `deletePhoto(info)` を実行 | 指定した写真がリポジトリ（DB）および物理ストレージから削除され、リストが再読み込みされること |

## 4. ナビゲーションテスト (Navigation)
**目的:** UI 操作に伴う副作用（ViewEvent）が正しく発行されることを検証する。

| ID | テスト項目 | 操作 | 期待結果 |
|:---|:---|:---|:---|
| NAV-01 | 戻る操作 | `navigateBack()` を呼び出し | `viewEvent` から `NavigateBack` が発行されること |
