# Modifier ルール厳格化 実装状況調査レポート

`doc/project_TODO_Phase_2.md` に定義された「Modifier ルールの厳格化」に基づき、各画面およびコンポーネントの実装状況を調査しました。

## 判定基準
1.  Composable 関数が `modifier: Modifier = Modifier` 引数を持っているか。
2.  `modifier` が（必須引数の後の）最初のオプション引数であるか。
3.  外部から渡された `modifier` がルート要素にのみ適用されているか。
4.  外部 `modifier` に対して内部で装飾（padding等）を加えていないか。

---

## 1. UI Components (ui/components)

### 【汎用基盤】 base
| ファイル名 | 状態 | 備考 |
|:---|:---:|:---|
| `AppCompactTextField.kt` | [x] | |
| `AppDeleteConfirmDialog.kt` | [ ] | `modifier` 引数なし |
| `AppDialog.kt` | [x] | `AppDialog`, `AppDialogConfirmButton` 等すべて対応済 |
| `AppInfoDialog.kt` | [x] | |
| `AppTextField.kt` | [x] | |
| `AppTopAppBarColors.kt` | [-] | UI部品ではない（配色定義） |
| `EmptyState.kt` | [x] | ルートの Box に適用済 |
| `LoadingScreen.kt` | [x] | ルートの Box に適用済 |
| `SearchBox.kt` | [x] | |
| `VerticalScrollIndicator.kt` | [ ] | `modifier` 引数なし |

### 【ドメイン共通】 common
| ファイル名 | 状態 | 備考 |
|:---|:---:|:---|
| `CategorySelectorBar.kt` | [x] | ルートの Surface に適用済 |
| `DateTimeInputFields.kt` | [x] | |
| `HistoryComponents.kt` | [x] | |
| `PdfExportActionHandler.kt` | [ ] | 論理コンポーネントだが、副作用を持つため `modifier` 検討の余地あり |
| `PdfSettingsDialog.kt` | [ ] | `modifier` 引数なし |
| `PersonHeaderTitle.kt` | [x] | |

### 【各機能固有】
| ファイル名 | 状態 | 備考 |
|:---|:---:|:---|
| **main/** | | |
| 　`BirthdayInputFields.kt` | [x] | |
| 　`CategoryBadges.kt` | [/] | `CategoryBadges` は [x], `BadgeChar` は [ ] |
| 　`KanaIndexBar.kt` | [x] | |
| 　`MainComponents.kt` | [x] | `UserListItem`, `CategorySelectionSheet` ともに対応済 |
| 　`QuickActionMenu.kt` | [ ] | `modifier` 引数なし |
| **health/** | | |
| 　`HealthGraphView.kt` | [ ] | `modifier` 引数なし |
| 　`LineChart.kt` | [x] | |
| 　`PersonHealthComponents.kt` | [x] | すべての内部コンポーネントで対応済 |
| **condition/** | | |
| 　`PersonConditionComponents.kt` | [x] | すべての内部コンポーネントで対応済 |
| **medication/** | | |
| 　`PersonMedicationComponents.kt` | [x] | すべての内部コンポーネントで対応済 |

---

## 2. Screens (ui/screens)

原則として、Screen エントリポイントは `modifier` を持たないことが多いですが、`Content` および `Phone/Tablet` 用のレイアウト、および内部の private な Composable は `modifier` を持つべきです。

### SCR-M-001〜004 (Main / Emergency Contact)
| 画面ID | ファイル名 | 状態 | 備考 |
|:---|:---|:---:|:---|
| SCR-M-001 | `MainScreen.kt` | [ ] | `EmergencyContactSelectionSheet` 等に `modifier` なし |
| | `MainScreenContent.kt` | [ ] | `modifier` 引数なし |
| SCR-M-002 | `PersonEditScreen.kt` | [ ] | `PersonEditScreenContent`, `BirthdayInputSection` に `modifier` なし |
| SCR-M-003 | `EmergencyContactListScreen.kt` | [ ] | `EmergencyContactListContent`, `EmergencyContactItem` に `modifier` なし |
| SCR-M-004 | `EmergencyContactEditScreen.kt` | [ ] | `EmergencyContactEditContent` に `modifier` なし |

### SCR-PH-001〜003 (Health)
| 画面ID | ファイル名 | 状態 | 備考 |
|:---|:---|:---:|:---|
| SCR-PH-001 | `PersonHealthScreen.kt` | [-] | エントリポイント |
| | `PersonHealthScreenPhone.kt` | [ ] | `modifier` 引数なし |
| | `PersonHealthScreenTablet.kt` | [ ] | `modifier` 引数なし |
| | `PersonHealthScreenContent.kt` | [ ] | `modifier` 引数なし |
| SCR-PH-002 | `BatchInputScreen.kt` | [ ] | `modifier` 引数なし |
| SCR-PH-003 | `GraphExpansionScreen.kt` | [/] | `SingleGraphInLandscape` は [x], 他は [ ] |

### SCR-PC-001〜003 (Condition)
| 画面ID | ファイル名 | 状態 | 備考 |
|:---|:---|:---:|:---|
| SCR-PC-001 | `PersonConditionScreen.kt` | [-] | エントリポイント |
| | `PersonConditionScreenPhone.kt` | [ ] | `modifier` 引数なし |
| | `PersonConditionScreenTablet.kt` | [ ] | `modifier` 引数なし |
| | `PersonConditionScreenContent.kt` | [ ] | `modifier` 引数なし |
| SCR-PC-002 | `ConditionPhotoPreviewScreen.kt` | [ ] | `modifier` 引数なし |
| SCR-PC-003 | `ConditionPhotoFullScreen.kt` | [ ] | `modifier` 引数なし |

### SCR-PM-001 (Medication)
| 画面ID | ファイル名 | 状態 | 備考 |
|:---|:---|:---:|:---|
| SCR-PM-001 | `PersonMedicationScreen.kt` | [-] | エントリポイント |
| | `PersonMedicationScreenPhone.kt` | [ ] | `modifier` 引数なし |
| | `PersonMedicationScreenTablet.kt` | [ ] | `modifier` 引数なし |
| | `PersonMedicationScreenContent.kt` | [ ] | `modifier` 引数なし |

### SCR-S-001〜004 (Settings / Logs)
| 画面ID | ファイル名 | 状態 | 備考 |
|:---|:---|:---:|:---|
| SCR-S-001 | `SettingsScreen.kt` | [ ] | `SettingsScreenContent` 等に `modifier` なし |
| SCR-S-002 | `AuditLogScreen.kt` | [ ] | `AuditLogScreenContent` に `modifier` なし |
| SCR-S-003 | `DeleteOrRestorePerson.kt` | [ ] | `modifier` 引数なし |
| SCR-S-004 | `OrphanedPhotoManagementScreen.kt` | [ ] | `modifier` 引数なし |
| | `OrphanedPhotoManagementContent.kt` | [x] | 対応済 |

---

## 調査結果まとめ
- **Components層**: 前回の作業により主要な部品は対応済みですが、一部のダイアログ（`AppDeleteConfirmDialog`, `PdfSettingsDialog`）や、小型部品（`BadgeChar`）に未対応箇所が残っています。
- **Screens層**: `Content` ファイルや `Phone/Tablet` 用のレイアウトファイルの多くが `modifier` を受け取れる構造になっておらず、呼び出し側からの自由なレイアウト制御が制限されています。

次のステップとして、Screen 層の `Content` および `Phone/Tablet` レイアウトファイルへの `modifier` 適用を進めることを推奨します。
