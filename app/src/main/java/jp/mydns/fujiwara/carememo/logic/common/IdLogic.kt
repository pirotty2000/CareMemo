package jp.mydns.fujiwara.carememo.logic.common

import jp.mydns.fujiwara.carememo.data.AppSpecifications

/**
 * Logic：IdLogic
 *
 * 【役割】
 * アプリ内で使用される各種エンティティのID（主キー）に関する共通ロジックを提供します。
 *
 * 【設計指針：Pure Kotlin / Android 非依存】
 * 1. 本クラスは Pure Kotlin で実装されており、Android API に依存しません。
 * 2. 永続化前のデータと永続化済みのデータを区別するための統一された判定基準を提供します。
 * 3. null や空文字に加え、システム共通の新規用ID定数 [AppSpecifications.Id.NEW_RECORD_ID] を「新規」として扱います。
 */
object IdLogic {
    /**
     * 指定されたIDが「新規レコード用（未保存）」かどうかを判定します。
     *
     * IDが以下のいずれかに該当する場合に true を返します：
     * ・null である
     * ・空文字（""）である
     * ・[AppSpecifications.Id.NEW_RECORD_ID] ("NEW" 等の定数) と一致する
     *
     * @param id 判定対象のID文字列
     * @return 新規用IDの場合は true、保存済みID（UUID等）の場合は false
     */
    fun isNew(id: String?): Boolean {
        return id.isNullOrEmpty() || id == AppSpecifications.Id.NEW_RECORD_ID
    }
}
