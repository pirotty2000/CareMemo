# CareMemo 開発 TODO (Phase 3: UI 境界の整合性と最適化)

本ドキュメントは、`project_RULES.md` で定義された「UI 境界における責務（ViewModel vs Composable）」の指針に基づき、既存コードの不整合を解消し、設計の純粋性を高めるためのタスクを管理します。

## 🎯 実施ロードマップ

以下の優先順位で段階的に進めます。

1.  **derivedStateOf の適正化** (ViewModel へのロジック集約)
2.  **ImmutableList 境界の整理** (Logic/ViewModel 間の依存関係適正化)
3.  **Activity 委譲ルールの適用** (プラットフォーム機能の安定性確保)
4.  **ViewModel の堅牢化** (異常系テスト・競合状態対策)
5.  **KDoc の完遂** (ドキュメントによる意図の継承)
6.  **完了確認**

---

## 1. 状態管理の ViewModel への移行 (derivedStateOf の適正化)

`derivedStateOf` を Composable 内で使用している箇所のうち、ビジネスロジック（保存可否、変更検知等）に関わるものを ViewModel / UiState へ移動します。

### 1.1. 健康記録 (Health) 関連
- [x] **変更検知 (`isChanged`) の ViewModel 移行**:
    - `PersonHealthComponents.kt` 内の `isChanged` 計算を ViewModel へ移動。
    - `UiState` に `isChanged: Boolean` を追加し、入力値の変更を ViewModel 側で判定する。
    - **理由**: 変更破棄ダイアログの表示判定は重要な業務ロジックであり、Unit Test で検証可能にすべきであるため。
- [x] **バリデーション (`isDateTimeValid`, `validationResult`) の ViewModel 移行**:
    - `HealthRecordInputPanel` 内で行っている日時の妥当性チェックと、`PersonHealthLogic.validateInputs` による判定を ViewModel へ移動。
    - **理由**: 保存ボタンの活性制御は UI 演出ではなく業務判断であるため。

### 1.2. 所見メモ (Condition) 関連
- [ ] **変更検知 (`isChanged`) の ViewModel 移行**:
    - `PersonConditionComponents.kt` 内の `isChanged` 計算を ViewModel へ移動。
    - 健康記録と同様に、`UiState` での状態管理に切り替える。

## 2. 不変コレクション (ImmutableList) の適用範囲の整理

- ドキュメントの修正に合わせて、不要な変換を整理し、UI 境界に絞った適用を徹底します。
- ImmutableList への変換は UI 境界となる ViewModel で行い、Logic や Repository では原則として行わないこととします。

### 2.1. Logic レイヤーからの ImmutableList 排除
UI 境界の外側である Logic クラスのメソッド戻り値を、標準の `List` / `Map` へ戻します。

- [ ] **AuditLogLogic.kt**:
    - `filterLogs`: `ImmutableList<AuditLog>` -> `List<AuditLog>`
    - `extractAvailableFeatures`: `ImmutableList<String>` -> `List<String>`
    - `extractAvailableResults`: `ImmutableList<String>` -> `List<String>`
- [ ] **BatchInputLogic.kt**:
    - `getEffectiveCategories`: `ImmutableList<BatchInputCategory>` -> `List<BatchInputCategory>`
    - `createEntities`: `ImmutableList<Any>` -> `List<Any>`
- [ ] **DeleteOrRestorePersonLogic.kt**:
    - `filterTargets`: `ImmutableList<Person>` -> `List<Person>`
- [ ] **PersonMedicationLogic.kt**:
    - `groupRecordsByDate`: `ImmutableMap<String, ImmutableList<MedicationRecord>>` -> `Map<String, List<MedicationRecord>>`

### 2.2. ViewModel での変換の徹底
Logic レイヤーから標準の `List` が返されるようになるため、ViewModel で `UiState` に反映する際の変換を確実に行います。

- [ ] **対象 ViewModel の確認と修正**:
    - `AuditLogViewModel`, `BatchInputViewModel`, `DeleteOrRestorePersonViewModel`, `PersonMedicationViewModel` 等において、Logic の結果を `.toImmutableList()` しているか確認する。

## 3. Activity への委譲ルールの適用と確認

新しく定義した「Activity への委譲ルール（3.6項）」に基づき、現状の調査と今後の対応を整理します。

- [ ] **既存の委譲実装の確認 (完了)**:
    - [x] **生体認証 (BiometricPrompt)**: `MainActivity` で一括管理され、Composable へラムダとして注入されていることを確認済み（ルール遵守）。
    - [x] **ActivityResult API**: `PersonConditionScreen` や `SettingsScreen` で `rememberLauncherForActivityResult` が Compose 内で使用されていることを確認済み（ルール遵守）。
- [ ] **今後の権限要求の実装**:
    - 今後、ストレージアクセスや音声認識などでランタイム権限が必要になった際、Composable 内で直接要求せず、`MainActivity` へ処理を委譲する構成を徹底する。

## 4. ViewModel の堅牢化と品質担保（旧 ViewModel 改善計画より）

ViewModel へのロジック集約に伴い、その振る舞いをより堅牢にし、テスト容易性を高めるタスクを実施します。

- [ ] **異常系・境界条件のユニットテスト拡充**:
    - [ ] `BatchInputViewModel`: 一括保存中の例外発生時に、入力データが安全に保持されているか。
    - [ ] `PersonEditViewModel`: 高速な連続タップや画面遷移時の競合状態 (Race Condition) 防止。
    - [ ] `PersonListViewModel`: 検索結果の古い非同期処理が最新の結果を上書きしないかの検証。
- [ ] **記録項目の処理構造の最適化検討**:
    - [ ] `BatchInputViewModel` 等における、カテゴリごとの条件分岐 (if-else) を整理し、共通インターフェース等を活用した拡張性の高い構造へのリファクタリングを検討する。

## 5. コードドキュメント (KDoc) の完遂 (旧 Phase 2 より)

継続課題として、リファクタリング対象のクラスを中心にドキュメント化を完了させます。

- [ ] **Logic, Repository, ViewModel 層의 KDoc 整備**:
    - 特に Phase 3 で責務を整理したメソッド（`isChanged` の判定理由、標準 `List` を返す意図など）について、後続の開発者が迷わないよう KDoc を記述する。

## 6. 確認済（修正不要）の項目
- [x] `VerticalScrollIndicator.kt`: スクロール位置に応じた表示制御（演出用）としての `derivedStateOf` 利用は、新ルールに合致しているため維持。
- [x] `MainActivity.kt`: `BiometricPrompt` の管理と注入フロー。
- [x] `SettingsScreen.kt` 等: `ActivityResult API` によるファイル・カメラ連携。

---
最終更新日: 2026/08/09
