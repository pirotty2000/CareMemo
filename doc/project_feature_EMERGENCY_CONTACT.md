# 緊急連絡先（主治医・訪問看護・家族）連携機能 仕様検討

## 1. 背景・目的
- 利用者の自宅訪問時等の緊急事態において、主治医や訪問看護ステーション、家族へ迅速に連絡を行えるようにする。
- 業務上の連絡先（病院等）は個人の端末のアドレス帳に登録されていないことが多いため、アプリ内の利用者情報に紐付けて管理する。
- 現場のプロフェッショナルが判断可能な必要十分な情報を保持し、入力の手間を最小限に抑える。

## 2. データモデル案
利用者に紐付く連絡先を 1:N で管理するため、専用の Entity を追加する。マスタテーブルは作成せず、アプリ内定数（Enum等）で種別を管理する。

### 2.1. EmergencyContact Entity
| フィールド          | 型      | 必須 | 説明                           |
|:---------------|:-------|:--:|:-----------------------------|
| `id`           | String | ○  | プライマリキー（UUID）                |
| `personId`     | String | ○  | 利用者ID（外部キー/UUID）             |
| `contactType`  | String | ○  | 種別（下記 6 類型）                  |
| `facilityName` | String | ○  | 病院名・事業所名・続柄（例：「○○クリニック」「長男」） |
| `personName`   | String | －  | 担当者名・個人名（例：「田中先生」「佐藤太郎」）     |
| `phoneNumber`  | String | －  | 電話番号（任意だが発信機能には必須）           |
| `priority`     | Int    | ○  | 表示順序（デフォルト：99）               |

#### contactType の定義（6 類型）
1. 病院
2. 訪問看護ステーション
3. 地域包括支援センター
4. ケースワーカー
5. 家族
6. その他

### 2.2. データ取得・表示の最適化
一覧画面（SCR-M-001）での表示パフォーマンスを維持するため、一覧取得時の結合（JOIN）を避け、必要なタイミングでデータを取得する設計とする。

- **オンデマンド方式の採用**: `PersonSummary` にフラグを持たせず、アクション実行時にのみ DB 問い合わせを行う。
- **UI 制御**: 連絡先が未登録の場合、タップ後にガイド（Snackbar 等）を表示。
- **ソート順**: `contactType` の定義順 ➔ `priority` ➔ `facilityName` の昇順。

## 3. UI/UX 設計案

### 3.1. 一覧画面 (SCR-M-001) でのアクション
- **採用案 (案1)**: **バッジ部分のタップにより「クイックメニュー」を表示。**
    - 構成: バッジ部分をタップすると、緊急連絡先を含む「クイックメニュー」をポップアップ表示する。
- **補完案 (案3)**: 利用者タップ時に表示されるボトムシートのヘッダーに「電話アイコン」を配置。

### 3.2. 管理画面 (SCR-M-003) ※新設
- **管理の入り口**: SCR-M-001 の鉛筆アイコンタップ時のメニューに「連絡先の管理・編集」を追加。
- **画面構成**:
    - **リスト表示**: 登録済みの `EmergencyContact` をソート順に表示。
    - **表示項目**: 種別アイコン、施設名/続柄、担当者名/氏名、電話番号（整形済み）。
    - **各行の操作**: 右端に「鉛筆アイコン」を配置。タップで DropdownMenu（編集・削除）を表示。
    - **削除操作**: 物理削除を採用。実行前に `AppDeleteConfirmDialog` による破壊的操作の最終確認を行う。
    - **追加操作**: 画面右下にフローティングアクションボタン（FAB）を配置。タップで登録画面へ遷移。
- **入力フォーム (SCR-M-004) の制約**:
    - `contactType` は選択式（プルダウン）。
    - `phoneNumber` には専用キーボードを割り当て、注記を添える。
    - **フォーカス制御**: 編集効率向上のため、フォーカス時はハイフンなし、非フォーカス時はハイフンありで表示を切り替える。

## 4. インタラクションフロー

### 4.1. 緊急連絡フロー
1. 一覧画面で「バッジ部分」をタップ ➔ クイックメニュー表示。
2. 「医師・看護師・家族に連絡」を選択。
3. 連絡先詳細をオンデマンドで取得。登録がない場合は Snackbar 等で通知。
4. 登録がある場合、連絡先一覧をボトムシートで表示。
5. 連絡先を選択 ➔ 確認ダイアログ表示 ➔ ダイヤラー起動。

