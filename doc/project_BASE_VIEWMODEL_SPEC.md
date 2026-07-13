# BaseViewModel 共通処理 (safeLaunch / safeCollect) 仕様書 (project_BASE_VIEWMODEL_SPEC.md)

## 1. 概要
本ドキュメントは、`BaseViewModel` に導入されるコルーチン実行および例外ハンドリングの共通基盤（`safeLaunch`, `safeCollect`）の仕様を定義します。

### 導入の目的
1.  **ボイラープレートの排除**: `try-catch` および `CancellationException` の判定ロジックを共通化する。
2.  **ローディング解除の徹底**: `finally` での `isLoading = false` を共通化し、画面フリーズを防止する。
3.  **責務の集約**: エラーの記録（Logcat/DB）および通知（UI）の判断をハンドラに委譲する。
4.  **Flow への対応**: 監視（collect）におけるエラーハンドリングとローディング制御を統一する。

---

## 2. 設計原則

-   **Safety First**: ネットワーク/DBエラーや、ハンドラ自体の失敗が発生しても、UI 状態を安全に戻す。
-   **Cancellation Aware**: `CancellationException` は再スローし、エラーとして扱わない。
-   **Structured Context**: エラー情報を `ErrorContext` に集約し、DSL 形式でイミュータブルに構築する。
-   **Non-Null Handler**: ハンドラは常に存在することを保証し、ViewModel 側の分岐を排除する。
-   **Diagnostic Fatal Errors**: `Error` 系の致命的例外も一時的にキャッチしてログ記録を試行し、記録後に再スローする。
-   **Single Ownership of Loading State**: 同一の `loadingState` (MutableStateFlow<Boolean>) を、複数の `safeLaunch` や `safeCollect` で同時に使用してはならない。

---

## 3. 構造定義

### 3.1. ErrorContext
例外発生時の付随情報を保持します。不変（Immutable）なデータクラスとし、構築は Builder を介して行います。

```kotlin
data class ErrorContext(
    val featureName: String,
    val operation: String,
    val tableName: String? = null,
    val affectedId: String? = null,
    val errorTitleRes: Int? = null,
    val errorMessageRes: Int? = null
)

/** DSL 用の Builder クラス */
class ErrorContextBuilder(val featureName: String, val operation: String) {
    var tableName: String? = null
    var affectedId: String? = null
    var errorTitleRes: Int? = null
    var errorMessageRes: Int? = null

    fun build() = ErrorContext(featureName, operation, tableName, affectedId, errorTitleRes, errorMessageRes)
}
```

### 3.2. CoroutineErrorHandler
例外発生時の具体的振る舞い（ログ出力・UI通知）を定義するインターフェースです。

**責務:**
- 例外の記録およびユーザーへの通知を行う。
- 記録先（DB/ファイル/外部サービス）や通知方法（ダイアログ/スナックバー）の詳細は、本インターフェースの実装クラスに委譲し、ViewModel からは隠蔽する。

```kotlin
interface CoroutineErrorHandler {
    /**
     * 例外を処理する。
     * Logcat への出力、監査ログへの記録、UI通知（showError）の呼び出しなどを実装する。
     */
    suspend fun handleException(e: Throwable, context: ErrorContext)
}
```

---

## 4. 例外分類ポリシー

発生した例外の型に基づき、以下のポリシーで処理を行います。特に `Error` 発生時は、通知よりも記録を優先し、安全なクラッシュを許容します。

| 例外型 | 振る舞い | 理由 |
| :--- | :--- | :--- |
| **CancellationException** | **再スロー (throw)** | ライフサイクルに伴う正常な中断であり、エラーではないため。 |
| **Exception** | **ハンドラへ委譲** | アプリケーションレベルで通知や記録が必要な通常の異常系. |
| **Error** | **ハンドラへ委譲後、再スロー** | 致命的な異常。ログ記録を試行するが、状態不安定のため UI 通知はベストエフォートとし、速やかにクラッシュさせる。 |

---

## 5. BaseViewModel への実装仕様

### 5.1. 基本プロパティ
```kotlin
abstract class BaseViewModel(...) : ViewModel() {
    protected abstract val featureName: String
    protected lateinit var coroutineErrorHandler: CoroutineErrorHandler
}
```

