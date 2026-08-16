package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import jp.mydns.fujiwara.carememo.data.AppSpecifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Utility：ImageUtils
 *
 * 【役割】
 * 写真撮影やギャラリー選択された画像に対する「加工・保存」を一手に引き受けるユーティリティです。
 * メモリ使用量を抑えつつ、適切なリサイズ、回転補正、およびサムネイル生成を行います。
 *
 * 【主な機能】
 * ・リサイズ・圧縮：指定された最大ファイルサイズ（MAX_SIZE_KB）に収まるようビットマップを最適化。
 * ・回転補正：Exif 情報（撮影時の向き）を解析し、垂直・水平方向を正しく修正。
 * ・プライバシー保護：保存時に Exif 情報（GPS 位置情報、デバイス情報等）を自動的に除去し、外部共有時の安全性を確保。
 * ・サムネイル生成：高速表示用の小型画像をメイン画像とペアで作成。
 * ・保守管理：一時ファイルの掃除、不要な画像ファイルの物理削除。
 * ・撮影日時取得：MediaStore または Exif から正確な撮影時刻を抽出。
 *
 * 【全体像：画像加工フロー】
 *
 * 1. [ 取得 ] Uri からの Exif 解析 (回転方向・日時)
 * 2. [ 読込 ] サンプリング（inSampleSize）によるメモリ節約読込
 * 3. [ 加工 ] BitMap 回転 ➔ 精密リサイズ (createScaledBitmap)
 * 4. [ 保存 ] メイン画像保存 (品質85%) ➔ サムネイル生成・保存 (品質75%)
 * 5. [ 解放 ] 使用済み Bitmap の recycle() を徹底
 *
 * 【このコンポーネントでは行わないこと】
 * ・UI 上での画像表示（表示は Coil 等のライブラリに委譲）。
 */
object ImageUtils {

    /**
     * 写真を保存するディレクトリを取得する（遅延初期化的に振る舞う）
     */
    private fun getPhotosDir(context: Context): File {
        return File(context.filesDir, AppSpecifications.Condition.Photo.DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 撮影された画像をリサイズ・回転補正して保存する。
     * @return 保存されたメイン画像とサムネイルのファイル名のペア。
     * @throws IOException 画像の読み込み、加工、または保存に失敗した場合
     */
    suspend fun processAndSaveImage(context: Context, inputUri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
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
            sourceBitmap = loadResizedBitmap(context, inputUri) ?: throw IOException("画像の読み込みに失敗しました。")
            
            // 3. 回転補正を適用
            rotatedBitmap = rotateBitmap(sourceBitmap, rotation)
            
            // 4. メイン画像を保存
            saveBitmapToFile(rotatedBitmap, originalFile, 85)

            // 5. サムネイルを作成して保存
            thumbBitmap = createScaledBitmap(rotatedBitmap, AppSpecifications.Condition.Photo.THUMBNAIL_SIZE_PX)
            saveBitmapToFile(thumbBitmap, thumbFile, 75)

            Pair(originalFileName, thumbFileName)
        } finally {
            // メモリ解放を確実に行う
            sourceBitmap?.let { if (!it.isRecycled && it != rotatedBitmap) it.recycle() }
            rotatedBitmap?.let { if (!it.isRecycled) it.recycle() }
            thumbBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    /**
     * 画像の撮影日時を取得する。
     * MediaStore からの取得を優先し、失敗した場合は Exif メタデータからの取得を試みる。
     * @return 撮影日時のミリ秒、取得できなかった場合は null
     */
    fun getCaptureTime(context: Context, uri: Uri): Long? {
        // 1. MediaStore からの取得試行 (ギャラリー等)
        try {
            val projection = arrayOf(android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateTakenIndex = cursor.getColumnIndex(android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN)
                    if (dateTakenIndex != -1) {
                        val dateTaken = cursor.getLong(dateTakenIndex)
                        if (dateTaken > 0) return dateTaken
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        // 2. ExifInterface からの取得試行 (直接ファイル等)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val dateTimeStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                if (dateTimeStr != null) {
                    val format = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    return format.parse(dateTimeStr)?.time
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        return null
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
        val maxSize = AppSpecifications.Condition.Photo.MAX_SIZE_KB
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
        return scaled
    }

    @Suppress("SameParameterValue")
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
     * @throws IOException 削除に失敗した場合
     */
    suspend fun clearPhotosDir(context: Context) = withContext(Dispatchers.IO) {
        val dir = getPhotosDir(context)
        if (dir.exists()) {
            dir.listFiles()?.forEach { 
                if (!it.delete()) throw IOException("ファイル ${it.name} の削除に失敗しました。")
            }
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
     * @throws IOException 削除に失敗した場合
     */
    suspend fun deleteImageFiles(context: Context, photoName: String?, thumbName: String?) = withContext(Dispatchers.IO) {
        val dir = getPhotosDir(context)
        photoName?.let { 
            val file = File(dir, it)
            if (file.exists() && !file.delete()) throw IOException("ファイル $it の削除に失敗しました。")
        }
        thumbName?.let { 
            val file = File(dir, it)
            if (file.exists() && !file.delete()) throw IOException("ファイル $it の削除に失敗しました。")
        }
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
