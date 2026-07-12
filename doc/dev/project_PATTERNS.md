# CareMemo 開発パターン集 (project_PATTERNS.md)

このドキュメントは、プロジェクト内で確立された「黄金パターン（ベストプラクティス）」を記録し、AI Agent および開発者が一貫した実装を行うためのリファレンスです。

---

## 1. ViewModel における堅牢なエラーハンドリングと可視化

### 1.1. 実装パターン (PersonHealthViewModel を基準とする)
エラー発生時、ユーザーを迷わせず（ローディング解除）、開発者が追跡可能にする（Logcat + 監査ログ）ための標準構成です。

#### Flow (監視系)
```kotlin
repository.getDataFlow(id)
    .onStart { _isLoading.value = true }
    .onEach { _isLoading.value = false }
    .catch { e ->
        _isLoading.value = false
        Log.e(TAG, "Data load error", e) // Logcat出力
        auditLogRepository.log( // 監査ログにERRORとして記録
            screenName = "ScreenName",
            operation = "operationName",
            tableName = "target_db",
            actionType = "ERROR",
            affectedId = id.toString(),
            details = e.toString()
        )
        showError(R.string.title, R.string.message, e.localizedMessage ?: "")
    }
```

#### Suspend関数 (保存・削除系)
```kotlin
viewModelScope.launch {
    _isLoading.value = true
    try {
        repository.saveData(data)
        showSnackbar(R.string.success_msg)
    } catch (e: Exception) {
        Log.e(TAG, "Save error", e)
        auditLogRepository.log( /* catch内でも上記と同様にログ記録 */ )
        showError(R.string.err_title, R.string.err_msg, e.localizedMessage ?: "")
    } finally {
        _isLoading.value = false // finallyで確実に解除
    }
}
```

---

## 2. ユニットテストにおける Android 依存の排除

### 2.1. Log クラスのモック化パターン
JVM 上のユニットテストで `android.util.Log` を使用しているコードをテストする場合、`Method e in android.util.Log not mocked` エラーを防ぐために以下の設定を必須とします。

```kotlin
@Before
fun setup() {
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0 // Log.e を無効化
    // ... 他のセットアップ
}

@After
fun tearDown() {
    unmockkStatic(Log::class) // 終了後に必ず解除
}
```

### 2.2. 例外発生時の挙動検証
`isLoading` が `false` に戻ることと、監査ログに `"ERROR"` が記録されたことをセットで検証します。

```kotlin
@Test
fun `例外発生時にisLoadingがfalseになりエラーログが記録されること`() = runTest {
    coEvery { repository.action() } throws RuntimeException("Error Message")

    viewModel.executeAction()

    assertEquals(false, viewModel.isLoading.value)
    coVerify(exactly = 1) { 
        auditLogRepository.log(
            actionType = "ERROR",
            details = match { it?.contains("Error Message") == true },
            // ... 他のパラメータ
        )
    }
}
```

---

## 3. ユニットテストにおけるモックの反映とタイミング

### 3.1. クラス初期化時に構築される Flow のテスト
ViewModel のプロパティとして定義されている `StateFlow`（例: `val userList = combine(...).stateIn(...)`）は、**ViewModel のインスタンス化時に一度だけリポジトリのメソッドを呼び出します。**

そのため、テストメソッド内で `every { repository.getAll() } throws ...` のようにモックを上書きしても、既に生成済みの Flow には反映されません。

#### 対策：例外系のテストでは ViewModel を再生成する
```kotlin
@Test
fun `例外発生時のテスト`() = runTest {
    // 1. ViewModel を作る「前」に、例外を投げるようにモックを設定
    every { repository.getAll() } returns flow { throw RuntimeException("Error") }

    // 2. その設定を反映させるために、テスト内で ViewModel を新規作成する
    val errorViewModel = PersonListViewModel(repository, ...)

    // 3. 検証を行う
    errorViewModel.userList.test { ... }
}
```
※ `flatMapLatest` を使用している Flow の場合は引数の変化で再実行されるため、ViewModel の再生成なしでモック上書きが効く場合がありますが、常に「モック設定 → ViewModel生成」の順序を守るのが最も安全です。
