# 配色とプライバシー（マスキング）調査結果レポート

## 1. 調査目的
`project_UI_GUIDELINES.md` の「6. 配色とプライバシー（マスキング）」に基づき、マスキングロジックの一元化状況、および配色セマンティクスの遵守状況を確認する。

## 2. 調査結果まとめ

| 項目 | 状況 | ガイドライン遵守状況 | 備考 |
| :--- | :--- | :--- | :--- |
| **マスキングロジックの一元化** | `Entity.kt` (`Person`) に集約 | ◎ 遵守 | 漢字（交互）、カナ（2文字目以降）のルールが正確に実装済み。 |
| **評価レベルの定義** | `AppThresholds.kt` | ○ 遵守 | `NORMAL`, `WARNING`, `ALERT` の3段階評価の閾値が定義済み。 |
| **セマンティックカラーの利用** | `HealthDisplayMapper.kt` 他 | × 違反 | `Color(0xFF...)` による固定色の直接指定が複数のマッパーやヘルパーで行われている。 |
| **注意/予備群の視覚的区別** | `HealthDisplayMapper.kt` | × 違反 | ガイドライン指定の「オレンジ系」ではなく「黒」が指定されている。 |
| **一覧画面のバッジ配色** | `CategoryBadges.kt` | × 違反 | バッジの色が 16 進数固定値でハードコードされている。 |
| **グラフ・チャートの配色** | `HealthChartHelper.kt` | × 違反 | グラフ線や背景ハイライトの色がハードコードされており、ダークモードへの配慮が不十分。 |
| **PDF出力の配色** | `PdfExporter.kt` | × 違反 | 背景色、グラフ、服薬ステータス等の色が RGB/16進数でハードコードされている。 |

## 3. 具体的な指摘事項と改善案

### マスキングロジック（Entity.kt / PersonHeaderTitle.kt / PdfExporter.kt）
- **現状**: `Person` クラスに集約されたロジックを、共通コンポーネント `PersonHeaderTitle` や各画面（SCR-M, PH, PC, PM）で一貫して使用。また、`PdfExporter` では出力時に強制的にマスキングを有効化している。
- **評価**: ◎ 遵守。プライバシー保護の観点から非常に優れた実装。

### 配色セマンティクス（マッパー・ヘルパー・ユーティリティ類）
- **現状**: 以下のファイルで `Color(0xFF...)` や `Color.rgb()` による固定色の直接指定を確認。
    - `HealthDisplayMapper.kt`: `getAlertColor` (Compose用), `getPdfBgColor` (PDF用)
    - `MedicationDisplayMapper.kt`: `getStatusColor`
    - `HealthChartHelper.kt`: グラフ線、ハイライト色
    - `AuditLogScreen.kt`: 操作種別ラベルの色
    - `PdfExporter.kt`: テーブル背景、土日ハイライト、服薬マークの色
- **問題点**: 
    1. ガイドラインで禁止されている「固定色の直接指定」に該当。ダークモード切替時に視認性が低下する恐れがある。
    2. `HealthAlertLevel.WARNING` の色がガイドライン指定の「オレンジ系」になっていない（一部で黒やグレーが指定されている）。
- **改善案**:
    1. アプリ画面（Compose）では `MaterialTheme.colorScheme` のセマンティックカラー（`primary`, `error`, `tertiary` 等）にマッピングする。
    2. グラフやPDF等の固定色が必要な箇所は、`Color.kt` 等で定義した共通パレットを参照するようにし、マジックナンバーを排除する。
    3. `WARNING` 判定時の色をアプリ・PDF共にオレンジ系（またはそれを象徴する色）で統一する。

### 一覧画面のバッジ配色（CategoryBadges.kt）
- **現状**: 16進数固定値を使用。
- **改善案**: テーマカラーへの統合。

---
最終更新日: 2026/07/15
