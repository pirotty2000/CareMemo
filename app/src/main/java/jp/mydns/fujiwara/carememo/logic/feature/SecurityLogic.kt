package jp.mydns.fujiwara.carememo.logic.feature

/**
 * アプリケーションのセキュリティ状態を定義する Enum です。
 */
enum class SecurityStatus {
    /** 初期化中（設定ロード待ち）。スプラッシュ画面を表示し続けるべき状態。 */
    INITIALIZING,
    /** デバイスセキュリティ（PIN/指紋等）が設定されていないため、利用不可。 */
    UNSECURED,
    /** アプリロック中（認証待ち）。ロック画面を表示すべき状態。 */
    LOCKED,
    /** 認証済み、またはロック不要。メインコンテンツを表示可能な状態。 */
    UNLOCKED
}

/**
 * Logic：SecurityLogic
 *
 * 【役割】
 * アプリ起動時および復帰時におけるセキュリティ状態（ロック要否）の判定ロジックを集約します。
 * Android フレームワークに依存せず、純粋な入力値に基づいて「現在のステータス」を決定します。
 */
object SecurityLogic {

    /**
     * 現在の各状態フラグに基づき、最終的な [SecurityStatus] を決定します。
     *
     * @param isConfigLoaded ユーザー設定（生体認証有効化フラグ等）のロードが完了しているか
     * @param isBiometricSupported 端末が認証（生体またはデバイス認証）をサポートし、かつ有効（登録済み）であるか
     * @param isBiometricEnabled 設定画面で生体認証（ロック）が有効にされているか
     * @param isAuthenticated すでに現在のセッションで認証が成功しているか
     * @return 決定されたセキュリティステータス
     */
    fun determineStatus(
        isConfigLoaded: Boolean,
        isBiometricSupported: Boolean,
        isBiometricEnabled: Boolean,
        isAuthenticated: Boolean
    ): SecurityStatus {
        // 1. 設定がまだロードされていない場合は初期化中
        if (!isConfigLoaded) {
            return SecurityStatus.INITIALIZING
        }

        // 2. 端末自体のセキュリティ設定がない場合は利用不可（強制要件）
        if (!isBiometricSupported) {
            return SecurityStatus.UNSECURED
        }

        // 3. ユーザーがロックを有効にしていない場合はアンロック状態
        if (!isBiometricEnabled) {
            return SecurityStatus.UNLOCKED
        }

        // 4. ロック有効かつ未認証の場合はロック状態
        return if (isAuthenticated) {
            SecurityStatus.UNLOCKED
        } else {
            SecurityStatus.LOCKED
        }
    }
}
