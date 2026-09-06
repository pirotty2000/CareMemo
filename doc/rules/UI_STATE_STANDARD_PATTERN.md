# UI 状態設計標準パターン (Reference Implementation: PersonList)

本ドキュメントは、CareMemo における「UI 状態設計の 3 層構造」を具現化した標準仕様です。
基盤（Base）と各画面（ViewModel）の責務を明確に分離し、一貫したユーザー体験を提供することを目的とします。

---

## 1. 責務境界の原則

### 1.1. BaseUiStateViewModel (共通基盤)
非同期処理の「仕組み」「タイミング」「共通分類」を担当します。
*   **管理**: 処理の開始・終了、排他制御、例外ハンドリング。
*   **中継**: `LoadingCategory` を `safeLaunch / safeCollect` から `copyWithLoadingState` へ運搬します。
*   **非依存**: 特定画面の具体的な Operation（`Deleting(id)` 等）は一切知らず、抽象的なカテゴリのみを扱います。

### 1.2. ViewModel (各画面固有)
非同期処理の「具体的意味」と「UI 状態への翻訳」を担当します。
*   **意味付け**: 操作開始時に具体的な Operation 状態（`Deleting(id)` 等）をセットします。
*   **翻訳**: `copyWithLoadingState` を実装し、基盤から受け取ったカテゴリと Loading 状態を、自身の UI State（`screenState` や `operation`）へ反転・反映させます。

---

## 2. LoadingCategory の定義 (Base 層)

非同期処理の性質を以下の 4 つに分類します。これらは Base 層における「非同期処理の種類」を表す概念です。

| カテゴリ             | 定義・用途                                        |
|:-----------------|:---------------------------------------------|
| **`Structural`** | **[構造的]** 画面構築に必須のデータが未利用で、画面を構築できない状態。      |
| **`Operation`**  | **[操作的]** 保存、削除、追加など、ユーザー操作に伴う一時的な処理。        |
| **`Refresh`**    | **[更新的]** すでに Content はあるが、最新状態との同期を行うための取得。 |
| **`Default`**    | **[暫定]** 既存画面との互換性のための移行期間限定カテゴリ。新規開発では非推奨。  |

---

## 3. 状態の遷移ルール

### 3.1. Structural Loading
画面構築に必須の Domain Content がまだ利用可能ではなく、画面構造として Loading UI を表示する状態です。
*   **遷移**: `初回ロード` → `Structural Loading` → `Active`
*   **指針**: すでに利用可能な Content を保持している再取得を Structural Loading として扱ってはなりません。

### 3.2. Operation Loading
ユーザー操作によって発生する一時的な処理です。具体的な意味（誰を削除しているか等）は ViewModel 側の `Operation State` で管理します。
*   **遷移**: `Active` → `Operation Loading` → `Active`

### 3.3. Refresh
既存 Content を維持したまま、最新状態との同期を行います。
*   **遷移**: `Active` → `Refresh` → `Active`
*   **注意**: Base 層の `LoadingCategory.Refresh` は、ViewModel 層の `Operation.Refreshing` 等の具体的な UI 状態へ翻訳されます。

---

## 4. 最重要ルール：直交性の維持

> **Operation / Refresh のロード中に、既存の Domain Content を消去（null や空リストへ置換）してはならない。**

「ロード中 ＝ 画面を白紙にする」という安易な実装を禁止し、ユーザーが直前まで見ていたデータを常に維持したまま、処理状態を「重ねる」設計を徹底します。

---

## 5. Error 設計の定義

### 5.1. Structural Error
画面構築に必要な Content を取得できず、利用可能な Content も存在しない致命的な状態。
*   **遷移**: `Structural Loading` → `Structural Error`
*   **表現**: 全画面エラー UI を表示し、リトライボタン等の回復手段を提供します。

### 5.2. Transient Error
既存 Content を維持したまま、Operation / Refresh 等が失敗した一時的な状態。
*   **遷移**: `Operation / Refresh` 中の失敗 → `Active` 維持 ＋ `Transient Error`
*   **表現**: 画面構造は変えず、`UiEvent` (Dialog/Snackbar) でユーザーに通知します。

---

## 6. 実装モデル (PersonList の例)

