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

非同期処理の性質を以下の 4 つに分類します。

| カテゴリ | 定義・用途 |
| :--- | :--- |
| **`Structural`** | **[構造的]** 画面構築に必須のデータが未利用で、画面を構築できない状態。 |
| **`Operation`** | **[操作的]** 保存、削除、追加など、ユーザー操作に伴う一時的な処理。 |
| **`Refresh`** | **[更新的]** すでに Content はあるが、最新状態との同期を行うための取得。 |
| **`Default`** | **[暫定]** 既存画面との互換性のための移行期間限定カテゴリ。新規開発では非推奨。 |

---

## 3. 状態の遷移ルール

### 3.1. Structural Loading
画面構築に必要な Domain Content がまだ利用可能ではなく、画面構造として Loading UI（シマー等）を表示する状態です。
*   **遷移**: `初回ロード` → `Structural Loading` → `Active`
*   **指針**: すでに利用可能な Content を保持している再取得を Structural Loading として扱ってはなりません。

### 3.2. Operation Loading
ユーザー操作によって発生する一時的な処理です。
*   **遷移**: `Active` → `Operation Loading` → `Active`
*   **指針**: 具体的な意味（何をしているか、誰を対象にしているか）は ViewModel 側で管理します。

### 3.3. Refresh
既存 Content を維持したまま、最新状態との同期を行います。
*   **遷移**: `Active` → `Refresh` → `Active`
*   **指針**: `Operation`（破壊的操作）とは明確に区別し、インジケータ（スワイプ更新等）で表現します。

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
│    ├─ Loading / Active / Error
│
├─ Domain Content (事実)
│    ├─ userList / emergencyContactsForSheet
│
├─ UI Content Details (操作・表示)
│    ├─ searchQuery / selectedSection / isNameMaskingEnabled
│
└─ Operation State (operation)
     ├─ Idle / Refreshing / Adding / Deleting(targetId) / Restoring(targetId)
```

### 6.2. 翻訳処理の責務 (copyWithLoadingState)
```kotlin
override fun copyWithLoadingState(state: S, isLoading: Boolean, category: LoadingCategory): S {
    return when (category) {
        is Structural -> state.copy(screenState = if (isLoading) Loading else Active)
        is Operation -> if (!isLoading) state.copy(operation = Idle) else state
        is Refresh -> state.copy(operation = if (isLoading) Refreshing else Idle)
        is Default -> state.copy(isLoading = isLoading) // 移行用
    }
}
```

---
最終更新日: 2026/09/06
