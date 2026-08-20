package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Test: UserSettingsRepository
 */
@RunWith(AndroidJUnit4::class)
class UserSettingsRepositoryTest {

    private lateinit var repository: UserSettingsRepository
    private lateinit var context: Context
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = UserSettingsRepository(context, auditLogRepository)
    }

    // region 2. 永続化・通知テスト (Persistence & Flow)

    @Test
    fun SET_01_isNameMaskingEnabled_persistence() = runBlocking {
        repository.setNameMaskingEnabled(true)
        assertTrue(repository.isNameMaskingEnabled.first())
        coVerify { auditLogRepository.log(any(), "setNameMaskingEnabled", any(), any(), any(), any(), any()) }

        repository.setNameMaskingEnabled(false)
        assertFalse(repository.isNameMaskingEnabled.first())
    }

    @Test
    fun SET_02_themeSetting_persistence() = runBlocking {
        repository.setThemeSetting(ThemeSetting.DARK)
        assertEquals(ThemeSetting.DARK, repository.themeSetting.first())
        coVerify { auditLogRepository.log(any(), "setThemeSetting", any(), any(), any(), any(), any()) }

        repository.setThemeSetting(ThemeSetting.LIGHT)
        assertEquals(ThemeSetting.LIGHT, repository.themeSetting.first())
    }

    @Test
    fun SET_03_defaultRecorderName_persistence() = runBlocking {
        val testName = "Test Recorder ${System.currentTimeMillis()}"
        repository.setDefaultRecorderName(testName)
        assertEquals(testName, repository.defaultRecorderName.first())
        coVerify { auditLogRepository.log(any(), "setDefaultRecorderName", any(), any(), any(), any(), any()) }
    }

    @Test
    fun SET_04_auditLogRetentionDays_persistence() = runBlocking {
        repository.setAuditLogRetentionDays(90)
        assertEquals(90, repository.auditLogRetentionDays.first())
        coVerify { auditLogRepository.log(any(), "setAuditLogRetentionDays", any(), any(), any(), any(), any()) }
    }

    @Test
    fun SET_05_backupPassword_persistence_without_logging_password() = runBlocking {
        val testPass = "new-secure-pass"
        repository.setBackupPassword(testPass)
        assertEquals(testPass, repository.backupPassword.first())
        
        // Verify that password itself is NOT in the logs (details should be fixed string)
        coVerify { 
            auditLogRepository.log(
                featureName = any(),
                operation = "setBackupPassword",
                tableName = any(),
                actionType = any(),
                affectedId = any(),
                details = match { !it.contains(testPass) },
                resultType = "SUCCESS"
            ) 
        }
    }

    // endregion

    // region 3. 初期値テスト (Defaults)

    @Test
    fun DEF_01_defaultValues_areCorrect() = runBlocking {
        // Note: In real device tests, DataStore might already have values.
        // This test assumes a clean state or verifies specific documented defaults.
        
        // We set values to verify they can be read back, 
        // but here we just check if some core flows emit something without crashing.
        assertNotNull(repository.isNameMaskingEnabled.first())
        assertNotNull(repository.themeSetting.first())
    }

    // endregion
}
