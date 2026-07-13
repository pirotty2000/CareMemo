package jp.mydns.fujiwara.carememo.ui.mapping

/**
 * 機能識別子 (featureName) を表示用の日本語名称に変換する拡張プロパティ
 */
val String.toFeatureLabel: String
    get() = when (this) {
        "PersonList" -> "利用者：一覧"
        "PersonEdit" -> "利用者：新規登録・編集"
        "DeleteOrRestorePerson" -> "利用者：利用終了"
        "PersonBase" -> "利用者：基底"
        "PersonHealth" -> "健康記録"
        "BatchInput" -> "一括入力"
        "PersonDetail/HEIGHT_AND_WEIGHT" -> "詳細/身長体重"
        "PersonDetail/BP_AND_PULSE" -> "詳細/バイタル"
        "PersonDetail/GLUCOSE_AND_HBA1C" -> "詳細/血糖値"
        "PersonCondition" -> "所見メモ"
        "PersonDetail/CONDITION" -> "詳細/所見メモ"
        "PersonMedication" -> "服薬管理"
        "PersonDetail/MEDICATION" -> "詳細/服薬管理"
        "Settings" -> "設定・管理"
        "PersonDetail/Base" -> "詳細/基底"
        else -> this // 定義がない場合は識別子をそのまま表示
    }
