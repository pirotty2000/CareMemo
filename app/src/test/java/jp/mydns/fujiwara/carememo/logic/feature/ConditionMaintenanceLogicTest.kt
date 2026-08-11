package jp.mydns.fujiwara.carememo.logic.feature

import io.mockk.every
import io.mockk.mockk
import jp.mydns.fujiwara.carememo.R
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

    // region 2. 迷子写真判定テスト (identifyOrphanedPhotos)

    @Test
    fun MAIN_01_identifyOrphanedPhotos_allNormal() {
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

        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(dbPhotos, conditionIds, files)
        
        assertTrue(orphaned.isEmpty())
    }

    @Test
    fun MAIN_02_identifyOrphanedPhotos_temporary() {
        val dbPhotos = listOf(
            ConditionPhoto(
                id = "p1",
                conditionId = "",
                personId = "u1",
                photoFileName = "img_1.jpg",
                thumbnailFileName = "thumb_1.jpg",
                capturedAt = now
            )
        )
        val conditionIds = emptySet<String>()
        val files = listOf(File("img_1.jpg"))

        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(dbPhotos, conditionIds, files)
        
        assertEquals(1, orphaned.size)
        assertEquals(OrphanedPhotoType.TEMPORARY, orphaned[0].type)
        assertEquals(R.string.orphaned_photo_type_temporary, orphaned[0].descriptionResId)
    }

    @Test
    fun MAIN_03_identifyOrphanedPhotos_orphanedRecord() {
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

        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(dbPhotos, conditionIds, files)
        
        assertEquals(1, orphaned.size)
        assertEquals(OrphanedPhotoType.ORPHANED_RECORD, orphaned[0].type)
        assertEquals(R.string.orphaned_photo_type_orphaned, orphaned[0].descriptionResId)
    }

    @Test
    fun MAIN_04_identifyOrphanedPhotos_fileOnly() {
        val dbPhotos = emptyList<ConditionPhoto>()
        val conditionIds = emptySet<String>()
        
        val mockFile = mockk<File>()
        every { mockFile.name } returns "img_orphaned.jpg"
        every { mockFile.lastModified() } returns 1000L
        
        val files = listOf(mockFile)

        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(dbPhotos, conditionIds, files)
        
        assertEquals(1, orphaned.size)
        assertEquals(OrphanedPhotoType.FILE_ONLY, orphaned[0].type)
        assertEquals("img_orphaned.jpg", orphaned[0].photoFileName)
        assertEquals(R.string.orphaned_photo_db_unregistered, orphaned[0].descriptionResId)
    }

    @Test
    fun MAIN_05_identifyOrphanedPhotos_sorting() {
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
        
        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(dbPhotos, emptySet(), emptyList())
        
        assertEquals(2, orphaned.size)
        assertEquals("img_new.jpg", orphaned[0].photoFileName) // 降順
        assertEquals("img_old.jpg", orphaned[1].photoFileName)
    }

    @Test
    fun MAIN_06_identifyOrphanedPhotos_thumbnailExcluded() {
        val dbPhotos = emptyList<ConditionPhoto>()
        val files = listOf(File("thumb_1.jpg")) // サムネイルのみ

        val orphaned = ConditionMaintenanceLogic.identifyOrphanedPhotos(dbPhotos, emptySet(), files)
        
        assertTrue(orphaned.isEmpty())
    }

    // endregion
}
