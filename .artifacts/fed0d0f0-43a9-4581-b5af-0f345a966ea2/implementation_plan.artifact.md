# ロック仕様の単純化 (即時一本化とタイマー廃止)

## 目的
`doc/project_TODO_Phase_4.md` のタスク **C-2** に基づき、アプリのロック仕様を単純化します。
現在の「一定時間経過後にロック」というタイマー設定を廃止し、「バックグラウンド遷移時に即時ロック」という挙動に一本化することで、セキュリティの強化とコードの簡素化を図ります。

## ユーザーレビューが必要な事項
- **設定項目の削除**: 設定画面から「再ロックまでの時間」の項目が削除されます。
- **挙動の変更**: 今後は、ロックが有効な場合、アプリを離れて戻ってくるたびに必ず認証が必要になります（以前の「即時」設定と同じ挙動）。
- **「ロックしない」の扱い**: 以前はタイマー設定の中に「ロックしない」がありましたが、今後は「アプリのロック」スイッチの ON/OFF のみがロックの有無を制御します。

## Proposed Changes

### 1. [MainActivity](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/MainActivity.kt)
- `lockTimeoutMinutes` および `lastPausedTime` に関連する変数を削除。
- `LifecycleEventObserver` のロジックを簡素化し、`ON_RESUME` 時にロックが有効かつバイパス中でなければ、経過時間に関わらず `isAuthenticated = false` とするように変更。

### 2. [UserSettingsRepository](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/UserSettingsRepository.kt)
- `LOCK_TIMEOUT_MINUTES` キーと、それに関連する Flow および Setter を削除。

### 3. [SettingsViewModel](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/SettingsViewModel.kt)
- `UiState` から `lockTimeoutMinutes` を削除。
- 初期化および更新ロジックからタイムアウト設定に関する記述を削除。

### 4. [SettingsScreen](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/ui/screens/settings/SettingsScreen.kt)
- 「再ロックまでの時間」の UI 項目および、タイムアウト選択ダイアログ (`showTimeoutDialog`) を削除。

### 5. [SettingsSpecifications](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/spec/SettingsSpecifications.kt)
- `LOCK_TIMEOUT_OPTIONS` 定数を削除。

### 6. [strings.xml](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/res/values/strings.xml)
- 使用されなくなったロックタイムアウト関連の文字列リソースを削除。

---

## Verification Plan

### Automated Tests
- `UserSettingsRepositoryTest` からタイムアウト設定に関するテストを削除または修正。
- `SettingsViewModelTest` を修正。

### Manual Verification
1. 設定画面を開き、「再ロックまでの時間」の項目が消えていることを確認。
2. 「アプリのロック」を ON にする。
3. アプリをバックグラウンドへ移動（ホーム画面に戻る、または他アプリへ切り替え）。
4. アプリに復帰した際、即座にロック画面（または生体認証プロンプト）が表示されることを確認。
5. 「アプリのロック」を OFF にし、同様の操作でロックされないことを確認。
