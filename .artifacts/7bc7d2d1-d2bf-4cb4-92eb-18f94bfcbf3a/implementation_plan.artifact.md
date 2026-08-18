# リポジトリ設計の標準化 (EmergencyContactRepository モデルケース)

`project_DECISIONS.md` 第8章および `project_RULES.md` に基づき、`EmergencyContactRepository` をモデルケースとして「リポジトリの純粋化」と「標準的な保存メソッドの導入」を行います。

## User Review Required

> [!IMPORTANT]
> 本修正により、`EmergencyContactRepository` の `insertContact` および `updateContact` メソッドが廃止され、`saveContact` に統合されます。
> また、IDの自動生成（UUID生成）の責務が Repository から ViewModel 層へと移動します。

## Proposed Changes

### 1. Data Layer (Repository)

#### [MODIFY] [EmergencyContactRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/EmergencyContactRepository.kt)
- `IdLogic` への依存を排除します。
- `insertContact` と `updateContact` を統合し、`saveContact(contact, isUpdate, ...)` メソッドを定義します。
- メソッド内部での ID 生成ロジックを削除し、渡された Entity をそのまま永続化するようにします。

### 2. ViewModel Layer

#### [MODIFY] [EmergencyContactEditViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/EmergencyContactEditViewModel.kt)
- 保存処理 (`saveContact`) において、`IdLogic.isNew` を使用して `isUpdate` フラグを決定します。
- 新規登録時 (`!isUpdate`) の場合、保存直前に `UUID` を生成して Entity にセットする責務を担います。
- リポジトリの新しい `saveContact` メソッドを呼び出すように変更します。

### 3. Test Layer

#### [MODIFY] [EmergencyContactRepositoryTest.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/test/java/jp/mydns/fujiwara/carememo/data/repository/EmergencyContactRepositoryTest.kt)
- リポジトリのインターフェース変更に合わせてテストケースを修正します。
- `IdLogic` への依存がリポジトリから消えるため、テスト内でのモックや検証ロジックを調整します。

---

## Verification Plan

### Automated Tests
- `EmergencyContactRepositoryTest` を実行し、保存ロジック（Insert/Update）が正しく動作することを確認します。
- `EmergencyContactEditViewModelTest`（存在する場合）を実行し、保存時の ID 生成とリポジトリ呼び出しが正しいことを確認します。

### Manual Verification
1. アプリを起動し、任意の利用者の「緊急連絡先」画面を開く。
2. 新規連絡先を追加し、正しく保存されることを確認する。
3. 既存の連絡先を編集し、内容が更新されることを確認する。
4. 監査ログ画面で、それぞれの操作が `INSERT` / `UPDATE` として正しく記録されていることを確認する。
