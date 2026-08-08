# Modifier ルールの厳格化 (Phase 4)

Jetpack Compose のベストプラクティスに基づき、コンポーネントの再利用性と予測可能性を高めるため、Modifier の扱いを厳格化します。

## ユーザーレビューが必要な項目
- 特になし。既存の UI 表示が変わらないようにリファクタリングを行います。

## 提案される変更点

### 共通基盤ルール
1. すべての Composable 関数に `modifier: Modifier = Modifier` 引数を追加する。
2. `modifier` は、必須引数の直後（最初のオプション引数）に配置する。
3. 外部から渡された `modifier` は、そのコンポーネントの**ルート要素**にのみ適用する。
4. 外部 `modifier` に対して、コンポーネント内部で `padding()` や `fillMaxSize()` などのレイアウトを決定付ける変更を加えない（`testTag` は許容）。内部で必要な装飾は、内部要素またはラップする Box 等で行う。

---

### [Component] [common/HistoryComponents.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/HistoryComponents.kt)

#### [MODIFY] [HistoryComponents.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/HistoryComponents.kt)
- `PersonHistoryList`: `modifier` 引数を追加し、`LazyColumn` に適用する。`Modifier.fillMaxSize()` のハードコードを排除し、外部から制御可能にする（デフォルト値として渡すか、内部で Box で包む）。
- `HistoryItemWrapper`: `modifier` の引数順序を修正（最初のオプション引数にする）。

### [Component] [health/PersonHealthComponents.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/health/PersonHealthComponents.kt)

#### [MODIFY] [PersonHealthComponents.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/health/PersonHealthComponents.kt)
- `HealthHistoryItemBody` 〜 `GlucoseRecordItemContent`: `modifier` 引数を追加し、ルートの `Row`/`Column` に適用する。
- `HealthRecordDetailPane`: `modifier` 引数を追加。内部の `LoadingScreen` および `Box` (編集モード) / `HealthRecordDisplayCard` (閲覧モード) に適切に伝播させる。
- `HealthRecordEditForm`: `modifier` 引数を追加。
- `HealthRecordDisplayCard`: `modifier` 引数を追加。
- `DetailRow`: `modifier` 引数を追加。

### [Component] [common/CategorySelectorBar.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/CategorySelectorBar.kt)

#### [MODIFY] [CategorySelectorBar.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/CategorySelectorBar.kt)
- `LazyRow` に直接適用されていた `fillMaxWidth().background(...).padding(...)` を整理し、外部 `modifier` の純粋性を保つ。

---

## 検証計画

### 自動テスト
- 既存の UI テスト（あれば）を実行し、表示崩れがないことを確認する。
- `gradlew test` (Logic)
- `gradlew connectedAndroidTest` (UI - 必要に応じて)

### 手動確認
- Compose Preview を使用して、各コンポーネントの表示が以前と変わっていないことを確認する。
- 実機/エミュレータにて、詳細画面の履歴リストや編集パネルのレイアウトが崩れていないことを確認する。
