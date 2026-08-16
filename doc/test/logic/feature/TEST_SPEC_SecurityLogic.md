# テスト仕様書 - SecurityLogic

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/feature/SecurityLogicTest.kt`

## 1. 概要
アプリ起動時および復帰時におけるセキュリティ状態（ロック要否、デバイス設定チェック等）の判定ロジックの正確性を検証する。
ユーザー設定、端末のサポート状況、および現在の認証状態を組み合わせた複雑な状態遷移が、期待通りに `SecurityStatus` へ変換されることを対象とする。

## 2. セキュリティステータス判定テスト (determineStatus)
**目的:** 設定ロード状況、デバイスのセキュリティ設定、ユーザーのロック有効化設定、および認証済みフラグに基づき、適切な `SecurityStatus` が決定されることを検証する。

| ID     | テスト項目         | 条件 (ConfigLoaded, Supported, Enabled, Authenticated) | 期待結果 (Enum)    |
|:-------|:--------------|:-----------------------------------------------------|:---------------|
| SEC-01 | 初期化中（ロード待ち）   | **false**, (any), (any), (any)                       | `INITIALIZING` |
| SEC-02 | デバイスセキュリティ未設定 | true, **false**, (any), (any)                        | `UNSECURED`    |
| SEC-03 | ロック設定無効       | true, true, **false**, (any)                         | `UNLOCKED`     |
| SEC-04 | ロック有効・未認証     | true, true, true, **false**                          | `LOCKED`       |
| SEC-05 | ロック有効・認証済み    | true, true, true, **true**                           | `UNLOCKED`     |
