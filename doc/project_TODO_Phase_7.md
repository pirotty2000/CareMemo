# Phase 7: Compose UI 基盤の高度化と UX 最適化

本ドキュメントは、CareMemo プロジェクトの Phase 7 における UI/UX 品質向上のための計画とタスクを管理します。

## 1. 概要
これまでに確立した堅牢なアーキテクチャ（ViewModelの責務分離、型安全なNavigation、監査ログ等）を基盤とし、Jetpack Compose の能力を最大限に引き出すための最適化を行います。
「性能（Recomposition）」「アクセシビリティ」「アダプティブUI」を三本柱とし、プロダクトとしての完成度を一段階引き上げます。

## 2. 重点調査・改善カテゴリ

### 🔴 高優先度：安定性と品質の核

#### A. State / Recomposition (性能・安定性)
- [x] **Compose Compiler Report に基づく State/Model の Stable 化**
    - 調査完了：主要なデータモデル（PersonUiState等）は Stable であり、現状維持で問題ないことを確認。
- [/] **Lambda の安定性と Callback 設計の最適化**
    - **Lambda の安定性**: キャプチャによる `unstable` 化を防ぐための検討（安定した引数の利用や、キャプチャ対象の安定性確保）。
    - **Callback 設計**: Composable 内での ViewModel 直接参照を排除し、疎結合な Action Callback 形式（`onAction: () -> Unit`）への統一を徹底（一部のダイアログ等で先行実施）。

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
- [ ] **UI 状態設計の標準化 (L/E/E/C + Content 詳細)**
    - Loading / Empty / Error / Content 状態の網羅と、それらをカバーする Preview の実装。
    - **Content 内部の状態網羅**: 各画面の主要コンテンツ内における「選択状態」「展開/折りたたみ」「表示モード切り替え」等の詳細な UI 状態が漏れなく定義されているか検証。

### 🟠 中優先度：体験の向上

#### D. Adaptive UI (多様なデバイス対応)
- [ ] **WindowSizeClass に基づくレイアウト調整**
    - コンパクト/ミディアム/エキスパンドの各サイズにおけるコンテンツ密度の最適化。
- [ ] **2ペイン遷移のシームレス化**
    - 画面回転や折りたたみ・展開時における入力状態・スクロール位置の維持。

#### E. Navigation & Lifecycle (整合性)
- [x] **Navigation & Lifecycle の整合性と責務整理**
    - 完了：UI/VM/Nav/Transient の 4 つの状態責務を定義し、設計指針を確立。
- [x] **結果返却パターンの洗練**
- [x] **プロセス死からの復元 (State Restoration)**
    - 完了：全主要画面（利用者一覧、登録編集、健康、所見、服薬、一括入力、緊急連絡先）において、状態管理 7 原則に基づく SavedStateHandle 復元ロジックを実装。

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
- [/] **Semantics テストの導入**
    - 状態復元テストを通じて testTag/Semantics の基盤を整備。今後は TalkBack 読み上げ内容の保証へ。

---

## 3. アクションプラン (調査 → 実行)

### Step 1: 性能とアクセシビリティの現状診断
- [x] Compose Compiler Report の生成と解析。
- [ ] 全主要画面でのアクセシビリティ・スキャナー実行。

### Step 2: 共通基盤の修正
- [ ] UI State 基底クラスや共通 UI コンポーネント（ErrorView等）の Material 3 適合化。

### Step 3: 画面個別対応
- [/] 高優先度カテゴリから順次、各画面の UI リファクタリングを実施。
    - 全主要画面（利用者一覧、登録編集、健康、所見、服薬、一括入力、緊急連絡先）の状態復元対応が完了。

---
最終更新日: 2026/08/23
