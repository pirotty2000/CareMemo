package jp.mydns.fujiwara.carememo.data.spec

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Spec：EmergencyContactSpecifications
 *
 * 【役割】
 * 緊急連絡先（SCR-M-003, M-004）に関連する入力制約（文字数等）、および
 * データベース・表示上で使用される連絡先種別の定数値を定義します。
 */
object EmergencyContactSpecifications {

    /** バリデーション制約 */
    object Validation {
        /** 施設名・事業所名・続柄の最大文字数 */
        const val MAX_LENGTH_FACILITY_NAME = 100

        /** 担当者名・個人名の最大文字数 */
        const val MAX_LENGTH_PERSON_NAME = 100

        /** 電話番号の最大文字数 (ハイフンなし数字のみを想定) */
        const val MAX_LENGTH_PHONE_NUMBER = 15

        /** 表示順序のデフォルト値 */
        const val DEFAULT_PRIORITY = 99
    }

    /** 連絡先種別の定数定義 */
    object Types {
        /** 病院・主治医 */
        const val DOCTOR = "DOCTOR"

        /** 訪問看護ステーション */
        const val NURSING_STATION = "NURSING_STATION"

        /** 地域包括支援センター */
        const val SUPPORT_CENTER = "SUPPORT_CENTER"

        /** ケースワーカー */
        const val CASE_WORKER = "CASE_WORKER"

        /** 家族 */
        const val FAMILY = "FAMILY"

        /** その他 */
        const val OTHER = "OTHER"

        /** 定義順のリスト (ソートの第一キーとして使用) */
        val ORDERED_TYPES: ImmutableList<String> = persistentListOf(
            DOCTOR,
            NURSING_STATION,
            SUPPORT_CENTER,
            CASE_WORKER,
            FAMILY,
            OTHER
        )
    }
}
