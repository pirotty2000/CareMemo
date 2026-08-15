package jp.mydns.fujiwara.carememo.data.spec

import android.graphics.Color

/**
 * Spec：ExportSpecifications
 *
 * 【役割】
 * PDF 帳票出力時のレイアウト、テーブル幅、フォントサイズ、および印刷に適した配色（カラーパレット）を定義します。
 */
object ExportSpecifications {
    /** PDF帳票仕様 */
    object Pdf {
        /** 基本レイアウト (A4 Points: 72dpi相当) */
        object Layout {
            const val PAGE_WIDTH = 595f  // A4 width
            const val PAGE_HEIGHT = 842f // A4 height
            const val MARGIN = 50f
            const val HEADER_HEIGHT = 125f
            const val SINGLE_GRAPH_HEIGHT = 130f
            const val LINE_SPACING = 15f
        }

        /** フォント・スタイル仕様 */
        object Style {
            const val FONT_SIZE_PAGE_TITLE = 18f
            const val FONT_SIZE_HEADER = 12f
            const val FONT_SIZE_BODY = 10f
            const val FONT_SIZE_TABLE_HEADER = 10f
            const val FONT_SIZE_TABLE_BODY = 9f
            const val FONT_SIZE_CAPTION = 7f
            const val FONT_SIZE_MED_STATUS = 10f
        }

        /** 配色仕様 (RGB) */
        object Colors {
            val BACKGROUND_LIGHT = Color.rgb(245, 245, 245)
            const val TABLE_LINE = Color.LTGRAY
            val SUN_BACKGROUND = Color.rgb(255, 240, 240)
            val SAT_BACKGROUND = Color.rgb(240, 248, 255)
            val SUN_TEXT = Color.rgb(211, 47, 47)
            val SAT_TEXT = Color.rgb(25, 118, 210)

            /** 服薬状況ステータス色 */
            object Medication {
                val STATUS_TAKEN = Color.rgb(103, 58, 183) // 〇
                val STATUS_ASSIST = Color.rgb(126, 87, 194) // △
                val STATUS_NONE = Color.rgb(211, 47, 47)    // ×
            }
        }

        /** テーブル列幅仕様 */
        object TableConfig {
            const val DATE_COL_WIDTH = 110f
            const val STATUS_COL_WIDTH_BASE = 150f
            
            object HeightWeight {
                const val HEIGHT_WIDTH = 75f
                const val WEIGHT_WIDTH = 100f
                const val BMI_WIDTH = 65f
            }

            object BpPulse {
                const val SYS_WIDTH = 45f
                const val DIA_WIDTH = 45f
                const val SAT_WIDTH = 40f
                const val PULSE_WIDTH = 40f
                const val TEMP_WIDTH = 45f
            }

            object Glucose {
                const val GLUCOSE_WIDTH = 115f
                const val HBA1C_WIDTH = 115f
            }

            object Medication {
                const val LABEL_WIDTH = 60f
                const val ROW_HEIGHT = 22f
            }
        }
    }
}
