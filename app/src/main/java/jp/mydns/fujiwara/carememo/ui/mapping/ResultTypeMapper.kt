package jp.mydns.fujiwara.carememo.ui.mapping

/**
 * 操作結果識別子 (resultType) を表示用の日本語名称に変換する拡張プロパティ
 */
val String.toResultLabel: String
    get() = when (this) {
        "SUCCESS" -> "成功"
        "DB_ERROR" -> "DBエラー"
        "OTHER_ERROR" -> "その他エラー"
        "UNKNOWN" -> "不明"
        else -> "不明"
    }
