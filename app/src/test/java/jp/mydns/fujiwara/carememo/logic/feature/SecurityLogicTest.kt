package jp.mydns.fujiwara.carememo.logic.feature

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit Test: SecurityLogic
 */
class SecurityLogicTest {

    @Test
    fun SEC_01_initializing_when_config_not_loaded() {
        val status = SecurityLogic.determineStatus(
            isConfigLoaded = false,
            isBiometricSupported = true,
            isBiometricEnabled = true,
            isAuthenticated = false
        )
        assertEquals(SecurityStatus.INITIALIZING, status)
    }

    @Test
    fun SEC_02_unsecured_when_device_not_supported() {
        val status = SecurityLogic.determineStatus(
            isConfigLoaded = true,
            isBiometricSupported = false,
            isBiometricEnabled = true,
            isAuthenticated = false
        )
        assertEquals(SecurityStatus.UNSECURED, status)
    }

    @Test
    fun SEC_03_unlocked_when_lock_disabled() {
        val status = SecurityLogic.determineStatus(
            isConfigLoaded = true,
            isBiometricSupported = true,
            isBiometricEnabled = false,
            isAuthenticated = false
        )
        assertEquals(SecurityStatus.UNLOCKED, status)
    }

    @Test
    fun SEC_04_locked_when_enabled_and_not_authenticated() {
        val status = SecurityLogic.determineStatus(
            isConfigLoaded = true,
            isBiometricSupported = true,
            isBiometricEnabled = true,
            isAuthenticated = false
        )
        assertEquals(SecurityStatus.LOCKED, status)
    }

    @Test
    fun SEC_05_unlocked_when_enabled_and_authenticated() {
        val status = SecurityLogic.determineStatus(
            isConfigLoaded = true,
            isBiometricSupported = true,
            isBiometricEnabled = true,
            isAuthenticated = true
        )
        assertEquals(SecurityStatus.UNLOCKED, status)
    }
}
