# CareMemo 次世代品質向上プロジェクト (project_TODO_Phase_2.md)

`project_structure.md` および `project_RULES.md` の整備完了を受け、Jetpack Compose / Material 3 の最新ベストプラクティスに基づき、アプリの堅牢性と保守性をさらに高めるためのロードマップです。

---

# 基本方針

CareMemo は「一貫性・責務分離・型安全性・保守性」を最優先とし、Jetpack Compose および Material 3 のベストプラクティスを取り入れながら、医療・介護現場で安心して利用できる品質を継続的に追求する。

## 🎯 優先度・戦略マトリックス

プロジェクトの安定性と開発効率のバランスを考慮した優先順位です。

| 項目                                          |  評価   |   優先度    |  コスト  | 段階的移行 |    進捗    | 取り組み方針                   |
|:--------------------------------------------|:-----:|:--------:|:-----:|:-----:|:--------:|:-------------------------|
| **1. Type-safe Navigation & ViewEvent 一元化** | ★★★★★ |  **高**   | **大** | **中** | **100%** | 完了。全画面の遷移定義を刷新。          |
| **2. SavedStateHandle**                     | ★★★★★ |  **高**   | **中** | **可** | **100%** | 完了。ViewModel の初期化を自律化。   |
| **3. 不変コレクション (ImmutableList)**             | ★★★★★ |  **高**   | **中** | **可** |    0%    | UiState と関連コンポーネントを順次変更。 |
| **4. Modifier ルールの厳格化**                     | ★★★★★ |  **継続**  | **小** | **可** |   10%    | 新規作成・改修時に都度適用。影響範囲は局所的。  |
| **5. PreviewParameterProvider**             | ★★★★☆ |  **中**   | **中** | **可** |    0%    | プレビュー関数の書き換えが必要だが効果は絶大。  |
| **6. Dynamic Color**                        | ★★★★★ | **設計判断** | **-** | **-** |    -     | 現状維持（コストゼロ）。             |

---

## 🏗 アーキテクチャの現代化
Android Jetpack の最新ライブラリを活用し、実行時の安定性と型安全性を強化します。

### 戦略的セット：Type-safe Navigation × SavedStateHandle [完了]
これら2つは「画面間データ受け渡し」の入り口と出口の関係にあるため、セットでの移行を推奨します。

- **Type-Safe Navigation & ViewEvent 一元化 [完了]**:
    - 文字列ベースのルート定義を廃止し、Navigation 2.8.0+ の型安全な遷移に移行する。
    - 画面遷移を ViewModel から発行される `ViewEvent`（sealed interface）として一元管理し、UI側（Composable）の `LaunchedEffect` で `navController` を操作する形式に統一する。これにより、遷移ロジックのテスタビリティと一貫性を向上させる。
    - `BaseUiStateViewModel.UiEvent.SaveSuccess` などの通知イベントを `data class` 化し、保存されたデータの ID 等を UI 側に伝播可能にすることで、遷移ロジックとの親和性を高める。
- **SavedStateHandle [完了]**: ViewModel で引数を直接取得し、プロセス死からの復帰耐性を高める。LaunchedEffect での `loadPerson` 呼び出しを削減し、ViewModel を自己完結させる。

### パフォーマンス最適化
- **不変コレクション (ImmutableList) の導入**: `kotlinx.collections.immutable` を採用し、`UiState` 内のリストをすべて `ImmutableList` に置き換える。これにより、Compose コンパイラがリストを「安定（Stable）」と判定できるようになり、Compose Compiler が安定性を正しく判定できるようになり、不要な再コンポーズを抑制できる。
- **Compose 安定性の明示**: 主要なドメインモデル（`Person` や `Entity`）に `@Immutable` / `@Stable` を付与し、最適化の精度をさらに高める（不変オブジェクトであることを満たすモデルに対して @Immutable を付与する。）。

---

## 🎨 UI/UX 実装の洗練
Material 3 の特性を活かし、開発効率とユーザー体験を向上させます。

### コンポーネント設計
- **Modifier 伝播ルールの厳格化**: 外部 Modifier は Root レイアウトにのみ適用し、内部要素の装飾と分離する。再利用性と予測可能性を高める。
- **PreviewParameterProvider の導入**: プレビュー用のテストデータを一括管理し、正常・異常・空状態のプレビューコードを簡素化する。

### 視認性の維持（設計判断）
- **Dynamic Color の非採用**: 医療・介護系アプリにおいて「色」は重要なセマンティクス（赤＝異常など）を持つため、壁紙による配色の自動変化は許容しない。固定テーマによる高いコントラストと視認性を維持する。

---

## 🛠 継続的改善タスク（低重要度）
既存コードの改修にあわせて順次適用する項目です。

