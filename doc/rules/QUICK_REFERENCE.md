# 開発クイックリファレンス (MUSTルール)

本プロジェクトにおいて、不具合防止と品質維持のために **必ず遵守すべき** 核心ルールをまとめています。

## 1. 状態管理 (State Management)

- **[MUST] `LaunchedEffect` で状態を初期化しない**
    - 理由: 非同期実行によるブランキング（チラつき）の原因となります。`remember` または ViewModel の初期値を使用してください。
- **[MUST] `MutableStateFlow` を `combine()` して `UiState` を構築しない**
    - 理由: 状態更新の原子性が失われ、不整合や無限ループの原因となります。
- **[MUST] `isChanged` を `derivedStateOf` で判定しない**
    - 理由: `isChanged` は業務判断を伴う「状態」であり、ViewModel で計算してユニットテストで保護する必要があります。
- **[MUST] 非同期取得プロパティを `init` で `.value` 参照しない**
    - 理由: ロード未完了による空振りを防ぐため。Flow の `collect` によるリアクティブな補完を必須とします。

## 2. 非同期処理 (Asynchronous Processing)

- **[MUST] `viewModelScope.launch()` を直接使用しない**
    - 理由: 例外ハンドリングと監査ログ記録を自動化するため、必ず **`safeLaunch()`** を使用してください。
- **[MUST] `saveJob` 等を必ず保持し、`safeLaunch` の戻り値を代入する**
    - 理由: 排他制御を有効にするため、「野良 Job」の作成は禁止です。
- **[MUST] 保存処理は「ガード型 (Guard Pattern)」を使用する**
    - `if (saveJob?.isActive == true) return` で二重実行を防止します。
- **[MUST] 検索処理は「上書き型 (Override Pattern)」を使用する**
    - `loadJob?.cancel()` で最新の要求のみを優先します。

## 3. ナビゲーション (Navigation)

- **[MUST] `navController.navigate()` を Composable から直接呼び出さない**
    - 理由: 画面遷移は「判断（ViewModel）」と「実行（Screen）」に分離します。
- **[MUST] 画面遷移は `ViewEvent` 経由で実行する**
    - ViewModel から `sendViewEvent` を発行し、`Screen.kt` の `LaunchedEffect` で購読します。
- **[MUST] `SavedStateHandle` から自律的に引数を取得する**
    - Composable を介さず、ViewModel が自ら引数（`personId` 等）をロードします。

## 4. UI とコーディング (UI & Coding)

- **[MUST] `UiState` のリストには `ImmutableList` を使用する**
    - 理由: Compose の再コンポーズ最適化（安定性の保証）に不可欠です。
- **[MUST] `Content` のルート要素に外部 `modifier` を適用する**
    - 理由: `Screen` 層でのレイアウト制御を反映させるため、必ず渡された `modifier` を起点に chain させてください。
- **[MUST] `Content` コンポーネントに「外側 padding」を付与しない**
    - 理由: 上位層との「二重余白」を防止し、レイアウト責務を分離するため。
- **[MUST] `collectAsStateWithLifecycle()` を使用する**
    - 理由: Android のライフサイクルに応じた安全な Flow 購読のため。

## 5. 設計構造 (Architecture)

- **[MUST] Logic レイヤーに Android API を持ち込まない**
    - 理由: `Context`, `R.string`, `Uri` 等への依存を排除し、JUnit による高速な単体テストを維持するため。

---
最終更新日: 2026/08/14
