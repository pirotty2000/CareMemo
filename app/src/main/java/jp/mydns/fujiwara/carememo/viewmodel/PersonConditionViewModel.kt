package jp.mydns.fujiwara.carememo.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.toRoute
import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.data.PersonCategorySummary
import jp.mydns.fujiwara.carememo.data.SecuritySession
import jp.mydns.fujiwara.carememo.data.repository.AuditLogRepository
import jp.mydns.fujiwara.carememo.data.repository.ConditionRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonRepository
import jp.mydns.fujiwara.carememo.data.repository.PersonSummaryRepository
import jp.mydns.fujiwara.carememo.data.repository.UserSettingsRepository
import jp.mydns.fujiwara.carememo.logic.common.ConditionLogic
import jp.mydns.fujiwara.carememo.logic.common.ConditionValidationResult
import jp.mydns.fujiwara.carememo.logic.common.IdLogic
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditInput
import jp.mydns.fujiwara.carememo.logic.feature.ConditionEditSession
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionLogic
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionOperation
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionScreenState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionUiState
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionValidationResult
import jp.mydns.fujiwara.carememo.logic.feature.PersonConditionViewEvent
import jp.mydns.fujiwara.carememo.ui.navigation.Destination
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * ViewModel：PersonConditionViewModel
 *
 * 【役割】
 * 所見メモ（訪問時の状態記録）の表示、検索、入力、および写真の管理を担当します。
 */
