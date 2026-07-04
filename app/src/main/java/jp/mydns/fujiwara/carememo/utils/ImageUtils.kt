package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import jp.mydns.fujiwara.carememo.data.AppThresholds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 画像処理（リサイズ、回転補正、サムネイル作成、保存）を担当するユーティリティ。
 * 重い処理は Dispatchers.IO で実行され、メモリ管理に配慮している。
 */
object ImageUtils {

    /**
     * 写真を保存するディレクトリを取得する（遅延初期化的に振る舞う）
     */
    private fun getPhotosDir(context: Context): File {
        return File(context.filesDir, AppThresholds.PHOTOS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 撮影された画像をリサイズ・回転補正して保存する。
     * @return 保存されたメイン画像とサムネイルのファイル名のペア。失敗時はnull。
     */
    suspend fun processAndSaveImage(context: Context, inputUri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        // 処理開始前に古い一時ファイルを掃除
        clearOldTempPhotos(context)

        val photosDir = getPhotosDir(context)
        val fileNameBase = UUID.randomUUID().toString()
        val originalFileName = "img_$fileNameBase.jpg"
        val thumbFileName = "thumb_$fileNameBase.jpg"

        val originalFile = File(photosDir, originalFileName)
        val thumbFile = File(photosDir, thumbFileName)

        var sourceBitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null
        var thumbBitmap: Bitmap? = null

        try {
            // 1. 回転情報を取得（Exif）
            val rotation = getRotation(context, inputUri)

            // 2. 画像を適切なサイズで読み込む
            sourceBitmap = loadResizedBitmap(context, inputUri) ?: return@withContext null
            
            // 3. 回転補正を適用
            rotatedBitmap = rotateBitmap(sourceBitmap, rotation)
            
            // 4. メイン画像を保存
            saveBitmapToFile(rotatedBitmap, originalFile, 85)

            // 5. サムネイルを作成して保存
            thumbBitmap = createScaledBitmap(rotatedBitmap, AppThresholds.IMAGE_THUMBNAIL_SIZE)
            saveBitmapToFile(thumbBitmap, thumbFile, 75)

            Pair(originalFileName, thumbFileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            // メモリ解放を確実に行う
            sourceBitmap?.let { if (!it.isRecycled && it != rotatedBitmap) it.recycle() }
            rotatedBitmap?.let { if (!it.isRecycled) it.recycle() }
            thumbBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun getRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun loadResizedBitmap(context: Context, uri: Uri): Bitmap? {
        val maxSize = AppThresholds.IMAGE_MAX_SIZE
        return try {
            // サイズ計測
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            // サンプリングレート計算
            options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
            options.inJustDecodeBounds = false

            // 実際の読み込み
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: return null

            // 指定サイズに正確にリサイズ
            val scaled = createScaledBitmap(sampledBitmap, maxSize)
            if (scaled != sampledBitmap) sampledBitmap.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun createScaledBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scale = maxSize.toFloat() / width.coerceAtLeast(height).toFloat()

        if (scale >= 1.0f) return bitmap

        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()

        return bitmap.scale(targetWidth, targetHeight)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { out ->
            // Bitmap.compressを実行すると、ピクセルデータのみが書き出され、
            // 元のファイルに含まれていたExif情報（GPS位置情報、端末情報など）は自動的に破棄される。
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }

    /**
     * 写真保存ディレクトリを取得する（公開用）
     */
    fun getPhotosDirPublic(context: Context): File = getPhotosDir(context)

    /**
     * 全ての写真ファイルを物理削除する。
     */
    suspend fun clearPhotosDir(context: Context) = withContext(Dispatchers.IO) {
        val dir = getPhotosDir(context)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 保存されている写真のフルパスを取得する。
     */
    fun getPhotoFile(context: Context, fileName: String): File {
        return File(getPhotosDir(context), fileName)
    }

    /**
     * 物理ファイルを削除する。
     */
    suspend fun deleteImageFiles(context: Context, photoName: String?, thumbName: String?) = withContext(Dispatchers.IO) {
        val dir = getPhotosDir(context)
        photoName?.let { File(dir, it).delete() }
        thumbName?.let { File(dir, it).delete() }
    }

    /**
     * カメラ撮影用の一時ファイルURIを取得する。
     */
    fun getTempPhotoUri(context: Context): Uri {
        val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    /**
     * 24時間以上経過した一時ファイルを掃除する。
     */
    private fun clearOldTempPhotos(context: Context) {
        try {
            val now = System.currentTimeMillis()
            val dayInMillis = 24 * 60 * 60 * 1000L
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_capture_") && (now - file.lastModified() > dayInMillis)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
