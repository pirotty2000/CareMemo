package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import jp.mydns.fujiwara.carememo.ui.preview.PersonConditionPreviewState
import kotlinx.collections.immutable.persistentListOf

/**
 * Component：PersonConditionPreviewParameterProvider
 *
 * 【役割】
 * Compose プレビューにおいて、所見記録画面（PersonConditionScreenContent 等）のテストデータを供給するためのプロバイダーです。
 *
 * 【主な機能】
 * ・正常系、ローディング中、空状態、詳細表示中、タブレット版表示等の多様なバリエーションを順次提供。
 */
class PersonConditionPreviewParameterProvider : PreviewParameterProvider<PersonConditionPreviewState> {
    override val values: Sequence<PersonConditionPreviewState> = sequenceOf(
        PersonConditionPreviewState(
            records = MockData.conditionRecords,
            selectedRecordId = null
        ),
        PersonConditionPreviewState(
            records = MockData.conditionRecords,
            isLoading = true
        ),
        PersonConditionPreviewState(
            records = persistentListOf()
        ),
        PersonConditionPreviewState(
            records = MockData.conditionRecords,
            selectedRecordId = "record-5" // Normal detail
        ),
        PersonConditionPreviewState(
            records = MockData.conditionRecords,
            isExpanded = true
        )
    )
}
