# BaseViewModel 共通処理 (safeLaunch / safeCollect) 仕様書 (project_BASE_VIEWMODEL_SPEC.md)

## 1. 概要
本ドキュメントは、`BaseUiStateViewModel` に導入されているコルーチン実行および例外ハンドリングの共通基盤（`safeLaunch`, `safeCollect`）の仕様を定義します。

### 導入の目的
| No | 目的              | 内容                                                     |
|:--:|:----------------|:-------------------------------------------------------|
| 1  | **ボイラープレートの排除** | `try-catch` および `CancellationException` の判定ロジックを共通化する。 |
| 2  | **ローディング解除の徹底** | `finally` でのローディングフラグ解除を共通化し、画面フリーズを防止する。              |
| 3  | **責務の集約**       | エラーの記録（監査ログ/Logcat）および通知（UI）の判断をハンドラに委譲する。             |
| 4  | **Flow への対応**   | 監視（collect）におけるエラーハンドリング、ローディング制御、および再試行（retry）を統一する。  |
| 5  | **状態管理の一貫性**    | MVI パターンに基づき、単一の `UiState` と型安全なイベント通知を実現する。           |

---

## 2. 設計原則
| No | 原則                                    | 内容                                                                                |
|:--:|:--------------------------------------|:----------------------------------------------------------------------------------|
| 1  | **Safety First**                      | ネットワーク/DBエラーや、ハンドラ自体の失敗が発生しても、UI 状態を安全に戻す。                                        |
| 2  | **Cancellation Aware**                | `CancellationException` は再スローし、エラーとして扱わない。                                        |
| 3  | **Structured Context**                | エラー情報を `ErrorContext` に集約し、DSL 形式でイミュータブルに構築する。                                   |
| 4  | **Non-Null Handler**                  | ハンドラは常に存在することを保証し、ViewModel 側の分岐を排除する。                                            |
| 5  | **Diagnostic Fatal Errors**           | `Error` 系の致命的例外も一時的にキャッチしてログ記録を試行し、記録後に再スローする。                                    |
| 6  | **Single Ownership of Loading State** | 同一の `loadingState` を複数の `safeLaunch` や `safeCollect` で同時に使用してはならない（レースコンディション防止）。 |
| 7  | **Stateless Communication**           | UI への通知は `UiEvent` (SharedFlow) を通じて行い、ViewModel は UI の状態を直接操作しない。                |

---

## 3. 構造定義

### 3.1. ErrorContext & Builder
例外発生時の付随情報を保持します。不変（Immutable）なデータクラスとし、DSL 形式で構築します。

```kotlin
data class ErrorContext(
    val featureName: String,
    val operation: String,
    val tableName: String? = null,
    val affectedId: String? = null,
    val errorTitleRes: Int? = null,
    val errorMessageRes: Int? = null
)

class ErrorContextBuilder(private val featureName: String, private val operation: String) {
    var tableName: String? = null
    var affectedId: String? = null
    var errorTitleRes: Int? = null
    var errorMessageRes: Int? = null

    fun build() = ErrorContext(featureName, operation, tableName, affectedId, errorTitleRes, errorMessageRes)
}
```

### 3.2. CoroutineErrorHandler
例外発生時の具体的振る舞い（ログ出力・UI通知）を定義するインターフェースです。

```kotlin
interface CoroutineErrorHandler {
    suspend fun handleException(e: Throwable, context: ErrorContext)
}
```

### 3.3. AppException (例外階層)
アプリ固有の意味を持つ例外群です。UI で表示すべきリソース ID を保持します。

```kotlin
open class AppException(
    val titleResId: Int? = null,
    val messageResId: Int? = null,
    val args: List<Any> = emptyList(),
    logMessage: String,
    cause: Throwable? = null
) : Exception(logMessage, cause)

class AppValidationException(...) : AppException(...)
class AppIOException(...) : AppException(...)
class AppDataException(...) : AppException(...)
class AppExternalException(...) : AppException(...)
class AppSecurityException(...) : AppException(...)
```

---

## 4. 例外分類ポリシー

発生した例外の型に基づき、以下のポリシーで処理を行います。

| 例外型                       | 振る舞い              | 理由                         |
|:--------------------------|:------------------|:---------------------------|
| **CancellationException** | **再スロー (throw)**  | ライフサイクルに伴う正常な中断のため。        |
| **Exception**             | **ハンドラへ委譲**       | 通知や記録が必要な通常の異常系。           |
| **Error**                 | **ハンドラへ委譲後、再スロー** | 致命的な異常。ログ記録後に速やかにクラッシュさせる。 |

