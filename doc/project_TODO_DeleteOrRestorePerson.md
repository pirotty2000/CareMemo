# SCR-S-003 利用者管理（復帰・抹消）調査結果と修正タスク

このドキュメントは、`project_RULES.md` の「4.2. 責務の分割」に基づいた `SCR-S-003` の調査結果と、今後実施すべき修正内容をまとめたものです。

---

## 1. 調査結果サマリー

`DeleteOrRestorePerson` 関連の実装を調査した結果、アーキテクチャの基本構造は維持されているものの、**「事実の判定と通知の分離（ルール 4.2 / 4.3）」**において改善の余地があることが判明しました。

### 各レイヤーの適合状況

| レイヤー | ファイル名 | 適合状況 | 備考 |
| :--- | :--- | :--- | :--- |
| **Repository** | `DeleteOrRestorePersonRepository.kt` | **良好** | 業務判断を持たず、純粋なデータ操作とログ記録に徹している。 |
| **Logic** | `DeleteOrRestorePersonLogic.kt` | **不十分** | Pure Kotlin であるが、バリデーション判定と「事実（Enum）」の返却が未実装。 |
| **ViewModel** | `DeleteOrRestorePersonViewModel.kt` | **不十分** | `safeLaunch` は活用されているが、Logic の判定結果を `AppException` へ翻訳する処理が欠落。 |
| **UI** | `DeleteOrRestorePerson.kt` | **良好** | System B の標準的なイベント収集パターンを実装済み。 |

---

## 2. 具体的な課題と乖離点

### Logic 層：事実（理由）の識別不足
- ルール 4.3 では「判定を行い、理由を詳細に識別可能な型（Enum等）を返す」ことが求められているが、現状は単純なフィルタリングロジックのみ。
- 「対象が選択されていない」等の業務バリデーションが Logic 層で定義・実装されていない。

### ViewModel 層：通知への翻訳ロジックの欠落
- ルール 4.4 では「Logic が返した事実を ViewModel が `AppException` に翻訳してスローする」ことが求められている。
- 現状は UI 側のボタン非活性制御に依存しており、ViewModel の `restoreSelectedPersons` 等のメソッド内でガード（例外スロー）が行われていない。

---

## 3. 今後の修正タスク (TODO)

### [ ] Logic の修正 (`DeleteOrRestorePersonLogic.kt`)
- [ ] 判定結果を示す `enum class DeleteOrRestoreValidationResult` を定義する（例：`SUCCESS`, `NO_SELECTION`）。
- [ ] 選択状態をチェックし、上記 Enum を返す `validate` メソッドを実装する。

### [ ] ViewModel の修正 (`DeleteOrRestorePersonViewModel.kt`)
- [ ] `restoreSelectedPersons` および `deleteSelectedPersons` の開始時に Logic の `validate` を呼び出す。
- [ ] 判定結果が `SUCCESS` 以外の場合、適切なメッセージリソースを指定して `AppValidationException` をスローする。

### [ ] UI の修正 (`DeleteOrRestorePerson.kt`)
- [ ] 現在、確認ダイアログの表示を UI 側の `selectedIds.isNotEmpty()` で制御しているが、これを ViewModel のガードと整合性が取れる形に維持する（または ViewModel 側の判定に寄せる）。

---
作成日: 2026/07/19
