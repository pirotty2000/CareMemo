package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 利用者詳細画面（各カテゴリの履歴表示・編集）用の ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModel(
    repository: CareMemoRepository,
    userSettingsRepository: UserSettingsRepository,
) : PersonBaseViewModel(repository, userSettingsRepository) {

    private val _currentCategory = MutableStateFlow<Category?>(null)

    /**
     * 現在のカテゴリに基づいたレコード一覧
     */
    val records: StateFlow<List<Any>> = combine(_currentPerson, _currentCategory) { person, category ->
        person to category
    }.flatMapLatest { (person, category) ->
        if (person == null || category == null) flowOf(emptyList())
        else when (category) {
            Category.HEIGHT_AND_WEIGHT -> repository.getHeightAndWeightByPersonId(person.id)
            Category.BP_AND_PULSE -> repository.getBpAndPulseByPersonId(person.id)
            Category.GLUCOSE_AND_HBA1C -> repository.getGlucoseAndHbA1cByPersonId(person.id)
            Category.CONDITION_AT_VISIT -> repository.getConditionAtVisitByPersonId(person.id)
            Category.MEDICATION -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 検索クエリでフィルタリングされたレコード一覧
     */
    val filteredRecords: StateFlow<List<Any>> = combine(records, _searchQuery) { records, query ->
        if (query.isBlank()) {
            records
        } else {
            records.filter { record ->
                when (record) {
                    is ConditionAtVisit -> {
                        val titleMatch = record.title?.contains(query, ignoreCase = true) == true
                        val conditionMatch = record.condition?.contains(query, ignoreCase = true) == true
                        titleMatch || conditionMatch
                    }
                    else -> true
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /**
     * 表示するカテゴリを設定します。
     */
    fun setCategory(category: Category) {
        _currentCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * レコードを保存または更新します。
     */
    fun saveRecord(record: Any?) {
        if (record == null) return
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val isUpdate = if (record is HistoryRecord) record.id != 0 else false
                performSave(record)
                showSnackbar(if (isUpdate) "記録を更新しました" else "記録を保存しました")
            } catch (e: Exception) {
                showError("保存エラー", "データの保存に失敗しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun performSave(record: Any) = when (record) {
        is HeightAndWeight -> repository.insertHeightAndWeight(record)
        is BpAndPulse -> repository.insertBpAndPulse(record)
        is GlucoseAndHbA1c -> repository.insertGlucoseAndHbA1c(record)
        is ConditionAtVisit -> repository.insertConditionAtVisit(record)
        else -> {}
    }

    /**
     * レコードを削除します。
     */
    fun deleteRecord(record: Any) {
        viewModelScope.launch {
            try {
                performDelete(record)
                showSnackbar("記録を削除しました")
            } catch (e: Exception) {
                showError("削除エラー", "データの削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun performDelete(record: Any) = when (record) {
        is HeightAndWeight -> repository.deleteHeightAndWeight(record)
        is BpAndPulse -> repository.deleteBpAndPulse(record)
        is GlucoseAndHbA1c -> repository.deleteGlucoseAndHbA1c(record)
        is ConditionAtVisit -> repository.deleteConditionAtVisit(record)
        else -> {}
    }

    class Factory(
        private val repository: CareMemoRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonDetailViewModel::class.java)) {
                return PersonDetailViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
