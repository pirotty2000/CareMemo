@file:Suppress("DEPRECATION")
package jp.mydns.fujiwara.carememo.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Data：DatabaseKeyManager
 *
 * 【役割】
 * SQLCipher によるデータベース暗号化に使用する「パスフレーズ」の生成・保存・取得を安全に管理します。
 * Android Keystore システムを使用してマスターキーを保護し、機密情報を平文で保存しないように設計されています。
 *
 * 【主な機能】
 * ・パスフレーズ生成：セキュアな乱数による暗号鍵の作成。
 * ・暗号化保存：生成した鍵を AES 暗号化し、SharedPreferences に永続化。
 * ・鍵の復元：デバイス起動時、Keystore のマスターキーを用いて保存済みの鍵を復号し、Room 構成時に提供。
 *
 * 【設計指針】
 * 1. データベースの物理ファイルが盗難された場合でも、鍵がデバイス内の安全な領域（Keystore）に
 *    隔離されていることで、データの機密性を担保する。
 * 2. 開発者が直接パスフレーズを指定するのではなく、デバイスごとに固有のランダム値を生成することで、
 *    ソースコードからの鍵漏洩リスクを排除する。
 */
class DatabaseKeyManager(context: Context) {
    
    /** 鍵を暗号化するためのマスターキー（Android Keystore により保護される） */
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    /** 鍵自体を安全に保存するための暗号化済み SharedPreference */
    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "db_key_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * データベース暗号化用のパスフレーズを取得します。
     * まだ生成されていない（初回利用時）場合は、新規に生成して安全に保存します。
     *
     * @return 32バイト（256ビット）のバイナリ形式のパスフレーズ
     */
    fun getOrCreatePassphrase(): ByteArray {
        val key = sharedPrefs.getString("db_passphrase", null)
        return if (key != null) {
            // 保存済みの鍵を Base64 デコードして復元
            Base64.decode(key, Base64.DEFAULT)
        } else {
            // 新規生成：256ビット（32バイト）の暗号学的に強力なランダム値を生成
            val newKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            val encoded = Base64.encodeToString(newKey, Base64.DEFAULT)
            
            // 暗号化ストレージに保存
            sharedPrefs.edit {
                putString("db_passphrase", encoded)
            }
            newKey
        }
    }
}
