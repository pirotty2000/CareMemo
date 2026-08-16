# [REF-001] 電話番号整形ロジックの統合と配置適正化

電話番号のハイフン挿入ロジックを整理し、アーキテクチャ規約（Android 依存の排除、レイヤー責務の明確化）に完全に準拠させます。

## User Review Required

> [!IMPORTANT]
> `PhoneNumberVisualTransformation` のパッケージが `logic.feature` から `ui.utils` に変更されます。これに伴い、インポート文が自動的に更新されます。

## Proposed Changes

### 1. ロジック層 (logic/common)

#### [NEW] [PhoneLogic.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/logic/common/PhoneLogic.kt)
- 純粋な Kotlin ロジックとして、電話番号のハイフン挿入アルゴリズムを実装します。
- 0120 / 0800 / 0570 の 4-3-3 形式に対応させます。

### 2. マッピング層 (ui/mapping)

#### [MODIFY] [EmergencyContactMapping.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/mapping/EmergencyContactMapping.kt)
- 内部に持っていた独自の整形ロジックを削除し、`PhoneLogic.formatPhoneNumber()` を呼び出すように変更します。

### 3. UI層 (ui/utils)

#### [NEW] [PhoneNumberVisualTransformation.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/utils/PhoneNumberVisualTransformation.kt)
- `PhoneNumberVisualTransformation` を `ui.utils` パッケージに新規作成します。
- 内部の判定ルールは `PhoneLogic` のロジックと同期させます。

#### [DELETE] [PhoneNumberVisualTransformation.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/logic/feature/PhoneNumberVisualTransformation.kt)
- 旧パッケージのファイルを削除します。

### 4. 既存コードの修正

#### [MODIFY] [EmergencyContactEditScreen.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/main/EmergencyContactEditScreen.kt)
- `PhoneNumberVisualTransformation` のインポート文を修正します。

## Verification Plan

### Automated Tests
- `EmergencyContactRegistrationScenarioTest` を実行し、緊急連絡先一覧での表示が `0120-000-000` になることを確認します。
- 必要に応じて、`PhoneLogic` のユニットテストを追加作成して検証します。

### Manual Verification
- 連絡先編集画面で電話番号を入力し、リアルタイムで正しいハイフンが挿入されることを確認します。
