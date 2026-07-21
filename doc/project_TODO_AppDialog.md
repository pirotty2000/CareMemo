# AppDialog 適用状況調査レポート (SCR-M-001, SCR-M-002, SCR-PH-*, SCR-PC-*, SCR-PM-*, SCR-S-*)

## 1. 調査目的
`project_UI_GUIDELINES.md` の「4. アクションの統一(AppDialog.kt)」に基づき、標準外の `AlertDialog` 使用の有無、および共通コンポーネント (`AppDialog`, `AppInfoDialog`, `AppDeleteConfirmDialog`) の使い分けが適切かを確認する。

## 2. 調査結果まとめ

| 画面 ID         | 箇所               | 使用コンポーネント       | ガイドライン遵守状況 | 備考                                                                                    |
|:--------------|:-----------------|:----------------|:-----------|:--------------------------------------------------------------------------------------|
| **SCR-M-001** | 通知・エラー (UiEvent) | `AppInfoDialog` | ○ 遵守       | ViewModel経由の通知に適切に使用。                                                                 |
| **SCR-M-001** | バージョン情報          | `AppDialog`     | △ 改善の余地あり  | `AppDialogConfirmButton` (塗りつぶし) を使用中。情報の通知には `AppDialogDismissButton` (文字のみ) が推奨される。 |
| **SCR-M-002** | 破棄確認             | `AppDialog`     | ○ 遵守       | 配置・色分け (`DELETE`) ともに適切。                                                              |
| **SCR-M-002** | 重複エラー            | `AppInfoDialog` | ○ 遵守       | ユーザーへの通知として適切に使用。                                                                     |
| **SCR-PH-***  | 通知・エラー          | `AppInfoDialog` | ○ 遵守       | 一括入力や詳細画面での通知に適切に使用。                                                                 |
| **SCR-PH-***  | 削除確認             | `AppDeleteConfirmDialog` | ○ 遵守       | 履歴削除時の最終確認に適切に使用。                                                              |
| **SCR-PH-***  | グラフヘルプ          | `AppDialog`     | △ 改善の余地あり  | `AppDialogConfirmButton` を使用中。情報の通知には `AppDialogDismissButton` が推奨される。 |
| **SCR-PC-***  | 写真プレビュー確認      | `AlertDialog`   | × 違反       | 標準外の `AlertDialog` を使用。 `AppDialog` 系への移行が必要。                               |
| **SCR-PM-***  | 服薬入力             | `AppDialog`     | ○ 遵守       | `MedicationInputDialog` にて適切に使用。                                                      |
| **SCR-PM-***  | 通知・エラー          | `AppInfoDialog` | ○ 遵守       | 画面内での通知に適切に使用。                                                                     |
| **SCR-S-***   | 各種設定・通知        | `AppDialog` 他   | ○ 遵守       | 設定選択、パスワード入力、削除確認など、各コンポーネントを正しく使い分け済み。                               |

## 3. 具体的な指摘事項と改善案

### SCR-M-001: バージョン情報ダイアログ (MainScreenContent.kt)
- **現状**: `AppDialog` の `confirmButton` スロットに `AppDialogConfirmButton`（塗りつぶしボタン）が配置されている。
- **問題点**: ガイドラインでは情報の周知には視覚적重みの低いボタン（`AppDialogDismissButton`）の使用を推奨している。塗りつぶしボタンは「データの保存」などのポジティブな実行アクションと誤認させる可能性がある。
- **改善案**: `AppDialogConfirmButton` を `AppDialogDismissButton` に差し替え、情報の通知としての視覚的バランスを整える。

### SCR-PH-*: グラフヘルプダイアログ (HealthGraphView.kt)
- **現状**: `AppDialog` の `confirmButton` スロットに `AppDialogConfirmButton`（塗りつぶしボタン）が配置されている。
- **問題点**: グラフの読み方等の「ヘルプ（情報の周知）」は実行アクションではないため、塗りつぶしボタンは視覚的に重すぎる。
- **改善案**: `AppDialogConfirmButton` を `AppDialogDismissButton` に差し替える。

### SCR-PC-*: 写真プレビュー画面 (ConditionPhotoPreviewScreen.kt)
- **現状**: 写真の「削除」と「変更破棄」の確認に標準の `AlertDialog` が使用されている。
- **問題点**: アプリ全体の統一デザインから外れており、ボタン配置や配色も共通ルール（`AppDialog`）に従っていない。
- **改善案**: 削除確認には `AppDeleteConfirmDialog` を、破棄確認には `AppDialog` を使用するように修正する。

## 4. 共通基盤の確認状況
- `AppDialog.kt`: 基盤として機能しており、ボタン配置ルールも実装済み。
- `AppInfoDialog.kt`: 正常に `AppDialogDismissButton` を利用する設計になっている。
- `AppDeleteConfirmDialog.kt`: `AppDialogActionType.DELETE` を使用し、警告色が適用される設計。

---
最終更新日: 2026/07/15
