# テスト仕様書 - PersonBase (利用者情報基底クラス)

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/PersonBaseViewModelTest.kt`

## 1. ロジック・安全性テスト (PersonBaseViewModel)
**目的:** 基底クラスで提供される共通の利用者ロード処理が、例外発生時も安全に終了することを検証する。

| ID | テスト項目 | 検証内容 |
| :--- | :--- | :--- |
| LG-01 | 利用者ロード失敗時の共通保護 | `loadPerson` 中に例外が発生した際、`isLoading` が `false` になり、共通の監査ログ記録が実行されること。 |
| LG-02 | 利用者不在時の処理 | `getPersonById` が null を返した際、`isLoading` が `false` になり、フリーズしないこと。 |
| OK-01 | 同一利用者のロードスキップ | すでにロード済みの ID で `loadPerson` を呼んだ場合、`isLoading` を true にせず、再ロードをスキップすること。 |

## 2. 実装状況
| セクション | 項目 ID | ステータス | 備考 |
| :--- | :--- | :---: | :--- |
| 1. ロジック・安全性 | LG-01 〜 OK-01 | ✅ 実装済み | 具象テストクラスによる検証済。 |
