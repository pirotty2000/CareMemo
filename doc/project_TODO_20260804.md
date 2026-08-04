# 作業計画書: 一括入力不具合修正とUI状態管理の改善 (2026/08/04)

## 1. 目的

### A.目的 

- 健康記録の一括入力画面 (SCR-PH-002) において、データが上書きされて1件しか保存されない不具合を修正する。
- UI側での「レコード未選択」の状態表現を空文字 `""` から `null` へ移行し、データ不備による誤表示（意図しない背景色ハイライト）が発生しない堅牢な設計に改善する。

### B. 修正作業のルール

- 既存コードとの一貫性の優先
  - この修正作業では「一般的なベストプラクティス」よりも、既存コードとの一貫性を最優先します。
  - すでに同様の実装（構造、命名、パターン）が存在する場合は、新しい手法を導入せず、既存の設計・命名規則・責務分割を忠実に踏襲してください。
  - 修正時は、対象ファイルだけでなく、同じ責務を持つ他画面（health, condition, medication 等）も参照し、プロジェクト内で最も一般的かつ洗練されている実装パターンに合わせてください。
- 影響範囲の最小化
  - 依頼された修正内容以外について、設計変更やファイル整理を行わない。
- 既存設計とUI/UXの尊重
  - 既存の設計思想、フォルダ構成、および既存のUI/UXを維持する。
  - 既存実装と異なる設計を採用したい場合は、必ず修正前に理由・メリット・影響範囲を説明し、承認を得ること。
- 不要なリファクタリングの禁止
  - 「将来のため」「フォルダ構成のみ」「命名変更のみ」といった理由での修正は行わない。
  - 改善案（設計変更を含む）がある場合は、コードを直接修正せず、必ず「改善提案」として提示し、承認を得るまで実装しないこと。

## 2. 修正対象と方針

### A. 【不具合修正】Entity 生成時の ID 採番 (BatchInputLogic)
- **対象**: `jp.mydns.fujiwara.carememo.logic.feature.BatchInputLogic.kt`
- **内容**: `HeightAndWeight`, `BpAndPulse`, `GlucoseAndHbA1c` の生成時に `id = ""` を指定している箇所を削除し、Entity のデフォルト値（UUID）が適用されるようにする。

### B. 【構造改善】未選択状態の Null 安全化 (パターン1, 2, 3)
- **対象**:
    - `ui/components/common/HistoryComponents.kt`
    - `ui/screens/health/PersonHealthScreen.kt` (および Phone/Tablet 版)
    - `ui/screens/condition/PersonConditionScreen.kt` (および Content/Phone/Tablet 版)
- **内容**:
    - `selectedRecordId` / `selectedId` の型を `String` から `String?` に変更。
    - 初期値および「未選択」の状態を `null` で表現。
    - UI 側での比較ロジックを `record.id == selectedId` に統一（`selectedId` が `null` なら必ず `false` になる）。
    - 選択解除時のコールバックで `""` ではなく `null` を渡すように修正。

## 3. 作業手順

### STEP 1: ロジック層の修正（実施済み）
- [x] `BatchInputLogic.kt` の修正。
- [x] エミュレータおよび操作ログでの UUID 生成確認。

### STEP 2: 共通コンポーネントの修正 (パターン1)
- [ ] `HistoryComponents.kt` の `PersonHistoryList` および `HistoryItemWrapper` の引数を `String? = null` に変更。

### STEP 3: 画面・ViewModel 連携層の修正 (パターン2, 3)
- [ ] `PersonHealthScreen` / `PersonConditionScreen` 等において、ViewModel からの `null` 状態をそのまま UI コンポーネントへ渡すよう修正。
- [ ] 選択解除ボタン（キャンセル等）のコールバックを `null` 送信に変更。
- [ ] 各 ViewModel 側の `setSelectedId` で行っていた `ifEmpty { null }` などの変換処理を整理。

### STEP 4: 動作確認
- [ ] 修正後のアプリで一括入力を実行し、連続でデータが保存されること。
- [ ] 保存直後、あるいは選択解除時に履歴のハイライトが正しく消えること。
- [ ] 既存の単発入力（SCR-PH-001等）の保存・選択動作に影響がないこと。

## 4. 備考 (対象外事項)
- **データベース上の空文字 ID (パターン4)**: 救済写真の `condition_id = ""` 等の仕様は、既存ロジックへの影響とマイグレーションのリスクを考慮し、今回の作業対象からは除外する。
- **既存の id="" データの自動クリーンアップ**: 実装せず、ユーザーによる手動削除（スワイプ削除）での対応とする。
