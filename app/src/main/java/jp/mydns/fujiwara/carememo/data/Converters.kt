package jp.mydns.fujiwara.carememo.data

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Data：Converters
 *
 * 【役割】
 * Room データベースにおいて、SQLite で直接扱えないカスタムデータ型（Instant, YearMonth）と
 * SQLite 互換型（Long, String）の相互変換を担当します。
 */
class Converters {
    @TypeConverter
    fun timestampToInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun instantToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilli()
    }
}
