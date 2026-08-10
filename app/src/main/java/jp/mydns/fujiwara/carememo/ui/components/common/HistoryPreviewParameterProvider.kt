package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

class HistoryPreviewParameterProvider : PreviewParameterProvider<ImmutableList<HistoryRecord>> {
    override val values: Sequence<ImmutableList<HistoryRecord>> = sequenceOf(
        MockData.healthRecords, // 正常系
        persistentListOf(),    // 空状態
        MockData.conditionRecords // 別データの正常系
    )
}
