package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R

/**
 * Component：ActionTypeMapper
 *
 * 【役割】
 * 監査ログにおける操作種別（actionType）を、表示用の日本語名称（リソースID）に変換します。
 */
val String.toActionLabelRes: Int
    get() = when (this) {
        "INSERT" -> R.string.audit_action_insert
        "UPDATE" -> R.string.audit_action_update
        "DELETE" -> R.string.audit_action_delete
        "LOGICAL_DELETE" -> R.string.audit_action_logical_delete
        "RESTORE" -> R.string.audit_action_restore
        "PERMANENT_DELETE" -> R.string.audit_action_permanent_delete
        "CLEAR_ALL_ARCHIVED" -> R.string.audit_action_clear_all
        else -> 0
    }
