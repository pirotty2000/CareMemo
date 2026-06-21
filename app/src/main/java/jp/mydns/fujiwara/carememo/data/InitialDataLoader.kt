package jp.mydns.fujiwara.carememo.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.temporal.ChronoUnit
import java.io.InputStream

// 旧形式のJSONを読み込むためのデータ構造
@Serializable
private data class PersonImportDto(
    val id: Int = 0,
    val name: String,
    val furigana: String? = null,
    @Serializable(with = InstantSerializer::class)
    val birthday: java.time.Instant,
    val note: String = ""
)

class InitialDataLoader(private val context: Context, private val repository: CareMemoRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 既存のすべてのデータを物理削除します。
     */
    suspend fun clearAllData() {
        repository.clearAllData()
    }

    /**
     * assets フォルダ内の標準ファイル名からデータをロードします。
     */
    suspend fun loadInitialDataFromAssets() {
        loadPersons(context.assets.open("person_db.json"))
        loadHeightAndWeight(context.assets.open("height_and_weight_db.json"))
        loadBpAndPulse(context.assets.open("bp_and_pulse_db.json"))
        loadGlucoseAndHbA1c(context.assets.open("glucose_and_hba1c_db.json"))
        loadConditionAtVisit(context.assets.open("condition_at_visit_db.json"))
        
        // 写真データがあれば読み込む（任意）
        try {
            loadConditionPhotos(context.assets.open("condition_photo_db.json"))
        } catch (e: Exception) {
            // ファイルがなくても無視
        }
    }

    /**
     * 指定された各InputStreamからデータをロードします（フォルダ選択時用）。
     */
    suspend fun loadPersons(inputStream: InputStream) {
        try {
            val rawJson = inputStream.bufferedReader().use { it.readText() }
            val dtos = json.decodeFromString<List<PersonImportDto>>(rawJson)
            dtos.forEach { dto ->
                val nameParts = dto.name.split(Regex("[\\s　]+"), limit = 2)
                val lastName = nameParts.getOrElse(0) { "" }
                val firstName = nameParts.getOrElse(1) { "" }

                val furiganaParts = (dto.furigana ?: "").split(Regex("[\\s　]+"), limit = 2)
                val lastNameFurigana = furiganaParts.getOrElse(0) { "" }
                val firstNameFurigana = furiganaParts.getOrElse(1) { "" }

                // 生年月日の時刻を 00:00:00 に厳密に正規化
                val normalizedBirthday = dto.birthday.atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()

                val person = Person(
                    id = dto.id,
                    lastName = lastName,
                    firstName = firstName,
                    lastNameFurigana = lastNameFurigana,
                    firstNameFurigana = firstNameFurigana,
                    birthday = normalizedBirthday,
                    note = dto.note
                )
                repository.insertPerson(person)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadHeightAndWeight(inputStream: InputStream) {
        try {
            val data = json.decodeFromString<List<HeightAndWeight>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertHeightAndWeight(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadBpAndPulse(inputStream: InputStream) {
        try {
            val data = json.decodeFromString<List<BpAndPulse>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertBpAndPulse(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadGlucoseAndHbA1c(inputStream: InputStream) {
        try {
            val data = json.decodeFromString<List<GlucoseAndHbA1c>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertGlucoseAndHbA1c(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadConditionAtVisit(inputStream: InputStream) {
        try {
            val data = json.decodeFromString<List<ConditionAtVisit>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertConditionAtVisit(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadConditionPhotos(inputStream: InputStream) {
        try {
            val data = json.decodeFromString<List<ConditionPhoto>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                repository.insertConditionPhoto(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
