# テスト仕様書 - BaseViewModel (共通コルーチン・エラーハンドリング基盤)

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/BaseViewModelTest.kt`

## 1. コルーチン実行制御テスト (safeLaunch)
**目的:** `safeLaunch` による実行制御が、正常系・異常系・キャンセル時に仕様通り動作することを検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| SL-01 | 正常終了時の状態遷移 | 処理終了後に `loadingState` が確実に `false` になること。 |
| SL-02 | Exception発生時の委譲 | `Exception` 発生時、`coroutineErrorHandler` に例外が委譲され、`loadingState` が `false` になること。 |
| SL-03 | キャンセル時の再スロー | `CancellationException` 発生時、ハンドラを呼ばずに例外が再スローされること。 |
| SL-04 | 致命的エラー時の診断 | `Error` 発生時、ハンドラに委譲された後に例外が再スローされ、`loadingState` が `false` になること。 |
| SL-05 | ハンドラ故障時の安全性 | ハンドラ自体が例外を投げた場合でも、`finally` ブロックが実行され `loadingState` が `false` になること。 |

## 2. Flow購読制御テスト (safeCollect)
**目的:** `safeCollect` における初回ロードと継続監視の挙動の差異、および例外保護を検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| SC-01 | 初回ロードモード (INITIAL) | データ受信時に `loadingState` が `false` になること。 |
| SC-02 | 継続監視モード (MONITORING) | データ受信によって `loadingState` が変化しない（初期値維持）こと。 |
| SC-03 | Flow例外発生時の保護 | Flow 内で例外が発生した際、ハンドラへ委譲され、`loadingState` が `false` になること。 |

## 3. 実装状況
| セクション | 項目 ID | ステータス | 備考 |
| :--- | :--- | :---: | :--- |
| 1. safeLaunch | SL-01 〜 SL-05 | ✅ 実装済み | `BaseViewModelTest.kt` にて検証済。 |
| 2. safeCollect | SC-01 〜 SC-03 | ✅ 実装済み | `BaseViewModelTest.kt` にて検証済。 |

---
作成日時: 2026/07/13