---

## 5. BaseUiStateViewModel への実装仕様

### 5.1. イベント通知仕様 (MVI)
一過性の通知（スナックバー、ダイアログ）と、画面固有のイベント（遷移等）を分離して管理します。

```kotlin
sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data class ShowSnackbarRes(val resId: Int, val args: List<Any> = emptyList()) : UiEvent
    data class ShowErrorDialog(val title: String, val message: String) : UiEvent
    data class ShowErrorDialogRes(val titleResId: Int, val messageResId: Int, val args: List<Any> = emptyList()) : UiEvent
    data class SaveSuccess(val id: String? = null) : UiEvent
    // ... 他、情報ダイアログ、上書き確認など
}

// 共通通知イベントを送出するメソッド
protected fun sendUiEvent(event: UiEvent) {
    viewModelScope.launch { _uiEventFlow.emit(event) }
}
```

### 5.2. safeLaunch (単発実行)
非同期処理の開始・終了時に `loadingState` を自動更新し、例外発生時は `featureName` の妥当性をチェックしながらハンドラへ委譲します。

```kotlin
open fun safeLaunch(
    operation: String,
    loadingState: MutableStateFlow<Boolean>? = null,
    contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job {
    var isFeatureNameValid = true
    val safeFeatureName = try { featureName } catch (_: Exception) {
        isFeatureNameValid = false
        "Unknown"
    }

    val context = ErrorContextBuilder(safeFeatureName, operation).apply { contextBuilder?.invoke(this) }.build()
    val actualLoadingState = loadingState ?: loadingStateProxy

    return scope.launch {
        actualLoadingState.value = true
        try {
            block()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            
            // featureName 未定義時は GUARD_SKIPPED として記録
            val effectiveContext = if (!isFeatureNameValid) context.copy(tableName = "GUARD_SKIPPED") else context
            coroutineErrorHandler.handleException(t, effectiveContext)
            
            if (t is Error) throw t
        } finally {
            actualLoadingState.value = false
        }
    }
}
```

### 5.3. safeCollect (Flow 購読)
再試行（retry）機能と、初回ロード（INITIAL）/ 継続監視（MONITORING）のモード切り替えを提供します。

```kotlin
open fun <T> safeCollect(
    operation: String,
    mode: CollectMode,
    loadingState: MutableStateFlow<Boolean>? = null,
    contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
    retryCount: Int = 0,
    retryDelayMillis: Long = 1000L,
    flowProvider: () -> Flow<T>,
    action: suspend (T) -> Unit
): Job {
    // ... context 構築ロジック ...
    return scope.launch {
        var currentRetry = 0
        while (true) {
            if (mode == CollectMode.INITIAL) actualLoadingState.value = true
            try {
                flowProvider().collect { value ->
                    if (mode == CollectMode.INITIAL) actualLoadingState.value = false
                    action(value)
                }
                break
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (currentRetry < retryCount) {
                    currentRetry++
                    delay(retryDelayMillis.milliseconds)
                    continue
                }
                // ... handleException 呼び出し ...
                break
            } finally {
                if (mode == CollectMode.INITIAL) actualLoadingState.value = false
            }
        }
    }
}
```

---

## 6. 識別子と運用のルール

### 6.1. 名称の定義ルール
| No | 項目              | 定義ルール                                                                               |
|:--:|:----------------|:------------------------------------------------------------------------------------|
| 1  | **featureName** | 各 ViewModel で `protected abstract val` として定義。マッパー (`FeatureNameMapper.kt`) に登録すること。 |
| 2  | **operation**   | 各 ViewModel 内で `private const val OP_...` として定義。                                    |
| 3  | **tableName**   | `AppConstants` またはリポジトリ層で定義される物理テーブル名。                                              |

### 6.2. 永続性と表示名の分離
| No | 項目        | 内容                                                                                    |
|:--:|:----------|:--------------------------------------------------------------------------------------|
| 1  | **DB保存値** | 英語の識別子（例: `PersonList`, `IO_ERROR`）。                                                  |
| 2  | **表示用名称** | `ui/mapping/` 配下のマッパーで `strings.xml` のリソース ID に変換。ViewModel やデータ層を `Context` に依存させない。 |

