package jp.mydns.fujiwara.carememo.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.ui.components.main.BirthEra
import jp.mydns.fujiwara.carememo.utils.DateTimeUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 利用者の新規登録・編集画面用の ViewModel
 */
class PersonEditViewModel(
    private val personId: Int,
    private val repository: PersonRepository,
    userSettingsRepository: UserSettingsRepository
) : BaseViewModel(userSettingsRepository) {

    // 入力項目の StateFlow
    val lastName = MutableStateFlow("")
    val firstName = MutableStateFlow("")
    val lastNameFurigana = MutableStateFlow("")
    val firstNameFurigana = MutableStateFlow("")
    val note = MutableStateFlow("")

    // 生年月日関連
    val era = MutableStateFlow(BirthEra.SHOWA)
    val year = MutableStateFlow("")
    val month = MutableStateFlow("")
    val day = MutableStateFlow("")

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
        viewModelScope.launch {
            repository.getPersonById(id).filterNotNull().first().let { person ->
                initialPerson = person
                lastName.value = person.lastName
                firstName.value = person.firstName
                lastNameFurigana.value = person.lastNameFurigana
                firstNameFurigana.value = person.firstNameFurigana
                note.value = person.note

                val date = person.birthday.atZone(ZoneId.systemDefault()).toLocalDate()
                val (initialEra, initialYearText) = calculateEraAndYear(date)
                era.value = initialEra
                year.value = initialYearText
                month.value = date.monthValue.toString()
                day.value = date.dayOfMonth.toString()
                
                _isLoading.value = false
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

    /**
     * 現在の入力内容が初期状態から変更されているかどうか
     */
    val isChanged: StateFlow<Boolean> = combine<Any, Boolean>(
        lastName, firstName, lastNameFurigana, firstNameFurigana, note, era, year, month, day
    ) { params ->
        val currentLastName = params[0] as String
        val currentFirstName = params[1] as String
        val currentLastNameFurigana = params[2] as String
        val currentFirstNameFurigana = params[3] as String
        val currentNote = params[4] as String
        val currentEra = params[5] as BirthEra
        val currentYear = params[6] as String
        val currentMonth = params[7] as String
        val currentDay = params[8] as String

        if (initialPerson == null) {
            // 新規登録時は、何かしら入力があれば変更ありとみなす
            currentLastName.isNotBlank() || currentFirstName.isNotBlank() || 
            currentLastNameFurigana.isNotBlank() || currentFirstNameFurigana.isNotBlank() ||
            currentNote.isNotBlank() || currentYear.isNotBlank() || 
            currentMonth.isNotBlank() || currentDay.isNotBlank()
        } else {
            val p = initialPerson!!
            val date = p.birthday.atZone(ZoneId.systemDefault()).toLocalDate()
            val (initEra, initYear) = calculateEraAndYear(date)

            currentLastName != p.lastName ||
            currentFirstName != p.firstName ||
            currentLastNameFurigana != p.lastNameFurigana ||
            currentFirstNameFurigana != p.firstNameFurigana ||
            currentNote != p.note ||
            currentEra != initEra ||
            currentYear != initYear ||
            currentMonth != date.monthValue.toString() ||
            currentDay != date.dayOfMonth.toString()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * 保存可能かどうか（バリデーション）
     */
    val isValid: StateFlow<Boolean> = combine<Any, Boolean>(
        lastName, firstName, era, year, month, day
    ) { params ->
        val lName = params[0] as String
        val fName = params[1] as String
        val cEra = params[2] as BirthEra
        val cYear = params[3] as String
        val cMonth = params[4] as String
        val cDay = params[5] as String

        lName.isNotBlank() && fName.isNotBlank() &&
        cYear.isNotBlank() && cMonth.isNotBlank() && cDay.isNotBlank() &&
        validateDate(cEra, cYear, cMonth, cDay)
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
            d in 1..YearMonth.of(westernYear, m).lengthOfMonth()
        } catch (_: Exception) {
            false
        }
    }

    fun save() {
        val birthday = calculateInstant() ?: return
        val person = (initialPerson?.copy(
            lastName = lastName.value.trim(),
            firstName = firstName.value.trim(),
            lastNameFurigana = lastNameFurigana.value.trim(),
            firstNameFurigana = firstNameFurigana.value.trim(),
            note = note.value.trim(),
            birthday = birthday
        ) ?: Person(
            lastName = lastName.value.trim(),
            firstName = firstName.value.trim(),
            lastNameFurigana = lastNameFurigana.value.trim(),
            firstNameFurigana = firstNameFurigana.value.trim(),
            note = note.value.trim(),
            birthday = birthday
        ))

        viewModelScope.launch {
            try {
                // 重複チェック
                val existing = repository.findExistingPerson(person)
                if (existing != null && (personId == -1 || existing.id != personId)) {
                    handleDuplicateError(existing, person, isUpdate = personId != -1)
                    return@launch
                }

                if (personId == -1) {
                    repository.insertPerson(person)
                    showSnackbar(R.string.main_msg_user_added, person.getMaskedName(isNameMaskingEnabled.value))
                } else {
                    repository.updatePerson(person)
                    showSnackbar(R.string.main_msg_user_updated)
                }
                sendUiEvent(UiEvent.SaveSuccess)
            } catch (_: SQLiteConstraintException) {
                showError(R.string.common_error_save, R.string.common_error_save)
            }
        }
    }

    private fun calculateInstant(): Instant? {
        val yStr = year.value
        val mStr = month.value
        val dStr = day.value
        val e = era.value

        val y = yStr.toIntOrNull() ?: return null
        val m = mStr.toIntOrNull() ?: return null
        val d = dStr.toIntOrNull() ?: return null

        val westernYear = when (e) {
            BirthEra.SHOWA -> y + 1925
            BirthEra.HEISEI -> y + 1988
            BirthEra.REIWA -> y + 2018
            BirthEra.AD -> y
        }

        return try {
            val instant = LocalDate.of(westernYear, m, d)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
            DateTimeUtils.normalizeBirthday(instant)
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
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PersonEditViewModel(personId, repository, userSettingsRepository) as T
        }
    }
}
