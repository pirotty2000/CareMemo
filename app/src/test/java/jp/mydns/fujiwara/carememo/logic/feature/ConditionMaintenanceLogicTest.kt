package jp.mydns.fujiwara.carememo.logic.feature

import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Logic層テスト：ConditionMaintenanceLogic
 */
class ConditionMaintenanceLogicTest {

    private val now = Instant.now()

    // region 2. 未割り当て写真判定テスト (identifyUnassignedPhotos)

    @Test
    fun MAIN_01_identifyUnassignedPhotos_allNormal() {
        val dbPhotos = listOf(
            ConditionPhoto(
                id = "p1",
                conditionId = "c1",
                personId = "u1",
                photoFileName = "img_1.jpg",
                thumbnailFileName = "thumb_1.jpg",
                capturedAt = now
            )
        )
        val conditionIds = setOf("c1")
        val files = listOf(File("img_1.jpg"))

        val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(dbPhotos, conditionIds, files)
        
        assertTrue(unassigned.isEmpty())
    }

    @Test
    fun MAIN_02_identifyUnassignedPhotos_temporary() {
        val dbPhotos = listOf(
            ConditionPhoto(
                id = "p1",
                conditionId = AppSpecifications.Id.NEW_RECORD_ID,
                personId = "u1",
                photoFileName = "img_1.jpg",
                thumbnailFileName = "thumb_1.jpg",
                capturedAt = now
            )
        )
        val conditionIds = emptySet<String>()
        val files = listOf(File("img_1.jpg"))

        val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(dbPhotos, conditionIds, files)
        
        assertEquals(1, unassigned.size)
        assertEquals(UnassignedPhotoType.TEMPORARY, unassigned[0].type)
        assertEquals(R.string.unassigned_photo_type_temporary, unassigned[0].descriptionResId)
    }

    @Test
    fun MAIN_03_identifyUnassignedPhotos_unassignedRecord() {
        val dbPhotos = listOf(
            ConditionPhoto(
                id = "p1",
                conditionId = "deleted-c",
                personId = "u1",
                photoFileName = "img_1.jpg",
                thumbnailFileName = "thumb_1.jpg",
                capturedAt = now
            )
        )
        val conditionIds = setOf("existing-c") // deleted-c は含まれない
        val files = listOf(File("img_1.jpg"))

        val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(dbPhotos, conditionIds, files)
        
        assertEquals(1, unassigned.size)
        assertEquals(UnassignedPhotoType.UNASSIGNED_RECORD, unassigned[0].type)
        assertEquals(R.string.unassigned_photo_type_unassigned, unassigned[0].descriptionResId)
    }

    @Test
    fun MAIN_04_identifyUnassignedPhotos_fileOnly() {
        val dbPhotos = emptyList<ConditionPhoto>()
        val conditionIds = emptySet<String>()
        
        val mockFile = mockk<File>()
        every { mockFile.name } returns "img_unassigned.jpg"
        every { mockFile.lastModified() } returns 1000L
        
        val files = listOf(mockFile)

        val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(dbPhotos, conditionIds, files)
        
        assertEquals(1, unassigned.size)
        assertEquals(UnassignedPhotoType.FILE_ONLY, unassigned[0].type)
        assertEquals("img_unassigned.jpg", unassigned[0].photoFileName)
        assertEquals(R.string.unassigned_photo_db_unregistered, unassigned[0].descriptionResId)
    }

    @Test
    fun MAIN_05_identifyUnassignedPhotos_sorting() {
        val t1 = now
        val t2 = now.plusSeconds(3600)
        
        val dbPhotos = listOf(
            ConditionPhoto(
                id = "p1",
                conditionId = "",
                personId = "u1",
                photoFileName = "img_old.jpg",
                thumbnailFileName = "thumb_old.jpg",
                capturedAt = t1
            ),
            ConditionPhoto(
                id = "p2",
                conditionId = "",
                personId = "u1",
                photoFileName = "img_new.jpg",
                thumbnailFileName = "thumb_new.jpg",
                capturedAt = t2
            )
        )
        
        val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(dbPhotos, emptySet(), emptyList())
        
        assertEquals(2, unassigned.size)
        assertEquals("img_new.jpg", unassigned[0].photoFileName) // 降順
        assertEquals("img_old.jpg", unassigned[1].photoFileName)
    }

    @Test
    fun MAIN_06_identifyUnassignedPhotos_thumbnailExcluded() {
        val dbPhotos = emptyList<ConditionPhoto>()
        val files = listOf(File("thumb_1.jpg")) // サムネイルのみ

        val unassigned = ConditionMaintenanceLogic.identifyUnassignedPhotos(dbPhotos, emptySet(), files)
        
        assertTrue(unassigned.isEmpty())
    }

    // endregion
}
