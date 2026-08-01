# テスト仕様書 - EmergencyContactEditViewModel

- **対象テストコード:** `app/src/test/java/jp/mydns/fujiwara/carememo/viewmodel/EmergencyContactEditViewModelTest.kt`

## 1. データ読込テスト (Init / loadContacts)
**目的:** 画面起動時やデータ取得時に、適切に State が更新されることを検証する。

 ID    | テスト項目          | 検証内容                                                                 |
:------|:---------------|:---------------------------------------------------------------------|
 RD-01 | 利用者名の取得       | コンストラクタで渡された `personId` に基づき、正しい利用者名が `uiState.personName` にセットされること。 |
 RD-02 | 連絡先一覧の取得      | 該当する利用者の `EmergencyContact` リストが `uiState.contacts` に反映されること。          |
 RD-03 | ロード中状態の管理     | データ取得開始時に `uiState.isLoading` が true になり、完了後に false になること。             |

## 2. 編集・保存ロジックテスト
**目的:** 新規登録、編集、保存、削除のフローが Repository と正しく連携することを検証する。

 ID    | テスト項目          | 検証内容                                                                 |
:------|:---------------|:---------------------------------------------------------------------|
 SV-01 | 新規登録モード開始     | `startAdd()` 呼出後、`uiState.editingContact` に初期値がセットされ、`isEditing` が true になること。 |
 SV-02 | 編集モード開始       | `startEdit(contact)` 呼出後、選択したデータが `editingContact` にセットされること。           |
 SV-03 | 変更検知 (isChanged) | 入力値を書き換えた際、`uiState.isChanged` が true になること。                          |
 SV-04 | 保存実行 (正常系)     | `saveContact()` 呼出時、Logic による正規化を経て Repository の保存メソッドが呼ばれること。           |
 SV-05 | 保存成功イベント      | 保存完了後、`ViewEvent.SaveSuccess` が発行されること。                              |
 SV-06 | 削除実行 (正常系)     | `deleteContact()` 呼出時、Repository の削除メソッドが呼ばれること。                         |

## 3. 安全性・例外テスト
**目的:** エラー発生時も適切に通知が行われ、不整合が起きないことを検証する。

 ID    | テスト項目          | 検証内容                                                                 |
:------|:---------------|:---------------------------------------------------------------------|
 ER-01 | 保存失敗時の通知      | Repository で例外が発生した際、`uiState.isLoading` が解除され、エラーダイアログ通知が飛ぶこと。       |
 ER-02 | バリデーションエラー    | 施設名未入力の状態で保存を試みた際、Repository を呼ばずにエラー通知を出すこと。                   |
