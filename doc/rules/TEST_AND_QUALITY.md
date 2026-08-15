# 品質保証とテスト

## 1. ユニットテスト (Unit Test)

- **[MUST] Logic層の全網羅**: ビジネスルールや計算を担う Logic クラス（Pure Kotlin）は、すべての条件分岐をユニットテストで検証してください。
- **[MUST] ViewModel の Pure Kotlin テスト**: 
    - ViewModel は Context への依存を排除し、Robolectric を使用せずに JUnit のみで高速にテストしてください。
- **[MUST] 変更検知 (isChanged) の検証**: 
    - 値を変更した際に `true` になること、および値を初期値に書き戻した際に正確に `false` に戻ることを検証してください。
- **[MUST] スナップショット比較の正当性**: `initialSnapshot` が正しく保持され、入力変更時の比較基準として機能していることを確認してください。

## 2. インストルメンタルテスト (Instrumented Test)

- **[MUST] NAV-ID シナリオテスト**: 実機抽出データ (`backup.json`) を用い、**NAV ID** と紐付いた画面遷移およびデータ表示の整合性を検証してください。
- **画面遷移の確実な検証**: ViewModel から発行される `ViewEvent` に応じて、期待される目的地へ遷移することを ComposeTestRule で検証してください。

## 3. プレビュー (Preview)

すべての画面（Content層）および主要なコンポーネントに対し、以下の状態を網羅するプレビューを義務化します。

- **PhonePreview**: スマートフォン（縦画面）でのレイアウト。
- **TabletPreview**: タブレット（横画面・2ペイン）でのレイアウト。
- **EmptyStatePreview**: データが 0 件の場合の「空状態」の表示。

## 4. 品質チェック

- **[MUST] QUICK_REFERENCE のセルフチェック**: 実装完了時、`QUICK_REFERENCE.md` の MUST ルールに抵触していないか必ず自己点検してください。
- **静的解析の遵守**: `ktlint` によるコードスタイルの統一、および `Detekt` によるコード品質の維持（警告ゼロ）を徹底してください。

---
最終更新日: 2026/08/14
