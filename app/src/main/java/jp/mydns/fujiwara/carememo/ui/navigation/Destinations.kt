package jp.mydns.fujiwara.carememo.ui.navigation

import kotlinx.serialization.Serializable

/**
 * CareMemo ナビゲーション目的地定義
 */
sealed interface Destination {

    @Serializable
    object Main : Destination

    @Serializable
    data class PersonEdit(val personId: String? = null) : Destination

    @Serializable
    data class MedicalContacts(val personId: String) : Destination

    @Serializable
    data class MedicalContactEdit(val personId: String, val contactId: String? = null) : Destination

    @Serializable
    data class HealthDetail(val personId: String, val categoryName: String) : Destination

    @Serializable
    data class BatchInput(val personId: String) : Destination

    @Serializable
    data class GraphExpansion(val personId: String, val categoryName: String, val initialIndex: Int) : Destination

    @Serializable
    data class ConditionDetail(val personId: String, val categoryName: String, val query: String? = null) : Destination

    @Serializable
    data class PhotoPreview(val uri: String, val personId: String, val conditionId: String) : Destination

    @Serializable
    data class PhotoFull(val conditionId: String, val initialPhotoId: String) : Destination

    @Serializable
    data class MedicationDetail(val personId: String, val categoryName: String) : Destination

    @Serializable
    object Settings : Destination

    @Serializable
    object AuditLog : Destination

    @Serializable
    data class ArchiveManagement(val mode: String) : Destination

    @Serializable
    object OrphanedPhotos : Destination
}
