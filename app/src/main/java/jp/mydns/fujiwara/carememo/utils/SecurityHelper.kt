package jp.mydns.fujiwara.carememo.utils

import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity

/**
 * 生体認証または端末ロックによる認証を補助するユーティリティ
 */
object SecurityHelper {

    /**
     * デバイスが認証に対応しているか確認する
     */
    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
