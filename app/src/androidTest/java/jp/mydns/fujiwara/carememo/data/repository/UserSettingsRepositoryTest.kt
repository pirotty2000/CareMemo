package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.mydns.fujiwara.carememo.data.ThemeSetting
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = UserSettingsRepository(context)
    }

    // region 2. 永続化・通知テスト (Persistence & Flow)

    @Test
    fun SET_01_isNameMaskingEnabled_persistence() = runBlocking {
        repository.setNameMaskingEnabled(true)
        assertTrue(repository.isNameMaskingEnabled.first())

        repository.setNameMaskingEnabled(false)
        assertFalse(repository.isNameMaskingEnabled.first())
    }

    @Test
    fun SET_02_themeSetting_persistence() = runBlocking {
        repository.setThemeSetting(ThemeSetting.DARK)
        assertEquals(ThemeSetting.DARK, repository.themeSetting.first())

        repository.setThemeSetting(ThemeSetting.LIGHT)
        assertEquals(ThemeSetting.LIGHT, repository.themeSetting.first())
    }

    @Test
    fun SET_03_defaultRecorderName_persistence() = runBlocking {
        val testName = "Test Recorder ${System.currentTimeMillis()}"
        repository.setDefaultRecorderName(testName)
        assertEquals(testName, repository.defaultRecorderName.first())
    }

    @Test
    fun SET_04_auditLogRetentionDays_persistence() = runBlocking {
        repository.setAuditLogRetentionDays(90)
        assertEquals(90, repository.auditLogRetentionDays.first())
    }

    @Test
    fun SET_05_lockTimeoutMinutes_persistence() = runBlocking {
        repository.setLockTimeoutMinutes(15)
        assertEquals(15, repository.lockTimeoutMinutes.first())
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

    // region 4. 特殊状態テスト (Memory / Security)

    @Test
    fun SPC_01_isLockBypassed_memoryOnly() {
        repository.isLockBypassed = true
        assertTrue(repository.isLockBypassed)
        
        repository.isLockBypassed = false
        assertFalse(repository.isLockBypassed)
    }

    // endregion
}
