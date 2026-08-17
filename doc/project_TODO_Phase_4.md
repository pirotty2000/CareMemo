# CareMemo 開発 TODO (Phase 4: アーキテクチャ境界の純粋化)

本ドキュメントは、`project_RULES.md` および `ARCHITECTURE.md` で定義された「レイヤー間の依存関係ルール（Dependency Matrix）」に基づき、現状のコードに見られる不整合を解消するための次期タスクを管理します。

## 📋 優先順位・影響範囲サマリー

| ID      | 章・節 | 項目名                              | 重要度 | 影響範囲        | 優先順位  | 進捗  | 検討結果・備考                                      |
|:--------|:----|:---------------------------------|:----|:------------|:-----:|:---:|:---------------------------------------------|
| **A-1** | 2.1 | **プラットフォーム依存の排除 (Context)**      | 高   | アプリ全体 (VM)  | **A** | 完了  | ViewModel のユニットテストを阻害する最大の要因。アーキテクチャの純粋化に必須。 |
| **A-2** | 1.1 | **AppMaintenanceRepository の整理** | 高   | メンテナンス機能    | **A** | 完了  | ルール違反（Logicへの依存）が顕著であり、基盤の安定性に直結するため最優先。     |
| **A-3** | 4.1 | **起動時ロックの厳格化 (要件強制)**            | 高   | アプリ起動時      | **A** | 完了  | 業務利用における最低限のセキュリティ境界。初期状態を「ロック」から開始。         |
| **B-1** | 1.3 | **HealthCategoryProcessor の整理**  | 高   | 健康記録機能全般    | **B** | 完了  | 業務ロジックと副作用の分離。リポジトリ依存を排除。                     |
| **B-2** | 2.2 | **状態管理ルールの徹底 (UiState)**         | 高   | 状態管理モデル     | **B** | 完了  | UI状態の不変性と事実（Fact）化を達成。                       |
| **B-3** | 4.2 | **重要操作時の再認証 (Step-up Auth)**     | 高   | PDF出力・データ管理 | **B** | 完了  | アンロック状態でのデータ持ち出し（PDF化等）を阻止するための個別認証。         |
| **C-1** | 1.2 | **リポジトリ設計の標準化**         | 中   | 部分（連絡先）     | **C** | 完了  | 子テーブル全域への ADR #8 適用（Health, Condition, Medication）。    |
| **C-2** | 4.3 | **ロック仕様の単純化 (即時一本化)**            | 高   | アプリ全体 (ロック) | **C** | 完了  | 複雑なタイマー設定を廃止。バックグラウンド遷移で即時ロックに一本化。          |
| **D-1** | 3.1 | **パッケージ構成の適正化**                  | 低   | 局所的         | **D** | 完了  | `PhoneNumberVisualTransformation` を `ui/utils` へ移動。             |

---

## 🎯 実施ロードマップ

1.  **[優先 A] 依存関係の排除と起動セキュリティの確立** (Context 排除, Repository 純粋化, 起動ロック)
2.  **[優先 B] 責務分離の徹底と操作セキュリティの強化** (Processor 分離, UiState 統一, 重要操作時認証)
3.  **[優先 C] 局所的なクリーンアップと仕様の単純化** (連絡先 Rep, タイマー廃止)
4.  **[優先 D] パッケージ構成の最終調整**

---

## 1. Repository 層の依存関係適正化 (Dependency Matrix 違反の解消)

### 1.1 AppMaintenanceRepository の整理
- **【検討：ID A-2 / 重要度 高 / 優先 A】**
- [x] **MedicationLogic への依存排除**:
    - `replaceAllData` メソッド内で `MedicationLogic.filterValidRecords` を呼び出している箇所を修正。
- [x] **SettingsLogic への依存排除**:
    - `importData` メソッド内で `SettingsLogic.validateVersion` を呼び出している箇所を修正。
- [x] **クレンジングロジックの抽出**:
    - `cleansePersonData` メソッド（生年月日の正規化等）を `PersonLogic` 等へ移動し、Repository は純粋な永続化のみを担当させる。

### 1.2 リポジトリ設計の標準化 (モデルケース：EmergencyContactRepository)
- **【検討：ID C-1 / 重要度 中 / 優先 C】**
- [x] **IdLogic への依存排除と標準化**:
    - `EmergencyContactRepository` をモデルケースとし、`insertContact` 内での `IdLogic.isNew` による自動 ID 生成（忖度）を廃止。
    - インターフェースを `saveContact(..., isUpdate: Boolean)` へ変更。
    - 他のリポジトリにおいても、内部で `IdLogic` を参照している箇所があれば同様に排除し、設計を統一（親：分離、子：フラグ併用）。
