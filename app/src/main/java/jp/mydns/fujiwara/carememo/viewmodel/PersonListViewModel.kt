package jp.mydns.fujiwara.carememo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.InitialDataLoader
import jp.mydns.fujiwara.carememo.data.Person
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.DocumentsContract
import jp.mydns.fujiwara.carememo.data.CareMemoBackup
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PersonListViewModel(
    private val repository: CareMemoRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : ViewModel() {

    private val json = Json { prettyPrint = true }

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow.asStateFlow()

    private val _infoFlow = MutableStateFlow<String?>(null)
    val infoFlow: StateFlow<String?> = _infoFlow.asStateFlow()

    private val _snackbarFlow = MutableSharedFlow<String>()
    val snackbarFlow = _snackbarFlow.asSharedFlow()

    val isNameMaskingEnabled: StateFlow<Boolean> = userSettingsRepository.isNameMaskingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun setNameMaskingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.setNameMaskingEnabled(enabled)
        }
    }

    fun clearError() {
        _errorFlow.value = null
    }

    fun clearInfo() {
        _infoFlow.value = null
    }
    
    val userList: StateFlow<List<Person>> = repository.getAllPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val deletedUserList: StateFlow<List<Person>> = repository.getDeletedPersons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 各カテゴリーのデータ有無を監視し、バッジ表示用のMapに統合するロジック。
     * いずれかのカテゴリーでデータが更新されると、自動的に再計算されてUIに通知されます。
     */
    val categorySummaries: StateFlow<Map<Int, PersonCategorySummary>> = combine(
        repository.getPersonIdsWithHeightWeight(),
        repository.getPersonIdsWithPulse(),
        repository.getPersonIdsWithBp(),
        repository.getPersonIdsWithGlucose(),
        repository.getPersonIdsWithCondition()
    ) { hwIds, pulseIds, bpIds, glucoseIds, conditionIds ->
        val allIds = (hwIds + pulseIds + bpIds + glucoseIds + conditionIds).distinct()
        allIds.associateWith { id ->
            PersonCategorySummary(
                hasHeightWeight = hwIds.contains(id),
                hasBpAndPulse = pulseIds.contains(id) || bpIds.contains(id),
                hasGlucoseAndHbA1c = glucoseIds.contains(id),
                hasCondition = conditionIds.contains(id)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun addPerson(person: Person) {
        viewModelScope.launch {
            try {
                repository.insertPerson(person)
                _snackbarFlow.emit("${person.getMaskedName(isNameMaskingEnabled.value)} さんを登録しました")
            } catch (_: SQLiteConstraintException) {
                _errorFlow.value = "この利用者は既に登録されています。"
            }
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch {
            try {
                repository.updatePerson(person)
                _snackbarFlow.emit("利用者情報を更新しました")
            } catch (_: SQLiteConstraintException) {
                _errorFlow.value = "変更後の内容は既に他の利用者として登録されています。"
            }
        }
    }

    fun logicalDeletePerson(person: Person) {
        viewModelScope.launch {
            repository.logicalDeletePerson(person.id)
        }
    }

    fun restorePerson(person: Person) {
        viewModelScope.launch {
            repository.restorePerson(person.id)
            _snackbarFlow.emit("${person.getMaskedName(isNameMaskingEnabled.value)} さんを一覧に戻しました")
        }
    }

    /**
     * 利用終了となっているすべての利用者のデータを完全に抹消します。
     */
    fun deleteEndedPersons() {
        viewModelScope.launch {
            try {
                repository.deleteEndedPersons()
                _infoFlow.value = "利用終了者のデータを完全に抹消しました。"
            } catch (e: Exception) {
                _errorFlow.value = "データの抹消に失敗しました: ${e.localizedMessage}"
            }
        }
    }

    // --- 新形式データのバックアップ・復元 ---

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val backup = repository.getBackupData()
                val jsonString = json.encodeToString(backup)
                context.contentResolver.openOutputStream(uri)?.use { it.write(jsonString.toByteArray()) }
                _infoFlow.value = "データのエクスポートが完了しました。"
            } catch (e: Exception) {
                _errorFlow.value = "エクスポートに失敗しました: ${e.localizedMessage}"
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (jsonString != null) {
                    val backup = json.decodeFromString<CareMemoBackup>(jsonString)
                    repository.replaceAllData(backup)
                    _infoFlow.value = "データの復元が完了しました。"
                }
            } catch (e: Exception) {
                _errorFlow.value = "復元に失敗しました: ${e.localizedMessage}"
            }
        }
    }

    // --- 旧アプリデータの移行 (初期データの読込) ---

    fun importLegacyDataFromAssets(context: Context) {
        viewModelScope.launch {
            try {
                val loader = InitialDataLoader(context, repository)
                loader.clearAllData()
                loader.loadInitialDataFromAssets()
                _infoFlow.value = "Assetsからの初期データ読込が完了しました。"
            } catch (e: Exception) {
                _errorFlow.value = "読込に失敗しました: ${e.localizedMessage}"
            }
        }
    }

    /**
     * データベース内のすべてのデータを物理削除します（開発用）。
     */
    fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.clearAllData()
                _infoFlow.value = "全てのデータを削除しました。"
            } catch (e: Exception) {
                _errorFlow.value = "データの削除に失敗しました: ${e.localizedMessage}"
            }
        }
    }

    fun importLegacyDataFromFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            try {
                val loader = InitialDataLoader(context, repository)
                loader.clearAllData()

                val resolver = context.contentResolver
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                )

                val fileMap = mutableMapOf<String, Uri>()
                resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx)
                        val id = cursor.getString(idIdx)
                        fileMap[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    }
                }

                fileMap["person_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadPersons(s) } }
                fileMap["height_and_weight_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadHeightAndWeight(s) } }
                fileMap["bp_and_pulse_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadBpAndPulse(s) } }
                fileMap["glucose_and_hba1c_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadGlucoseAndHbA1c(s) } }
                fileMap["condition_at_visit_db.json"]?.let { resolver.openInputStream(it)?.let { s -> loader.loadConditionAtVisit(s) } }

                _infoFlow.value = "旧アプリデータの引き継ぎが完了しました。"
            } catch (e: Exception) {
                _errorFlow.value = "引き継ぎに失敗しました: ${e.localizedMessage}"
            }
        }
    }

    class Factory(
        private val repository: CareMemoRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonListViewModel::class.java)) {
                return PersonListViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
