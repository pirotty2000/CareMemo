package jp.mydns.fujiwara.carememo.data

import androidx.room.TypeConverter
import java.time.Instant

@Suppress("unused")
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
