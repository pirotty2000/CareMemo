package jp.mydns.fujiwara.carememo.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.time.temporal.ChronoUnit

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
            val persons = json.decodeFromString<List<Person>>(inputStream.bufferedReader().use { it.readText() })
            persons.forEach { 
                // 生年月日は日単位までなので丸め不要だが、念のため。noteは自動で""が入る。
                repository.insertPerson(it) 
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
