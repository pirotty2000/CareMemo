# プロジェクト TODO (2026/07/30)

## 完了済み事項
- **Product Flavor の実装**
  - `stable` と `dev` の分離。
  - Application ID、アプリ名、アイコン、`versionNameSuffix`、`BuildConfig.DEV_MODE` の設定完了。
  - `AndroidManifest.xml` の `FileProvider` Authority を汎用化 (`${applicationId}.fileprovider`)。

## 現在の課題と方針
### 1. バージョン不一致によるインポート制限の緩和
- **現状**: `dev` 版（高 `versionCode`）でエクスポートしたデータを `stable` 版（低 `versionCode`）にインポートしようとすると、`SettingsLogic.validateVersion` によってブロックされる。
- **背景**: `dev` 版は F-Droid での頻繁な配信・実機テストのために `versionCode` が先行して上がっていく運用であるため。
- **対応方針**: 開発者自身がリスクを承知でデータを移行できるよう、制限を緩和する。
- **実装案**:
  - `SettingsLogic.validateVersion` に `isDeveloperMode: Boolean` 引数を追加。
  - 開発者モードがオンの場合、`backupVersionCode > currentVersionCode` であっても `ImportValidationResult.SUCCESS` を返すように変更。
  - これにより、開発者は Stable 版で事前にバックアップを取った上で、安心して Dev 版からのデータ取り込み・検証を行えるようにする。

### 2. コードインスペクションへの対応 (継続)
- `feature/setup-product-flavors` ブランチにおいて、未使用シンボルの削除やスタイルの最適化を、安全を確認しながら順次実施する。
- `SampleDataGenerator.kt` の Kotlin 化（`roundToLong()` への置換等）の完遂。

---
## 運用ルール
- **ブランチ戦略**: 
  - `main` からブランチを切って開発 ＞ `devRelease` ＞ F-Droid (dev) で配信・テスト。
  - 十分に検証された段階で `main` にマージ ＞ `stableRelease` ＞ F-Droid (stable) で本番公開。
