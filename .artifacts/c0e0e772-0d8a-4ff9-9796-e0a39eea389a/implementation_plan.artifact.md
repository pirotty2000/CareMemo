# B-1: HealthCategoryProcessor の整理 (Logic 層からの副作用排除)

`HealthCategoryProcessor` が `HealthRepository` に直接依存している現状を改善し、アーキテクチャ境界（Dependency Matrix: Logic -> Repository 違反）を適正化します。

## ユーザーレビュー要求事項
> [!IMPORTANT]
> 本修正により、Logic レイヤーの実装クラス（`GlucoseProcessor` 等）から Repository への参照が完全になくなります。これにより、これらのクラスのユニットテストにおいて MockRepository が不要になり、テストの実行速度と安定性が向上します。

## 提案される変更点

### [Data Layer] HealthRepository の汎用化

#### [MODIFY] [HealthRepository.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/data/repository/HealthRepository.kt)
ViewModel からカテゴリ指定で検索できるよう、汎用的な検索メソッドを追加します。
- `findHistoryRecordAtTime(category: Category, personId: String, time: Instant): HistoryRecord?` を追加。

---

### [Logic Layer] Processor インターフェースの純粋化

#### [MODIFY] [HealthCategoryProcessor.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/logic/feature/HealthCategoryProcessor.kt)
副作用を伴うメソッドを削除し、純粋な変換・判定ロジックのみの定義にします。
- `findExisting`, `save`, `delete` メソッドを削除。

#### [MODIFY] 各プロセッサ実装クラス
インターフェースの変更に伴い、リポジトリ操作のオーバーライドを削除します。
- [GlucoseProcessor.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/logic/feature/GlucoseProcessor.kt)
- [HeightWeightProcessor.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/logic/feature/HeightWeightProcessor.kt)
- [VitalProcessor.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/logic/feature/VitalProcessor.kt)

---

### [ViewModel Layer] 保存・削除・検索の直接実行

#### [MODIFY] [PersonHealthViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/PersonHealthViewModel.kt)
プロセッサに委譲していた DB 操作を、`healthRepository` を直接呼び出す形に書き換えます。
- `saveRecord` 内: `processor.save(...)` -> `healthRepository.insertHistoryRecord(...)`
- `deleteRecord` 内: `processor.delete(...)` -> `healthRepository.deleteHistoryRecord(...)`
- 重複チェック: `processor.findExisting(...)` -> `healthRepository.findHistoryRecordAtTime(...)`

#### [MODIFY] [BatchInputViewModel.kt](file:///D:/Users/pirotty.galaxy/Documents/MyGitHub/CareMemo/app/src/main/java/jp/mydns/fujiwara/carememo/viewmodel/BatchInputViewModel.kt)
一括保存時の重複チェックロジックを修正します。
- `saveBatch` 内: `it.findExisting(healthRepository, ...)` -> `healthRepository.findHistoryRecordAtTime(it.generalCategory, ...)`

## 検証計画

### 自動テスト
- `gradlew :app:assembleDebug` でビルドが通ることを確認。
- `PersonHealthViewModelTest`, `BatchInputViewModelTest` (存在する場合) を実行し、保存・削除・重複チェックの挙動にデグレがないか確認。

### 手動確認
1. 健康記録（個別）の保存・更新・削除が正常に動作することを確認。
2. 健康記録（一括）の保存が正常に動作することを確認。
3. 同一時刻の重複チェックが正しく機能し、エラーメッセージが表示されることを確認。
