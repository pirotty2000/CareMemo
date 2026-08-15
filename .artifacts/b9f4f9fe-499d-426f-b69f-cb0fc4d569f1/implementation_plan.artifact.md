# ViewModel からのプラットフォーム依存 (Context) 排除計画 (A-1)

本計画は、`project_TODO_Phase_4.md` の **ID A-1** に基づき、ViewModel 層に含まれる `android.content.Context` および関連するプラットフォーム依存クラス（`BiometricManager`, `Uri` 等）を排除し、アーキテクチャの純粋化とテスト容易性の向上を目指します。

## User Review Required

> [!IMPORTANT]
> **生体認証判定の責務移動**
> `SettingsViewModel` で行っていた生体認証の「利用可能かどうかの判定」を `MainActivity` 側へ移動します。これにより、ViewModel はプラットフォームのハードウェア状態に直接触れることなく、渡されたフラグに基づいてロジックを遂行するようになります。

> [!NOTE]
> **Repository への物理ファイル操作委譲**
> 写真の保存・削除・ディレクトリ操作（`ImageUtils` 経由）の呼び出し元を ViewModel から Repository へ移動します。Repository は `ApplicationContext` をコンストラクタで受け取るように変更します。

## Proposed Changes

### 1. Repository 層の強化 (依存の注入と責務の拡大)

物理ファイル操作を Repository 層に隠蔽することで、ViewModel を Android フレームワークから解放します。

#### [MODIFY] [ConditionRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/ConditionRepository.kt)
- コンストラクタに `ApplicationContext` を追加。
- `processAndSavePhoto`, `deletePhotoFiles`, `getUnassignedPhysicalFiles` 等の物理ファイル操作メソッドを追加し、内部で `ImageUtils` を呼び出す。

#### [MODIFY] [AppMaintenanceRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/AppMaintenanceRepository.kt)
- `exportData`, `importData` メソッドから `context: Context` パラメータを削除（コンストラクタで保持している `context` を使用）。

---

### 2. ViewModel 層の適正化 (Context 排除)

#### [MODIFY] [PersonConditionViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/PersonConditionViewModel.kt)
- コンストラクタおよび各メソッドから `Context` を削除。
- 写真の保存・削除を `conditionRepository` の新設メソッド経由で実行するように変更。
- `SavedStateHandle` および ViewEvent で扱う `Uri` を `String` ベースに変更し、フレームワーク依存を最小化。

#### [MODIFY] [SettingsViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/SettingsViewModel.kt)
- `BiometricManager` への直接参照を排除。
- `canAuthenticate`, `setBiometricEnabled`, `exportData`, `importData` 等のメソッドから `Context` パラメータを削除。
- 生体認証の可否判定結果を外部（UI層）から受け取る方式に変更。

#### [MODIFY] [UnassignedPhotoViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/UnassignedPhotoViewModel.kt)
- コンストラクタおよび `Factory` から `Context` を削除。
- ディレクトリ内のファイルスキャンや物理削除を `conditionRepository` へ委譲。

---

### 3. UI/構成層の調整

#### [MODIFY] [MainActivity.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/MainActivity.kt)
- ViewModel の `Factory` 呼び出し箇所を修正（`Context` を渡さないように変更）。
- 設定画面表示時などに `BiometricManager` を用いて認証可否を確認し、ViewModel に状態を通知するロジックを追加。

---

## Verification Plan

### Automated Tests
- 各 ViewModel のユニットテストを JUnit で実行し、`Context` をモックすることなく（または最小限のモックで）ロジックが検証可能であることを確認。
- ※現状、リポジトリ層のテストは Android Instrumented Test が主となるため、そちらの動作継続も確認する。

### Manual Verification
- **写真機能**: 所見メモの新規作成時に写真が正常に保存され、削除時に物理ファイルも消えることを確認。
- **未割り当て写真管理**: DBと紐付かない写真が正しくリストアップされ、削除できることを確認。
- **設定/バックアップ**: データのインポート・エクスポートが従来どおり動作することを確認。
- **生体認証**: 設定画面での生体認証 ON/OFF 切り替えが、端末の認証機能状態に応じて正しく制御されることを確認。
