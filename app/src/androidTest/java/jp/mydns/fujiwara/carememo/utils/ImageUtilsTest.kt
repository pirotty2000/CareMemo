@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * ImageUtils Test based on TEST_SPEC_ImageUtils.md
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

    // --- 3.1. Image Processing (processAndSaveImage) ---

    @Test
    fun IMG_01_processAndSaveImage_success() = runBlocking {
        val dummyFile = File(context.cacheDir, "dummy_01.jpg")
        createDummyImageFile(dummyFile, 100, 100)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dummyFile)

        val result = ImageUtils.processAndSaveImage(context, uri)

        assertNotNull(result)
        assertTrue(File(ImageUtils.getPhotosDirPublic(context), result.first).exists())
        assertTrue(File(ImageUtils.getPhotosDirPublic(context), result.second).exists())
        dummyFile.delete()
        Unit
    }

    @Test
    fun IMG_02_processAndSaveImage_rotation_correction() = runBlocking {
        val dummyFile = File(context.cacheDir, "dummy_02.jpg")
        createDummyImageFile(dummyFile, 200, 100) // 横長
        
        // Exif で 90度回転情報を付与
        val exif = ExifInterface(dummyFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        exif.saveAttributes()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dummyFile)
        val result = ImageUtils.processAndSaveImage(context, uri)

        // 保存された画像を読み込んで、サイズ（縦横）が入れ替わっている（回転補正されている）ことを確認
        val savedFile = ImageUtils.getPhotoFile(context, result.first)
        val options = BitmapFactory.Options()
        BitmapFactory.decodeFile(savedFile.absolutePath, options)
        
        // 元が 200x100 で 90度回転なら、補正後は 100x200 になるはず
        assertTrue("Width should be smaller than height after 90 degree rotation correction", options.outWidth < options.outHeight)
        
        dummyFile.delete()
        Unit
    }

    @Test
    fun IMG_03_processAndSaveImage_resize() = runBlocking {
        val dummyFile = File(context.cacheDir, "dummy_03.jpg")
        // 上限を超える大きな画像を作成
        val maxSpec = AppSpecifications.Condition.Photo.MAX_SIZE_KB
        val largeSize = maxSpec + 500
        createDummyImageFile(dummyFile, largeSize, largeSize)
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dummyFile)
        val result = ImageUtils.processAndSaveImage(context, uri)

        val savedFile = ImageUtils.getPhotoFile(context, result.first)
        val options = BitmapFactory.Options()
        BitmapFactory.decodeFile(savedFile.absolutePath, options)

        assertTrue("Saved width should be <= MAX_SIZE_KB", options.outWidth <= maxSpec)
        assertTrue("Saved height should be <= MAX_SIZE_KB", options.outHeight <= maxSpec)
        
        dummyFile.delete()
        Unit
    }

    @Test
    fun IMG_04_processAndSaveImage_read_failure() = runBlocking {
        val invalidUri = Uri.parse("content://non.existent.provider/image.jpg")
        assertThrows(IOException::class.java) {
            runBlocking { ImageUtils.processAndSaveImage(context, invalidUri) }
        }
        Unit
    }

    @Test
    fun IMG_05_processAndSaveImage_save_failure() = runBlocking {
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        photosDir.deleteRecursively()
        photosDir.createNewFile() // ディレクトリの場所にファイルを置いて保存を失敗させる

        val dummyFile = File(context.cacheDir, "dummy_05.jpg")
        createDummyImageFile(dummyFile, 100, 100)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dummyFile)

        assertThrows(IOException::class.java) {
            runBlocking { ImageUtils.processAndSaveImage(context, uri) }
        }

        photosDir.delete()
        dummyFile.delete()
        Unit
    }

    // --- 3.2. File Operations ---

    @Test
    fun FIL_01_deleteImageFiles_success() = runBlocking {
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        val photoName = "test_photo.jpg"
        val thumbName = "test_thumb.jpg"
        val photoFile = File(photosDir, photoName)
        val thumbFile = File(photosDir, thumbName)
        photoFile.createNewFile()
        thumbFile.createNewFile()

        ImageUtils.deleteImageFiles(context, photoName, thumbName)

        assertFalse(photoFile.exists())
        assertFalse(thumbFile.exists())
        Unit
    }

    @Test
    fun FIL_02_deleteImageFiles_failure() = runBlocking {
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        val dir = File(photosDir, "non_empty_dir")
        dir.mkdirs()
        File(dir, "data.txt").createNewFile()

        assertThrows(IOException::class.java) {
            runBlocking { ImageUtils.deleteImageFiles(context, "non_empty_dir", null) }
        }
        dir.deleteRecursively()
        Unit
    }

    @Test
    fun FIL_03_clearPhotosDir_success() = runBlocking {
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        repeat(3) { File(photosDir, "file_$it.jpg").createNewFile() }
        
        ImageUtils.clearPhotosDir(context)

        assertEquals(0, photosDir.listFiles()?.size ?: 0)
        Unit
    }

    @Test
    fun FIL_04_clearPhotosDir_failure() = runBlocking {
        val photosDir = ImageUtils.getPhotosDirPublic(context)
        val subDir = File(photosDir, "sub")
        subDir.mkdirs()
        File(subDir, "data.txt").createNewFile()

        assertThrows(IOException::class.java) {
            runBlocking { ImageUtils.clearPhotosDir(context) }
        }
        subDir.deleteRecursively()
        Unit
    }

    // --- 3.3. Temp File Management ---

    @Test
    fun TMP_01_getTempPhotoUri_success() {
        val uri = ImageUtils.getTempPhotoUri(context)
        assertNotNull(uri)
        assertEquals("content", uri.scheme)
        assertTrue(uri.toString().contains(context.packageName + ".fileprovider"))
    }

    @Test
    fun TMP_02_clearOldTempPhotos_success() = runBlocking {
        val cacheDir = context.cacheDir
        val now = System.currentTimeMillis()
        val oldFile = File(cacheDir, "temp_capture_old.jpg")
        oldFile.createNewFile()
        oldFile.setLastModified(now - (25 * 60 * 60 * 1000L))

        val newFile = File(cacheDir, "temp_capture_new.jpg")
        newFile.createNewFile()
        newFile.setLastModified(now)

        val clearMethod = ImageUtils::class.java.getDeclaredMethod("clearOldTempPhotos", Context::class.java)
        clearMethod.isAccessible = true
        clearMethod.invoke(ImageUtils, context)

        assertFalse(oldFile.exists())
        assertTrue(newFile.exists())
        newFile.delete()
        Unit
    }

    // Helper
    private fun createDummyImageFile(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
    }
}
