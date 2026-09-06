# 状態管理と非同期実行制御

## 1. UI 状態 (UiState) の集約と原子性

- **1画面 1UiState**: 画面に必要な全状態（loading, data, isValid, isChanged等）を一つのデータクラスに集約します。
- **原子的な更新**: `_uiState.update { it.copy(...) }` のみで変更を行い、一時的な矛盾した状態の露出を防ぎます。
- **[MUST] ImmutableList の強制**: `UiState` および Composable 引数には必ず `ImmutableList` を使用してください。
    - **変換責任**: ViewModel が `.toImmutableList()` を行います。Repo/Logic 層では標準の `List` を使用します。

## 2. UI 状態設計のセマンティクス (L/E/E/C + Content 詳細)

- CareMemoでは、単一のUiState内部の状態を、その役割・意味に基づき以下の3つのセマンティクス（意味論）で分類・設計します。
- その上で、プロセス死に対する復元要件や状態の寿命を評価し、適切な保存機構を決定します。

### 2.1. 3層構造の定義

1.  **構造的状態 (Structural State: L/E/E/C)**
    *   画面全体が現在どのフェーズにあるかを表す（`Loading`, `Empty`, `Error`, `Content`）。
    *   **役割**: 画面全体の表示構造（レイアウトの切り替え）を決定する。
    *   **[原則]**: 画面が意味的に取り得る状態を明示し、それらを Preview で網羅すること。

2.  **ドメイン・コンテンツ (Domain Content)**
    *   Repository 等から取得した業務上の事実（利用者のリスト、記録データ等）。
    *   **役割**: ユーザーが見たい「事実」を提供する。

3.  **UI 詳細状態 (UI Content Details)**
    *   ユーザーの操作状態（検索クエリ、選択中の ID、表示モード、展開/折りたたみ、メニュー開閉等）。
    *   **役割**: ユーザーが「どのように操作・表示しているか」を保持する。

### 2.2. 設計の基本原則

- **[原則] 構造と詳細の直交**: データの再読み込み（Structural Loading）が発生しても、UI Content Details（検索クエリや入力値）を不用意に破棄してはならない。
- **[原則] Empty と Error の厳格な区別**: Empty は「正常に処理された結果、データが 0 件」という正常系である。Error（異常系）と混同せず、ユーザーへの次アクションを適切に提示する。
- **[原則] エラーの二分類**:
    - **Structural Error**: ロード失敗など、画面全体の表示構造をエラー表示へ切り替えるべき致命的なエラー。
    - **Transient Error**: 保存失敗など、現在の画面状態を維持したまま通知（Dialog/Snackbar）を行う一時的なエラー。
- **[原則] 1画面1UiState との整合性**: この3層構造は概念上の分類であり、StateFlow を物理的に分割することを推奨するものではない。単一の `UiState` クラス内でこれらの責務を意識してプロパティを構成する。

### 2.3. Preview による検証
Preview は単なる外観確認ではなく、設計した各状態（L/E/E/C および主要な UI Details）が正しく描画されるかを検証する手段として位置づける。

### 2.4. セマンティクスからストレージへの決定フロー

CareMemo では、「何であるか（セマンティクス）」を定義した上で、「復元が必要か（寿命）」という要件を評価し、最終的な「保存先（ストレージ）」を決定します。概念を物理的な場所に 1 対 1 で固定せず、ユーザー体験に基づいた柔軟な選択を行います。

**決定プロセス:**
1.  **[定義]**: その状態のセマンティクス（Structural / Domain / Details）を特定する。
2.  **[評価]**: プロセス死を跨いでその状態を失った際、ユーザーが感じる「不便さ（入力のやり直し等）」を評価する。
3.  **[選択]**: 評価に基づき、最適なストレージ（保存機構）を選択する。詳細は「9. プロセス死耐性と状態復元」を参照。

| 状態の意味・役割         | 主な判断基準         | ストレージ例             |
|------------------|----------------|--------------------|
| **Nav Identity** | 画面の起動・識別に必須か   | Navigation State   |
| **Structural**   | 再取得・再構築可能か     | UI State           |
| **Domain**       | 真実のソースから再取得可能か | UI State           |
| **UI Details**   | 失うと作業のやり直しになるか | Restorable State   |
| **UI Details**   | 局所的・一時的な表示状態か  | Transient UI State |

---

## 3. 状態初期化の安定性

- **[MUST] `LaunchedEffect` による初期化の禁止**: チラつき防止のため、初期値は ViewModel または `remember(key)` で設定します。
- **`remember` の鍵 (Key) の安定性**: 入力中に変動する値（時刻等）を鍵にしないでください。
- **[MUST] 非同期プロパティの補完**: デフォルト記録者名等の非同期値は、`init` 内で `.value` 参照せず、Flow を `collect` して「未入力時のみ埋める」ロジックで反映します。

## 4. 確実な変更検知 (Snapshot Comparison)

ユーザーの意図しないデータ破棄を防ぐため、以下のパターンを標準とします。

- **`baseline` の保持**: 編集開始時点の比較基準となる原始データを `baseline` として保持します。
- **[MUST] `isChanged` の算出**: ViewModel内で現在の入力値と `baseline` を比較し、`UiState.isChanged` を更新します。
- **[MUST] Process Death対策**: Process Death後も編集開始時点の `baseline` を維持する必要がある場合、SavedStateHandleへ必要なフィールドを退避します。
- **[禁止] UI層での検知**: `derivedStateOf` によるUI層での変更検知は禁止です。
- **一貫性の検証**: 値をbaselineと同じ状態に戻した際、`isChanged == false` になることをユニットテストで検証してください。