- [ ] **collectAsStateWithLifecycle への完全置換**: ライフサイクルに応じた安全な状態収集を徹底。
- [ ] **リソース ID への文言集約**: ハードコードされた日本語を `strings.xml` へ順次移行。
- [ ] **KDoc の継続的な整備**: Logic, Repository, ViewModel 層のドキュメント化を完遂する。

---

## 🚀 実装ロードマップ：Type-safe Navigation & SavedStateHandle 移行 (Phase 2-1)

この移行は「定義」→「ロジック(ViewModel)」「表示(UI)」「結合」の順に、段階的に実施する。各ステップの完了により、いつでも作業を中断・再開可能とする。

### ステップ 1：共通基盤とナビゲーション定義の作成
- [x] **ナビゲーション目的地の定義**: `ui.navigation.Destinations.kt` を作成し、全ルートを `@Serializable` な object/class として定義する。
- [x] **基底クラスの拡張**: `BaseUiStateViewModel` において、`UiEvent.SaveSuccess` を `data class` に変更し、保存後に遷移が必要な ID 等を保持可能にする。

### ステップ 2：ViewModel 層の近代化（SavedStateHandle 導入と ViewEvent 定義）
各 ViewModel で `SavedStateHandle` から引数を取得し、遷移を `ViewEvent` で判断するように変更する。
- [x] `PersonListViewModel` (SCR-M-001)
- [x] `PersonEditViewModel` (SCR-M-002)
- [x] `EmergencyContactEditViewModel` (SCR-M-003, 004)
- [x] `PersonDetailUiStateViewModel` / `PersonHealthViewModel` (SCR-PH-001, 003)
- [x] `BatchInputViewModel` (SCR-PH-002)
- [x] `PersonConditionViewModel` (SCR-PM-001, 002, 003)
- [x] `PersonMedicationViewModel` (SCR-PM-001)
- [x] `SettingsViewModel` (SCR-S-001)
- [x] `AuditLogViewModel` (SCR-S-002)
- [x] `DeleteOrRestorePersonViewModel` (SCR-S-003)
- [x] `OrphanedPhotoViewModel` (SCR-S-004)

### ステップ 3：UI (Screen) 層の遷移ロジック一元化
Composable の `LaunchedEffect` で ViewModel の `ViewEvent` を購読し、`navController.navigate()` を実行するように変更する。
- [x] `MainScreen`
- [x] `PersonEditScreen`
- [x] `EmergencyContactListScreen` / `EmergencyContactEditScreen`
- [x] `PersonHealthScreen` / `GraphExpansionScreen`
- [x] `BatchInputScreen`
- [x] `PersonConditionScreen` / `ConditionPhotoPreviewScreen` / `ConditionPhotoFullScreen`
- [x] `PersonMedicationScreen`
- [x] `SettingsScreen` / `AuditLogScreen` / `DeleteOrRestorePersonScreen` / `OrphanedPhotoManagementScreen`

### ステップ 4：MainActivity での最終結合（システム切り替え）
- [x] **NavHost 刷新**: `composable<T>` 形式に全面移行し、文字列ベースの定義を廃止する。
- [x] **ViewModelFactory 整理**: 各 Factory 内での ID 抽出ロジックを削除し、コンストラクタ注入を簡素化する。
- [x] **レガシーコードの削除**: 各 `Category` Enum 等に残る文字列ルート生成ロジック (`getRoute` 等) を完全に整理する。

### ステップ 5：型安全ナビゲーションの純粋化と完全移行の仕上げ
これまでの移行で構築した土台を活かし、不自然に残っている「接着コード」を排除して、真の型安全な構造を完成させる。
- [x] **MainActivity の冗長な詰め替えを削除**: `backStackEntry.savedStateHandle["personId"] = args.personId` などの手動代入を廃止し、Navigation コンポーネントによる `SavedStateHandle` への自動連携に一本化する。
- [x] **初期化ロジックの ViewModel への完全移管**: `MedicalContactEdit` 等の初期化（`startEdit` 呼び出し等）を `MainActivity` の `LaunchedEffect` から ViewModel 内の `SavedStateHandle` 監視（`startObservePersonId` 等のパターン）に移行し、ViewModel の自己完結性を高める。
- [x] **Screen 引数の最適化と Source of Truth の一元化**: Composable 関数の引数から、ViewModel が `SavedStateHandle` 経由で既に保持している重複パラメータ（`personId`, `category`等）を整理し、ViewModel の状態を参照する形に統一する。
- [x] **非推奨コード（Deprecated）の物理削除**: `Category.getRoute` などの旧ナビゲーション関連コードを完全に削除し、コードベースを最新の設計にクリーンアップする。

---
最終更新日: 2026/08/05
