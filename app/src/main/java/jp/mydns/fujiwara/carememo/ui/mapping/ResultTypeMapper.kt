package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R

/**
 * Component：ResultTypeMapper
 *
 * 【役割】
 * 監査ログにおける操作結果（resultType）を、表示用の日本語名称（リソースID）に変換します。
 */
val String.toResultLabelRes: Int
    get() = when (this) {
        "SUCCESS" -> R.string.audit_result_success
        "DB_ERROR" -> R.string.audit_result_db_error
        "IO_ERROR" -> R.string.audit_result_io_error
        "FORMAT_ERROR" -> R.string.audit_result_format_error
        "VALIDATION_ERROR" -> R.string.audit_result_validation_error
        "OTHER_ERROR" -> R.string.audit_result_other_error
        "SECURITY_ERROR" -> R.string.audit_result_security_error
        "EXTERNAL_ERROR" -> R.string.audit_result_external_error
        "GUARD_SKIPPED" -> R.string.audit_result_guard_skipped
        "UNKNOWN" -> R.string.audit_result_unknown
        else -> R.string.audit_result_unknown
    }