### 4.2. データ管理フロー
1. 一覧画面で「鉛筆アイコン」をタップ ➔ 「連絡先の管理・編集」を選択。
2. 管理画面 (SCR-M-003) で一覧を確認。
3. **新規登録/修正**: 入力フォーム (SCR-M-004) で編集・保存。変更があれば破棄確認ダイアログを表示。
4. **削除**: 行のメニューから「削除」を選択 ➔ `AppDeleteConfirmDialog` で確定。

## 5. 関連ルール・制約
- **project_RULES.md 3.6**: OS 標準 UI（Intent 発行）の Activity 委譲。
- **project_RULES.md 3.3.2**: 編集画面での破棄確認ダイアログの必須化。
- **project_UI_GUIDELINES.md 4**: `AppDeleteConfirmDialog` による削除確認。
- **project_RULES.md 8**: ログ記録（`emergency_contact_db` に対する操作）。

## 6. 実装完了ファイル一覧

### 6.1. Data レイヤー (仕様・Entity・DAO)
| ファイルパス                                        | 区分 | 概要                                                           |
|:----------------------------------------------|:--:|:-------------------------------------------------------------|
| `data/spec/EmergencyContactSpecifications.kt` | 新規 | 文字数制限、デフォルト優先度、種別定数。                                         |
| `data/AppSpecifications.kt`                   | 修正 | 上記仕様への窓口追加。                                                  |
| `data/Entity.kt`                              | 修正 | `EmergencyContact` Entity の追加（テーブル: `emergency_contact_db`）。 |
| `data/Dao.kt`                                 | 修正 | `EmergencyContactDao` インターフェースの追加。                           |
| `data/AppDatabase.kt`                         | 修正 | Entity 登録、DAO メソッド追加、DB Version 15。                          |

### 6.2. Domain / Repository レイヤー
| ファイルパス                                            | 区分 | 概要                             |
|:--------------------------------------------------|:--:|:-------------------------------|
| `data/repository/EmergencyContactRepository.kt`   | 新規 | 連絡先データアクセスの抽象化、監査ログ連携。         |
| `logic/common/EmergencyContactLogic.kt`           | 新規 | 共通ドメインルール（バリデーション、正規化、Enum定義）。 |
| `logic/common/PhoneNumberVisualTransformation.kt` | 新規 | 電話番号の動的ハイフン整形（03/06系対応）。       |

### 6.3. ViewModel レイヤー
| ファイルパス                                       | 区分 | 概要                                     |
|:---------------------------------------------|:--:|:---------------------------------------|
| `viewmodel/PersonListViewModel.kt`           | 修正 | クイックメニュー制御、オンデマンドデータ取得。                |
| `viewmodel/EmergencyContactEditViewModel.kt` | 新規 | 管理・編集画面の実行制御（BaseUiStateViewModel 継承）。 |

### 6.4. UI レイヤー (Screens / Components / Mapping)
| ファイルパス                                          | 区分 | 概要                                              |
|:------------------------------------------------|:--:|:------------------------------------------------|
| `ui/screens/main/EmergencyContactListScreen.kt` | 新規 | 管理画面 (SCR-M-003) の実装（Stateless 分離・Preview付）。    |
| `ui/screens/main/EmergencyContactEditScreen.kt` | 新規 | 登録・編集画面 (SCR-M-004) の実装（Stateless 分離・Preview付）。 |
| `ui/screens/main/MainScreen.kt`                 | 修正 | クイックメニュー選択後のボトムシート表示。                           |
| `ui/screens/main/MainScreenContent.kt`          | 修正 | クイックメニュー部品の組み込み、イベント伝搬。                         |
| `ui/components/main/QuickActionMenu.kt`         | 新規 | バッジタップ用クイックアクションメニュー部品。                         |
| `ui/components/main/MainComponents.kt`          | 修正 | `UserListItem` のタップ領域分離、鉛筆メニュー拡張。               |
| `ui/mapping/EmergencyContactMapping.kt`         | 新規 | 種別ごとの日本語名・アイコン・番号整形定義。                          |

### 6.5. その他
| ファイルパス                   | 区分 | 概要                                     |
|:-------------------------|:--:|:---------------------------------------|
| `res/values/strings.xml` | 修正 | 画面ラベル、メッセージ、種別名のリソース追加。                |
| `CareMemoApplication.kt` | 修正 | `EmergencyContactRepository` の DI 登録。  |
| `MainActivity.kt`        | 修正 | NavHost へのルート追加（SCR-M-003, SCR-M-004）。 |

---
最終更新日: 2026/08/02
