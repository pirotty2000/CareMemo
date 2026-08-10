# テスト仕様書 - PersonDetailLogic

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/PersonDetailLogicTest.kt`

## 1. 概要
利用者詳細画面（A/B/C共通）の共通フレームワークとしての UI 状態（UiState）およびイベントの定義を管理する。
現時点ではデータ定義が中心であるが、共通の表示・遷移ロジックが追加された際に本テストを拡張する。

## 2. UI状態テスト (UiState)
**目的:** 共通状態を保持する `PersonDetailUiState` が、初期値や各カテゴリでの定義に矛盾がないことを検証する。

| ID    | テスト項目    | 検証内容             |                    期待結果                     |
|:------|:---------|:-----------------|:-------------------------------------------:|
| UI-01 | 初期状態の整合性 | デフォルトコンストラクタでの生成 | カテゴリが `HEIGHT_AND_WEIGHT` で、他が空/false であること |
