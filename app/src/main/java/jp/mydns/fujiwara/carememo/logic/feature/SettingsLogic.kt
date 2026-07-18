package jp.mydns.fujiwara.carememo.logic.feature

/**
 * インポートデータの検証結果（事実）
 */
enum class ImportValidationResult {
    SUCCESS,
    NOT_A_ZIP,
    INCOMPATIBLE
}

/**
 * ストレージ容量の検証結果（事実）
 */
enum class StorageValidationResult {
    SUCCESS,
    INSUFFICIENT_SPACE
}

/**
 * 設定画面・バックアップ管理に関するドメインロジック。
 */
object SettingsLogic {

    /**
     * ファイルヘッダーが Zip 形式（マジックナンバー）に合致するか判定します。
     */
    fun validateImportFormat(header: ByteArray): ImportValidationResult {
        if (header.size < 4) return ImportValidationResult.NOT_A_ZIP
        
        val isZip = header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        
        return if (isZip) ImportValidationResult.SUCCESS else ImportValidationResult.NOT_A_ZIP
    }

    /**
     * バックアップデータが現在のアプリバージョンと互換性があるか判定します。
     */
    fun validateVersion(backupVersionCode: Int, currentVersionCode: Int): ImportValidationResult {
        return if (backupVersionCode <= currentVersionCode) {
            ImportValidationResult.SUCCESS
        } else {
            ImportValidationResult.INCOMPATIBLE
        }
    }

    /**
     * 要求されたバイト数以上の空き容量があるか判定します。
     * 依存性を排除するため、数値の比較のみを行います。
     */
    fun validateStorageSpace(availableBytes: Long, requiredBytes: Long): StorageValidationResult {
        return if (availableBytes >= requiredBytes) {
            StorageValidationResult.SUCCESS
        } else {
            StorageValidationResult.INSUFFICIENT_SPACE
        }
    }

    /**
     * 開発者モードを有効にすべきか判定します。
     */
    fun shouldEnableDeveloperMode(tapCount: Int): Boolean {
        return tapCount >= 7
    }
}
