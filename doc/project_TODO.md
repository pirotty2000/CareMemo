# CareMemo 課題・改善プロジェクト (project_TODO.md)

`project_RULES.md` に基づく現状のソースコードの課題一覧です。

---

## 🔴 重要度：高（システムの安定性・フリーズ防止・データ保護）

| 課題内容 | 対象ファイル | ルール参照 | ステータス |
|:---|:---|:---|:---|
| 監査ログ記録時の例外保護: `log` メソッド内で `try-catch` がなく、DBエラー時に業務処理が中断する。 | `data.repository.AuditLogRepository` | 7.3 | ✅ 完了 |
| リポジトリ側でのログ記録失敗の許容: ログ記録の失敗が保存処理等の成功を妨げないよう例外をキャッチする。 | 各 `data.repository.*` | 7.3 | ✅ 完了 |
| **エラー時のローディング解除の徹底**: `Flow.catch` 等を用い、データ取得失敗時も確実に `isLoading` を false にする。 | 下記チェックリスト参照 | 4.3 | 進行中 |

### 【チェックリスト】エラー時のローディング解除の徹底

データ取得（Flow）や保存（Suspend関数）において、例外発生時も確実に `isLoading` を false に戻す修正の進捗状況です。

#### 共通改修ルール（PersonHealthViewModelを基準とする）
1.  **Flow (監視系)**: `onEach` で成功時解除、`catch` で失敗時解除を行う。
2.  **Suspend関数 (単発操作系)**: `try-finally` を使用し、`finally` ブロックで確実に解除する。
3.  **例外の可視化**: `catch` ブロック内で以下の2点を実施する。
    - `Log.e(TAG, "message", e)` による開発者用ログ出力。
    - `auditLogRepository.log(...)` を使用し、`actionType = "ERROR"` として監査ログに記録する（`details` に `e.toString()` を格納）。
4.  **テストの義務化**: ViewModel のユニットテストで、例外発生時に `isLoading == false` となること、および `auditLogRepository.log` (actionType = "ERROR") が呼ばれることを検証する。

- [x] `PersonListViewModel` (利用者一覧)
- [ ] `PersonDetailViewModel` (利用者共通フレーム)
- [x] `PersonHealthViewModel` (健康記録)
- [ ] `PersonConditionViewModel` (所見メモ・写真)
- [ ] `PersonMedicationViewModel` (服薬管理)
- [ ] `PersonEditViewModel` (利用者登録・編集)
- [ ] `BatchInputViewModel` (一括入力)
- [ ] `SettingsViewModel` (設定・バックアップ)
- [ ] `DeleteOrRestorePersonViewModel` (復帰・抹消)

---

## 🟡 重要度：中（UX・リソース効率・ライフサイクル管理）

| 課題内容 | 対象ファイル | ルール参照 | ステータス |
|:---|:---|:---|:---|
| **ブランキング（チラつき）抑制**: 新規作成時の初期値セットに `LaunchedEffect` を使用している。 | `ui.components.health.PersonHealthComponents` | 3.3.1 | |
| **ライフサイクル対応の状態収集**: `collectAsState()` を `collectAsStateWithLifecycle()` に置き換える。 | 各 `ui.screens.*` | 10 | |

## 🔵 重要度：低（コーディング規約・保守性）

| 課題内容 | 対象ファイル | ルール参照 | ステータス |
|:---|:---|:---|:---|
| **Modifier 引数の欠落・位置不備**: 多くの Composable で `Modifier` が引数にない、または最初のオプション引数になっていない。 | ほぼすべての `ui.*` ファイル | 10 | |
| **Stateless Composable の Modifier 対応**: 下位コンポーネントが外部からレイアウト調整できるよう Modifier を受け取る。 | 各 `ui.components.*` | 10 | |

---
最終更新日: 2026/07/12
