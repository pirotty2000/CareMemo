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

## ✅ 完了したリファクタリング

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

## 🏗️ 監査ログ拡張：操作結果（resultType）の導入
操作の「意図(actionType)」と「結果(resultType)」を分離し、DBエラー等の絞り込みを容易にします。

### 【対応状況】resultType 導入プロジェクト

| フェーズ | 作業内容 | 対象ファイル | ステータス |
|:---|:---|:---|:---|
| **1. 基盤定義** | `AuditLog` エンティティへの `resultType` カラム追加 | `data.AuditLog` | [✅] 完了 |
| | データベース・マイグレーションの実装と適用 | `data.AppDatabase` | [✅] 完了 |
| **2. 共通改修** | `AuditLogRepository.log` の引数拡張（初期値 `UNKNOWN`） | `AuditLogRepository` | [✅] 完了 |
| | `ErrorHandler` での例外自動判定ロジック実装 | `ViewModelCoroutineErrorHandler` | [✅] 完了 |
| **3. 既存適用** | 全リポジトリの成功ログ記録箇所への `SUCCESS` 明示 | 各 `data.repository.*` | [✅] 完了 |
| | 既存テストコードの期待値修正（ERROR → DB_ERROR等） | 各 `*Test.kt` | [✅] 完了 |
| **4. 表示対応** | 監査ログ画面への `resultType` 表示およびフィルター追加 | `AuditLogScreen` | [✅] 完了 |

#### 詳細作業ステップ
- [✅] **[Entity]** `AuditLog` に `resultType: String` を追加。
- [✅] **[Migration]** 既存データに対し `UNKNOWN` を設定するマイグレーションを作成。
- [✅] **[Repository]** `log()` メソッドの引数を `(..., resultType: String = "UNKNOWN")` に変更。
- [✅] **[ErrorHandler]** `e is SQLException` 等を判定し、`DB_ERROR` か `OTHER_ERROR` を設定するよう修正。
- [✅] **[Refactor]** 成功時に `log()` を呼んでいる全箇所で `resultType = "SUCCESS"` を明示的に指定。
- [✅] **[Test]** `actionType = "ERROR"` をチェックしている既存テストを、新しい `resultType` の検証に移行。
- [✅] **[Mapper]** 識別子を日本語に変換する Mappers を `ui/mapping` に新規作成。
- [✅] **[UI]** 監査ログ画面に `resultType` の表示とフィルターを追加。

---

## 🚀 将来的なサーバー移行（PostgreSQL/SpringBoot）への備え
将来的なサーバー同期を見据え、データの整合性とエラーハンドリングの堅牢性を高めるための基盤準備です。

| 課題内容                                                                          | 対象ファイル            | ルール参照 | ステータス |
|:------------------------------------------------------------------------------|:------------------|:------|:------|
| **主キーの UUID 化**: サーバーとの ID 競合を避けるため、`Long` (Auto-increment) から `String` (UUID) へ移行する。 | 全 `data.entity.*` | -     |       |
| **リモート例外の定義**: `AppException` に通信エラー（Network/Server/Auth）を追加し、`BaseViewModel` でのハンドリングに備える。 | `util.AppException` | 3.6    |       |
| **更新日時 (updated_at) の導入**: 同期時の競合解決（楽観的ロック）のため、全テーブルに最終更新日時カラムを追加する。<br>※「生年月日」等の日付型で発生した1日のズレを防ぐため、時刻を含む更新日時は一貫して **UTC (Unix Timestamp)** で扱う。 | 全 `data.entity.*` | -     |       |
| **DTO と Entity の分離徹底**: Logic レイヤーにおいて、APIデータ(DTO)とDBデータ(Entity)の変換を分離できる構造を維持する。  | 各 `logic.*`       | 3.5.1  |       |

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
