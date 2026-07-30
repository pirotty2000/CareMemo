package jp.mydns.fujiwara.carememo.data.spec

/**
 * 服薬管理に関する業務仕様定義
 */
object MedicationSpecifications {

    /**
     * 服薬タイミング（時間枠）に関する仕様
     * 介護業務における標準的な1日の服薬サイクルを定義する。
     */
    object TimeSlot {
        /** 1日に記録すべき時間枠の総数 */
        const val COUNT = 4

        /** 朝 (Index: 0) */
        const val INDEX_MORNING = 0
        /** 昼 (Index: 1) */
        const val INDEX_LUNCH = 1
        /** 夕 (Index: 2) */
        const val INDEX_DINNER = 2
        /** 寝る前 (Index: 3) */
        const val INDEX_BEDTIME = 3

        /** インデックス順の業務上の呼称 */
        val LABELS = listOf("朝", "昼", "夕", "寝る前")
    }

    /**
     * 服薬状況のステータスに関する仕様
     * 「誰がどのように服用したか、あるいは忘れたか」という事実を識別する。
     */
    object Status {
        /** 未服用（飲み忘れ、拒否などを含む） */
        const val CODE_NONE = 0
        /** 服薬介助（介護者による声かけや直接の介助により服用） */
        const val CODE_ASSIST = 1
        /** 服用（利用者本人が自立して服用） */
        const val CODE_TAKEN = 2

        /** 有効なステータスコードの範囲 */
        val VALID_RANGE = CODE_NONE..CODE_TAKEN
    }
}
