# PreviewParameterProvider 導入によるプレビューの標準化

Jetpack Compose の `PreviewParameterProvider` を導入し、UI コンポーネントのプレビューコードを簡素化するとともに、「正常系」「空状態」「エラー/注意状態」などの多様な状態を網羅的にプレビューできるようにします。

## User Review Required

- **MockData の配置場所**: `ui/preview/MockData.kt` に共有のテストデータを集約します。
- **PreviewState の導入**: 引数が多い Composable に対しては、引数をまとめた `PreviewState` クラスを定義し、それを `PreviewParameterProvider` で提供します。

## Proposed Changes

### [Common Preview Infrastructure]

#### [NEW] [MockData.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/preview/MockData.kt)
共有の `Person`, `HistoryRecord`, `ConditionAtVisit` などのモックデータを定義します。

#### [NEW] [PreviewStates.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/preview/PreviewStates.kt)
プレビュー用の状態保持クラスを定義します。

---

### [Common Components]

#### [NEW] [HistoryPreviewParameterProvider.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/HistoryPreviewParameterProvider.kt)
`PersonHistoryList` 用のプロバイダー。

#### [MODIFY] [HistoryComponents.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/common/HistoryComponents.kt)
プレビューを追加します。

---

### [Health Screen]

#### [NEW] [PersonHealthPreviewParameterProvider.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/health/PersonHealthPreviewParameterProvider.kt)
`PersonHealthScreenContent` およびその Phone/Tablet 版用のプロバイダー。

#### [MODIFY] [PersonHealthScreenPhone.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/health/PersonHealthScreenPhone.kt)
#### [MODIFY] [PersonHealthScreenTablet.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/health/PersonHealthScreenTablet.kt)
`@PreviewParameter` を使用するように更新し、複数の状態を表示します。

---

### [Condition Screen]

#### [NEW] [PersonConditionPreviewParameterProvider.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionPreviewParameterProvider.kt)
`PersonConditionScreenContent` および関連コンポーネント用のプロバイダー。

#### [MODIFY] [PersonConditionComponents.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/components/condition/PersonConditionComponents.kt)
#### [MODIFY] [PersonConditionScreenContent.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/condition/PersonConditionScreenContent.kt)
プレビューを更新します。

## Verification Plan

### Automated Tests
- プロジェクトのビルドが通り、プレビューが Android Studio 上で正しくレンダリングされることを確認します。
- `render_compose_preview` ツールを使用して、主要なプレビューのレンダリング結果を確認します。

### Manual Verification
- Android Studio の Design タブで、複数のプレビュー状態（Normal, Empty, Loading 等）が正しく表示されることを確認します。
