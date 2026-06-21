package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 画像処理（リサイズ、回転補正、サムネイル作成、保存）を担当するユーティリティ
 */
object ImageUtils {
    private const val MAX_IMAGE_SIZE = 1024
    private const val THUMBNAIL_SIZE = 256
    private const val PHOTOS_DIR = "photos"

    /**
     * 撮影された画像をリサイズ・回転補正して保存する
     * @return 保存されたメイン画像とサムネイルのファイル名のペア。失敗時はnull。
     */
    fun processAndSaveImage(context: Context, inputUri: Uri): Pair<String, String>? {
        val photosDir = File(context.filesDir, PHOTOS_DIR)
        if (!photosDir.exists()) {
            if (!photosDir.mkdirs()) return null
        }

        val fileNameBase = UUID.randomUUID().toString()
        val originalFileName = "img_$fileNameBase.jpg"
        val thumbFileName = "thumb_$fileNameBase.jpg"

        val originalFile = File(photosDir, originalFileName)
        val thumbFile = File(photosDir, thumbFileName)

        try {
            // 1. 回転情報を取得（Exif）
            val rotation = getRotation(context, inputUri)

            // 2. 画像を適切なサイズで読み込む
            val bitmap = loadResizedBitmap(context, inputUri, MAX_IMAGE_SIZE) ?: return null
            
            // 3. 回転補正を適用
            val rotatedBitmap = rotateBitmap(bitmap, rotation)
            
            // 4. メイン画像を保存
            saveBitmapToFile(rotatedBitmap, originalFile, 85)

            // 5. サムネイルを作成して保存
            val thumbBitmap = createScaledBitmap(rotatedBitmap, THUMBNAIL_SIZE)
            saveBitmapToFile(thumbBitmap, thumbFile, 75)

            // メモリ解放（rotatedBitmapはメイン画像保存に使用。bitmapとは別インスタンスの場合がある）
            if (bitmap != rotatedBitmap) bitmap.recycle()
            rotatedBitmap.recycle()
            thumbBitmap.recycle()

            return Pair(originalFileName, thumbFileName)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
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
        } catch (e: Exception) {
            0
        }
    }

    private fun loadResizedBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? {
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
            createScaledBitmap(sampledBitmap, maxSize)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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
        val scale = maxSize.toFloat() / Math.max(width, height).toFloat()
        
        if (scale >= 1.0f) return bitmap // 既に小さい場合はそのまま

        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }

    /**
     * 写真保存ディレクトリを取得する
     */
    fun getPhotosDir(context: Context): File {
        val dir = File(context.filesDir, PHOTOS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 全ての写真ファイルを物理削除する
     */
    fun clearPhotosDir(context: Context) {
        val dir = File(context.filesDir, PHOTOS_DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 保存されている写真のフルパスを取得する
     */
    fun getPhotoFile(context: Context, fileName: String): File {
        return File(File(context.filesDir, PHOTOS_DIR), fileName)
    }

    /**
     * 物理ファイルを削除する
     */
    fun deleteImageFiles(context: Context, photoName: String?, thumbName: String?) {
        photoName?.let { File(File(context.filesDir, PHOTOS_DIR), it).delete() }
        thumbName?.let { File(File(context.filesDir, PHOTOS_DIR), it).delete() }
    }
}
