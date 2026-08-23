# Phase 7: Compose UI 基盤の高度化と UX 最適化

本ドキュメントは、CareMemo プロジェクトの Phase 7 における UI/UX 品質向上のための計画とタスクを管理します。

## 1. 概要
これまでに確立した堅牢なアーキテクチャ（ViewModelの責務分離、型安全なNavigation、監査ログ等）を基盤とし、Jetpack Compose の能力を最大限に引き出すための最適化を行います。
「性能（Recomposition）」「アクセシビリティ」「アダプティブUI」を三本柱とし、プロダクトとしての完成度を一段階引き上げます。

## 2. 重点調査・改善カテゴリ

### 🔴 高優先度：安定性と品質の核

#### A. State / Recomposition (性能・安定性)
- [ ] **Compose Compiler Report に基づく State/Model の Stable 化**
    - `unstable` な State を抽出・特定し、再コンポーズ抑制のための根本原因を解消する。
    - ワークフロー:
        1. `unstable` な State の抽出
        2. 原因の特定（不安定なプロパティ、外部ライブラリ依存等）
        3. 不変 (Immutable) 化の検討（`ImmutableList` 等の適用はあくまで手段）
        4. `@Immutable` / `@Stable` アノテーションの適用可否を検討
        5. 必要に応じて「UI専用モデル」を導入し、安定性を確保
        6. 改善前後の Compiler Report を比較し、効果を検証
- [ ] **Lambda の安定性と Callback 設計の最適化**
    - **Lambda の安定性**: キャプチャによる `unstable` 化を防ぐための検討（安定した引数の利用や、キャプチャ対象の安定性確保）。
    - **Callback 設計**: Composable 内での ViewModel 直接参照を排除し、疎結合な Action Callback 形式（`onEvent: () -> Unit`）への統一を徹底。

#### B. アクセシビリティ (TalkBack & Semantics)
- [ ] **TalkBack 読み上げの最適化**
    - カスタムコンポーネントやアイコンボタンに対する `contentDescription` と `Modifier.semantics` の付与。
- [ ] **タップターゲットの全点検**
    - アクセシビリティ・スキャナーを用いて、インタラクティブ要素が 48dp 以上を確保しているか検証。
- [ ] **動的フォントサイズへの耐性向上**
    - システム設定のフォントサイズ拡大時にレイアウトが崩れないことを全画面で確認。

#### C. Material 3 適合性と UI 状態設計
- [ ] **Material 3 テーマ・トークンの徹底利用**
    - `ColorScheme`, `Typography`, `Shape` への完全移行。ハードコードされた色の撲滅。
- [ ] **L/E/E/C パターンの標準化**
    - Loading / Empty / Error / Content 状態の網羅と、それらをカバーする Preview の実装。

### 🟠 中優先度：体験の向上

#### D. Adaptive UI (多様なデバイス対応)
- [ ] **WindowSizeClass に基づくレイアウト調整**
    - コンパクト/ミディアム/エキスパンドの各サイズにおけるコンテンツ密度の最適化。
- [ ] **2ペイン遷移のシームレス化**
    - 画面回転や折りたたみ・展開時における入力状態・スクロール位置の維持。

#### E. Navigation & Lifecycle (整合性)
- [ ] **結果返却パターンの洗練**
    - `SavedStateHandle` を用いた型安全な遷移先からのデータ返却処理の統一。
- [ ] **プロセス死からの復元 (State Restoration)**
    - 入力フォーム等の重要画面における `rememberSaveable` と `SavedStateHandle` の連携強化。

#### F. 入力 UI (滑らかなインタラクション)
- [ ] **IME 連動の最適化**
    - `imePadding` の適切な配置と、入力欄への自動フォーカス・スクロールの調整。
- [ ] **Validation Feedback の UX 改善**
    - エラー発生時の視覚的通知と、修正後の即時反映（リアクティブなバリデーション）。

### 🟡 低優先度：完成度の追求

#### G. Animation (視覚的フィードバック)
- [ ] **UI 状態遷移のアニメーション**
    - `AnimatedContent` や `animateContentSize` を用いた、状態変化時のスムーズな視覚効果。

#### H. Preview / UI Test (品質の可視化)
- [ ] **Multi-Preview 構成の拡充**
    - ダークモード、フォントスケール、多言語をカバーするプレビュー環境の構築。
- [ ] **Semantics テストの導入**
    - アクセシビリティ属性に基づいた UI テストを行い、TalkBack 利用者向けの操作性をプログラムで保証。

---

## 3. アクションプラン (調査 → 実行)

### Step 1: 性能とアクセシビリティの現状診断
- [ ] Compose Compiler Report の生成と解析。
- [ ] 全主要画面でのアクセシビリティ・スキャナー実行。

### Step 2: 共通基盤の修正
- [ ] UI State 基底クラスや共通 UI コンポーネント（ErrorView等）の Material 3 適合化。

### Step 3: 画面個別対応
- [ ] 高優先度カテゴリから順次、各画面の UI リファクタリングを実施。

---
最終更新日: 2026/08/23
