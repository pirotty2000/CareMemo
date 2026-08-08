package jp.mydns.fujiwara.carememo.ui.screens.condition

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import jp.mydns.fujiwara.carememo.ui.preview.PersonConditionPreviewState
import kotlinx.collections.immutable.persistentListOf

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
