package jp.mydns.fujiwara.carememo.ui.mapping

import jp.mydns.fujiwara.carememo.R

/**
 * 操作種別識別子 (actionType) を表示用の名称（リソースID）に変換する拡張プロパティ
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
