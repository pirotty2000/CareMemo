package jp.mydns.fujiwara.carememo.ui.theme


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import jp.mydns.fujiwara.carememo.data.ThemeSetting

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// 各カスタムテーマの定義
private val HealingGreenColorScheme = lightColorScheme(
    primary = HealingGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF002107),
    secondary = Color(0xFF55634E),
    tertiary = Color(0xFF386567)
)

private val SereneBlueColorScheme = lightColorScheme(
    primary = SereneBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFF6B5778)
)

private val WarmApricotColorScheme = lightColorScheme(
    primary = WarmApricot,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF350B00),
    secondary = Color(0xFF745B32),
    tertiary = Color(0xFF516440)
)

private val MidnightNavyColorScheme = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF001666),
    primaryContainer = MidnightNavy,
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC3C5DD),
    tertiary = Color(0xFFE3BADA),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6)
)

private val ClassicSandColorScheme = lightColorScheme(
    primary = ClassicSand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7D1C1),
    onPrimaryContainer = Color(0xFF211000),
    secondary = Color(0xFF6D5C39),
    tertiary = Color(0xFF496548),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF)
)

/**
 * Component：CareMemoTheme
 *
 * 【役割】
 * アプリケーション全体の視覚的テーマ（ColorScheme, Typography）を定義・適用する最上位コンポーネントです。
 * Material 3 に準拠し、システムのダークモード、動的カラー（Android 12+）、およびユーザー設定によるカスタムテーマの切り替えをサポートします。
 *
 * 【主な機能】
 * ・テーマ切り替え：`ThemeSetting` に基づく複数のカラーパレット（Healing Green, Serene Blue 等）の動的適用。
 * ・ダークモード対応：システム設定および手動設定によるライト/ダークパレットの選択。
 * ・動的カラー統合：対応デバイスにおいて、壁紙に基づいた `dynamicColorScheme` の適用。
 *
 * 【全体像：テーマ構造 (Theme Structure)】
 *
 * [ThemeSetting] (data/ThemeSetting)
 *  ├─ SYSTEM ➔ Dynamic (12+) or Light/Dark
 *  ├─ LIGHT / DARK ➔ 標準 Material パレット
 *  └─ カスタムテーマ (独自定義 ColorScheme)
 *       ├─ HEALING_GREEN (緑：癒やし)
 *       ├─ SERENE_BLUE (青：平穏)
 *       ├─ WARM_APRICOT (橙：温かみ)
 *       ├─ MIDNIGHT_NAVY (紺：夜間・高コントラスト)
 *       └─ CLASSIC_SAND (茶：伝統・安心)
 */
@Composable
fun CareMemoTheme(
    themeSetting: ThemeSetting = ThemeSetting.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeSetting) {
        ThemeSetting.SYSTEM -> {
            if (dynamicColor) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        ThemeSetting.LIGHT -> LightColorScheme
        ThemeSetting.DARK -> DarkColorScheme
        ThemeSetting.HEALING_GREEN -> HealingGreenColorScheme
        ThemeSetting.SERENE_BLUE -> SereneBlueColorScheme
        ThemeSetting.WARM_APRICOT -> WarmApricotColorScheme
        ThemeSetting.MIDNIGHT_NAVY -> MidnightNavyColorScheme
        ThemeSetting.CLASSIC_SAND -> ClassicSandColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}