# 定数・バリデーションの集約（AppSpecifications）調査結果と修正タスク

このドキュメントは、`project_RULES.md` の「3.2. 定数・バリデーションの集約（AppSpecifications）」に基づいた調査結果と、実施した修正内容をまとめたものです。

---

## 0. 根本的な設計指針（2026/07/22 確定）

本修正にあたり、以下の通り方針を決定し、実施しました。

### 名称の変更と構造の再編
- **結論**: `AppThresholds`（しきい値）という名称は実態に対して限定的すぎるため、アプリのあらゆる仕様を包含する **`AppSpecifications`** へ改名し、ドメインごとに構造化（nested object）した。
- **物理構造**: ファイルの肥大化を防ぐため、`data/spec/` パッケージへドメインごとに分割して配置した。 `AppSpecifications.kt` はそれらへの窓口（Facade）として機能する。

### 「再利用性の有無」か「仕様の所在」か
- **方針**: **「再利用性の有無」よりも「仕様の所在」を優先して集約する。**
- **理由**: プロジェクトの辞書としての機能を重視し、不整合の防止と保守性の向上を図る。

---

## 1. 修正完了サマリー (2026/07/22 完了)

全主要ドメインの移行、および「アプリの辞書」としての構造化が完了しました。

### 実施内容の詳細

| 項目           | 適合状況   | 備考                                                                            |
|:-------------|:-------|:------------------------------------------------------------------------------|
| **健康管理定義**   | **遵守** | `HealthSpecifications.kt` へ移行。血圧、血糖、BMI等の閾値・単位・精度を定義。                         |
| **業務仕様の明文化** | **遵守** | `MedicationSpecifications.kt` にて、服薬管理の「時間枠」と「ステータス」の業務上の意味（呼称）を定義。            |
| **アプリ制約の分離** | **遵守** | `ConstraintSpecifications.kt` を新設。文字数制限や写真枚数など、実装上の制限事項を集約。                   |
| **日本の暦定義**   | **遵守** | `CalendarSpecifications.kt` へ移行。和暦のエポック、オフセット、最大年数、西暦上限を定義。                   |
| **帳票・出力定義**  | **遵守** | `ExportSpecifications.kt` へ移行。PDFのA4レイアウト、マージン、フォントサイズ、配色、テーブル幅を定義。           |
| **設定・検索定義**  | **遵守** | `SettingsSpecifications` (選択肢リスト), `SearchSpecifications` (五十音インデックス) を整理。    |
| **ロジックの分離**  | **遵守** | `isWithinFormat` や数値フォーマッタなどの「振る舞い」を `HealthLogic` へ移動。                       |
| **参照の全面刷新**  | **遵守** | UI層の `TextField.maxLength` や Logic層の比較処理からマジックナンバーを排除し、辞書参照に統一。               |
| **データの整合性**  | **遵守** | 生年月日の「時分秒なし(00:00:00)」の仕様を徹底。インポート時も `AppMaintenanceRepository` で強制正規化するよう強化。 |

---

## 2. 最終的なパッケージ構造 (`data/spec/` 配下)

```kotlin
// 各ドメインごとの物理ファイル
- data/spec/HealthSpecifications.kt     // 健康指標の閾値・単位
- data/spec/MedicationSpecifications.kt // 服薬管理の業務定義
- data/spec/CalendarSpecifications.kt   // 和暦・西暦の定義
- data/spec/ExportSpecifications.kt     // PDF帳票のレイアウト・配色
- data/spec/ConstraintSpecifications.kt // 文字数制限などのアプリ制約
- data/spec/SearchSpecifications.kt     // 五十音インデックス定義
- data/spec/SettingsSpecifications.kt   // 設定画面の選択肢リスト

// AppSpecifications.kt (各ファイルへの参照を保持する窓口)
object AppSpecifications {
    val Health = HealthSpecifications
    val Condition = ConstraintSpecifications.Condition
    val Medication = MedicationSpecifications
    val JapaneseCalendar = CalendarSpecifications
    val Export = ExportSpecifications
    val Constraints = ConstraintSpecifications
    val Search = SearchSpecifications
    val Settings = SettingsSpecifications
}
```

---
最終更新日: 2026/07/22
