package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.Person
import java.time.ZoneOffset
import java.util.UUID

/**
 * Logic：PersonLogic
 *
 * 【役割】
 * 利用者情報（Person エンティティ）に関するドメインロジックを提供します。
 * インポート時のデータクレンジングや、ビジネスルールに基づく属性加工を担当します。
 *
 * 【設計指針：Pure Kotlin / Android 非依存】
 * 1. Android フレームワーク（Context 等）に依存せず、純粋なデータ変換のみを行います。
 * 2. 永続化（DB保存）は行わず、Repository へ渡す前の「正しい状態のデータ」を構築することに専念します。
 */
object PersonLogic {

    /**
     * インポート対象の利用者データに対してクレンジング（正規化および不整合回避）を行います。
     *
     * 【処理内容】
     * 1. 生年月日の時分秒を 00:00:00 (UTC) に正規化します。
     * 2. 正規化の結果、SQLite の一意制約（姓, 名, 生年月日, メモ）に違反するデータが発生した場合、
     *    識別用文字列を自動設定して保存の失敗を防ぎます。
     *
     * @param persons 処理対象の利用者リスト
     * @param identifierSuffixGenerator 識別子が必要な場合に、サフィックス（例："[識別子:xxxx]"）を生成する関数。
     *                                 Android のリソース（R.string）に依存しないよう外部から注入します。
     * @return クレンジング後の利用者リスト
     */
    fun cleansePersonData(
        persons: List<Person>,
        identifierSuffixGenerator: (identifier: String) -> String
    ): List<Person> {
        val seen = mutableSetOf<String>()
        return persons.map { p ->
            // 1. 生年月日の正規化 (UTC 00:00:00)
            val normalizedBirthday = p.birthday.atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()

            // 2. ユニーク制約 (姓, 名, 生年月日, メモ) の重複チェック
            var finalNote = p.note
            var key = "${p.lastName}|${p.firstName}|${normalizedBirthday.toEpochMilli()}|$finalNote"

            if (seen.contains(key)) {
                // 重複が発生した場合、救済措置としてメモに短い識別子を付記
                val identifier = UUID.randomUUID().toString().take(4)
                val suffix = identifierSuffixGenerator(identifier)
                
                finalNote = if (finalNote.length + suffix.length <= 255) {
                    finalNote + suffix
                } else {
                    // 万が一メモが長すぎる場合は末尾を削って付記 (最大255文字)
                    finalNote.take(255 - suffix.length) + suffix
                }
                key = "${p.lastName}|${p.firstName}|${normalizedBirthday.toEpochMilli()}|$finalNote"
            }

            seen.add(key)
            p.copy(birthday = normalizedBirthday, note = finalNote)
        }
    }
}
