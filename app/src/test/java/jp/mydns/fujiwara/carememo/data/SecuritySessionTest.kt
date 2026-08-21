package jp.mydns.fujiwara.carememo.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test: SecuritySession
 */
class SecuritySessionTest {

    @Test
    fun testLockBypassFlag() {
        val session = SecuritySession()
        
        // 初期値は false であること
        assertFalse(session.isLockBypassed)
        
        // フラグのセットができること
        session.isLockBypassed = true
        assertTrue(session.isLockBypassed)
        
        // フラグのリセットができること
        session.isLockBypassed = false
        assertFalse(session.isLockBypassed)
    }
}
