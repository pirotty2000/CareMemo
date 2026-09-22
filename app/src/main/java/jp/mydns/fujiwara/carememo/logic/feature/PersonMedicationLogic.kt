package jp.mydns.fujiwara.carememo.logic.feature

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.MedicationRecord
import jp.mydns.fujiwara.carememo.viewmodel.PersonAwareState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import java.time.LocalDate
import java.time.YearMonth

/**
 * UI State：PersonMedicationUiState
 *
 * 【役割】
 * 服薬管理画面における、カレンダー表示、履歴テーブル、および選択されている期間（月）の状態を保持します。
 * 
 * @param screenState 構造的状態 (Loading / Active / Error)
 * @param operation 実行中の操作状態 (Idle / Refreshing / Syncing)
 * @param personId 対象の利用者ID
 * @param currentCategory 現在のカテゴリ（常に Category.MEDICATION）
 * @param selectedMonth 表示対象として選択されている年月
 * @param monthlyRecords 選択された月の全服薬記録リスト (Domain Content)
 * @param recordsByDate 日付（"yyyy-MM-dd"）をキーとしたマップ (Domain Content)
 * @param allRecords 全期間の服薬記録リスト (Domain Content)
 * @param dialogSession ダイアログ表示の詳細状態 (UI Content Details)
 */
@Immutable
data class PersonMedicationUiState(
    val screenState: PersonMedicationScreenState = PersonMedicationScreenState.Loading,
    val operation: PersonMedicationOperation = PersonMedicationOperation.Idle,

    override val personId: String? = null,
    override val currentCategory: Category = Category.MEDICATION,

    val selectedMonth: YearMonth = YearMonth.now(),
    val monthlyRecords: ImmutableList<MedicationRecord> = persistentListOf(),
    val recordsByDate: ImmutableMap<String, ImmutableList<MedicationRecord>> = persistentMapOf(),
    val allRecords: ImmutableList<MedicationRecord> = persistentListOf(),

    val dialogSession: MedicationDialogSession = MedicationDialogSession(),

    @Deprecated("Use screenState and operation")
    override val isLoading: Boolean = false
) : PersonAwareState

/**
 * 構造的状態 (Structural State)
 */
sealed interface PersonMedicationScreenState {
    data object Loading : PersonMedicationScreenState
    data object Active : PersonMedicationScreenState
    data class Error(val throwable: Throwable) : PersonMedicationScreenState
}

/**
 * 操作状態 (Operation State)
 */
sealed interface PersonMedicationOperation {
    data object Idle : PersonMedicationOperation
    data object Refreshing : PersonMedicationOperation
    data object Syncing : PersonMedicationOperation
}

/**
 * ダイアログ・セッション状態 (UI Content Details)
 */
@Immutable
data class MedicationDialogSession(
    val selectedDialogDate: LocalDate? = null,
    val dialogTempRecords: ImmutableList<MedicationRecord?> = persistentListOf(null, null, null, null)
)

/**
 * View Event：PersonMedicationViewEvent
 *
 * 【役割】
 * 服薬管理画面固有の、一過性のアクションや通知（特定の日付へのスクロール要求等）を定義します。
 */
sealed interface PersonMedicationViewEvent {
    /** 一覧画面へ戻る */
    // object NavigateBackToMain : PersonMedicationViewEvent
}

/**
 * Logic：PersonMedicationLogic
 *
 * 【役割】
 * 服薬管理画面における、データの集計、表示形式の変換、および期間制御に関するドメインロジックを提供します。
 *
 * 【主な機能】
 * ・服薬履歴リストから日付をキーとした集計マップ（Map<String, List>）への変換。
 * ・カレンダーやテーブル表示に最適化したデータ構造の構築。
 *
 * 【設計指針】
 * 1. UI層でのレンダリング効率（特にカレンダーの各セルからのデータ取得）を考慮し、
 *    List 形式のデータをあらかじめ日付キーの Map に変換しておく。
 * 2. 日付キーは、LocalDate.toString() と互換性のある "yyyy-MM-dd" 形式を一貫して使用する。
 */
object PersonMedicationLogic {
    /**
     * 服薬履歴レコードを、日付（"yyyy-MM-dd"）ごとのリストに変換します。
     * カレンダーのセル描画時などに、特定の日付のデータを O(1) で取得できるようにします。
     *
     * 【設計意図】
     * Logic レイヤーでは UI ライブラリに依存しないよう標準の [Map] および [List] を返します。
     * 不変コレクションへの変換は ViewModel の責務とします。
     *
     * @param records 変換対象の履歴レコードリスト
     * @return 日付文字列をキー、その日のレコードリスト（最大4スロット分）を値とするマップ
     */
    fun groupRecordsByDate(records: List<MedicationRecord>): Map<String, List<MedicationRecord>> {
        return records.groupBy { it.dosageDate }
    }
}
