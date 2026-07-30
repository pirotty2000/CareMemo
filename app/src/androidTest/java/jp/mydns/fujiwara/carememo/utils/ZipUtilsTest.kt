@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * ZipUtils Test based on TEST_SPEC_ZipUtils.md
 */
@RunWith(AndroidJUnit4::class)
class ZipUtilsTest {
    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var outputZip: File
    private lateinit var extractDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val cacheDir: File = context.cacheDir!!
        testDir = File(cacheDir, "zip_test_src")
        outputZip = File(cacheDir, "test_output.zip")
        extractDir = File(cacheDir, "zip_test_extract")
        
        testDir.mkdirs()
        extractDir.mkdirs()
        outputZip.delete()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
        extractDir.deleteRecursively()
        outputZip.delete()
    }

    // --- 3.1. Compression (zip) ---

    @Test
    fun ZIP_01_zip_no_password_success() = runBlocking {
        File(testDir, "file1.txt").apply { writeText("Content 1") }
        val subDir = File(testDir, "sub").apply { mkdirs() }
        File(subDir, "file2.txt").apply { writeText("Content 2") }

        ZipUtils.zip(listOf(testDir), outputZip, null)

        assertTrue("Zip file should be created", outputZip.exists())
    }

    @Test
    fun ZIP_02_zip_with_password_success() = runBlocking {
        File(testDir, "secret.txt").apply { writeText("Secret Content") }

        ZipUtils.zip(listOf(testDir), outputZip, "password123")

        assertTrue(outputZip.exists())
        assertTrue("File should be encrypted", ZipUtils.isEncrypted(outputZip))
    }

    @Test
    fun ZIP_03_zip_progress_notification() = runBlocking {
        File(testDir, "large.txt").apply { writeText("A".repeat(1000)) }
        var progressCalled = false

        ZipUtils.zip(listOf(testDir), outputZip, null) { progress ->
            assertTrue(progress in 0..100)
            progressCalled = true
        }

        assertTrue("Progress callback should be invoked", progressCalled)
    }

    @Test
    fun ZIP_04_zip_invalid_input_throws_exception() = runBlocking {
        val cacheDir: File = context.cacheDir!!
        val nonExistentFile = File(cacheDir, "none_existent_file_12345")
        
        // Zip4j throws exception if input file doesn't exist
        assertThrows(Exception::class.java) {
            runBlocking {
                ZipUtils.zip(listOf(nonExistentFile), outputZip, null)
            }
        }
    }

    @Test
    fun ZIP_05_zip_output_failure_throws_exception() = runBlocking {
        // Create a directory where the zip file should be to block creation
        outputZip.mkdirs() 

        assertThrows(IOException::class.java) {
            runBlocking {
                ZipUtils.zip(listOf(testDir), outputZip, null)
            }
        }
    }

    // --- 3.2. Decompression (unzip) ---

    @Test
    fun UNZ_01_unzip_no_password_success() = runBlocking {
        val originalText = "Original Content"
        File(testDir, "test.txt").apply { writeText(originalText) }
        ZipUtils.zip(listOf(testDir), outputZip, null)

        ZipUtils.unzip(outputZip, extractDir, null)

        val extractedFile = File(extractDir, "zip_test_src/test.txt")
        assertTrue(extractedFile.exists())
        assertEquals(originalText, extractedFile.readText())
    }

    @Test
    fun UNZ_02_unzip_with_password_success() = runBlocking {
        val secretText = "Secret Data"
        File(testDir, "secret.txt").apply { writeText(secretText) }
        ZipUtils.zip(listOf(testDir), outputZip, "pass123")

        ZipUtils.unzip(outputZip, extractDir, "pass123")

        val extractedFile = File(extractDir, "zip_test_src/secret.txt")
        assertTrue(extractedFile.exists())
        assertEquals(secretText, extractedFile.readText())
    }

    @Test
    fun UNZ_03_unzip_wrong_password_throws_exception() = runBlocking {
        File(testDir, "data.txt").apply { writeText("Data") }
        ZipUtils.zip(listOf(testDir), outputZip, "correct_pass")

        assertThrows(Exception::class.java) {
            runBlocking {
                ZipUtils.unzip(outputZip, extractDir, "wrong_pass")
            }
        }
    }

    @Test
    fun UNZ_04_unzip_corrupted_file_throws_exception() = runBlocking {
        outputZip.writeText("Not a zip content")

        assertThrows(Exception::class.java) {
            runBlocking {
                ZipUtils.unzip(outputZip, extractDir, null)
            }
        }
    }

    // --- 3.3. Verification (isEncrypted / isValidPassword) ---

    @Test
    fun CHK_01_isEncrypted_detection() = runBlocking {
        File(testDir, "1.txt").apply { writeText("1") }
        
        // No password
        ZipUtils.zip(listOf(testDir), outputZip, null)
        assertFalse(ZipUtils.isEncrypted(outputZip))
        
        // With password
        outputZip.delete()
        ZipUtils.zip(listOf(testDir), outputZip, "pw")
        assertTrue(ZipUtils.isEncrypted(outputZip))
    }

    @Test
    fun CHK_02_isValidPassword_correct() = runBlocking {
        File(testDir, "data.txt").apply { writeText("data") }
        ZipUtils.zip(listOf(testDir), outputZip, "mypass")

        assertTrue(ZipUtils.isValidPassword(outputZip, "mypass"))
    }

    @Test
    fun CHK_03_isValidPassword_incorrect() = runBlocking {
        File(testDir, "data.txt").apply { writeText("data") }
        ZipUtils.zip(listOf(testDir), outputZip, "mypass")

        assertFalse(ZipUtils.isValidPassword(outputZip, "wrong"))
    }

    @Test
    fun CHK_04_isValidPassword_fatal_error_throws_exception() = runBlocking {
        val cacheDir: File = context.cacheDir!!
        val nonExistent = File(cacheDir, "not_found.zip")

        assertThrows(IOException::class.java) {
            runBlocking {
                ZipUtils.isValidPassword(nonExistent, "any")
            }
        }
    }
}
