# CareMemo テスト統合ガイドライン (Master Guide)

## 1. 概要
このドキュメントは、CareMemo プロジェクトにおけるテスト戦略の全体像と、各テスト階層の役割、および詳細仕様書へのインデックスを定義します。
プロジェクト全体の品質を、**「基盤の堅牢性」** と **「機能の正確性」** の両面から支えることを目的とします。

---

## 2. テストの階層構造 (Test Pyramid)

### 2.1. 基盤・整合性テスト (Foundation & Integrity)
アプリの屋台骨となる、データの破壊や不整合、ライフサイクルの競合を防ぐための最重要テストです。
- **詳細仕様書:** [TEST_SPEC_LOGIC_DATA.md](./TEST_SPEC_LOGIC_DATA.md)
- **検証項目:** データベース整合性 (Room), バックアップ完全性, 非同期ライフサイクル競合, ViewModel 基盤機能。

### 2.2. 機能別ロジックテスト (Local Unit Test)
各実装クラスと 1:1 で対応し、計算ルールやデータ変換の正確性を検証します。
- **[Logic層 (共通)](./logic/common/)**: `JapaneseDateLogic`, `PersonLogic` 等の計算・変換ロジック。
- **[Logic層 (機能)](./logic/feature/)**: `SecurityLogic`, `SettingsLogic` 等の画面固有ロジック。
- **[Mapping層](./mapping/)**: Entity ↔ UiState 間の相互変換。
- **[Repository層](./repository/)**: DB 操作と監査ログ（AuditLog）の自動記録。
- **[ViewModel層](./viewmodel/)**: 画面状態管理と UI Intent 処理。
- **[Utils層](./utils/)**: 日時・画像・PDF 等の共通ユーティリティ。

### 2.3. シナリオ・結合テスト (Scenario & Integration)
複数の画面や機能を跨ぐ一連のユーザーフローを検証します。
- **[シナリオテスト](./scenario/)**: [利用者詳細フロー (A/B/C 跨ぎ)](./scenario/TEST_SCENARIO_PersonDetailFlow.md) 等。

### 2.4. UI 表示・挙動テスト (Component & Screen Test)
画面（Composable）が状態を正しく描画し、操作が ViewModel に伝達されるかを検証します。
- **[Screen層](./screen/)**: Phone/Tablet 別の Adaptive レイアウト、ナビゲーション、ダイアログ制御。

---

## 3. テスト作成の基本ルール
1. **1:1 対応の原則**: 原則として 1 つの実装クラスに対し、1 つのテスト仕様書（`TEST_SPEC_ClassName.md`）と 1 つのテストプログラムを作成する。
2. **Single Source of Truth**: 閾値や定数は全て `AppSpecifications` を参照し、テストコードへのハードコードを禁止する。
3. **監査ログの必須検証**: 保存・更新を伴う処理では、必ず正しい `AuditLog` が記録されることを検証する。
4. **Adaptive UI 対応**: 画面（Screen）テストでは、Phone 幅と Tablet 幅の両方での描画整合性を検証する。

---

## 4. 実行方法
- **Local Unit Test**: Android Studio の `app/src/test` を右クリックして実行。
- **Instrumented Test**: エミュレータまたは実機を起動し、`app/src/androidTest` を実行。

---

## 5. 画面遷移の責任分界 (Navigation Responsibility)
仕様書の肥大化を防ぐため、画面遷移の検証責任を以下のように分離します。
- **親画面の責任**: 子画面への「遷移開始」操作と、正しい引数が渡されたかの検証を担当。
- **子画面の責任**: その画面自体の「表示・操作」と、「戻る」操作による終了（PopBackStack）の検証を担当。

[詳細な遷移対応表はこちら](#付録画面遷移の親子関係テスト分担の基準)

---

## 付録：画面遷移の親子関係（テスト分担の基準）
どの画面（仕様書）で「遷移の開始」を検証し、どの画面で「画面自体の振る舞い」を検証するかの分担表です。

| 親（遷移開始の検証担当）             | 子（画面内の検証担当）                | 備考               |
|:-------------------------|:---------------------------|:-----------------|
| **MainScreen** (一覧)      | **PersonHealth** (健康)      | 詳細画面への基本遷移       |
|                          | **BatchInput** (一括入力)      | FAB またはメニューからの遷移 |
|                          | **PersonEdit** (利用者編集)     | 編集ボタンからの遷移       |
| **PersonHealth** (健康)    | **GraphExpansion** (グラフ拡大) | グラフエリアタップ        |
| **PersonCondition** (所見) | **ConditionPhotoPreview**  | カメラ撮影・写真タップ（編集）  |
|                          | **ConditionPhotoFull**     | 写真タップ（閲覧）        |
| **SettingsScreen** (設定)  | **AuditLogScreen** (操作ログ)  | 設定項目からの遷移        |
|                          | **ArchiveManagement**      | 利用終了者の管理         |

---
最終更新日: 2026/08/15
