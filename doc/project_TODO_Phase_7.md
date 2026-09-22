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
- [x] **Lambda の安定性と Callback 設計の最適化 (疎結合化と実測に基づく改善)**
    - **境界設計の洗練**: 全主要画面（14画面）において、Composable 内での ViewModel 直接参照を排除し、Stateless な `Content` 抽出と、型安全な Action Callback 形式（`onAction: (UiAction) -> Unit`）への集約を完了。
    - **実測ベースの最適化**: `remember` による Lambda 固定を全画面に適用。Compiler Report に基づき、Lambda 内で参照する最小限のプロパティのみを `remember` のキーに指定することで、不必要な再生成を抑えつつ状態変化に正しく追従する、実測と理論に基づいた最適化を完遂。

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
- [x] **UI 状態設計の標準化 (L/E/E/C + Content 詳細)**
    - 完了：`STATE_MANAGEMENT.md` に 3 層構造（Structural/Domain/Details）と直交性の設計原則を確立。
    - **今後の各画面対応**: 各画面の主要コンテンツ内における「選択状態」「展開/折りたたみ」「表示モード切り替え」等の詳細な UI 状態が漏れなく定義されているか検証し、順次標準へ適合させる。

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
- [x] **Semantics テストの導入**
    - 完了：リファクタリング後の全 14 画面において、計 73 項目のインストルメンタルテストを実行し、機能の正常性と `testTag` によるアクセシビリティ基盤の整備を確認。

### 🔴 新機能：アラート・レポート (Alert Report)
- [ ] **アラート・レポート機能の仕様策定**
    - [ ] `AppSpecifications` に基づく異常値判定ロジックの定義。
    - [ ] 急激な体重変化（例：前回比 -3kg）等の時系列異常検知ロジックの定義。
- [ ] **アラート抽出エンジン (AlertEngine) の実装**
    - [ ] 保存済みデータから異常値を一括抽出するロジックの実装。
- [ ] **アラート表示画面 (AlertReportScreen) の実装**
    - [ ] 抽出されたアラートをリスト表示し、各詳細画面へ遷移できる UI の構築。
- [ ] **通知・警告の UX 最適化**
    - [ ] 重大度に応じた視覚的フィードバックの設計。

---

## 3. アクションプラン (調査 → 実行)

### Step 1: 性能とアクセシビリティの現状診断
- [x] Compose Compiler Report の生成と解析。
- [ ] 全主要画面でのアクセシビリティ・スキャナー実行。

### Step 2: 共通基盤の修正
- [ ] UI State 基底クラスや共通 UI コンポーネント（ErrorView等）の Material 3 適合化。

### Step 3: 画面個別対応
- [x] 高優先度カテゴリから順次、各画面の UI リファクタリングを実施。
    - 完了：全主要画面（14画面）の境界設計の洗練、Action パターンへの集約、表示層の Stateless 化、および Lambda 安定化による性能最適化を完遂。

### Step 4: 現状の UI 状態監査と分類 (2026/09/06 実施)

現在の CareMemo における主要画面の UI 状態を、新標準の 3 層構造および Loading の性質に基づき監査・分類しました。

| 画面名 (Screen / ViewModel) | Structural Loading (画面構築用) | Operation Loading (操作用) | Domain Content (事実)      | UI Content Details (操作・表示)            |
|:-------------------------|:---------------------------|:------------------------|:-------------------------|:--------------------------------------|
| **利用者一覧**                | 初回の全利用者リスト取得               | 追加・削除・復旧、連絡先取得          | `userList`               | 検索クエリ、セクション、メニュー開閉                    |
| **利用者登録・編集**             | 編集時の既存データ取得                | 保存処理 (Insert/Update)    | 元の `Person` データ          | 入力値、`isValid`, `isChanged`, エラー       |
| **健康記録**                 | カテゴリ選択後の履歴取得               | レコード保存・削除               | 履歴データ (`records`)        | `currentCategory`, `editInput`, 表示モード |
| **所見メモ**                 | 記録リストと写真情報の取得              | 保存・削除・写真紐付け変更           | 記録・写真データ                 | 検索クエリ、`editInput`, プレビュー関連            |
| **服薬管理**                 | 選択月の記録取得                   | ダイアログ内ステータス更新           | 月間記録 (`monthlyRecords`)  | `selectedMonth`, ダイアログ内一時入力値          |
| **一括入力**                 | 利用者基本情報・サマリー取得             | 全カテゴリの一括保存処理            | `person`, サマリー           | バイタル入力値、記録時刻、`isValid`                |
| **利用者詳細(共通)**            | 共通ヘッダー（氏名等）の取得             | (特になし)                  | `person`, サマリー           | `currentCategory` (選択中タブ)             |
| **緊急連絡先**                | 連絡先一覧の取得                   | 連絡先の保存・削除               | `contacts`, `personName` | `editingContact`, `isEditing`         |
| **復帰・抹消**                | アーカイブ済みリストの取得              | 復帰・抹消の実行処理              | `archivedPersons`        | `mode`, `selectedIds`                 |
| **監査ログ**                 | 全ログの初回読み込み                 | (特になし)                  | `auditLogs`              | フィルタ条件、ソート順                           |
| **設定**                   | 統計情報・不整合のロード               | バックアップ・復元・ログ削除          | 統計数値、不整合リスト              | ユーザー設定値, PW、進捗率                       |
| **未割当写真**                | 不整合写真のスキャン検出               | 写真の物理削除                 | `unassignedPhotos`       | (特になし)                                |

#### 監査結果のポイント
1.  **Loading の分離**: 現状は `BaseUiStateViewModel` の `loadingStateProxy` が全画面をブロックしがちだが、今後は「画面構築 (Structural)」と「操作 (Operation)」を分離し、操作中は Content を維持したまま部分的なインジケータ（ボタン内等）で表現する設計へ移行する。
2.  **Domain と Details の物理的分離**: 編集系画面において `initialData` (Domain) と `input` (Details) を明確に分けることで、`isChanged` 判定のロジックを簡素化し、直交性を構造的に保証する。
3.  **Structural State の明示化**: `isLoading: Boolean` を `ScreenState: Sealed Class` 等へ昇格させ、`Loading`, `Empty`, `Error`, `Content` を型安全に切り替える実装標準を各画面に適用する。

---
最終更新日: 2026/09/06
