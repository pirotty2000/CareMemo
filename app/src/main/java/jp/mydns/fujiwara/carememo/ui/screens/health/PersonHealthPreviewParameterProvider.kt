package jp.mydns.fujiwara.carememo.ui.screens.health

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import jp.mydns.fujiwara.carememo.ui.preview.PersonHealthPreviewState
import kotlinx.collections.immutable.persistentListOf

class PersonHealthPreviewParameterProvider : PreviewParameterProvider<PersonHealthPreviewState> {
    override val values: Sequence<PersonHealthPreviewState> = sequenceOf(
        PersonHealthPreviewState(
            category = Category.BP_AND_PULSE,
            records = MockData.healthRecords,
            selectedRecordId = null
        ),
        PersonHealthPreviewState(
            category = Category.BP_AND_PULSE,
            records = MockData.healthRecords,
            isLoading = true
        ),
        PersonHealthPreviewState(
            category = Category.BP_AND_PULSE,
            records = persistentListOf(),
            person = MockData.person
        ),
        PersonHealthPreviewState(
            category = Category.GLUCOSE_AND_HBA1C,
            records = MockData.healthRecords,
            selectedRecordId = "record-3" // Glucose record
        )
    )
}