class PersonConditionViewModel(
    private val conditionRepository: ConditionRepository,
    personRepository: PersonRepository,
    summaryRepository: PersonSummaryRepository,
    userSettingsRepository: UserSettingsRepository,
    securitySession: SecuritySession,
    auditLogRepository: AuditLogRepository,
    savedStateHandle: SavedStateHandle
) : PersonBaseUiStateViewModel<PersonConditionUiState, PersonConditionViewEvent>(
    personRepository,
    summaryRepository,
    userSettingsRepository,
    securitySession,
    auditLogRepository,
    PersonConditionUiState(),
    savedStateHandle
) {

    companion object {
        private const val FEATURE_NAME = "PersonCondition"
        private const val OP_SAVE = "saveRecord"
        private const val OP_DELETE = "deleteRecord"
        private const val OP_SAVE_PHOTO = "processAndSavePhoto"
        private const val OP_DELETE_PHOTO = "deletePhoto"
        private const val OP_RECORDS_FLOW = "recordsFlow"
        private const val OP_PHOTO_MAP_FLOW = "photoMapFlow"
        private const val OP_PHOTOS_FLOW = "photosFlow"
        private const val TABLE_CONDITION = "condition_db"

        // --- Restoration Keys ---
        private const val KEY_SEARCH_QUERY = "restoration_search_query"
        private const val KEY_SELECTED_ID = "restoration_selected_id"
        private const val KEY_IS_EDITING = "restoration_is_editing"
        private const val KEY_PREVIEW_URI = "restoration_preview_uri"
        private const val KEY_PREVIEW_CAPTION = "restoration_preview_caption"
        // Input Fields
        private const val KEY_IN_TITLE = "restoration_in_title"
        private const val KEY_IN_BODY = "restoration_in_body"
        private const val KEY_IN_AUTHOR = "restoration_in_author"
        private const val KEY_IN_TIME = "restoration_in_time"
        // Snapshot (Baseline) Fields
        private const val KEY_BASE_TITLE = "restoration_base_title"
        private const val KEY_BASE_BODY = "restoration_base_body"
        private const val KEY_BASE_AUTHOR = "restoration_base_author"
        private const val KEY_BASE_TIME = "restoration_base_time"
    }

    override val featureName: String = FEATURE_NAME

    /** 復元中であることを示すフラグ */
    private var isRestoring = false

    /** 各種ロード用の Job */
    private var recordsJob: Job? = null
    private var photoJob: Job? = null
    private var photoMapJob: Job? = null

    /** 保存処理用の Job */
    private var saveJob: Job? = null

    /** 削除処理用の Job */
    private var deleteJob: Job? = null

    /** 写真操作（追加・削除・再紐付け）用の Job */
    private var photoActionJob: Job? = null

    init {
        // --- State Restoration ---
        if (savedStateHandle.contains(KEY_RESTORE_VERSION)) {
            isRestoring = true
            restoreState()
        }

        // 初期化
        initializeFromNavigation()
        
        // 利用者情報の購読開始
        startObservePersonId()

        // 共通設定（デフォルト記録者名）の変更を購読し、新規作成時の入力に自動反映
        scope.launch {
            defaultRecorderName.collect { name ->
                updateUiState { state ->
                    val session = state.editSession
                    // 復元中、または既にユーザーが入力済みの場合は自動セットをバイパスする
                    if (!isRestoring && session.isEditing && IdLogic.isNew(session.selectedConditionId ?: "") && session.editInput.author.isBlank()) {
                        state.copy(editSession = session.copy(editInput = session.editInput.copy(author = name)))
                    } else {
                        state
                    }
                }
            }
        }
    }

    /**
     * SavedStateHandle から状態を復元します。
     */
    private fun restoreState() {
        val handle = savedStateHandle ?: return
        val query = handle.get<String>(KEY_SEARCH_QUERY) ?: ""
        val selectedId = handle.get<String>(KEY_SELECTED_ID)
        val isEditing = handle.get<Boolean>(KEY_IS_EDITING) ?: false
        val previewUri = handle.get<String>(KEY_PREVIEW_URI)
        val previewCaption = handle.get<String>(KEY_PREVIEW_CAPTION) ?: ""

        val input = ConditionEditInput(
            title = handle.get<String>(KEY_IN_TITLE) ?: "",
            condition = handle.get<String>(KEY_IN_BODY) ?: "",
            author = handle.get<String>(KEY_IN_AUTHOR) ?: "",
            recordTime = handle.get<Long>(KEY_IN_TIME)?.let { Instant.ofEpochMilli(it) }
        )

        val snapshot = if (handle.contains(KEY_BASE_TIME)) {
            ConditionEditInput(
                title = handle.get<String>(KEY_BASE_TITLE) ?: "",
                condition = handle.get<String>(KEY_BASE_BODY) ?: "",
                author = handle.get<String>(KEY_BASE_AUTHOR) ?: "",
                recordTime = handle.get<Long>(KEY_BASE_TIME)?.let { Instant.ofEpochMilli(it) }
            )
        } else null

        val isChanged = PersonConditionLogic.isChanged(input, snapshot)

        updateUiState { current ->
            current.copy(
                screenState = PersonConditionScreenState.Active,
                searchQuery = query,
                previewUri = previewUri,
                previewCaption = previewCaption,
                editSession = ConditionEditSession(
                    selectedConditionId = selectedId,
                    isEditing = isEditing,
                    editInput = input,
                    initialSnapshot = snapshot,
                    isChanged = isChanged,
                    isSaveEnabled = PersonConditionLogic.isValid(input) && isChanged
                )
            )
        }
    }

    /**
     * 復元対象の状態をバックアップします。
     */
    private fun backupRestorableState(state: PersonConditionUiState) {
        val handle = savedStateHandle ?: return
        val session = state.editSession
        handle[KEY_RESTORE_VERSION] = RESTORE_VERSION
        handle[KEY_SEARCH_QUERY] = state.searchQuery
        handle[KEY_SELECTED_ID] = session.selectedConditionId
        handle[KEY_IS_EDITING] = session.isEditing
        handle[KEY_PREVIEW_URI] = state.previewUri
        handle[KEY_PREVIEW_CAPTION] = state.previewCaption

        // Input
        handle[KEY_IN_TITLE] = session.editInput.title
        handle[KEY_IN_BODY] = session.editInput.condition
        handle[KEY_IN_AUTHOR] = session.editInput.author
        handle[KEY_IN_TIME] = session.editInput.recordTime?.toEpochMilli()

        // Snapshot
        session.initialSnapshot?.let { base ->
            handle[KEY_BASE_TITLE] = base.title
            handle[KEY_BASE_BODY] = base.condition
            handle[KEY_BASE_AUTHOR] = base.author
            handle[KEY_BASE_TIME] = base.recordTime?.toEpochMilli()
        }
    }

    private fun initializeFromNavigation() {
        val handle = savedStateHandle ?: return
        
        // 各画面遷移時の引数読み込み
        try {
            val args = handle.toRoute<Destination.PhotoFull>()
            updateUiState { it.copy(personId = args.personId, initialPhotoId = args.initialPhotoId) }
            setSelectedConditionId(args.conditionId)
        } catch (_: Exception) {}

        try {
            val args = handle.toRoute<Destination.PhotoPreview>()
            updateUiState { it.copy(personId = args.personId, previewUri = args.uri) }
            setSelectedConditionId(args.conditionId)
        } catch (_: Exception) {}

        try {
            val args = handle.toRoute<Destination.ConditionDetail>()
            updateUiState { it.copy(personId = args.personId) }
            args.query?.let { updateSearchQuery(it) }
        } catch (_: Exception) {}
    }

    override fun copyWithLoadingState(state: PersonConditionUiState, isLoading: Boolean, category: LoadingCategory): PersonConditionUiState {
        return when (category) {
            is LoadingCategory.Structural -> {
                val nextScreenState = if (!isLoading) {
                    (state.screenState as? PersonConditionScreenState.Error) ?: PersonConditionScreenState.Active
                } else {
                    (state.screenState as? PersonConditionScreenState.Active) ?: PersonConditionScreenState.Loading
                }
                state.copy(screenState = nextScreenState)
            }
            is LoadingCategory.Operation -> {
                if (!isLoading) state.copy(operation = PersonConditionOperation.Idle) else state
            }
            is LoadingCategory.Refresh -> {
                state.copy(operation = if (isLoading) PersonConditionOperation.Refreshing else PersonConditionOperation.Idle)
            }
            is LoadingCategory.Default -> {
                state.copy(isLoading = isLoading)
            }
        }
    }

    override fun updateWithPersonData(
        state: PersonConditionUiState,
        person: Person,
        summary: PersonCategorySummary?,
    ): PersonConditionUiState {
        val next = state.copy(personId = person.id)
        refreshRecords(next)
        refreshPhotoMap(next)
        return next
    }

    override fun onPrepareLoadPerson(state: PersonConditionUiState): PersonConditionUiState {
        if (isRestoring) return state

        return state.copy(
            personId = null,
            searchQuery = "",
            filteredRecords = persistentListOf(),
            editSession = ConditionEditSession()
        )
    }

    private fun refreshRecords(state: PersonConditionUiState) {
        val personId = state.personId ?: return
        recordsJob?.cancel()
        recordsJob = safeCollect(
            operation = OP_RECORDS_FLOW,
            mode = CollectMode.INITIAL,
            loadingCategory = LoadingCategory.Structural,
            contextBuilder = { tableName = TABLE_CONDITION },
            flowProvider = { 
                conditionRepository.getConditionAtVisitByPersonId(personId).catch { e ->
                    updateUiState { it.copy(screenState = PersonConditionScreenState.Error(e)) }
                    throw e
                }
            }
        ) { records ->
            updateUiState { current ->
                // UI 境界で ImmutableList に変換し、表示の安定性を確保
                val immutableRecords = records.toImmutableList()
                current.copy(
                    records = immutableRecords,
                    filteredRecords = ConditionLogic.filterRecords(immutableRecords, current.searchQuery).toImmutableList()
                )
            }
        }
    }

    private fun refreshPhotoMap(state: PersonConditionUiState) {
        val personId = state.personId ?: return
        photoMapJob?.cancel()
        photoMapJob = safeCollect(
            operation = OP_PHOTO_MAP_FLOW,
            mode = CollectMode.INITIAL,
            contextBuilder = { tableName = TABLE_CONDITION },
            flowProvider = { conditionRepository.getAllPhotosByPersonIdFlow(personId) }
        ) { photos ->
            val dbPhotos = conditionRepository.getAllConditionPhotosRaw()
            val existingConditionIds = conditionRepository.getAllConditionAtVisitIds()
            val physicalFiles = conditionRepository.getPhotoPhysicalFiles()

            val allUnassigned = jp.mydns.fujiwara.carememo.logic.feature.ConditionMaintenanceLogic.identifyUnassignedPhotos(
                dbPhotos = dbPhotos,
                existingConditionIds = existingConditionIds,
                physicalFiles = physicalFiles
            )

            val adoptableUnassigned = allUnassigned.filter { 
                (it.personId == personId) || (it.type == jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoType.FILE_ONLY) 
            }

            updateUiState { current ->
                val map = current.records.associateBy({ it.id }) { memo ->
                    photos.any { it.conditionId == memo.id }
                }
                current.copy(
                    conditionPhotoMap = map, 
                    unassignedPhotoCount = adoptableUnassigned.size,
                    availableUnassignedPhotos = adoptableUnassigned.toImmutableList()
                )
            }
        }
    }

    fun setSelectedConditionId(id: String?) {
        updateUiState {
            val next = it.copy(editSession = it.editSession.copy(selectedConditionId = id))
            if (id == null) {
                val cleared = next.copy(
                    editSession = ConditionEditSession()
                )
                clearRestorableState(
                    KEY_SELECTED_ID, KEY_IS_EDITING, KEY_IN_TITLE, KEY_IN_BODY, KEY_IN_AUTHOR, KEY_IN_TIME,
                    KEY_BASE_TITLE, KEY_BASE_BODY, KEY_BASE_AUTHOR, KEY_BASE_TIME, KEY_PREVIEW_URI, KEY_PREVIEW_CAPTION
                )
                cleared
            } else if (IdLogic.isNew(id)) {
                // 復元中の場合は、SSH からの値を優先する
                if (isRestoring) {
                    isRestoring = false // 復元処理を消費
                    next
                } else {
                    // 新規作成時は即座に編集セッションを開始
                    val now = Instant.now()
                    val initialInput = ConditionEditInput(
                        author = defaultRecorderName.value,
                        recordTime = now
                    )
                    val nextWithSession = next.copy(
                        editSession = ConditionEditSession(
                            isEditing = true,
                            selectedConditionId = id,
                            editInput = initialInput,
                            initialRecordTime = now,
                            initialSnapshot = initialInput,
                            isChanged = false,
                            isSaveEnabled = false
                        )
                    )
                    backupRestorableState(nextWithSession)
                    nextWithSession
                }
            } else {
                // 既存レコード選択時は閲覧モードから開始
                // 復元中の場合は既に state に値が入っているため、そのまま返す
                if (isRestoring) {
                    isRestoring = false
                    next
                } else {
                    val nextView = next.copy(editSession = next.editSession.copy(isEditing = false, initialRecordTime = null))
                    backupRestorableState(nextView)
                    nextView
                }
            }
        }

        photoJob?.cancel()
        if (id != null) {
            photoJob = safeCollect(
                operation = OP_PHOTOS_FLOW,
                mode = CollectMode.INITIAL,
                loadingCategory = LoadingCategory.Structural,
                contextBuilder = { tableName = TABLE_CONDITION },
                flowProvider = { conditionRepository.getConditionPhotosByConditionId(id) }
            ) { photos ->
                updateUiState { it.copy(currentConditionPhotos = photos.toImmutableList()) }
            }
        } else {
            updateUiState { it.copy(currentConditionPhotos = persistentListOf()) }
        }
    }

    fun updateSearchQuery(query: String) {
        updateUiState { current ->
            val next = current.copy(
                searchQuery = query,
                filteredRecords = ConditionLogic.filterRecords(current.records, query).toImmutableList()
            )
            backupRestorableState(next)
            next
        }
    }

    /**
     * 現在選択されているレコードの編集セッションを開始します。
     */
    fun startEditSession() {
        val session = currentState.editSession
        val recordId = session.selectedConditionId ?: return
        val record = currentState.records.find { it.id == recordId } ?: return

        val initialInput = ConditionEditInput(
            title = record.title ?: "",
            condition = record.condition ?: "",
            author = record.author,
            recordTime = record.recordTime
        )

        updateUiState {
            val next = it.copy(
                editSession = session.copy(
                    isEditing = true,
                    editInput = initialInput,
                    initialRecordTime = record.recordTime,
                    initialSnapshot = initialInput,
                    isChanged = false,
                    isSaveEnabled = false
                )
            )
            backupRestorableState(next)
            next
        }
    }

    /**
     * 編集をキャンセルします。新規なら閉じ、既存なら閲覧モードに戻ります。
     */
    fun cancelEditSession() {
        val recordId = currentState.editSession.selectedConditionId
        if (recordId != null && IdLogic.isNew(recordId)) {
            setSelectedConditionId(null)
        } else {
            updateUiState { 
                val next = it.copy(editSession = it.editSession.copy(isEditing = false))
                backupRestorableState(next)
                next
            }
        }
    }

    /**
     * 入力フォームの内容を更新し、変更検知とバリデーションを再計算します。
     */
    fun updateEditInput(update: (ConditionEditInput) -> ConditionEditInput) {
        updateUiState { state ->
            val session = state.editSession
            val nextInput = update(session.editInput)
            val isChanged = PersonConditionLogic.isChanged(nextInput, session.initialSnapshot)
            
            // 操作されたフィールドの追跡
            val nextTouched = getNewlyTouchedFields(session.editInput, nextInput, session.touchedFields)
            
            // フィールドごとのエラーを計算
            val errors = calculateFieldErrors(nextInput, nextTouched)

            val isSaveEnabled = PersonConditionLogic.isValid(nextInput) && isChanged

            val next = state.copy(
                editSession = session.copy(
                    editInput = nextInput,
                    isChanged = isChanged,
                    isSaveEnabled = isSaveEnabled,
                    touchedFields = nextTouched,
                    fieldErrors = errors
                )
            )
            backupRestorableState(next)
            next
        }
    }

    /** フィールドにフォーカスが当たったことを記録します */
    fun markFieldAsTouched(fieldName: String) {
        updateUiState { state ->
            val session = state.editSession
            val nextTouched = session.touchedFields + fieldName
            val errors = calculateFieldErrors(session.editInput, nextTouched)
            val next = state.copy(
                editSession = session.copy(
                    touchedFields = nextTouched,
                    fieldErrors = errors
                )
            )
            backupRestorableState(next)
            next
        }
    }

    private fun getNewlyTouchedFields(old: ConditionEditInput, next: ConditionEditInput, current: Set<String>): Set<String> {
        val touched = current.toMutableSet()
        if (old.title != next.title) touched.add("title")
        if (old.condition != next.condition) touched.add("condition")
        if (old.author != next.author) touched.add("author")
        if (old.recordTime != next.recordTime) touched.add("recordTime")
        return touched
    }

    private fun calculateFieldErrors(input: ConditionEditInput, touched: Set<String>): Map<String, Int?> {
        val errors = mutableMapOf<String, Int?>()
        
        val result = PersonConditionLogic.validate(input)
        if (result != PersonConditionValidationResult.SUCCESS) {
            val resId = translateValidationResultToResId(result)
            val field = when (result) {
                PersonConditionValidationResult.EMPTY_CONDITION,
                PersonConditionValidationResult.CONDITION_TOO_LONG -> "condition"
                PersonConditionValidationResult.EMPTY_AUTHOR -> "author"
                PersonConditionValidationResult.TITLE_TOO_LONG -> "title"
                PersonConditionValidationResult.INVALID_TIME -> "recordTime"
                else -> null
            }
            if (field != null && touched.contains(field)) {
                errors[field] = resId
            }
        }

        // 未来日チェック
        if (touched.contains("recordTime") && input.recordTime != null && input.recordTime.isAfter(Instant.now())) {
            errors["recordTime"] = R.string.common_err_future_date_not_allowed
        }

        return errors
    }

    private fun translateValidationResultToResId(result: PersonConditionValidationResult): Int? {
        return when (result) {
            PersonConditionValidationResult.EMPTY_CONDITION -> R.string.p_cond_err_empty_condition
            PersonConditionValidationResult.EMPTY_AUTHOR -> R.string.p_cond_err_empty_author
            PersonConditionValidationResult.CONDITION_TOO_LONG -> R.string.p_cond_err_condition_too_long
            PersonConditionValidationResult.TITLE_TOO_LONG -> R.string.p_cond_err_title_too_long
            PersonConditionValidationResult.INVALID_TIME -> R.string.common_err_invalid_date
            else -> null
        }
    }

    /**
     * 現在の入力内容で保存を実行します。
     */
    fun saveCurrentEdit(onSuccess: (String) -> Unit = {}) {
        // 二重実行防止
        if (saveJob?.isActive == true) return

        val session = currentState.editSession
        val input = session.editInput
        val conditionId = session.selectedConditionId ?: ""

        updateUiState { it.copy(operation = PersonConditionOperation.Saving) }
        
        saveJob = safeLaunch(
            operation = OP_SAVE,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
            }
        ) {
            val validationResult = PersonConditionLogic.validate(input)
            if (validationResult != PersonConditionValidationResult.SUCCESS) {
                translateValidationResult(validationResult)
            }

            val record = PersonConditionLogic.createRecord(requiredPersonId, conditionId, input)
            val isUpdate = !IdLogic.isNew(conditionId)

            val existing = conditionRepository.findConditionAtTime(record.personId, record.recordTime)
            val duplicateResult = ConditionLogic.validateDuplicate(record, existing)
            if (duplicateResult != ConditionValidationResult.SUCCESS) {
                translateValidationResult(duplicateResult)
            }

            conditionRepository.saveConditionAtVisit(record, isUpdate, featureName, OP_SAVE)

            if (!isUpdate) {
                conditionRepository.linkTemporaryPhotosToRecord(record.personId, record.id, featureName, "$OP_SAVE(link)")
            }

            showSnackbar(if (isUpdate) R.string.p_cond_msg_update_success else R.string.p_cond_msg_save_success)
            sendUiEvent(UiEvent.SaveSuccess(record.id))

            val finalId = record.id
            setSelectedConditionId(finalId)
            onSuccess(finalId)
        }
    }

    private fun translateValidationResult(result: PersonConditionValidationResult) {
        val messageRes = when (result) {
            PersonConditionValidationResult.EMPTY_CONDITION -> R.string.p_cond_err_empty_condition
            PersonConditionValidationResult.EMPTY_AUTHOR -> R.string.p_cond_err_empty_author
            PersonConditionValidationResult.CONDITION_TOO_LONG -> R.string.p_cond_err_condition_too_long
            PersonConditionValidationResult.TITLE_TOO_LONG -> R.string.p_cond_err_title_too_long
            PersonConditionValidationResult.INVALID_TIME -> R.string.main_err_edit_invalid_birthday
            else -> R.string.common_error_save
        }
        throw AppValidationException(R.string.common_error_title_save, messageRes, emptyList(), "Validation failed: $result")
    }

    private fun translateValidationResult(result: ConditionValidationResult) {
        val messageRes = if (result == ConditionValidationResult.DUPLICATE_TIME) R.string.common_err_duplicate_blocked_simple else R.string.common_error_save
        throw AppValidationException(R.string.common_error_title_save, messageRes, emptyList(), "Validation failed: $result")
    }

    fun deleteRecord(record: ConditionAtVisit) {
        // 二重実行防止
        if (deleteJob?.isActive == true) return

        updateUiState { it.copy(operation = PersonConditionOperation.Deleting(record.id)) }

        deleteJob = safeLaunch(
            operation = OP_DELETE,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = record.id
            }
        ) {
            conditionRepository.deleteConditionAtVisit(record, featureName, OP_DELETE)
            showSnackbar(R.string.p_cond_msg_delete_success)
        }
    }

    fun onPhotoCaptured(uri: Uri, conditionId: String) {
        // プレビュー画面に渡すために URI を保持
        updateUiState { current ->
            val next = current.copy(previewUri = uri.toString())
            backupRestorableState(next)
            next
        }
        sendViewEvent(PersonConditionViewEvent.NavigateToPhotoPreview(uri.toString(), conditionId))
    }

    /**
     * 写真プレビュー中のキャプションを更新します。
     */
    fun updatePreviewCaption(caption: String) {
        updateUiState { current ->
            val next = current.copy(previewCaption = caption)
            backupRestorableState(next)
            next
        }
    }

    /**
     * ナビゲーション引数から取得したコンテキストを ViewModel の状態に反映します。
     */
    fun setNavContext(
        personId: String,
        conditionId: String? = null,
        previewUri: String? = null,
        initialPhotoId: String? = null
    ) {
        updateUiState { current ->
            current.copy(
                personId = personId,
                previewUri = previewUri ?: current.previewUri,
                initialPhotoId = initialPhotoId ?: current.initialPhotoId,
                editSession = current.editSession.copy(selectedConditionId = conditionId ?: current.editSession.selectedConditionId)
            )
        }
        
        // 利用者情報のロードを開始（まだ開始されていない場合）
        if (personId != currentState.personId) {
            startObservePersonId()
        }

        // レコードIDが指定されている場合はデータのロードを誘発
        if (conditionId != null && conditionId != currentState.editSession.selectedConditionId) {
            setSelectedConditionId(conditionId)
        }
    }

    fun reattachUnassignedPhoto(conditionId: String, photoInfo: jp.mydns.fujiwara.carememo.logic.feature.UnassignedPhotoInfo) {
        if (IdLogic.isNew(conditionId)) return
        // 二重実行防止
        if (photoActionJob?.isActive == true) return

        updateUiState { it.copy(operation = PersonConditionOperation.PhotoProcessing) }

        photoActionJob = safeLaunch(
            operation = "reattachUnassignedPhoto",
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
            }
        ) {
            if (photoInfo.photoId != null) {
                conditionRepository.reattachPhotoToRecord(photoInfo.photoId, conditionId, featureName, "reattachUnassignedPhoto")
            } else {
                conditionRepository.adoptFileAsPhoto(
                    personId = requiredPersonId,
                    conditionId = conditionId,
                    photoFileName = photoInfo.photoFileName,
                    thumbnailFileName = photoInfo.thumbnailFileName,
                    capturedAt = photoInfo.capturedAt,
                    id = UUID.randomUUID().toString(),
                    featureName = featureName,
                    operation = "adoptFileAsPhoto"
                )
            }
            showSnackbar(R.string.p_cond_msg_photo_save_success)
        }
    }

    fun processAndSavePhoto(uri: Uri, conditionId: String, caption: String) {
        // 二重実行防止
        if (photoActionJob?.isActive == true) return

        updateUiState { it.copy(operation = PersonConditionOperation.PhotoProcessing) }

        photoActionJob = safeLaunch(
            operation = OP_SAVE_PHOTO,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = conditionId
                errorMessageRes = R.string.p_cond_err_photo_process_failure
            }
        ) {
            val (photoName, thumbName) = conditionRepository.processAndSavePhoto(uri)

            val photo = ConditionPhoto(
                id = UUID.randomUUID().toString(),
                conditionId = conditionId,
                personId = requiredPersonId,
                photoFileName = photoName,
                thumbnailFileName = thumbName,
                capturedAt = Instant.now(),
                caption = caption
            )
            conditionRepository.saveConditionPhoto(photo, isUpdate = false, featureName, OP_SAVE_PHOTO)
            showSnackbar(R.string.p_cond_msg_photo_save_success)
        }
    }

    fun deletePhoto(photo: ConditionPhoto) {
        // 二重実行防止
        if (photoActionJob?.isActive == true) return

        updateUiState { it.copy(operation = PersonConditionOperation.PhotoProcessing) }

        photoActionJob = safeLaunch(
            operation = OP_DELETE_PHOTO,
            loadingCategory = LoadingCategory.Operation,
            contextBuilder = {
                tableName = TABLE_CONDITION
                affectedId = photo.id
            }
        ) {
            conditionRepository.deleteConditionPhotoById(photo.id, photo.personId, featureName, OP_DELETE_PHOTO)
            conditionRepository.deletePhotoFiles(photo.photoFileName, photo.thumbnailFileName)
            showSnackbar(R.string.p_cond_msg_photo_delete_success)
        }
    }

    fun notifyPhotoError(message: String) {
        showError(message)
        
        // カメラ起動失敗や写真処理エラーを証跡として記録する
        scope.launch {
            auditLogRepository.log(
                featureName = featureName,
                operation = "cameraOrPhotoError",
                tableName = "external_storage",
                actionType = "INFO",
                affectedId = currentState.personId ?: "unknown",
                details = message,
                resultType = "EXTERNAL_ERROR"
            )
        }
    }

    /**
     * カメラ撮影用の一時URIを取得します。
     */
    fun getTempPhotoUri(): Uri {
        return conditionRepository.getTempPhotoUri()
    }

    suspend fun getAllPhotosForPerson(): List<ConditionPhoto> {
        return conditionRepository.getAllPhotosByPersonId(requiredPersonId)
    }

    fun navigateToPhotoFullScreen(photoId: String, conditionId: String) {
        sendViewEvent(PersonConditionViewEvent.NavigateToPhotoFullScreen(conditionId, photoId))
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val summaryRepository: PersonSummaryRepository,
        private val conditionRepository: ConditionRepository,
        private val userSettingsRepository: UserSettingsRepository,
        private val securitySession: SecuritySession,
        private val auditLogRepository: AuditLogRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PersonConditionViewModel(
                conditionRepository,
                personRepository,
                summaryRepository,
                userSettingsRepository,
                securitySession,
                auditLogRepository,
                savedStateHandle
            ) as T
        }
    }
}
