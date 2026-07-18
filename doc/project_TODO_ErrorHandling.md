# Error Handling & Execution Control TODO List

このドキュメントは、`project_RULES.md`（2026/07/15更新版）に基づき、既存ソースコードのエラーハンドリング、実行制御、および Logic と ViewModel の役割分担に関する修正状況を管理するものです。

## 1. 全体方針 (4層の責務分担)

- **Repository**: 純粋なデータの出し入れ。低レイヤー例外は再スローする。
- **Logic**: 業務ルールの判定。判定結果は詳細な「事実（Enum/Sealed型）」として返す。契約違反には標準例外を使用。
- **ViewModel**: UI判断と状態管理。Logic の「事実」を UIリソースに「翻訳」し、`AppException` をスローする。
- **Handler**: 通知と記録。`AppException` を受け取り、ダイアログ表示と監査ログ記録を自動完遂する。

---

## 2. 修正・確認が必要な箇所

### 2.1. ViewModels (実行制御と翻訳)

#### [完了済み]
- [x] **viewModelScope.launch の一掃**: `BaseViewModel` を除く全 ViewModel で `safeLaunch` / `safeCollect` への移行完了。
- [x] **手動ローディング制御の廃止**: `onEach { _isLoading.value = false }` 等を `safeCollect` 等の基盤制御へ移行完了。
- [x] **正常系通知の統一**: 成功時の通知を `showSnackbar` に統一。
- [x] **Logic 判定結果の翻訳実装**: 全ての対象 Logic の修正に合わせ、ViewModel 側での Enum マッピングと `AppException` スローを実装完了。

### 2.2. Repositories (データ操作の純粋化)

#### [完了済み]
- [x] **AppMaintenanceRepository.kt**: 服薬レコードのクレンジングロジックを `MedicationLogic` へ抽出完了。

### 2.3. Logics (事実の定義)

※ Logic は `AppException` (UI寄り) を直接投げず、失敗理由を Enum や Sealed 型で返し、Pure Kotlin を維持します。

#### [logic/common]
- [x] **HealthLogic.kt**: 判定失敗（異常値・不正値）の詳細を Enum 化し、ViewModel で `AppValidationException` にマッピング。
- [x] **ConditionLogic.kt**: 重複チェック時の判定詳細（事実）を返し、ViewModel での例外通知を詳細化。
- [x] **JapaneseDateLogic.kt**: パース失敗や不正指定時の「理由」を詳細に返し、ViewModel が最適なエラー通知を行えるよう改善。
- [x] **BirthEra.kt**: Androidリソース依存を排除し、`EraDisplayMapper` へ表示責務を分離。

#### [logic/feature]
- [x] **SettingsLogic.kt**: ZIP検証、バージョン、容量等のチェック結果を Enum 化し、Android依存（StatFs）を排除。
- [x] **BatchInputLogic.kt**: 一括入力の整合性チェック失敗（どのカテゴリが重複したか等）を識別可能にし、具体的エラー通知を実現。
- [x] **PersonEditLogic**: 生年月日妥当性を含むビジネスルール違反を詳細な型で返し、ViewModel側での翻訳を実装。
- [x] **PersonListLogic**: 重複判定（有効/終了者の別）を事実として定義し、ViewModel側での翻訳を実装。
- [x] **PersonHealthLogic / PersonConditionLogic**: 完了。

---

## 3. 実装上の注意（AppException の活用）

ViewModel での翻訳時は、以下の継承関係に基づいて適切な例外を選択してください。

```kotlin
AppException
├── AppValidationException (バリデーション失敗・業務ルール違反)
├── AppIOException (ファイル入出力・接続エラー)
├── AppDataException (データ整合性・DBエラー)
├── AppExternalException (外部アプリ連携・ハードウェアエラー)
└── AppSecurityException (生体認証・権限エラー)
```

## 4. 完了確認チェックリスト

- [x] `viewModelScope.launch` の直接使用がプロジェクトから一掃されているか。
- [x] `safeLaunch` ブロック内で `showError` が直接呼ばれていないか。（※パスワード再試行フロー等の特殊ケースを除く）
- [x] すべての `AppException` が適切にログ（AuditLog）に記録されているか。
- [x] 正常系の完了通知が `showSnackbar` で一貫して行われているか。
- [x] 全ての Logic 判定が Boolean ではなく詳細な「事実（Enum等）」を返すようになっているか。
- [x] ViewModel が Logic の結果を適切に `AppException` へ翻訳しているか。
- [x] 全ての Logic クラスが Pure Kotlin であり、Android フレームワークやリソースに依存していないか。
