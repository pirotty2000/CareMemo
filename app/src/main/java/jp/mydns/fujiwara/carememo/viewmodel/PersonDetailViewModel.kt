package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit

class PersonDetailViewModel : ViewModel() {
    private val _currentRecordState = MutableStateFlow<Any?>(null)
    val currentRecordState: StateFlow<Any?> = _currentRecordState.asStateFlow()

    private val _records = MutableStateFlow<List<Any>>(emptyList())
    val records: StateFlow<List<Any>> = _records.asStateFlow()

    fun loadSampleData(category: Category) {
        val now = Instant.now()
        val samples: List<Any> = when (category) {
            Category.HEIGHT_AND_WEIGHT -> listOf(
                HeightAndWeight(id = 1, personId = 1, height = 170.0, weight = 70.0, recordTime = now),
                HeightAndWeight(id = 2, personId = 1, height = 170.0, weight = 71.5, recordTime = now.minus(2, ChronoUnit.DAYS)),
                HeightAndWeight(id = 3, personId = 1, height = 170.0, weight = 70.8, recordTime = now.minus(5, ChronoUnit.DAYS)),
                HeightAndWeight(id = 4, personId = 1, height = 170.0, weight = 72.0, recordTime = now.minus(8, ChronoUnit.DAYS)),
                HeightAndWeight(id = 5, personId = 1, height = 170.0, weight = 71.2, recordTime = now.minus(12, ChronoUnit.DAYS))
            ).sortedBy { (it as HeightAndWeight).recordTime }
            
            Category.BP_AND_PULSE -> listOf(
                // 1. 高・頻 (Systolic >= 140 or Diastolic >= 90 AND Pulse >= 100)
                BpAndPulse(id = 1, personId = 1, bpSystolic = 150, bpDiastolic = 95, pulse = 110, recordTime = now),
                // 2. 高・徐 (Systolic >= 140 or Diastolic >= 90 AND Pulse <= 50)
                BpAndPulse(id = 2, personId = 1, bpSystolic = 145, bpDiastolic = 85, pulse = 45, recordTime = now.minus(1, ChronoUnit.HOURS)),
                // 3. 低・頻 (Systolic < 100 AND Pulse >= 100)
                BpAndPulse(id = 3, personId = 1, bpSystolic = 90, bpDiastolic = 60, pulse = 105, recordTime = now.minus(2, ChronoUnit.HOURS)),
                // 4. 低・徐 (Systolic < 100 AND Pulse <= 50)
                BpAndPulse(id = 4, personId = 1, bpSystolic = 85, bpDiastolic = 55, pulse = 48, recordTime = now.minus(3, ChronoUnit.HOURS)),
                // 5. 高血圧 (Systolic >= 140 or Diastolic >= 90)
                BpAndPulse(id = 5, personId = 1, bpSystolic = 142, bpDiastolic = 88, pulse = 75, recordTime = now.minus(4, ChronoUnit.HOURS)),
                // 6. 低血圧 (Systolic < 100)
                BpAndPulse(id = 6, personId = 1, bpSystolic = 95, bpDiastolic = 65, pulse = 70, recordTime = now.minus(5, ChronoUnit.HOURS)),
                // 7. 頻脈 (Pulse >= 100)
                BpAndPulse(id = 7, personId = 1, bpSystolic = 120, bpDiastolic = 80, pulse = 102, recordTime = now.minus(6, ChronoUnit.HOURS)),
                // 8. 徐脈 (Pulse <= 50)
                BpAndPulse(id = 8, personId = 1, bpSystolic = 115, bpDiastolic = 75, pulse = 49, recordTime = now.minus(7, ChronoUnit.HOURS)),
                // 9. 正常 (None of the above)
                BpAndPulse(id = 9, personId = 1, bpSystolic = 125, bpDiastolic = 82, pulse = 72, recordTime = now.minus(8, ChronoUnit.HOURS))
            ).sortedBy { (it as BpAndPulse).recordTime }
            
            Category.GLUCOSE_AND_HBA1C -> listOf(
                GlucoseAndHbA1c(id = 1, personId = 1, glucose = 110, hba1c = 5.8, recordTime = now),
                GlucoseAndHbA1c(id = 2, personId = 1, glucose = 140, hba1c = 6.2, recordTime = now.minus(7, ChronoUnit.DAYS)),
                GlucoseAndHbA1c(id = 3, personId = 1, glucose = 125, hba1c = 6.0, recordTime = now.minus(14, ChronoUnit.DAYS)),
                GlucoseAndHbA1c(id = 4, personId = 1, glucose = 115, hba1c = 5.9, recordTime = now.minus(21, ChronoUnit.DAYS)),
                GlucoseAndHbA1c(id = 5, personId = 1, glucose = 150, hba1c = 6.4, recordTime = now.minus(28, ChronoUnit.DAYS))
            ).sortedBy { (it as GlucoseAndHbA1c).recordTime }
            
            Category.CONDITION_AT_VISIT -> listOf(
                ConditionAtVisit(id = 1, personId = 1, title = "定期検診", condition = "良好です", author = "看護師 A", recordTime = now),
                ConditionAtVisit(id = 2, personId = 1, title = "相談", condition = "少し目眩がするとのこと", author = "医師 B", recordTime = now.minus(2, ChronoUnit.DAYS))
            ).sortedByDescending { (it as ConditionAtVisit).recordTime }
        }
        _records.value = samples
    }

    fun clearCurrentRecord() {
        _currentRecordState.value = null
    }

    fun saveRecord(@Suppress("UNUSED_PARAMETER") record: Any?) {
        // 保存ロジック（後ほど実装）
        _currentRecordState.value = null
    }

    fun selectRecord(record: Any) {
        _currentRecordState.value = record
    }
}
