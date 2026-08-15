package jp.mydns.fujiwara.carememo.data.spec

/**
 * Spec：ConstraintSpecifications
 *
 * 【役割】
 * アプリ全体における「入力制約（文字数制限）」、写真の「ファイルサイズ・サムネイルサイズ」、
 * およびシステムの「パスワードルール」を一括定義します。
 */
object ConstraintSpecifications {

    /** 利用者情報 (Person) */
    object Person {
        /** 業務ロジック・データ整合性のための制約 (logic) */
        object Validation {
            const val MAX_LENGTH_LAST_NAME = 50
            const val MAX_LENGTH_FIRST_NAME = 50
            const val MAX_LENGTH_LAST_NAME_FURIGANA = 100
            const val MAX_LENGTH_FIRST_NAME_FURIGANA = 100
            const val MAX_LENGTH_NOTE = 255
        }

    }

    /** 所見メモ (Condition) */
    object Condition {
        /** 業務ロジック・データ整合性のための制約 (logic) */
        object Validation {
            const val MAX_LENGTH_TITLE = 50
            const val MAX_LENGTH_MEMO = 1000
        }

        /** アプリ実装上の都合による写真の制約 (technical/technical constraints) */
        object Photo {
            const val MAX_COUNT = 3
            const val MAX_SIZE_KB = 1024
            const val THUMBNAIL_SIZE_PX = 256
            const val DIR_NAME = "photos"
        }
    }

    /** 設定・システム (System) */
    object System {
        /** セキュリティに関する制約 */
        object Security {
            const val MIN_PASSWORD_LENGTH = 6
            const val MAX_PASSWORD_LENGTH = 20
            const val DEVELOPER_MODE_TAP_COUNT = 7
        }
        
        /** 監査ログに関する制約 */
        object AuditLog {
            const val DEFAULT_RETENTION_DAYS = 30
        }
    }
}