### 5.2. safeLaunch (単発実行)
**loadingState の同時利用に関する制約:**
- 理由: 先行する処理が終了した際、後続の処理が実行中であっても `loadingState` が `false` になり、UI の整合性が崩れる（レースコンディション）ため。
- 対策: 複数の非同期処理が並行する可能性がある場合は、それぞれに個別のフラグを用意するか、呼び出し前に既存の `Job` をキャンセルして排他実行すること。

```kotlin
protected fun safeLaunch(
    operation: String,
    loadingState: MutableStateFlow<Boolean>? = null,
    contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val context = ErrorContextBuilder(featureName, operation).apply { contextBuilder?.invoke(this) }.build()
    return scope.launch {
        loadingState?.value = true
        try {
            block()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            coroutineErrorHandler.handleException(t, context)
            if (t is Error) throw t
        } finally {
            loadingState?.value = false
        }
    }
}
```

### 5.3. safeCollect (Flow 購読)
**責務:**
- ViewModel が Flow を購読する際の標準実装を提供する。
- 特殊な Flow 制御（複雑な retry、combine、flatMapLatest 等）が必要な場合は、本メソッドを使用せず、個別実装を行うことを許容する。その際も、第2章の設計原則（例外分類やローディング解除）に従うこと。

```kotlin
enum class CollectMode {
    INITIAL,   // 初回ロード用：データ受信またはエラーで loadingState を解除
    MONITORING // 継続監視用：原則として loadingState を制御しない
}

protected fun <T> safeCollect(
    operation: String,
    mode: CollectMode = CollectMode.INITIAL,
    loadingState: MutableStateFlow<Boolean>? = null,
    contextBuilder: (ErrorContextBuilder.() -> Unit)? = null,
    flowProvider: () -> Flow<T>,
    action: suspend (T) -> Unit
): Job {
    val context = ErrorContextBuilder(featureName, operation).apply { contextBuilder?.invoke(this) }.build()
    return scope.launch {
        if (mode == CollectMode.INITIAL) loadingState?.value = true
        try {
            flowProvider().collect { 
                if (mode == CollectMode.INITIAL) loadingState?.value = false
                action(it)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            coroutineErrorHandler.handleException(t, context)
            if (t is Error) throw t
        } finally {
            if (mode == CollectMode.INITIAL) loadingState?.value = false
        }
    }
}
```

---

## 6. 識別子と運用のルール
監査ログおよびエラーハンドリングで使用する各名称は、以下のルールに従って定義する。

### 6.1. 名称の定義場所
1.  **tableName (テーブル名)**:
    -   `AppConstants` 等のプロジェクト共通の Enum または共有定数として管理する。
2.  **featureName (機能名)**:
    -   各 ViewModel で `companion object` の定数を定義し、`abstract val featureName` でその定数を返す。
    -   論理的な機能の集合である場合は、`Detail/Health` のように階層化して定義することを推奨する。
3.  **operation (操作名)**:
    -   各 ViewModel 内で `private const val OP_...` として定義する。
4.  **resultType (操作結果)**:
    -   操作が成功したか、どのような種類のエラーが発生したかを分類する。
    -   後述の `resultType` 一覧に従い、自動判定または明示的に指定する。

### 6.2. 永続性と表示名の分離 (マッピング方針)
監査ログの可読性と保守性を両立するため、以下の設計指針を適用する。

1.  **DB保存値 (識別子)**:
    -   `featureName`, `operation`, `actionType`, `resultType` には、不変な英語の識別子を使用する。
    -   理由: DB 検索の容易性と、ソースコードとの紐付けの確実性を担保するため。
2.  **表示用名称 (ラベル)**:
    -   ログ画面等で人間が読みやすい名前が必要な場合は、表示層（UI）でマッピングを行う。
    -   配置場所: `ui/mapping/` パッケージ配下の Mapper クラス（例: `FeatureNameMapper.kt`）。
    -   実装方法: 英語識別子と `strings.xml` のリソース ID を紐付ける。
    -   理由: ViewModel や DB を `Context` (Resource) に依存させず、多言語対応や文言変更を容易にするため。

---

## 7. テスト指針

