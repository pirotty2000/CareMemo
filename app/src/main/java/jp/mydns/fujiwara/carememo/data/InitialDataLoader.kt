package jp.mydns.fujiwara.carememo.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.temporal.ChronoUnit

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

    suspend fun loadInitialData() {
        loadPersons()
        loadHeightAndWeight()
        loadBpAndPulse()
        loadGlucoseAndHbA1c()
        loadConditionAtVisit()
    }

    private suspend fun loadPersons() {
        try {
            val inputStream = context.assets.open("person_db.json")
            val rawJson = inputStream.bufferedReader().use { it.readText() }
            
            val dtos = json.decodeFromString<List<PersonImportDto>>(rawJson)
            dtos.forEach { dto ->
                // 姓名分割ロジック
                val nameParts = dto.name.split(Regex("[\\s　]+"), limit = 2)
                val lastName = nameParts.getOrElse(0) { "" }
                val firstName = nameParts.getOrElse(1) { "" }

                val furiganaParts = (dto.furigana ?: "").split(Regex("[\\s　]+"), limit = 2)
                val lastNameFurigana = furiganaParts.getOrElse(0) { "" }
                val firstNameFurigana = furiganaParts.getOrElse(1) { "" }

                val person = Person(
                    lastName = lastName,
                    firstName = firstName,
                    lastNameFurigana = lastNameFurigana,
                    firstNameFurigana = firstNameFurigana,
                    birthday = dto.birthday,
                    note = dto.note
                )
                repository.insertPerson(person)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadHeightAndWeight() {
        try {
            val inputStream = context.assets.open("height_and_weight_db.json")
            val data = json.decodeFromString<List<HeightAndWeight>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertHeightAndWeight(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadBpAndPulse() {
        try {
            val inputStream = context.assets.open("bp_and_pulse_db.json")
            val data = json.decodeFromString<List<BpAndPulse>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertBpAndPulse(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadGlucoseAndHbA1c() {
        try {
            val inputStream = context.assets.open("glucose_and_hba1c_db.json")
            val data = json.decodeFromString<List<GlucoseAndHbA1c>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertGlucoseAndHbA1c(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadConditionAtVisit() {
        try {
            val inputStream = context.assets.open("condition_at_visit_db.json")
            val data = json.decodeFromString<List<ConditionAtVisit>>(inputStream.bufferedReader().use { it.readText() })
            data.forEach { 
                val trimmed = it.copy(recordTime = it.recordTime.truncatedTo(ChronoUnit.MINUTES))
                repository.insertConditionAtVisit(trimmed) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
