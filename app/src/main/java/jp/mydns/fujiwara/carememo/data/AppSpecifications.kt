package jp.mydns.fujiwara.carememo.data

import jp.mydns.fujiwara.carememo.data.spec.*

/**
 * Data：AppSpecifications
 *
 * 【役割】
 * CareMemo アプリの「仕様定義（辞書）」の統一窓口オブジェクトです。
 * 閾値、文字数制限、配色、暦、バリデーションルール等、アプリ全体の振る舞いを決定する定数群を集約します。
 *
 * 【構造】
 * 本オブジェクトはファサード（窓口）として機能し、実際の実装（値の定義）は
 * [jp.mydns.fujiwara.carememo.data.spec] パッケージ配下の各カテゴリ別ファイルに委譲しています。
 *
 * 【設計指針】
 * 1. マジックナンバーの排除：コード内で直接数値を記述せず、必ず本オブジェクトの定数を参照する。
 * 2. 仕様の一元管理：仕様変更（例：血圧の閾値変更、文字数制限の緩和等）が発生した際、
 *    本パッケージ配下の定義を修正するだけでアプリ全体に反映されることを保証する。
 */
object AppSpecifications {
    
    /**
     * 健康データ（カテゴリA, C, D）に関する仕様。
     * 血圧、脈拍、体温、血糖値等の閾値、入力桁数、グラフ描画範囲を含みます。
     */
    object Health {
        /** 血圧に関する閾値・表示設定 */
        object BloodPressure {
            const val THRESHOLD_HIGH_SYSTOLIC = HealthSpecifications.BloodPressure.THRESHOLD_HIGH_SYSTOLIC
            const val THRESHOLD_HIGH_DIASTOLIC = HealthSpecifications.BloodPressure.THRESHOLD_HIGH_DIASTOLIC
            const val THRESHOLD_LOW_SYSTOLIC = HealthSpecifications.BloodPressure.THRESHOLD_LOW_SYSTOLIC
            const val THRESHOLD_LOW_DIASTOLIC = HealthSpecifications.BloodPressure.THRESHOLD_LOW_DIASTOLIC
            const val DIGITS_INT = HealthSpecifications.BloodPressure.DIGITS_INT
            const val MIN_VALUE = HealthSpecifications.BloodPressure.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.BloodPressure.MAX_VALUE
            const val UNIT = HealthSpecifications.BloodPressure.UNIT
            /** 血圧グラフ固有の設定 */
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.BloodPressure.Graph.Y_AXIS_STEP
                const val Y_MIN_VIEW_LIMIT = HealthSpecifications.BloodPressure.Graph.Y_MIN_VIEW_LIMIT
                const val Y_MAX_VIEW_LIMIT = HealthSpecifications.BloodPressure.Graph.Y_MAX_VIEW_LIMIT
                const val RANGE_MAX = HealthSpecifications.BloodPressure.Graph.RANGE_MAX
                const val RANGE_MIN = HealthSpecifications.BloodPressure.Graph.RANGE_MIN
            }
        }
        /** 脈拍に関する閾値・表示設定 */
        object Pulse {
            const val THRESHOLD_HIGH = HealthSpecifications.Pulse.THRESHOLD_HIGH
            const val THRESHOLD_LOW = HealthSpecifications.Pulse.THRESHOLD_LOW
            const val THRESHOLD_GRAPH_NORMAL_UPPER = HealthSpecifications.Pulse.THRESHOLD_GRAPH_NORMAL_UPPER
            const val THRESHOLD_GRAPH_NORMAL_LOWER = HealthSpecifications.Pulse.THRESHOLD_GRAPH_NORMAL_LOWER
            const val DIGITS_INT = HealthSpecifications.Pulse.DIGITS_INT
            const val MIN_VALUE = HealthSpecifications.Pulse.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.Pulse.MAX_VALUE
            const val UNIT = HealthSpecifications.Pulse.UNIT
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.Pulse.Graph.Y_AXIS_STEP
                const val Y_MIN_VIEW_LIMIT = HealthSpecifications.Pulse.Graph.Y_MIN_VIEW_LIMIT
                const val Y_MAX_VIEW_LIMIT = HealthSpecifications.Pulse.Graph.Y_MAX_VIEW_LIMIT
                const val RANGE_MAX = HealthSpecifications.Pulse.Graph.RANGE_MAX
                const val RANGE_MIN = HealthSpecifications.Pulse.Graph.RANGE_MIN
            }
        }
        /** 酸素飽和度(SpO2)に関する閾値・表示設定 */
        object OxygenSaturation {
            const val THRESHOLD_LOW = HealthSpecifications.OxygenSaturation.THRESHOLD_LOW
            const val THRESHOLD_GRAPH_NORMAL_LOWER = HealthSpecifications.OxygenSaturation.THRESHOLD_GRAPH_NORMAL_LOWER
            const val DIGITS_INT = HealthSpecifications.OxygenSaturation.DIGITS_INT
            const val MIN_VALUE = HealthSpecifications.OxygenSaturation.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.OxygenSaturation.MAX_VALUE
            const val UNIT = HealthSpecifications.OxygenSaturation.UNIT
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.OxygenSaturation.Graph.Y_AXIS_STEP
                const val Y_MIN_VIEW_LIMIT = HealthSpecifications.OxygenSaturation.Graph.Y_MIN_VIEW_LIMIT
                const val Y_MAX_VIEW_LIMIT = HealthSpecifications.OxygenSaturation.Graph.Y_MAX_VIEW_LIMIT
                const val RANGE_MIN = HealthSpecifications.OxygenSaturation.Graph.RANGE_MIN
                const val RANGE_MAX = HealthSpecifications.OxygenSaturation.Graph.RANGE_MAX
            }
        }
        /** 体温に関する閾値・表示設定 */
        object BodyTemperature {
            const val THRESHOLD_HIGH = HealthSpecifications.BodyTemperature.THRESHOLD_HIGH
            const val THRESHOLD_LOW = HealthSpecifications.BodyTemperature.THRESHOLD_LOW
            const val DIGITS_INT = HealthSpecifications.BodyTemperature.DIGITS_INT
            const val DIGITS_DEC = HealthSpecifications.BodyTemperature.DIGITS_DEC
            const val MIN_VALUE = HealthSpecifications.BodyTemperature.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.BodyTemperature.MAX_VALUE
            const val UNIT = HealthSpecifications.BodyTemperature.UNIT
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.BodyTemperature.Graph.Y_AXIS_STEP
                const val Y_MIN_VIEW_LIMIT = HealthSpecifications.BodyTemperature.Graph.Y_MIN_VIEW_LIMIT
                const val Y_MAX_VIEW_LIMIT = HealthSpecifications.BodyTemperature.Graph.Y_MAX_VIEW_LIMIT
                const val RANGE_MAX = HealthSpecifications.BodyTemperature.Graph.RANGE_MAX
                const val RANGE_MIN = HealthSpecifications.BodyTemperature.Graph.RANGE_MIN
            }
        }
        /** 血糖値に関する閾値・表示設定 */
        object BloodGlucose {
            const val THRESHOLD_HIGH = HealthSpecifications.BloodGlucose.THRESHOLD_HIGH
            const val THRESHOLD_PREDIABETES = HealthSpecifications.BloodGlucose.THRESHOLD_PREDIABETES
            const val THRESHOLD_NORMAL_UPPER = HealthSpecifications.BloodGlucose.THRESHOLD_NORMAL_UPPER
            const val THRESHOLD_LOW = HealthSpecifications.BloodGlucose.THRESHOLD_LOW
            const val THRESHOLD_GRAPH_NORMAL_UPPER = HealthSpecifications.BloodGlucose.THRESHOLD_GRAPH_NORMAL_UPPER
            const val THRESHOLD_GRAPH_NORMAL_LOWER = HealthSpecifications.BloodGlucose.THRESHOLD_GRAPH_NORMAL_LOWER
            const val DIGITS_INT = HealthSpecifications.BloodGlucose.DIGITS_INT
            const val MIN_VALUE = HealthSpecifications.BloodGlucose.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.BloodGlucose.MAX_VALUE
            const val UNIT = HealthSpecifications.BloodGlucose.UNIT
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.BloodGlucose.Graph.Y_AXIS_STEP
                const val DEFAULT_MIN = HealthSpecifications.BloodGlucose.Graph.DEFAULT_MIN
                const val DEFAULT_MAX = HealthSpecifications.BloodGlucose.Graph.DEFAULT_MAX
                const val VIEW_PADDING = HealthSpecifications.BloodGlucose.Graph.VIEW_PADDING
                const val RANGE_MAX = HealthSpecifications.BloodGlucose.Graph.RANGE_MAX
                const val RANGE_MIN = HealthSpecifications.BloodGlucose.Graph.RANGE_MIN
            }
        }
        /** HbA1c に関する閾値・表示設定 */
        object HbA1c {
            const val THRESHOLD_NORMAL_UPPER = HealthSpecifications.HbA1c.THRESHOLD_NORMAL_UPPER
            const val THRESHOLD_DIABETES = HealthSpecifications.HbA1c.THRESHOLD_DIABETES
            const val THRESHOLD_GRAPH_NORMAL_UPPER = HealthSpecifications.HbA1c.THRESHOLD_GRAPH_NORMAL_UPPER
            const val DIGITS_INT = HealthSpecifications.HbA1c.DIGITS_INT
            const val DIGITS_DEC = HealthSpecifications.HbA1c.DIGITS_DEC
            const val MIN_VALUE = HealthSpecifications.HbA1c.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.HbA1c.MAX_VALUE
            const val UNIT = HealthSpecifications.HbA1c.UNIT
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.HbA1c.Graph.Y_AXIS_STEP
                const val DEFAULT_MIN = HealthSpecifications.HbA1c.Graph.DEFAULT_MIN
                const val DEFAULT_MAX = HealthSpecifications.HbA1c.Graph.DEFAULT_MAX
                const val VIEW_PADDING = HealthSpecifications.HbA1c.Graph.VIEW_PADDING
                const val RANGE_MAX = HealthSpecifications.HbA1c.Graph.RANGE_MAX
                const val RANGE_MIN = HealthSpecifications.HbA1c.Graph.RANGE_MIN
            }
        }
        /** BMI (体格指数) に関する判定閾値 */
        object BodyMassIndex {
            const val THRESHOLD_UNDERWEIGHT = HealthSpecifications.BodyMassIndex.THRESHOLD_UNDERWEIGHT
            const val THRESHOLD_NORMAL_UPPER = HealthSpecifications.BodyMassIndex.THRESHOLD_NORMAL_UPPER
            const val THRESHOLD_OBESITY_1 = HealthSpecifications.BodyMassIndex.THRESHOLD_OBESITY_1
            const val THRESHOLD_OBESITY_2 = HealthSpecifications.BodyMassIndex.THRESHOLD_OBESITY_2
            const val THRESHOLD_OBESITY_3 = HealthSpecifications.BodyMassIndex.THRESHOLD_OBESITY_3
            const val THRESHOLD_GRAPH_NORMAL_UPPER = HealthSpecifications.BodyMassIndex.THRESHOLD_GRAPH_NORMAL_UPPER
            const val THRESHOLD_GRAPH_NORMAL_LOWER = HealthSpecifications.BodyMassIndex.THRESHOLD_GRAPH_NORMAL_LOWER
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.BodyMassIndex.Graph.Y_AXIS_STEP
                const val DEFAULT_MIN = HealthSpecifications.BodyMassIndex.Graph.DEFAULT_MIN
                const val DEFAULT_MAX = HealthSpecifications.BodyMassIndex.Graph.DEFAULT_MAX
                const val VIEW_PADDING = HealthSpecifications.BodyMassIndex.Graph.VIEW_PADDING
                const val RANGE_MAX = HealthSpecifications.BodyMassIndex.Graph.RANGE_MAX
                const val RANGE_MIN = HealthSpecifications.BodyMassIndex.Graph.RANGE_MIN
            }
        }
        /** 身長に関する入力設定 */
        object Height {
            const val DIGITS_INT = HealthSpecifications.Height.DIGITS_INT
            const val DIGITS_DEC = HealthSpecifications.Height.DIGITS_DEC
            const val MIN_VALUE = HealthSpecifications.Height.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.Height.MAX_VALUE
            const val UNIT = HealthSpecifications.Height.UNIT
        }
        /** 体重に関する入力設定 */
        object Weight {
            const val DIGITS_INT = HealthSpecifications.Weight.DIGITS_INT
            const val DIGITS_DEC = HealthSpecifications.Weight.DIGITS_DEC
            const val MIN_VALUE = HealthSpecifications.Weight.MIN_VALUE
            const val MAX_VALUE = HealthSpecifications.Weight.MAX_VALUE
            const val UNIT = HealthSpecifications.Weight.UNIT
            object Graph {
                const val Y_AXIS_STEP = HealthSpecifications.Weight.Graph.Y_AXIS_STEP
                const val DEFAULT_MIN = HealthSpecifications.Weight.Graph.DEFAULT_MIN
                const val DEFAULT_MAX = HealthSpecifications.Weight.Graph.DEFAULT_MAX
                const val VIEW_PADDING = HealthSpecifications.Weight.Graph.VIEW_PADDING
            }
        }
    }

    /**
     * 所見メモ（カテゴリB）に関する仕様。
     * 文字数制限や写真の保存サイズ、ディレクトリ名を管理します。
     */
    object Condition {
        object Validation {
            const val MAX_LENGTH_TITLE = ConstraintSpecifications.Condition.Validation.MAX_LENGTH_TITLE
            const val MAX_LENGTH_MEMO = ConstraintSpecifications.Condition.Validation.MAX_LENGTH_MEMO
        }
        /** 写真の保存・表示に関する制約 */
        object Photo {
            const val MAX_COUNT = ConstraintSpecifications.Condition.Photo.MAX_COUNT
            const val MAX_SIZE_KB = ConstraintSpecifications.Condition.Photo.MAX_SIZE_KB
            const val THUMBNAIL_SIZE_PX = ConstraintSpecifications.Condition.Photo.THUMBNAIL_SIZE_PX
            const val DIR_NAME = ConstraintSpecifications.Condition.Photo.DIR_NAME
        }
    }

    /**
     * 服薬管理（カテゴリE）に関する仕様。
     * 1日あたりの服用回数（4回固定）やステータスの範囲を定義します。
     */
    object Medication {
        object TimeSlot {
            const val COUNT = MedicationSpecifications.TimeSlot.COUNT
            const val INDEX_MORNING = MedicationSpecifications.TimeSlot.INDEX_MORNING
            const val INDEX_LUNCH = MedicationSpecifications.TimeSlot.INDEX_LUNCH
            const val INDEX_DINNER = MedicationSpecifications.TimeSlot.INDEX_DINNER
            const val INDEX_BEDTIME = MedicationSpecifications.TimeSlot.INDEX_BEDTIME
            /** UIに表示する時間枠の日本語ラベルリスト */
            val LABELS = MedicationSpecifications.TimeSlot.LABELS
        }
        /** 服薬ステータス（未・介助・服用）の内部コード定義 */
        object Status {
            const val CODE_NONE = MedicationSpecifications.Status.CODE_NONE
            const val CODE_ASSIST = MedicationSpecifications.Status.CODE_ASSIST
            const val CODE_TAKEN = MedicationSpecifications.Status.CODE_TAKEN
            val VALID_RANGE = MedicationSpecifications.Status.VALID_RANGE
        }
    }

    /**
     * 日本の暦（和暦）に関する仕様。
     * 改元日、西暦オフセット、アプリがサポートする最小日付を定義します。
     */
    object JapaneseCalendar {
        val MIN_DATE = CalendarSpecifications.MIN_DATE
        const val MAX_WESTERN_YEAR = CalendarSpecifications.MAX_WESTERN_YEAR
        object Era {
            val Showa = CalendarSpecifications.Era.Showa
            val Heisei = CalendarSpecifications.Era.Heisei
            val Reiwa = CalendarSpecifications.Era.Reiwa
        }
    }

    /**
     * 緊急連絡先（MedicalContact）に関する仕様。
     * バリデーションルールおよび連絡先の種別定義。
     */
    object MedicalContact {
        object Validation {
            const val MAX_LENGTH_FACILITY_NAME = EmergencyContactSpecifications.Validation.MAX_LENGTH_FACILITY_NAME
            const val MAX_LENGTH_PERSON_NAME = EmergencyContactSpecifications.Validation.MAX_LENGTH_PERSON_NAME
            const val MAX_LENGTH_PHONE_NUMBER = EmergencyContactSpecifications.Validation.MAX_LENGTH_PHONE_NUMBER
            const val DEFAULT_PRIORITY = EmergencyContactSpecifications.Validation.DEFAULT_PRIORITY
        }
        /** 連絡先種別の定数（Room等で使用） */
        object Types {
            const val DOCTOR = EmergencyContactSpecifications.Types.DOCTOR
            const val NURSING_STATION = EmergencyContactSpecifications.Types.NURSING_STATION
            const val SUPPORT_CENTER = EmergencyContactSpecifications.Types.SUPPORT_CENTER
            const val CASE_WORKER = EmergencyContactSpecifications.Types.CASE_WORKER
            const val FAMILY = EmergencyContactSpecifications.Types.FAMILY
            const val OTHER = EmergencyContactSpecifications.Types.OTHER
            /** リスト表示時のデフォルトの並び順 */
            val ORDERED_TYPES = EmergencyContactSpecifications.Types.ORDERED_TYPES
        }
    }

    /**
     * 利用者情報やシステム全般の制約に関する仕様。
     */
    object Constraints {
        /** 利用者（Person）に関するバリデーション制約 */
        object Person {
            object Validation {
                const val MAX_LENGTH_LAST_NAME = ConstraintSpecifications.Person.Validation.MAX_LENGTH_LAST_NAME
                const val MAX_LENGTH_FIRST_NAME = ConstraintSpecifications.Person.Validation.MAX_LENGTH_FIRST_NAME
                const val MAX_LENGTH_LAST_NAME_FURIGANA = ConstraintSpecifications.Person.Validation.MAX_LENGTH_LAST_NAME_FURIGANA
                const val MAX_LENGTH_FIRST_NAME_FURIGANA = ConstraintSpecifications.Person.Validation.MAX_LENGTH_FIRST_NAME_FURIGANA
                const val MAX_LENGTH_NOTE = ConstraintSpecifications.Person.Validation.MAX_LENGTH_NOTE
            }
        }
        /** システム全体（セキュリティ、ログ等）に関する制約 */
        object System {
            object Security {
                const val MIN_PASSWORD_LENGTH = ConstraintSpecifications.System.Security.MIN_PASSWORD_LENGTH
                const val MAX_PASSWORD_LENGTH = ConstraintSpecifications.System.Security.MAX_PASSWORD_LENGTH
                /** 開発者モードを有効にするために必要なタップ回数 */
                const val DEVELOPER_MODE_TAP_COUNT = ConstraintSpecifications.System.Security.DEVELOPER_MODE_TAP_COUNT
            }
            object AuditLog {
                /** 操作ログのデフォルト保持期間（日） */
                const val DEFAULT_RETENTION_DAYS = ConstraintSpecifications.System.AuditLog.DEFAULT_RETENTION_DAYS
            }
        }
    }

    /**
     * 出力・エクスポートに関する仕様。
     * PDFのレイアウト、テーブル幅、配色などを定義します。
     */
    object Export {
        /** PDF帳票固有の定義 */
        object Pdf {
            val Layout = ExportSpecifications.Pdf.Layout
            val Style = ExportSpecifications.Pdf.Style
            /** 帳票内で使用するカラーパレット（印刷適性を考慮） */
            object Colors {
                val BACKGROUND_LIGHT = ExportSpecifications.Pdf.Colors.BACKGROUND_LIGHT
                const val TABLE_LINE = ExportSpecifications.Pdf.Colors.TABLE_LINE
                val SUN_BACKGROUND = ExportSpecifications.Pdf.Colors.SUN_BACKGROUND
                val SAT_BACKGROUND = ExportSpecifications.Pdf.Colors.SAT_BACKGROUND
                val SUN_TEXT = ExportSpecifications.Pdf.Colors.SUN_TEXT
                val SAT_TEXT = ExportSpecifications.Pdf.Colors.SAT_TEXT
                val Medication = ExportSpecifications.Pdf.Colors.Medication
            }
            /** PDFテーブルの列幅やカテゴリ別の構成 */
            object TableConfig {
                const val DATE_COL_WIDTH = ExportSpecifications.Pdf.TableConfig.DATE_COL_WIDTH
                const val STATUS_COL_WIDTH_BASE = ExportSpecifications.Pdf.TableConfig.STATUS_COL_WIDTH_BASE
                val HeightWeight = ExportSpecifications.Pdf.TableConfig.HeightWeight
                val BpPulse = ExportSpecifications.Pdf.TableConfig.BpPulse
                val Glucose = ExportSpecifications.Pdf.TableConfig.Glucose
                val Medication = ExportSpecifications.Pdf.TableConfig.Medication
            }
        }
    }

    /** 検索インデックス（五十音行等）に関する仕様 */
    val Search = SearchSpecifications

    /** 設定項目の選択肢（タイムアウト時間等）に関する仕様 */
    val Settings = SettingsSpecifications

    /**
     * ID管理に関する仕様。
     *
     * 文字数制限などのバリデーション制約ではないため、Constraints ではなく
     * 独立したオブジェクトとして定義。実体は [IdSpecifications] を参照。
     */
    object Id {
        /** 新規レコードであることを示す共通識別子（システム予約語） */
        const val NEW_RECORD_ID = IdSpecifications.NEW_RECORD_ID
    }
}