1.  **正常系**: 終了後に `loadingState` が `false` になること。
2.  **異常系**: `handleException` が呼ばれ、`loadingState` が `false` になること。
3.  **キャンセル**: `CancellationException` 時、例外が伝搬されること。
4.  **致命的エラー**: `Error` 発生時、`handleException` が呼ばれた後に例外が再スローされること。
5.  **ハンドラ故障**: **`handleException` 自体が例外を投げた場合でも、ViewModel の `finally` ブロックでローディングが解除されること。**
6.  **モード別挙動**: `MONITORING` モード時、データ受信によって `loadingState` が変化しないこと。

---
## 8. Appendix: featureName 一覧 (2026/07/13時点)

日本語マッピング検討のための、現状の全識別子リストです。

| 分類           | featureName (識別子)                | 想定される日本語名 (検討案) | 備考       |
|:-------------|:---------------------------------|:----------------|:---------|
| **利用者管理**    | `PersonList`                     | 利用者：一覧          |          |
|              | `PersonEdit`                     | 利用者：新規登録・編集     |          |
|              | `DeleteOrRestorePerson`          | 利用者：利用終了        |          |
|              | `PersonBase`                     | 利用者：基底          |          |
| **健康管理 (A)** | `PersonHealth`                   | 健康記録            | 単発の保存・削除 |
|              | `BatchInput`                     | 一括入力            |          |
|              | `PersonDetail/HEIGHT_AND_WEIGHT` | 詳細/身長体重         | 共通サマリー部分 |
|              | `PersonDetail/BP_AND_PULSE`      | 詳細/バイタル         | 共通サマリー部分 |
|              | `PersonDetail/GLUCOSE_AND_HBA1C` | 詳細/血糖値          | 共通サマリー部分 |
| **所見メモ (B)** | `PersonCondition`                | 所見メモ            | 写真処理含む   |
|              | `PersonDetail/CONDITION`         | 詳細/所見メモ         | 共通サマリー部分 |
| **服薬管理 (C)** | `PersonMedication`               | 服薬管理            |          |
|              | `PersonDetail/MEDICATION`        | 詳細/服薬管理         | 共通サマリー部分 |
| **システム**     | `Settings`                       | 設定・管理           |          |
|              | `PersonDetail/Base`              | 詳細/基底           | カテゴリ未指定時 |

---
## 9. Appendix: actionType 一覧 (2026/07/13時点)

監査ログの `actionType` カラムに記録される識別子と、日本語表示名のマッピング案です。

| 識別子 (actionType)         | 日本語名称 (案)  | 主な発生箇所・意味                     |
|:-------------------------|:-----------|:------------------------------|
| **`INSERT`**             | **INSERT** | 利用者、バイタル、血糖値、所見メモなどの新規作成      |
| **`UPDATE`**             | **UPDATE** | 既存データの修正、写真の紐付け更新など           |
| **`DELETE`**             | **DELETE** | 記録単位（バイタル1件など）の削除             |
| **`LOGICAL_DELETE`**     | **利用終了**   | 利用者のアーカイブ化（論理削除）および関連データの非表示化 |
| **`RESTORE`**            | **利用復帰**   | 利用終了状態からの復元（論理削除の解除）          |
| **`PERMANENT_DELETE`**   | **利用抹消**   | アーカイブされた利用者の物理的な完全削除          |
| **`CLEAR_ALL_ARCHIVED`** | **一括抹消**   | 利用終了者全員のデータを物理的に一括削除          |

---
## 10. Appendix: resultType 一覧 (2026/07/13時点)

監査ログの `resultType` カラムに記録される識別子です。
「どのような操作をしたか(actionType)」に対し、「どのような結果になったか」を記録します。

| 識別子 (resultType) | 日本語名称 (案) | 意味 |
|:---|:---|:---|
| **`SUCCESS`** | **成功** | 操作が正常に完了した場合。Repository 等で明示的に記録する。 |
| **`DB_ERROR`** | **DBエラー** | データベース操作に関連する例外（SQLException 等）が発生した場合。 |
| **`OTHER_ERROR`** | **その他エラー** | DB 以外（ロジック、ファイル IO、システム等）の例外が発生した場合。 |
| **`UNKNOWN`** | **不明** | **デフォルト値。** 結果が判定されていない、または古いバージョンのデータ。 |

※ 将来的に `NETWORK_ERROR` や `VALIDATION_ERROR` など、必要に応じて分類を追加することを想定しています。

---
最終更新日: 2026/07/13