### 6.1. 状態の構造 (PersonListUiState)
```text
PersonListUiState
│
├─ Structural State (screenState)
│    ├─ Loading / Active / Error(throwable)
│    └─ ※ 画面全体の表示権限を司る（Content 表示の有無を決定）。
│
├─ Domain Content (事実)
│    ├─ userList / records / person
│    └─ ※ DBから取得した純粋なデータ。Structural State が Active の時に表示される。
│
├─ UI Content Details (操作・表示)
│    ├─ Session パターン: `session: PersonEditSession`
│    ├─ Input パターン: `input: PersonEditInput`
│    └─ ※ 編集中の未保存データ、検索クエリ、タブ選択、ダイアログ開閉状態など。
│
└─ Operation State (operation)
     ├─ Idle / Saving / Deleting / Exporting ...
     └─ ※ 副作用の進行状況。ボタンの二重押し防止やインジケータ表示に使用。
```

### 6.2. 翻訳処理の概念モデル (copyWithLoadingState)
基盤から通知される抽象的な「カテゴリ + 状態」を、各 ViewModel で具体的な UI 状態へ翻訳します。

```kotlin
// 翻訳プロセスの概念イメージ
when (category) {
    Structural -> // screenState（Loading / Active / Error）の反転を担当
    Operation -> // operation の終了（Idle への復帰）などを担当
    Refresh -> // operation (Refreshing 等) の開始・終了を担当
    Default -> // 移行期間中の既存画面用 Boolean ロジックを担当
}
```

## 7. 画面別実装パターン一覧

プロジェクト内の主要画面における、3 層構造の適用例です。

| 画面カテゴリ | 代表画面 | Structural (screenState) | Operation (operation) | Domain / UI Details の特徴 |
|:---|:---|:---|:---|:---|
| **一覧表示型** | `PersonList` | `Loading / Active / Error` | `Adding`, `Deleting(id)` | `userList` を外部に持ち、`searchQuery` でフィルタ。 |
| **単一入力型** | `PersonEdit` | `Loading / Active / Error` | `Saving` | `input: PersonEditInput` に未保存状態を隔離。 |
| **セッション型** | `BatchInput` | `Loading / Active / Error` | `Saving` | `session: BatchInputSession` に多項目状態を集約。 |
| **表示切替型** | `PersonHealth` | `Loading / Active / Error` | `Saving` | `records` (Domain) と `displayMode` (UI) の組合せ。 |
| **アクション型** | `Settings` | `Loading / Active / Error` | `Exporting`, `Resetting` | 多数の独立した副作用を `operation` で排他制御。 |
| **一括選択型** | `DeleteOrRestore` | `Loading / Active / Error` | `Restoring`, `Deleting` | `selectedIds` (UI) と `archivedPersons` (Domain)。 |

---

## 8. 実装のベストプラクティス

### 8.1. 初期ロードの定石 (safeCollect)
画面起動時のデータ取得は、`CollectMode.INITIAL` と `LoadingCategory.Structural` を組み合わせて行います。

```kotlin
safeCollect(
    operation = "loadData",
    mode = CollectMode.INITIAL,
    loadingCategory = LoadingCategory.Structural,
    flowProvider = { repository.getDataFlow().catch { e ->
        updateUiState { it.copy(screenState = ScreenState.Error(e)) }
        throw e // 基盤側のエラーハンドラにも通知
    }}
) { data ->
    updateUiState { it.copy(domainData = data) }
}
```

### 8.2. 副作用のガード (safeLaunch)
保存や削除などの操作は、`LoadingCategory.Operation` を使用し、ViewModel 側で `operation` 状態をセットしてから起動します。

```kotlin
fun save() {
    if (actionJob?.isActive == true) return // 二重実行防止
    updateUiState { it.copy(operation = Operation.Saving) }
    
    actionJob = safeLaunch(
        operation = "save",
        loadingCategory = LoadingCategory.Operation
    ) {
        repository.save(...)
        sendViewEvent(Success)
    }
}
```

### 8.3. 旧 loading プロパティの廃止
過去のコードに見られる `val isLoading: Boolean` は非推奨です。
新設計では `copyWithLoadingState` 内で、受け取った `category` に応じて `screenState` または `operation` のいずれかを適切に更新してください。

---
最終更新日: 2026/09/06 (全画面移行完了に伴う精査済み)