- [x] **呼び出し側の調整**:
    - `EmergencyContactEditViewModel` 側で、保存前に `IdLogic.isNew` を用いて ID の確定および `isUpdate` フラグの決定を行うよう変更。
- [x] **子テーブル・リポジトリへの横展開 (ADR #8 準拠)**:
    - [x] **HealthRepository**: `save...` メソッドへの統合、`BatchInputViewModel` および `PersonHealthViewModel` での ID 事前確定。
    - [x] **ConditionRepository**: `save...` メソッドへの統合、`PersonConditionViewModel` および `UnassignedPhotoViewModel` での ID 事前確定。
    - [x] **MedicationRepository**: `save...` メソッドへの統合、`PersonMedicationViewModel` での ID 事前確定。

### 1.3 HealthCategoryProcessor の整理
- **【検討：ID B-1 / 重要度 高 / 優先 B】**
- [x] **HealthRepository への依存排除**:
    - 各プロセッサからの `HealthRepository` への直接参照および副作用の実行を完全に排除しました。
    - 保存・削除などのリポジトリ操作は ViewModel へ移動し、プロセッサは「純粋な判定と Entity 生成」のみを担当するように責務を整理しました（完了）。

## 2. ViewModel 層の適正化 (プラットフォーム依存と状態管理ルールの適用)

### 2.1 プラットフォーム依存の排除
- **【検討：ID A-1 / 重要度 高 / 優先 A】**
- [x] **Context への依存排除**:
    - `SettingsViewModel`, `UnassignedPhotoViewModel` 等から `Context` を排除し、ファイル操作やリソース取得は Activity/Repository 側へ委譲。
- [x] **BiometricManager の操作委譲**:
    - `SettingsViewModel` が直接 `BiometricManager` を操作している。生体認証の可否判定は Activity へ委譲。

### 2.2 状態管理ルールの徹底
- **【検討：ID B-2 / 重要度 高 / 優先 B】**
- [x] **UiState でのロジック計算の排除**:
    - `EmergencyContactUiState` 等が `get()` プロパティで状態を計算していた設計を廃止。
    - 入力更新時に ViewModel 側で計算して State を更新する構造へ変更（完了）。

## 3. パッケージ構成と配置の適正化

### 3.1 PhoneNumberVisualTransformation の移動
- **【検討：ID D-1 / 重要度 低 / 優先 D】**
- [x] **PhoneNumberVisualTransformation の移動**:
    - `logic/feature` から `ui/utils` へ移動しました（完了）。
    - 関連するインポートおよびテストコードの参照先を更新済み。

## 4. セキュリティ強化 (Security Enhancement)

### 4.1 起動時ロックの厳格化 (デバイスセキュリティ要件の強制)
- **【検討：ID A-3 / 重要度 高 / 優先 A】**
- [x] **デバイスセキュリティのチェック**:
    - 起動時に端末の認証設定の有無を確認。未設定の場合は「利用不可画面」を表示。
- [x] **デフォルト・ロックの導入**:
    - `isAppLocked` の初期値を `true` とし、認証成功までコンテンツを表示しない。

### 4.2 重要操作時の再認証 (Step-up Authentication)
- **【検討：ID B-3 / 重要度 高 / 優先 B】**
- [x] **データ持ち出し時の認証強制**:
    - `PdfExportActionHandler` 等の実行前に必ず生体認証を要求する仕組みを導入。

### 4.3 ロック仕様の単純化 (即時ロックへの一本化とタイマー廃止)
- **【検討：ID C-2 / 重要度 高 / 優先 C】**
- [x] **タイマー設定の廃止と即時ロック統一**:
    - 設定項目を削除し、バックグラウンド遷移した時点で即座にロックするロジックへ単純化。
- [x] **関連コードのクリーンアップ**:
    - `UserSettingsRepository`, `SettingsViewModel`, `MainActivity` 等からタイムアウト関連のロジックおよびリソースを削除。
- [x] **テスト・仕様書の更新**:
    - ユニットテストおよびテスト仕様書を現行の即時ロック仕様に合わせて修正。

---
作成日: 2026/08/15
