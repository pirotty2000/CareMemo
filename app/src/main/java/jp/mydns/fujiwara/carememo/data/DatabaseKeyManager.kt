@file:Suppress("DEPRECATION")
package jp.mydns.fujiwara.carememo.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * データベース暗号化用のパスフレーズを安全に管理するクラス
 */
class DatabaseKeyManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "db_key_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * 保存されているパスフレーズを取得するか、存在しない場合は新規生成して保存します。
     */
    fun getOrCreatePassphrase(): ByteArray {
        val key = sharedPrefs.getString("db_passphrase", null)
        return if (key != null) {
            Base64.decode(key, Base64.DEFAULT)
        } else {
            // 256ビット（32バイト）のランダムなキーを生成
            val newKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            val encoded = Base64.encodeToString(newKey, Base64.DEFAULT)
            sharedPrefs.edit {
                putString("db_passphrase", encoded)
            }
            newKey
        }
    }
}
