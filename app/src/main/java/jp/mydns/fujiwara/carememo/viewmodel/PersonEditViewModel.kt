package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.ui.components.main.BirthEra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 利用者の新規登録・編集画面用の UI 状態
 */
data class PersonEditUiState(
    val lastName: String = "",
    val firstName: String = "",
    val lastNameFurigana: String = "",
    val firstNameFurigana: String = "",
    val note: String = "",
    val era: BirthEra = BirthEra.SHOWA,
    val year: String = "",
    val month: String = "",
    val day: String = ""
)

/**
 * 利用者の新規登録・編集画面用の ViewModel
 */
class PersonEditViewModel(
    private val personId: Int,
    private val repository: PersonRepository,
    userSettingsRepository: UserSettingsRepository,
    auditLogRepository: AuditLogRepository
) : BaseViewModel(userSettingsRepository) {

    companion object {
        private const val FEATURE_NAME = "PersonEdit"
        private const val OP_LOAD = "loadPerson"
        private const val OP_SAVE = "save"
        private const val TABLE_PERSON = "person_db"
    }

    override val featureName: String = FEATURE_NAME

    init {
        coroutineErrorHandler = ViewModelCoroutineErrorHandler(auditLogRepository) { title, msg, args ->
            showError(title, msg, *args)
        }
    }

    // UI状態の一括管理
    private val _uiState = MutableStateFlow(PersonEditUiState())
    val uiState: StateFlow<PersonEditUiState> = _uiState.asStateFlow()

    // ロードされた初期データ（変更検知用）
    private var initialPerson: Person? = null

    private val _isLoading = MutableStateFlow(personId != -1)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isNew: Boolean = personId == -1

    init {
        if (personId != -1) {
            loadPerson(personId)
        }
    }

    private fun loadPerson(id: Int) {
        safeLaunch(
            operation = OP_LOAD,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = id.toString()
            }
        ) {
            repository.getPersonById(id).filterNotNull().first().let { person ->
                initialPerson = person
                // 誕生日は常に UTC 基準で読み込む
                val date = person.birthday.atZone(ZoneOffset.UTC).toLocalDate()
                val (initialEra, initialYearText) = calculateEraAndYear(date)

                _uiState.value = PersonEditUiState(
                    lastName = person.lastName,
                    firstName = person.firstName,
                    lastNameFurigana = person.lastNameFurigana,
                    firstNameFurigana = person.firstNameFurigana,
                    note = person.note,
                    era = initialEra,
                    year = initialYearText,
                    month = date.monthValue.toString(),
                    day = date.dayOfMonth.toString()
                )
            }
        }
    }

    private fun calculateEraAndYear(date: LocalDate): Pair<BirthEra, String> {
        return when {
            date.year in 1926..1989 -> {
                val e = BirthEra.SHOWA
                val y = (date.year - 1925).toString()
                e to y
            }
            date.year in 1990..2019 -> {
                val e = BirthEra.HEISEI
                val y = (date.year - 1988).toString()
                e to y
            }
            date.year >= 2020 -> {
                val e = BirthEra.REIWA
                val y = (date.year - 2018).toString()
                e to y
            }
            else -> {
                val e = BirthEra.AD
                val y = date.year.toString()
                e to y
            }
        }
    }

    // 更新用メソッド群
    fun updateLastName(value: String) { _uiState.update { it.copy(lastName = value) } }
    fun updateFirstName(value: String) { _uiState.update { it.copy(firstName = value) } }
    fun updateLastNameFurigana(value: String) { _uiState.update { it.copy(lastNameFurigana = value) } }
    fun updateFirstNameFurigana(value: String) { _uiState.update { it.copy(firstNameFurigana = value) } }
    fun updateNote(value: String) { _uiState.update { it.copy(note = value) } }
    fun updateEra(value: BirthEra) { _uiState.update { it.copy(era = value) } }
    fun updateYear(value: String) { _uiState.update { it.copy(year = value) } }
    fun updateMonth(value: String) { _uiState.update { it.copy(month = value) } }
    fun updateDay(value: String) { _uiState.update { it.copy(day = value) } }

    /**
     * 現在の入力内容が初期状態から変更されているかどうか
     */
    val isChanged: StateFlow<Boolean> = uiState.map { current ->
        if (initialPerson == null) {
            // 新規登録時は、何かしら入力があれば変更ありとみなす
            current.lastName.isNotBlank() || current.firstName.isNotBlank() || 
            current.lastNameFurigana.isNotBlank() || current.firstNameFurigana.isNotBlank() ||
            current.note.isNotBlank() || current.year.isNotBlank() || 
            current.month.isNotBlank() || current.day.isNotBlank()
        } else {
            val p = initialPerson!!
            val date = p.birthday.atZone(ZoneOffset.UTC).toLocalDate()
            val (initEra, initYear) = calculateEraAndYear(date)

            current.lastName != p.lastName ||
            current.firstName != p.firstName ||
            current.lastNameFurigana != p.lastNameFurigana ||
            current.firstNameFurigana != p.firstNameFurigana ||
            current.note != p.note ||
            current.era != initEra ||
            current.year != initYear ||
            current.month != date.monthValue.toString() ||
            current.day != date.dayOfMonth.toString()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 保存可能かどうか（バリデーション）
     */
    val isValid: StateFlow<Boolean> = uiState.map { current ->
        current.lastName.isNotBlank() && current.firstName.isNotBlank() &&
        current.year.isNotBlank() && current.month.isNotBlank() && current.day.isNotBlank() &&
        validateDate(current.era, current.year, current.month, current.day)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun validateDate(e: BirthEra, yStr: String, mStr: String, dStr: String): Boolean {
        val y = yStr.toIntOrNull() ?: return false
        val m = mStr.toIntOrNull() ?: return false
        val d = dStr.toIntOrNull() ?: return false

        if (m !in 1..12) return false

        val westernYear = when (e) {
            BirthEra.SHOWA -> y + 1925
            BirthEra.HEISEI -> y + 1988
            BirthEra.REIWA -> y + 2018
            BirthEra.AD -> y
        }

        return try {
            d in 1..java.time.YearMonth.of(westernYear, m).lengthOfMonth()
        } catch (_: Exception) {
            false
        }
    }

    fun save() {
        val birthday = calculateInstant() ?: return
        val current = _uiState.value
        val person = (initialPerson?.copy(
            lastName = current.lastName.trim(),
            firstName = current.firstName.trim(),
            lastNameFurigana = current.lastNameFurigana.trim(),
            firstNameFurigana = current.firstNameFurigana.trim(),
            note = current.note.trim(),
            birthday = birthday
        ) ?: Person(
            lastName = current.lastName.trim(),
            firstName = current.firstName.trim(),
            lastNameFurigana = current.lastNameFurigana.trim(),
            firstNameFurigana = current.firstNameFurigana.trim(),
            note = current.note.trim(),
            birthday = birthday
        ))

        safeLaunch(
            operation = OP_SAVE,
            loadingState = _isLoading,
            contextBuilder = {
                tableName = TABLE_PERSON
                affectedId = person.id.toString()
            }
        ) {
            // 重複チェック
            val existing = repository.findExistingPerson(person)
            if (existing != null && (personId == -1 || existing.id != personId)) {
                handleDuplicateError(existing, person, isUpdate = personId != -1)
                return@safeLaunch
            }

            try {
                if (personId == -1) {
                    repository.insertPerson(person, featureName, OP_SAVE)
                    showSnackbar(R.string.main_msg_user_added, person.getMaskedName(isNameMaskingEnabled.value))
                } else {
                    repository.updatePerson(person, featureName, OP_SAVE)
                    showSnackbar(R.string.main_msg_user_updated)
                }
                sendUiEvent(UiEvent.SaveSuccess)
            } catch (e: SQLiteConstraintException) {
                // 重複の可能性が高いが、一応ログとエラー表示
                showError(R.string.common_error_save, R.string.common_error_save)
                throw e // 再スローしてハンドラに記録させる
            }
        }
    }

    private fun calculateInstant(): Instant? {
        val current = _uiState.value
        val y = current.year.toIntOrNull() ?: return null
        val m = current.month.toIntOrNull() ?: return null
        val d = current.day.toIntOrNull() ?: return null

        val westernYear = when (current.era) {
            BirthEra.SHOWA -> y + 1925
            BirthEra.HEISEI -> y + 1988
            BirthEra.REIWA -> y + 2018
            BirthEra.AD -> y
        }

        return try {
            LocalDate.of(westernYear, m, d)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        } catch (_: Exception) {
            null
        }
    }

    private fun handleDuplicateError(existing: Person, input: Person, isUpdate: Boolean) {
        val personName = input.getMaskedName(isNameMaskingEnabled.value)
        val titleRes = if (isUpdate) R.string.main_err_title_duplicate_archived_update else R.string.main_err_title_duplicate_archived_add
        
        if (existing.deletedAt == null) {
            showError(titleRes, R.string.main_err_duplicate_active)
        } else {
            showError(titleRes, R.string.main_err_duplicate_archived, personName)
        }
    }

    class Factory(
        private val personId: Int,
        private val repository: PersonRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonEditViewModel(personId, repository, userSettingsRepository, auditLogRepository) as T
        }
    }
}