---

## 7. Appendix: featureName 一覧 (2026/08/22更新)

監査ログに出力される機能識別子です。

| 識別子 (featureName)                | 日本語名称 (マッピング案) | 備考         |
|:---------------------------------|:---------------|:-----------|
| `PersonList`                     | 利用者一覧          |            |
| `PersonEdit`                     | 利用者登録・編集       |            |
| `PersonBase`                     | 利用者：基底         |            |
| `PersonHealth`                   | 健康記録           |            |
| `BatchInput`                     | 一括入力           |            |
| `PersonCondition`                | 所見メモ           |            |
| `PersonMedication`               | 服薬管理           |            |
| `MedicalContact`                 | 緊急連絡先          |            |
| `PersonDetail`                   | 利用者詳細          | カテゴリ未指定時   |
| `PersonDetail/Base`              | 詳細/基底          |            |
| `PersonDetail/HEIGHT_AND_WEIGHT` | 詳細/身長体重        | 共通サマリー部分   |
| `PersonDetail/BP_AND_PULSE`      | 詳細/バイタル        | 共通サマリー部分   |
| `PersonDetail/GLUCOSE_AND_HBA1C` | 詳細/血糖値         | 共通サマリー部分   |
| `PersonDetail/CONDITION`         | 詳細/所見メモ        | 共通サマリー部分   |
| `PersonDetail/MEDICATION`        | 詳細/服薬管理        | 共通サマリー部分   |
| `UnassignedPhotoManagement`      | 未割り当て写真管理      |            |
| `AppMaintenance`                 | メンテナンス         | バックアップ・復元等 |
| `System`                         | システム           | 起動時・初期化処理等 |
| `Settings`                       | 設定             |            |
| `AuditLog`                       | 操作ログ参照         |            |
| `DeleteOrRestorePerson`          | 利用者アーカイブ管理     |            |

---

## 8. Appendix: resultType 一覧 (2026/08/22更新)

監査ログに記録される結果識別子です。

| 識別子 (resultType)       | 日本語名称 (案) | 意味                                   |
|:-----------------------|:----------|:-------------------------------------|
| **`SUCCESS`**          | 成功        | 操作が正常に完了した。                          |
| **`DB_ERROR`**         | DBエラー     | SQL 実行失敗、制約違反など。                     |
| **`IO_ERROR`**         | IOエラー     | ファイル削除・保存失敗、容量不足など。                  |
| **`FORMAT_ERROR`**     | 形式エラー     | データのパース失敗、不正なフォーマットなど。               |
| **`VALIDATION_ERROR`** | 検証エラー     | バリデーションチェック（必須、文字数など）の失敗。            |
| **`EXTERNAL_ERROR`**   | 外部エラー     | カメラ起動失敗、インテント連携失敗など。                 |
| **`SECURITY_ERROR`**   | セキュリティエラー | 生体認証失敗、暗号化失敗など。                      |
| **`OTHER_ERROR`**      | その他エラー    | 想定外の Exception / Error。              |
| **`GUARD_SKIPPED`**    | ガード回避     | 重複ロードの防止、または実装ミスによる featureName 未定義。 |
| **`UNKNOWN`**          | 不明        | 結果判定不能、または初期値。                       |

---

## 9. Appendix: actionType 一覧 (2026/08/22更新)

監査ログにおける操作種別の識別子です。

| 識別子 (actionType)         | 日本語名称 (案) | 主な発生箇所・意味            |
|:-------------------------|:----------|:---------------------|
| **`INSERT`**             | 新規作成      | レコードの新規追加。           |
| **`UPDATE`**             | 更新        | 既存レコードの修正。           |
| **`DELETE`**             | 削除        | 記録単位（1件のデータ等）の削除。    |
| **`LOGICAL_DELETE`**     | 利用終了      | 利用者のアーカイブ（論理削除）。     |
| **`RESTORE`**            | 利用復帰      | アーカイブ状態からの復元。        |
| **`PERMANENT_DELETE`**   | 利用抹消      | 物理的な完全削除。            |
| **`CLEAR_ALL_ARCHIVED`** | 一括抹消      | 利用終了者全員の物理削除。        |
| **`INFO`**               | 情報        | ガードの発動や設定の読み込みなどの通知。 |
| **`ERROR`**              | エラー       | 予期せぬ例外の捕捉。           |

---
最終更新日: 2026/08/22
