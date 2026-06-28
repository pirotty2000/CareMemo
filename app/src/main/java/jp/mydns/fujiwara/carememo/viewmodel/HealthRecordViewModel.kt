package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 健康記録（身長体重、バイタル、血糖値）固有のロジックを扱う ViewModel。
 * これら3つのカテゴリに共通するが、所見メモや服薬管理には含まれない処理をここに記述します。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthRecordViewModel(
    repository: CareMemoRepository,
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(repository, userSettingsRepository) {

    /**
     * 指定された数値系カテゴリの履歴データを取得します。
     */
    fun getHealthRecords(category: Category): StateFlow<List<Any>> {
        return _currentPerson.flatMapLatest { person ->
            if (person == null) flowOf(emptyList())
            else when (category) {
                Category.HEIGHT_AND_WEIGHT -> repository.getHeightAndWeightByPersonId(person.id)
                Category.BP_AND_PULSE -> repository.getBpAndPulseByPersonId(person.id)
                Category.GLUCOSE_AND_HBA1C -> repository.getGlucoseAndHbA1cByPersonId(person.id)
                else -> flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    class Factory(
        private val repository: CareMemoRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HealthRecordViewModel::class.java)) {
                return HealthRecordViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
