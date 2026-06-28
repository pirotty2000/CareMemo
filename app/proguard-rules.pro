# CareMemo ProGuard Rules

# SQLCipher rules
-keep class net.zetetic.** { *; }
-keep class androidx.sqlite.** { *; }

# Room rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# --- セキュリティ対策 (SecurityEvaluation.md 3.2 対応) ---
# ログ出力 (android.util.Log) を完全に削除し、機密情報の漏洩を防ぎます
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# --- ライブラリの動作保証設定 ---

# kotlinx.serialization: JSON保存やエクスポート時にクラス名が消えてエラーになるのを防ぎます
-keepattributes *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
    public static ** Companion;
    public static ** $serializer;
}

# Zip4j: バックアップ作成時に使用
-keep class net.lingala.zip4j.** { *; }

# PDFBox-Android: PDF生成時に使用
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# Google ErrorProne annotations (SQLCipherやRoomなどが内部で使用)
-dontwarn com.google.errorprone.annotations.**

# クラッシュレポートで意味のあるスタックトレースを得るために、
# 行番号の情報だけは保持しておくことを推奨します
-keepattributes SourceFile,LineNumberTable