## 5. 非同期処理の実行と排他制御

### 5.1. 基盤機能 (safeLaunch / safeCollect)
- **原則**: すべての非同期処理は `BaseUiStateViewModel` の基盤機能を使用してください。`viewModelScope.launch` の直接使用は原則禁止です。
- **[MUST] 排他制御の徹底**: `safeLaunch` の戻り値である `Job` は、必ず対応する Job 変数に代入してください。

### 5.2. ガード型 (Guard Pattern)
- **対象**: 保存、削除、同期、インポート等。
- **実装**: メソッド先頭で `if (saveJob?.isActive == true) return`。
- **目的**: ボタン連打による二重保存の防止。

### 5.3. 上書き型 (Override Pattern)
- **対象**: 検索、詳細ロード等。
- **実装**: メソッド先頭で `loadJob?.cancel()`。
- **目的**: 常に最新の要求結果のみを UI に反映させ、競合を防ぐ。

## 6. 標準実装テンプレート

### 6.1. 変更検知と更新ヘルパー
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

### 6.2. 非同期検索フロー (上書き型)
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

### 6.3. 保存・破壊的操作 (ガード型)
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

## 7. データモデルの境界とマッピング (DTO & Entity)

- **外部形式 (DTO)**: バックアップファイル（JSON）などの外部保存形式。
- **内部形式 (Entity)**: Room データベース等の内部永続化形式。
- **[MUST] マッピングの実行場所**: 
    - 外部形式から内部形式への変換、および変換時のバリデーションやフィルタリングは、**ViewModel または Logic レイヤー**で制御してください。
    - Repository は「Entity そのもの」を受け取るか、変換・加工が済んだ DTO を受け取って保存に専念する責務を負います。

## 8. 揮発性セッション状態 (Volatile Session State)

特定のライフサイクルイベントや、外部アプリ連携時のみ必要となる一時的な状態（フラグ）については、以下の指針で管理します。

- **`StateFlow` 非採用の基準**:
    - ストリームとして継続的に監視し続ける必要がないもの。
    - 特定のイベント（`onResume` 等）で命令的に一度だけ参照・消費されるもの。
    - UI コンポーネントがその値の変化に直接反応して表示を更新する必要がないもの。
- **実装パターン**:
    - 専用の Session クラス（例: `SecuritySession`）を作成し、`@Volatile var` を使用してスレッド間の可視性を確保します。
    - `CareMemoApplication` でシングルトンとして保持し、ViewModel や Activity へ注入します。
- **目的**: 不必要な Observable パターンのオーバーヘッドを避け、コードの意図（ワンショットの制御であること）を明確にします。

## 9. プロセス死耐性と状態復元 (State Restoration)

アプリケーションがシステムによってメモリ解放（Process Death）された後、ユーザーが期待する状態を復元するための指針です。

### 9.1. 状態の 4 分類と保存機構
画面上の全状態を以下の 4 つに分類し、セマンティクスに応じた復元要件に基づき最適な保存機構を選択します（詳細は 2.4 項を参照）。

| 分類                     | SSOT          | 保存機構               | 内容                       |
|:-----------------------|:--------------|:-------------------|:-------------------------|
| **UI State**           | ViewModel     | `StateFlow`        | 通常動作時の真実のソース。            |
| **Restorable State**   | ViewModel     | `SavedStateHandle` | **「復元用のバックアップ」**。未保存入力等。 |
| **Nav State**          | NavController | `Type-safe Nav`    | 画面のアイデンティティ（ID等）。        |
| **Transient UI State** | Composable    | `rememberSaveable` | スクロール位置等の純粋な UI 状態。      |

### 9.2. CareMemo 状態管理 8 原則
1. **SSOT の確立**: `ViewModel.uiState` (StateFlow) を真実のソースとする。
2. **バックアップ限定**: `SavedStateHandle` は Restorable State のバックアップのみに使用する。
3. **機械的同期の禁止**: `updateUiState()` と `SavedStateHandle` を連動させない。
4. **明示的バックアップ**: ビジネスロジック上の状態変更箇所で明示的に SSH を更新する。
5. **論理的一括復元**: 復元時は SSH から原始データを一括で読み込み、Logic 層を通して `UiState` を再構築する。
6. **初期化上書きの防止**: 復元データが存在する場合、通常の初期化処理（DBロード等）での上書きを確実に防ぐ。`isRestoring` フラグを用いて、ロード完了後の `uiState` 構築時に復元値を優先するガードを実装してください。
7. **未保存入力の優先**: ユーザーが入力した情報を失わないことを、復元における最優先事項とする。
8. **揮発性 UI 状態の ViewModel 昇格**: ダイアログの入力途中、表示モードの切り替え、写真キャプション等の「作業中の一時状態」は Composable の `rememberSaveable` ではなく ViewModel の `uiState` で管理し、プロセス死に備えてバックアップしてください。

### 9.3. 編集基準 (Baseline) の保護
プロセス死の間に DB の値が変化しても `isChanged` を正確に判定できるよう、編集開始時点の baseline フィールド（または `initialSnapshot`）を個別に `SavedStateHandle` へ退避してください。ID による DB 再取得は baseline の維持には不十分です。

---
最終更新日: 2026/09/06
