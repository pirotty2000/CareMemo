# CareMemo 課題・改善プロジェクト (project_TODO.md)

`project_RULES.md` に基づく現状のソースコードの課題一覧です。

---

## 🔴 重要度：高（システムの安定性・フリーズ防止・データ保護）

| 課題内容 | 対象ファイル | ルール参照 |
| :--- | :--- | :--- |
| **監査ログ記録時の例外保護**: `log` メソッド内で `try-catch` がなく、DBエラー時に業務処理が中断する。 | `data.repository.AuditLogRepository` | 7.3 |
| **リポジトリ側でのログ記録失敗の許容**: ログ記録の失敗が保存処理等の成功を妨げないよう例外をキャッチする。 | 各 `data.repository.*` | 7.3 |
| **エラー時のローディング解除の徹底**: `Flow.catch` 等を用い、データ取得失敗時も確実に `isLoading` を false にする。 | 各 `viewmodel.*ViewModel` | 4.3 |

## 🟡 重要度：中（UX・リソース効率・ライフサイクル管理）

| 課題内容 | 対象ファイル | ルール参照 |
| :--- | :--- | :--- |
| **ブランキング（チラつき）抑制**: 新規作成時の初期値セットに `LaunchedEffect` を使用している。 | `ui.components.health.PersonHealthComponents` | 3.3.1 |
| **ライフサイクル対応の状態収集**: `collectAsState()` を `collectAsStateWithLifecycle()` に置き換える。 | 各 `ui.screens.*` | 10 |

## 🔵 重要度：低（コーディング規約・保守性）

| 課題内容 | 対象ファイル | ルール参照 |
| :--- | :--- | :--- |
| **Modifier 引数の欠落・位置不備**: 多くの Composable で `Modifier` が引数にない、または最初のオプション引数になっていない。 | ほぼすべての `ui.*` ファイル | 10 |
| **Stateless Composable の Modifier 対応**: 下位コンポーネントが外部からレイアウト調整できるよう Modifier を受け取る。 | 各 `ui.components.*` | 10 |

---
最終更新日: 2026/07/09
