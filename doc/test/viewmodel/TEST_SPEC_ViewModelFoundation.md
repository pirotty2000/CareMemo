# テスト仕様書 - ViewModel 共通基盤 (Foundation)

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/ViewModelFoundationTest.kt`

## 1. 概要
CareMemo アプリ全体の ViewModel における「状態管理の原子性」および「実行制御の安全性」という、アーキテクチャ上の根幹（契約）を検証する。

## 2. 状態管理の原子性 (State Atomicity)
**目的:** UI 状態が常に一貫性を保ち、途中の矛盾した状態が露出しないことを保証する。

| ID | テスト項目 | 検証内容 | 期待結果 |
|:---|:---|:---|:---|
| BASE_01 | 状態更新の原子性 | `updateUiState` を連続して実行 | 全ての更新が原子的に（スレッドセーフに）反映され、最終的な状態が正しいこと |

## 3. 実行制御の安全性 (Safe Execution)
**目的:** `safeLaunch` および `safeCollect` による、共通のエラーハンドリングとローディング表示の挙動を保証する。

| ID | テスト項目 | 検証内容 | 期待結果 |
|:---|:---|:---|:---|
| BASE_02 | ローディング自動管理 | `safeLaunch` の開始・終了 | 処理開始時に `isLoading=true`、終了（成功・失敗問わず）時に `false` になること |
| BASE_03 | 例外ハンドリング委譲 | ブロック内で未キャッチ例外が発生 | `CoroutineErrorHandler` に処理が委譲され、監査ログへの記録が試みられること |
| BASE_04 | キャンセル例外の伝播 | `CancellationException` が発生 | ハンドラへ委譲せず、コルーチンの標準仕様通り上位へ再スローされること |
