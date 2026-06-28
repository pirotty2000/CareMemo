package jp.mydns.fujiwara.carememo.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.mydns.fujiwara.carememo.data.CareMemoRepository
import jp.mydns.fujiwara.carememo.data.ConditionAtVisit
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.UserSettingsRepository
import jp.mydns.fujiwara.carememo.utils.ImageUtils
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
import java.io.File
import java.time.Instant

/**
 * 所見メモ（体調記録）固有のロジック（写真処理など）を扱う ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonConditionViewModel(
    repository: CareMemoRepository,
    userSettingsRepository: UserSettingsRepository
) : PersonBaseViewModel(repository, userSettingsRepository) {

    private val _selectedConditionId = MutableStateFlow<Int?>(null)

    /**
     * 選択された所見メモに紐づく写真一覧
     */
    val currentConditionPhotos: StateFlow<List<ConditionPhoto>> = _selectedConditionId
        .flatMapLatest { id ->
            if (id != null) repository.getConditionPhotosByConditionId(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 所見メモIDと「写真の有無」のマップ（一覧表示でのアイコン制御用）
     */
    fun getConditionPhotoMap(records: StateFlow<List<Any>>): StateFlow<Map<Int, Boolean>> {
        return combine(_currentPerson, records) { person, recs ->
            person to recs
        }.flatMapLatest { (person, recs) ->
            if (person == null || recs.isEmpty() || recs.first() !is ConditionAtVisit) {
                flowOf(emptyMap())
            } else {
                repository.getAllPhotosByPersonIdFlow(person.id).map { photos ->
                    recs.filterIsInstance<ConditionAtVisit>().associate { memo ->
                        memo.id to photos.any { it.conditionId == memo.id }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun setSelectedConditionId(id: Int?) {
        _selectedConditionId.value = id
    }

    /**
     * 一時ファイルを削除します。
     */
    fun deleteTempFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                if (uri.scheme == "file" || uri.scheme == "content") {
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (ignore: Exception) {
                        uri.path?.let { File(it).delete() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 写真をリサイズ・保存し、データベースに登録します。
     */
    fun processAndSavePhoto(context: Context, uri: Uri, personId: Int, conditionId: Int, caption: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val fileNames = ImageUtils.processAndSaveImage(context, uri)
                if (fileNames != null) {
                    val (photoName, thumbName) = fileNames
                    val photo = ConditionPhoto(
                        conditionId = conditionId,
                        personId = personId,
                        photoFileName = photoName,
                        thumbnailFileName = thumbName,
                        capturedAt = Instant.now(),
                        caption = caption
                    )
                    repository.insertConditionPhoto(photo)
                    
                    // Exif情報（GPS等）が含まれている可能性がある一時ファイルを削除
                    if (uri.scheme == "file" || uri.scheme == "content") {
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (ignore: Exception) {
                            uri.path?.let { File(it).delete() }
                        }
                    }

                    showSnackbar("写真を保存しました")
                } else {
                    showError("保存エラー", "画像の処理に失敗しました。空き容量を確認してください。")
                }
            } catch (e: Exception) {
                showError("保存エラー", "写真の保存中にエラーが発生しました: ${e.localizedMessage}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 写真データおよび物理ファイルを削除します。
     */
    fun deletePhoto(context: Context, photo: ConditionPhoto) {
        viewModelScope.launch {
            try {
                repository.deleteConditionPhotoById(photo.id)
                ImageUtils.deleteImageFiles(context, photo.photoFileName, photo.thumbnailFileName)
                showSnackbar("写真を削除しました")
            } catch (e: Exception) {
                showError("削除エラー", "写真の削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    suspend fun getAllPhotosForPerson(personId: Int): List<ConditionPhoto> {
        return repository.getAllPhotosByPersonId(personId)
    }

    class Factory(
        private val repository: CareMemoRepository,
        private val userSettingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonConditionViewModel::class.java)) {
                return PersonConditionViewModel(repository, userSettingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
