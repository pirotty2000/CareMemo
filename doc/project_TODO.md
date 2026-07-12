# CareMemo 課題・改善プロジェクト (project_TODO.md)

`project_RULES.md` に基づく現状のソースコードの課題一覧です。

---

## 🔴 重要度：高（システムの安定性・フリーズ防止・データ保護）

| 課題内容                                                                          | 対象ファイル                               | ルール参照 | ステータス |
|:------------------------------------------------------------------------------|:-------------------------------------|:------|:------|
| 監査ログ記録時の例外保護: `log` メソッド内で `try-catch` がなく、DBエラー時に業務処理が中断する。                  | `data.repository.AuditLogRepository` | 7.3   | ✅ 完了  |
| リポジトリ側でのログ記録失敗の許容: ログ記録の失敗が保存処理等の成功を妨げないよう例外をキャッチする。                          | 各 `data.repository.*`                | 7.3   | ✅ 完了  |
| **エラー時のローディング解除の徹底**: `Flow.catch` 等を用い、データ取得失敗時も確実に `isLoading` を false にする。 | 下記チェックリスト参照                          | 4.3   | ✅ 完了  |

### 【対応状況】エラー時のローディング解除の徹底

データ取得（Flow）や保存（Suspend関数）において、例外発生時も確実に `isLoading` を false に戻す修正の進捗状況です。

#### 共通改修ルール（PersonHealthViewModelを基準とする）
1.  **Flow (監視系)**: `onEach` で成功時解除、`catch` で失敗時解除を行う。
2.  **Suspend関数 (単発操作系)**: `try-finally` を使用し、`finally` ブロックで確実に解除する。
3.  **例外の可視化**: `catch` ブロック内で `Log.e` および `auditLogRepository.log` (ERROR) を記録する。
4.  **テストの義務化**: ユニットテストで、例外発生時の `isLoading == false` と監査ログ記録を検証する。

#### ViewModel別 対応状況総括表

| No | ViewModel名         | 対応の必要性 | ソース対応 | テスト実装 | 特記事項                          |
|:---|:-------------------|:-------|:------|:------|:------------------------------|
| 1  | `PersonHealth`     | 完了     | ✅     | ✅     | 基準パターン。Flow/Suspend両対応。       |
| 2  | `PersonMedication` | 完了     | ✅     | ✅     | 同期処理(Suspend)も保護済。            |
| 3  | `PersonEdit`       | 完了     | ✅     | ✅     | `LG-01`, `LG-02` テストケース実装済。   |
| 4  | `PersonCondition`  | 完了     | ✅     | ✅     | 写真処理(`LG-04`, `LG-05`)も検証済。   |
| 5  | `PersonList`       | 完了     | ✅     | ✅     | 一覧Flowと追加処理の両方を検証済。           |
| 6  | `BatchInput`       | 完了     | ✅     | ✅     | 一括保存時の `LG-01` を検証済。          |
| 7  | `Settings`         | 完了     | ✅     | ✅     | Flowの保護と例外テストを実装済。            |
| 8  | `DeleteOrRestore`  | 完了     | ✅     | ✅     | 一覧Flowの保護と例外テストを実装済。          |
| 9  | `PersonDetail`     | 完了     | ✅     | ✅     | サマリーFlowとloadPersonを検証済。      |
| 10 | `PersonBase`       | 完了     | ✅     | ✅     | 共通の `loadPerson` 強化と例外テスト実装済。 |
| 11 | `Base`             | なし     | -     | -     | ロジックを持たない基底クラス。               |

---

## 🏗️ 次回実施予定のリファクタリング（実施中）

### BaseViewModel への共通処理（safeLaunch）の導入
- **背景**: 現状、各 ViewModel に `try-catch` と `isLoading` の制御コードが散在しており、今回の `CancellationException` のように共通の修正が必要になった際の影響範囲が大きい。
- **基本方針**: `BaseViewModel` は「コルーチンのライフサイクル管理」と「例外の分類」を担当し、例外への具体的な対応（ログ出力・UiEvent生成など）は委譲先へ任せる。
- **TODO**:
    - **フェーズ 1: 基盤構築と検証**
        - ✅ エラー記録・通知の責務を抽象化したインターフェース（`CoroutineErrorHandler`）の定義。
        - ✅ `BaseViewModel` への `safeLaunch` / `safeCollect` 仕様策定（`project_BASE_VIEWMODEL_SPEC.md`）。
        - ✅ `BaseViewModel` への実装と標準ハンドラの作成。
        - ✅ `BaseViewModelTest.kt` による基盤ロジックの網羅的検証。
        - ✅ 既存 ViewModel への `screenName` 実装とコンパイルエラーの解消。
    - **フェーズ 2: 段階的な移行（ViewModel への適用）**
        - [✅] `PersonBaseViewModel`（`loadPerson` の完全リファクタリング）
        - [✅] `PersonHealthViewModel`
        - [✅] `PersonMedicationViewModel`
        - [✅] `PersonConditionViewModel`
        - [✅] `PersonListViewModel`
        - [✅] `PersonEditViewModel`
        - [✅] `SettingsViewModel`
        - [✅] `DeleteOrRestorePersonViewModel`
        - [✅] `BatchInputViewModel`
        - [✅] `PersonDetailViewModel`
    - **フェーズ 3: ルールの確定とクリーンアップ**
        - [✅] `project_RULES.md` のエラーハンドリング規定を `safeLaunch` 推奨に更新。
        - [✅] 各 ViewModel 内の重複するプライベート・エラーハンドリング関数の削除。
    - **フェーズ 4: 最終検証**
        - [✅] 実機での例外発生時の挙動（監査ログ記録・UI通知）の最終確認。

---

## 🟡 重要度：中（UX・リソース効率・ライフサイクル管理）

| 課題内容                                                                               | 対象ファイル                                   | ルール参照 | ステータス |
|:-----------------------------------------------------------------------------------|:-----------------------------------------|:------|:------|
| **ブランキング（チラつき）抑制**: 新規作成時の初期値セットに `LaunchedEffect` を使用している。                        | 主要な `ui.components.*` および `ui.screens.*` | 3.3.1 | ✅ 完了  |
| **ライフサイクル対応の状態収集**: `collectAsState()` に代わり `collectAsStateWithLifecycle()` を使用する。 | 各 `ui.screens.*`                         | 10    | ✅ 完了  |

## 🔵 重要度：低（コーディング規約・保守性）

| 課題内容                                                                               | 対象ファイル              | ルール参照 | ステータス |
|:-----------------------------------------------------------------------------------|:--------------------|:------|:------|
| **Modifier 引数の欠落・位置不備**: 多くの Composable で `Modifier` が引数にない、または最初のオプション引数になっていない。  | ほぼすべての `ui.*` ファイル  | 10    |       |
| **Stateless Composable の Modifier 対応**: 下位コンポーネントが外部からレイアウト調整できるよう Modifier を受け取る。 | 各 `ui.components.*` | 10    |       |

---
最終更新日: 2026/07/13
