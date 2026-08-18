# 規約に基づく「新規判定」の一本化 (IdLogic.isNew への完全委譲) 完了報告

プロジェクト全体に残っていた生の `null` や `isEmpty()` による新規判定を排除し、`IdLogic.isNew()` を用いた統一的な規約へ一本化しました。

## 実施内容

### 1. プログラム本体の修正
- **EmergencyContactEditScreen**: タイトルの出し分け判定を `contact.id.isEmpty()` から `IdLogic.isNew(contact.id)` へ変更しました。
- **ConditionMaintenanceLogic**: 一時保存写真の判定ロジックを `IdLogic.isNew()` へ統一しました。
- **PersonEditViewModel**: `isNew` フラグの初期化および保存時の重複チェック判定を `IdLogic.isNew()` を用いた記述に整理しました。
- **EmergencyContactEditViewModel**: 初期化時の `contactId` 判定を `IdLogic.isNew()` へ変更し、`null` や `"NEW"` 定数を安全に扱えるようにしました。

### 2. テストプログラムの修正
- **PersonEditViewModelTest**: 規約に合わせ、新規作成時の ID 指定を `AppSpecifications.Id.NEW_RECORD_ID` へ変更しました。
- **EmergencyContactEditViewModelTest**: `null` 判定に加え、明示的な `"NEW"` 定数による初期化テストケースを追加しました。
- **ConditionMaintenanceLogicTest**: テストデータ内の `conditionId` を空文字から規約定数へ変更しました。

### 3. テスト仕様書の更新
- **TEST_SPEC_PersonEditViewModel.md**: 条件欄の表現を `IdLogic.isNew` に基づく規約的な表現に修正しました。
- **TEST_SPEC_ConditionMaintenanceLogic.md**: 「ID が空文字」等の条件を規約に基づいた表現に修正しました。

## 検証結果
- `testStableDebugUnitTest` および `testDevDebugUnitTest` を実行し、全 521 テストがパスすることを確認しました。
- プロジェクトのクリーンビルド（`app:assembleDebug`）が正常に完了することを確認しました。

## 成果
今回の修正により、「何をもって新規とみなすか」というドメイン知識が `IdLogic` に完全に集約されました。これにより、将来的な ID 体系の変更に対する耐性が高まり、コードの意図がより明確になりました。
