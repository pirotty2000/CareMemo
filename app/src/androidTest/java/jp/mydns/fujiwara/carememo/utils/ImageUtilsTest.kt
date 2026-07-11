@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 画像・ファイル操作の物理的な挙動を検証するテスト。
 */
@RunWith(AndroidJUnit4::class)
class ImageUtilsTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            ImageUtils.clearPhotosDir(context)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            ImageUtils.clearPhotosDir(context)
        }
    }

    @Test
    fun ファイル削除_指定したメイン画像とサムネイルが物理削除されること() = runBlocking {
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        val photoName = "test_photo.jpg"
        val thumbName = "test_thumb.jpg"
        
        val photoFile = File(photosDir, photoName)
        val thumbFile = File(photosDir, thumbName)
        
        // ダミーファイル作成
        photoFile.createNewFile()
        thumbFile.createNewFile()
        assertTrue(photoFile.exists())
        assertTrue(thumbFile.exists())

        // 削除実行
        ImageUtils.deleteImageFiles(context, photoName, thumbName)

        // 削除確認
        assertFalse("メイン画像が削除されていること", photoFile.exists())
        assertFalse("サムネイルが削除されていること", thumbFile.exists())
    }

    @Test
    fun clearPhotosDir_ディレクトリ内の全ファイルが削除されること() = runBlocking {
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        repeat(5) { i ->
            File(photosDir, "file_$i.jpg").createNewFile()
        }
        assertEquals(5, photosDir.listFiles()?.size ?: 0)

        ImageUtils.clearPhotosDir(context)

        assertEquals(0, photosDir.listFiles()?.size ?: 0)
    }

    @Test
    fun 一時ファイルの掃除_24時間以上前のファイルだけが削除されること() = runBlocking {
        val cacheDir = context.cacheDir
        val now = System.currentTimeMillis()
        val oldTime = now - (25 * 60 * 60 * 1000L) // 25時間前
        
        val oldFile = File(cacheDir, "temp_capture_old.jpg")
        oldFile.createNewFile()
        oldFile.setLastModified(oldTime)

        val newFile = File(cacheDir, "temp_capture_new.jpg")
        newFile.createNewFile()
        newFile.setLastModified(now)

        // 代替案：ImageUtilsのロジックを手動で模倣して検証するか、リフレクションを使用
        val clearMethod = ImageUtils::class.java.getDeclaredMethod("clearOldTempPhotos", Context::class.java)
        clearMethod.isAccessible = true
        clearMethod.invoke(ImageUtils, context)

        assertFalse("24時間以上前のファイルは削除されること", oldFile.exists())
        assertTrue("新しいファイルは維持されること", newFile.exists())
        
        newFile.delete()
        Unit // 明示的に Unit を返して JUnit4 の制約 (void return) を満たす
    }
}
