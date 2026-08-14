package jp.mydns.fujiwara.carememo.utils

import android.content.Context
import android.graphics.Paint
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
import jp.mydns.fujiwara.carememo.ui.components.common.ExportRange
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
 * PdfExporter Test based on TEST_SPEC_PdfExporter.md
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
        birthday = Instant.now()
    )

    @Before
    fun setup() {
        // ApplicationContextをスパイ化し、startActivityを無効化する
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        context = spyk(appContext)
        every { context.startActivity(any()) } just runs

        PdfExporter.clearOldExports(context)
    }

    @After
    fun tearDown() {
        PdfExporter.clearOldExports(context)
    }

    // --- 3.1. PDF Generation and Sharing (exportAndShare) ---

    @Test
    fun PDF_01_exportAndShare_health_success() {
        runBlocking {
            val records = listOf(
                BpAndPulse(personId = "1", bpSystolic = 120, bpDiastolic = 80, pulse = 70, recordTime = Instant.now())
            )
            PdfExporter.exportAndShare(context, testPerson, Category.BP_AND_PULSE, records)
            
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_BP_AND_PULSE") }
            assertTrue("PDF file should be created in cache", files != null && files.isNotEmpty())
        }
    }

    @Test
    fun PDF_02_exportAndShare_condition_success() {
        runBlocking {
            val records = listOf(
                ConditionAtVisit(personId = "1", title = "Title", condition = "Memo", author = "Author", recordTime = Instant.now())
            )
            PdfExporter.exportAndShare(context, testPerson, Category.CONDITION_AT_VISIT, records)
            
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_CONDITION_AT_VISIT") }
            assertTrue(files != null && files.isNotEmpty())
        }
    }

    @Test
    fun PDF_03_exportAndShare_medication_success() {
        runBlocking {
            val records = listOf(
                MedicationRecord(personId = "1", dosageDate = "2023-10-01", timeSlot = 0, status = 2, recordTime = Instant.now())
            )
            PdfExporter.exportAndShare(context, testPerson, Category.MEDICATION, records)
            
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_MEDICATION") }
            assertTrue(files != null && files.isNotEmpty())
        }
    }

    @Test
    fun PDF_04_exportAndShare_filtering() {
        runBlocking {
            val now = Instant.now()
            val old = now.minus(java.time.Duration.ofDays(60))
            val records = listOf(
                BpAndPulse(personId = "1", bpSystolic = 120, recordTime = now),
                BpAndPulse(personId = "1", bpSystolic = 110, recordTime = old)
            )
            PdfExporter.exportAndShare(context, testPerson, Category.BP_AND_PULSE, records, range = ExportRange.ONE_MONTH)
            
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_BP_AND_PULSE") }
            assertTrue(files != null && files.isNotEmpty())
        }
    }

    @Test
    fun PDF_05_exportAndShare_password_protected() {
        runBlocking {
            val records = listOf(
                BpAndPulse(personId = "1", bpSystolic = 120, recordTime = Instant.now())
            )
            PdfExporter.exportAndShare(context, testPerson, Category.BP_AND_PULSE, records, password = "password123")
            
            val files = context.cacheDir.listFiles { _, name -> name.startsWith("CareMemo_BP_AND_PULSE") }
            assertTrue(files != null && files.isNotEmpty())
        }
    }

    @Test
    fun PDF_06_exportAndShare_empty_data_throws_exception() {
        runBlocking {
            val records = emptyList<Any>()
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    PdfExporter.exportAndShare(context, testPerson, Category.BP_AND_PULSE, records)
                }
            }
        }
    }

    // --- 3.2. Internal Utilities ---

    @Test
    fun UTL_01_clearOldExports_success() {
        runBlocking {
            val file1 = File(context.cacheDir, "CareMemo_test1.pdf")
            val file2 = File(context.cacheDir, "CareMemo_test2.pdf")
            file1.createNewFile()
            file2.createNewFile()
            
            PdfExporter.clearOldExports(context)
            
            assertFalse("Old PDF file 1 should be deleted", file1.exists())
            assertFalse("Old PDF file 2 should be deleted", file2.exists())
        }
    }

    @Test
    fun UTL_02_splitTextIntoLines_success() {
        val paint = Paint()
        paint.textSize = 10f
        val maxWidth = 100f
        val longText = "This is a very long text that should be split into multiple lines because it exceeds the maximum width."
        
        val method = PdfExporter::class.java.getDeclaredMethod("splitTextIntoLines", String::class.java, Paint::class.java, Float::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(PdfExporter, longText, paint, maxWidth) as List<String>
        
        assertTrue("Text should be split into multiple lines", result.size > 1)
        result.forEach { line ->
            assertTrue("Each line should be within maxWidth", paint.measureText(line) <= maxWidth)
        }
    }
}
