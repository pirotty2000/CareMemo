# 状態管理と非同期実行制御

## 1. UI 状態 (UiState) の集約と原子性

- **1画面 1UiState**: 画面に必要な全状態（loading, data, isValid, isChanged等）を一つのデータクラスに集約します。
- **原子的な更新**: `_uiState.update { it.copy(...) }` のみで変更を行い、一時的な矛盾した状態の露出を防ぎます。
- **[MUST] ImmutableList の強制**: `UiState` および Composable 引数には必ず `ImmutableList` を使用してください。
    - **変換責任**: ViewModel が `.toImmutableList()` を行います。Repo/Logic 層では標準の `List` を使用します。

## 2. 状態初期化の安定性

- **[MUST] `LaunchedEffect` による初期化の禁止**: チラつき防止のため、初期値は ViewModel または `remember(key)` で設定します。
- **`remember` の鍵 (Key) の安定性**: 入力中に変動する値（時刻等）を鍵にしないでください。
- **[MUST] 非同期プロパティの補完**: デフォルト記録者名等の非同期値は、`init` 内で `.value` 参照せず、Flow を `collect` して「未入力時のみ埋める」ロジックで反映します。

## 3. 確実な変更検知 (Snapshot Comparison)

ユーザーの意図しないデータ破棄を防ぐため、以下のパターンを標準とします。

- **`initialSnapshot` の保持**: 編集開始時の値を `initialSnapshot` に保持します。
- **[MUST] `isChanged` の算出**: ViewModel 内で最新入力と `initialSnapshot` を比較し、`UiState.isChanged` を更新します。
    - **禁止**: `derivedStateOf` による UI 層での変更検知。
- **一貫性の検証**: 値を初期値に書き戻した際、`isChanged` が正確に `false` に戻ることをユニットテストで検証してください。

## 4. 非同期処理の実行と排他制御

### 4.1. 基盤機能 (safeLaunch / safeCollect)
- **原則**: すべての非同期処理は `BaseUiStateViewModel` の基盤機能を使用してください。`viewModelScope.launch` の直接使用は原則禁止です。
- **[MUST] 排他制御の徹底**: `safeLaunch` の戻り値である `Job` は、必ず対応する Job 変数に代入してください。

### 4.2. ガード型 (Guard Pattern)
- **対象**: 保存、削除、同期、インポート等。
- **実装**: メソッド先頭で `if (saveJob?.isActive == true) return`。
- **目的**: ボタン連打による二重保存の防止。

### 4.3. 上書き型 (Override Pattern)
- **対象**: 検索、詳細ロード等。
- **実装**: メソッド先頭で `loadJob?.cancel()`。
- **目的**: 常に最新の要求結果のみを UI に反映させ、競合を防ぐ。

## 5. 標準実装テンプレート

### 5.1. 変更検知と更新ヘルパー
```kotlin
private var initialSnapshot: EditInput? = null

private fun updateState(reducer: (UiState) -> UiState) {
    updateUiState { current ->
        val next = reducer(current)
        // [MUST] 最新入力とスナップショットを比較して一元管理
        next.copy(
            isChanged = next.editInput != initialSnapshot,
            isValid = Logic.isValid(next.editInput)
        )
    }
}
```

### 5.2. 非同期検索フロー (上書き型)
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
private val searchResults = uiState
    .map { it.searchQuery }
    .distinctUntilChanged()
    .flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else repository.searchFlow(query) // [MUST] 古い検索は自動キャンセルされる
    }
```

### 5.3. 保存・破壊的操作 (ガード型)
```kotlin
fun save() {
    // [MUST] 二重実行防止
    if (saveJob?.isActive == true) return

    saveJob = safeLaunch(
        operation = OP_SAVE,
        loadingState = loadingStateProxy,
        contextBuilder = {
            tableName = TABLE_NAME
            affectedId = currentState.id ?: ""
        }
    ) {
        // 保存ロジック...
        
        // 最終行で成功通知
        sendUiEvent(UiEvent.SaveSuccess())
        sendViewEvent(ViewEvent.NavigateBack)
    }
}
```

---
最終更新日: 2026/08/14
