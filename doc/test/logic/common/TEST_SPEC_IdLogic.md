# テスト仕様書 - IdLogic

- **対象テストコード:**
    - `app/src/test/java/jp/mydns/fujiwara/carememo/logic/common/IdLogicTest.kt`

## 1. 新規判定テスト (isNew)
**目的:** 指定された ID が「新規レコード用（未保存）」かどうかを正しく判定できることを検証する。

| ID | テスト項目 | 条件 (入力) | 期待結果 |
|:---|:---|:---|:---:|
| ID-01 | 新規用定数 | `AppSpecifications.Id.NEW_RECORD_ID` ("NEW") | `true` |
| ID-02 | 空文字 | "" | `true` |
| ID-03 | null | `null` | `true` |
| ID-04 | 保存済みID | "550e8400-e29b-41d4-a716-446655440000" (UUID等) | `false` |
