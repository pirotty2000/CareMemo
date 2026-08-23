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

- **`baseline` の保持**: 編集開始時点の比較基準となる原始データを `baseline` として保持します。
- **[MUST] `isChanged` の算出**: ViewModel内で現在の入力値と `baseline` を比較し、`UiState.isChanged` を更新します。
- **[MUST] Process Death対策**: Process Death後も編集開始時点の `baseline` を維持する必要がある場合、SavedStateHandleへ必要なフィールドを退避します。
- **[禁止] UI層での検知**: `derivedStateOf` によるUI層での変更検知は禁止です。
- **一貫性の検証**: 値をbaselineと同じ状態に戻した際、`isChanged == false` になることをユニットテストで検証してください。

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

## 6. データモデルの境界とマッピング (DTO & Entity)

- **外部形式 (DTO)**: バックアップファイル（JSON）などの外部保存形式。
- **内部形式 (Entity)**: Room データベース等の内部永続化形式。
- **[MUST] マッピングの実行場所**: 
    - 外部形式から内部形式への変換、および変換時のバリデーションやフィルタリングは、**ViewModel または Logic レイヤー**で制御してください。
    - Repository は「Entity そのもの」を受け取るか、変換・加工が済んだ DTO を受け取って保存に専念する責務を負います。

## 7. 揮発性セッション状態 (Volatile Session State)

特定のライフサイクルイベントや、外部アプリ連携時のみ必要となる一時的な状態（フラグ）については、以下の指針で管理します。

- **`StateFlow` 非採用の基準**:
    - ストリームとして継続的に監視し続ける必要がないもの。
    - 特定のイベント（`onResume` 等）で命令的に一度だけ参照・消費されるもの。
    - UI コンポーネントがその値の変化に直接反応して表示を更新する必要がないもの。
- **実装パターン**:
    - 専用の Session クラス（例: `SecuritySession`）を作成し、`@Volatile var` を使用してスレッド間の可視性を確保します。
    - `CareMemoApplication` でシングルトンとして保持し、ViewModel や Activity へ注入します。
- **目的**: 不必要な Observable パターンのオーバーヘッドを避け、コードの意図（ワンショットの制御であること）を明確にします。

## 8. プロセス死耐性と状態復元 (State Restoration)

アプリケーションがシステムによってメモリ解放（Process Death）された後、ユーザーが期待する状態を復元するための指針です。

### 8.1. 状態の 4 分類と保存機構
画面上の全状態を以下の 4 つに分類し、最適な保存機構を選択します。

| 分類                     | SSOT          | 保存機構               | 内容                       |
|:-----------------------|:--------------|:-------------------|:-------------------------|
| **UI State**           | ViewModel     | `StateFlow`        | 通常動作時の真実のソース。            |
| **Restorable State**   | ViewModel     | `SavedStateHandle` | **「復元用のバックアップ」**。未保存入力等。 |
| **Nav State**          | NavController | `Type-safe Nav`    | 画面のアイデンティティ（ID等）。        |
| **Transient UI State** | Composable    | `rememberSaveable` | スクロール位置等の純粋な UI 状態。      |

### 8.2. CareMemo 状態管理 7 原則
1. **SSOT の確立**: `ViewModel.uiState` (StateFlow) を真実のソースとする。
2. **バックアップ限定**: `SavedStateHandle` は Restorable State のバックアップのみに使用する。
3. **機械的同期の禁止**: `updateUiState()` と `SavedStateHandle` を連動させない。
4. **明示的バックアップ**: ビジネスロジック上の状態変更箇所で明示的に SSH を更新する。
5. **論理的一括復元**: 復元時は SSH から原始データを一括で読み込み、Logic 層を通して `UiState` を再構築する。
6. **初期化上書きの防止**: 復元データが存在する場合、通常の初期化処理（DBロード等）での上書きを確実に防ぐ。`isRestoring` フラグを用いて、ロード完了後の `uiState` 構築時に復元値を優先するガードを実装してください。
7. **未保存入力の優先**: ユーザーが入力した情報を失わないことを、復元における最優先事項とする。
8. **揮発性 UI 状態の ViewModel 昇格**: ダイアログの入力途中、表示モードの切り替え、写真キャプション等の「作業中の一時状態」は Composable の `rememberSaveable` ではなく ViewModel の `uiState` で管理し、プロセス死に備えてバックアップしてください。

### 8.3. 編集基準 (Baseline) の保護
プロセス死の間に DB の値が変化しても `isChanged` を正確に判定できるよう、編集開始時点の baseline フィールド（または `initialSnapshot`）を個別に `SavedStateHandle` へ退避してください。ID による DB 再取得は baseline の維持には不十分です。

---
最終更新日: 2026/08/23
