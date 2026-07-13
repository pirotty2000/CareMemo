package jp.mydns.fujiwara.carememo.ui.mapping

/**
 * 操作種別識別子 (actionType) を表示用の名称に変換する拡張プロパティ
 */
val String.toActionLabel: String
    get() = when (this) {
        "INSERT" -> "INSERT"
        "UPDATE" -> "UPDATE"
        "DELETE" -> "DELETE"
        "LOGICAL_DELETE" -> "利用終了"
        "RESTORE" -> "利用復帰"
        "PERMANENT_DELETE" -> "利用抹消"
        "CLEAR_ALL_ARCHIVED" -> "一括抹消"
        else -> this
    }
