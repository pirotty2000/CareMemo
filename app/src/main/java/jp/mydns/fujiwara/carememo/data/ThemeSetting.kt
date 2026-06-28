package jp.mydns.fujiwara.carememo.data

/**
 * アプリのテーマ設定を定義する列挙型
 */
enum class ThemeSetting(val label: String, val description: String) {
    SYSTEM("システムの設定に従う", "端末の設定に合わせて自動で切り替わります。"),
    LIGHT("ライトモード (標準)", "標準的な明るい配色です。"),
    DARK("ダークモード (夜間)", "標準的な暗い配色です。"),
    HEALING_GREEN("ヒーリング・グリーン", "目に優しく、安心感のある緑の配色です。"),
    SERENE_BLUE("セレネ・ブルー", "清潔感があり、信頼感を与える青の配色です。"),
    WARM_APRICOT("ウォーム・アプリコット", "温かみがあり、親しみやすいオレンジの配色です。"),
    MIDNIGHT_NAVY("ミッドナイト・ネイビー", "夜勤中や暗い場所での利用に最適な深い紺色です。"),
    CLASSIC_SAND("クラシック・サンド", "紙のような質感で、目が疲れにくい茶系の配色です。")
}
