package jp.mydns.fujiwara.carememo.utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ファイルやフォルダのZip圧縮・解凍を行うユーティリティ
 */
object ZipUtils {

    /**
     * 指定されたファイルやフォルダをZip圧縮する
     */
    fun zip(files: List<File>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            files.forEach { file ->
                zipFileOrDirectory(zos, file, "")
            }
        }
    }

    private fun zipFileOrDirectory(zos: ZipOutputStream, file: File, parentPath: String) {
        val path = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            val entries = file.listFiles()
            if (entries != null && entries.isNotEmpty()) {
                entries.forEach { entry ->
                    zipFileOrDirectory(zos, entry, path)
                }
            } else {
                // 空のディレクトリ
                zos.putNextEntry(ZipEntry("$path/"))
                zos.closeEntry()
            }
        } else {
            BufferedInputStream(FileInputStream(file)).use { bis ->
                zos.putNextEntry(ZipEntry(path))
                bis.copyTo(zos)
                zos.closeEntry()
            }
        }
    }

    /**
     * Zipファイルを指定されたディレクトリに解凍する
     */
    fun unzip(zipFile: File, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
