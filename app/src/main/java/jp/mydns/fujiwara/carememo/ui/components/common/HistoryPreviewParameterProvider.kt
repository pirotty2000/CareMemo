package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.ui.preview.MockData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Component：HistoryPreviewParameterProvider
 *
 * 【役割】
 * Compose プレビューにおいて、履歴リスト（HistoryRecord）のテストデータを供給するためのプロバイダーです。
 *
 * 【主な機能】
 * ・正常系（健康記録）、空状態、および別カテゴリ（所見メモ）のモックデータを順次提供。
 * ・不変リスト（ImmutableList）形式でのデータ供給。
 *
 * 【想定する利用場所】
 * ・HistoryComponents.kt のプレビュー。
 * ・各種詳細画面（健康、所見）のコンポーネントプレビュー。
 */
class HistoryPreviewParameterProvider : PreviewParameterProvider<ImmutableList<HistoryRecord>> {
    override val values: Sequence<ImmutableList<HistoryRecord>> = sequenceOf(
        MockData.healthRecords, // 正常系
        persistentListOf(),    // 空状態
        MockData.conditionRecords // 別データの正常系
    )
}
