package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.spyk
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.data.Person
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * PdfExporter Test
 */
@RunWith(AndroidJUnit4::class)
class PdfExporterTest {
    private lateinit var context: Context
    private val testPerson = Person(
        id = "1",
        lastName = "テスト",
        firstName = "太郎",
        lastNameFurigana = "てすと",
        firstNameFurigana = "たろう",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    @Before
    fun setup() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        context = spyk(appContext)
        every { context.startActivity(any()) } just runs

        PdfExporter.clearOldExports(context)
    }

    @After
    fun tearDown() {
        PdfExporter.clearOldExports(context)
    }

    @Test
    fun PDF_01_exportAndShare_health_success() = runBlocking {
        val records = listOf(
            BpAndPulse(personId = "1", bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
        )
        PdfExporter.exportAndShare(context, testPerson, Category.BP_AND_PULSE, records)
        
        val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_BP_AND_PULSE") }
        assertTrue("PDF file should be created in cache", files != null && files.isNotEmpty())
    }

    @Test
    fun PDF_02_exportAndShare_condition_success() = runBlocking {
        val records = listOf(
            ConditionAtVisit(personId = "1", title = "Title", condition = "Memo", author = "Author", recordTime = Instant.now())
        )
        PdfExporter.exportAndShare(context, testPerson, Category.CONDITION_AT_VISIT, records)
        
        val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_CONDITION_AT_VISIT") }
        assertTrue(files != null && files.isNotEmpty())
    }

    @Test
    fun PDF_03_exportAndShare_medication_success() = runBlocking {
        val records = listOf(
            MedicationRecord(personId = "1", dosageDate = "2023-10-01", timeSlot = 0, status = 2, recordTime = Instant.now())
        )
        PdfExporter.exportAndShare(context, testPerson, Category.MEDICATION, records)
        
        val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_MEDICATION") }
        assertTrue(files != null && files.isNotEmpty())
    }

    @Test
    fun PDF_05_exportAndShare_password_protected() = runBlocking {
        val records = listOf(
            BpAndPulse(personId = "1", bpSystolic = 120, recordTime = Instant.now())
        )
        PdfExporter.exportAndShare(context, testPerson, Category.BP_AND_PULSE, records, password = "password123")
        
        val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_BP_AND_PULSE") }
        assertTrue(files != null && files.isNotEmpty())
    }

    @Test
    fun PDF_06_exportAndShare_empty_data_throws_exception() {
        val records = emptyList<Any>()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                PdfExporter.exportAndShare(context, testPerson, Category.BP_AND_PULSE, records)
            }
        }
    }

    @Test
    fun UTL_01_clearOldExports_success() = runBlocking {
        val file1 = File(context.cacheDir, "CareMemo_test1.pdf")
        val file2 = File(context.cacheDir, "CareMemo_test2.pdf")
        file1.createNewFile()
        file2.createNewFile()
        
        PdfExporter.clearOldExports(context)
        
        assertFalse("Old PDF file 1 should be deleted", file1.exists())
        assertFalse("Old PDF file 2 should be deleted", file2.exists())
    }
}
