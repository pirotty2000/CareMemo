package jp.mydns.fujiwara.carememo.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.mydns.fujiwara.carememo.data.ThemeSetting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ユーザー設定の永続化とFlowによる通知を検証するテスト。
 */
@RunWith(AndroidJUnit4::class)
class UserSettingsRepositoryTest {

    private lateinit var repository: UserSettingsRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = UserSettingsRepository(context)
        // テスト前にデフォルト値に戻すか、テスト専用のDataStore名を使いたいが、
        // 既存のコードを活かすため、各テストで値をセットして検証する。
    }

    @Test
    fun 氏名伏せ字設定_保存した値がFlowから即座に取得できること() = runBlocking {
        repository.setNameMaskingEnabled(true)
        assertTrue(repository.isNameMaskingEnabled.first())

        repository.setNameMaskingEnabled(false)
        assertFalse(repository.isNameMaskingEnabled.first())
    }

    @Test
    fun テーマ設定_Enum値が正しく保存され復元されること() = runBlocking {
        repository.setThemeSetting(ThemeSetting.DARK)
        assertEquals(ThemeSetting.DARK, repository.themeSetting.first())

        repository.setThemeSetting(ThemeSetting.LIGHT)
        assertEquals(ThemeSetting.LIGHT, repository.themeSetting.first())
    }

    @Test
    fun デフォルト記録者名_文字列が正しく保存されること() = runBlocking {
        val testName = "テスト記録者_${System.currentTimeMillis()}"
        repository.setDefaultRecorderName(testName)
        assertEquals(testName, repository.defaultRecorderName.first())
    }

    @Test
    fun ログ保存期間_数値が正しく保存されること() = runBlocking {
        repository.setAuditLogRetentionDays(90)
        assertEquals(90, repository.auditLogRetentionDays.first())
    }

    @Test
    fun ロックバイパス_メモリ上のフラグが正しく保持されること() {
        repository.isLockBypassed = true
        assertTrue(repository.isLockBypassed)
        
        repository.isLockBypassed = false
        assertFalse(repository.isLockBypassed)
    }
}
