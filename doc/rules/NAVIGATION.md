# 画面遷移 (NAV) ルール

## 1. Type-safe Navigation

- すべての目的地は `ui/navigation/Destinations.kt` に定義し、文字列ベースのルート指定を禁止します。
- 画面が必要とする引数は目的地（data class）のプロパティとして定義します。

## 2. 遷移トリガーの一元化 (ViewEvent)

- **責務の分離**:
    - **ViewModel**: `ViewEvent`（例：`NavigateToDetail`）を発行し、遷移の判断のみを行います。
    - **Screen**: `LaunchedEffect` 内でイベントを受け、`navController.navigate()` を実行します。
- **[MUST] 直接遷移の禁止**: Composable の `onClick` 等から直接 `navController` を操作しないでください。

## 3. 引数の受け取り (SavedStateHandle)

- 遷移引数は `SavedStateHandle` を介して ViewModel が直接取得します。
- `MainActivity` や Composable を経由して手動で引数を渡す実装は禁止します。

## 4. 標準実装テンプレート (遷移イベント購読)

```kotlin
/**
 * Screen (エントリポイント) でのイベント監視
 */
@Composable
fun FeatureScreen(viewModel: FeatureViewModel) {
    val context = LocalContext.current
    
    // [MUST] LaunchedEffect(Unit) 内で一過性イベントを購読
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is ViewEvent.NavigateToDetail -> {
                    navController.navigate(Destination.Detail(event.id))
                }
                ViewEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }
}
```

---
最終更新日: 2026/08/14
