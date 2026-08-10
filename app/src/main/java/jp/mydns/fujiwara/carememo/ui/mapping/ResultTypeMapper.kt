package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R

/**
 * 操作結果識別子 (resultType) を表示用の日本語名称（リソースID）に変換する拡張プロパティ
 */
val String.toResultLabelRes: Int
    get() = when (this) {
        "SUCCESS" -> R.string.audit_result_success
        "DB_ERROR" -> R.string.audit_result_db_error
        "IO_ERROR" -> R.string.audit_result_io_error
        "FORMAT_ERROR" -> R.string.audit_result_format_error
        "VALIDATION_ERROR" -> R.string.audit_result_validation_error
        "OTHER_ERROR" -> R.string.audit_result_other_error
        "UNKNOWN" -> R.string.audit_result_unknown
        else -> R.string.audit_result_unknown
    }
