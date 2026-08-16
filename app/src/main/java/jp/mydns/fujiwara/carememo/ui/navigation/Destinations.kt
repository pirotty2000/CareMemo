package jp.mydns.fujiwara.carememo.ui.navigation

import kotlinx.serialization.Serializable


/**
 * Component：Navigation Destinations
 *
 * 【役割】
 * Jetpack Navigation (Type-safe) におけるアプリ全体の「目的地」および「画面間遷移パラメータ」を定義します。
 * Kotlin Serialization を使用して、型安全な引数の受け渡しを実現します。
 *
 * 【主な機能】
 * ・目的地定義 (Destination)：各画面に対応するデータクラスまたはオブジェクト。
 * ・結果定義 (EditResult)：登録・編集画面からの戻り値（成功ステータス）。
 * ・キー定義 (NavigationKeys)：SavedStateHandle を介した結果受け渡し用の識別子。
  *
 * 【全体像：画面遷移構造 (NAV ID 対応)】
 *
 * 【Main：利用者一覧系】
 * ├─ Main (SCR-M-001)
 * ├─ PersonEdit (SCR-M-002)：利用者登録・編集
 * ├─ MedicalContacts (SCR-M-003)：緊急連絡先一覧
 * └─ MedicalContactEdit (SCR-M-004)：緊急連絡先登録・編集
 *
 * 【Detail：利用者詳細 (カテゴリ別)】
 * ├─ HealthDetail (SCR-PH-001)：健康記録 (A)
 * │    └─ GraphExpansion (SCR-PH-003)：グラフ拡大
 * ├─ ConditionDetail (SCR-PC-001)：所見メモ (B)
 * │    ├─ PhotoPreview (SCR-PC-002)：写真撮影プレビュー
 * │    └─ PhotoFull (SCR-PC-003)：写真全画面
 * ├─ MedicationDetail (SCR-PM-001)：服薬管理 (C)
 * └─ BatchInput (SCR-PH-002)：健康一括入力
 *
 * 【Settings：設定・保守系】
 * ├─ Settings (SCR-S-001)：アプリ設定
 * ├─ AuditLog (SCR-S-002)：操作ログ
 * ├─ ArchiveManagement (SCR-S-003)：終了利用者管理
 * └─ UnassignedPhotos (SCR-S-004)：未割り当て写真管理
 */


////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * 編集画面からの実行結果を定義する Enum
 */
enum class EditResult {
    /** 新規登録成功 */
    ADDED,
    /** 更新成功 */
    UPDATED
}

/**
 * ナビゲーション結果受け渡し用のキー
 */
object NavigationKeys {
    /** 編集結果（ADDED / UPDATED）の伝搬用キー */
    const val PERSON_EDIT_RESULT = "person_edit_result"
    /** 編集された利用者の氏名（通知用）の伝搬用キー */
    const val PERSON_EDIT_NAME = "person_edit_name"
}

////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * CareMemo ナビゲーション目的地定義
 */
sealed interface Destination {

    /** 利用者一覧画面 */
    @Serializable
    object Main : Destination

    /** 利用者登録・編集画面 (personId が null または "_new" の場合は新規登録) */
    @Serializable
    data class PersonEdit(val personId: String? = null) : Destination

    /** 緊急連絡先一覧画面 */
    @Serializable
    data class MedicalContacts(val personId: String) : Destination

    /** 緊急連絡先登録・編集画面 (contactId が null の場合は新規登録) */
    @Serializable
    data class MedicalContactEdit(val personId: String, val contactId: String? = null) : Destination

    /** 利用者詳細画面：健康記録 (A) */
    @Serializable
    data class HealthDetail(val personId: String, val categoryName: String) : Destination

    /** 健康記録一括入力画面 */
    @Serializable
    data class BatchInput(val personId: String) : Destination

    /** グラフ拡大表示画面 */
    @Serializable
    data class GraphExpansion(val personId: String, val categoryName: String, val initialIndex: Int) : Destination

    /** 所見メモ関連画面のルート（ViewModel共有用） */
    @Serializable
    object ConditionDetailRoot : Destination

    /** 利用者詳細画面：所見メモ (B) */
    @Serializable
    data class ConditionDetail(val personId: String, val categoryName: String, val query: String? = null) : Destination

    /** 所見写真撮影プレビュー画面 */
    @Serializable
    data class PhotoPreview(val uri: String, val personId: String, val conditionId: String) : Destination

    /** 所見写真全画面表示画面 */
    @Serializable
    data class PhotoFull(val personId: String, val conditionId: String, val initialPhotoId: String) : Destination

    /** 利用者詳細画面：服薬管理 (C) */
    @Serializable
    data class MedicationDetail(val personId: String, val categoryName: String) : Destination

    /** アプリ設定画面 */
    @Serializable
    object Settings : Destination

    /** 監査ログ参照画面 */
    @Serializable
    object AuditLog : Destination

    /** 終了利用者管理画面 (mode: RESTORE / DELETE) */
    @Serializable
    data class ArchiveManagement(val mode: String) : Destination

    /** 未割り当て写真の確認・管理画面 */
    @Serializable
    object UnassignedPhotos : Destination
}


