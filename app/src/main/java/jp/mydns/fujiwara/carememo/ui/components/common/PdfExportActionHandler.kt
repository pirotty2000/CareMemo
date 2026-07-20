package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel

/**
 * (B)系統：PDF出力アクションを共通で処理するハンドラーコンポーネント。
 */
@Composable
fun PdfExportActionHandler(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    category: Category,
    person: Person?,
    records: List<HistoryRecord>,
    viewModel: BaseUiStateViewModel<*, *>,
    onRequireAuthentication: (titleResId: Int?, subtitleResId: Int?, onSuccess: () -> Unit) -> Unit = { _, _, onSuccess -> onSuccess() },
    photos: List<ConditionPhoto> = emptyList()
) {
    val context = LocalContext.current

    if (showDialog && person != null) {
        PdfSettingsDialog(
            category = category,
            onDismiss = onDismiss,
            onRequireAuthentication = onRequireAuthentication
        ) { range, order, start, end, includePhotos, password ->
            onDismiss()
            // PDF出力後の共有UI（システムの共有シート）から戻ってきたときに
            // アプリロックがかからないようにバイパスを設定
            viewModel.setLockBypassEnabled(true)
            
            viewModel.safeLaunch(
                operation = "exportAndShare",
                contextBuilder = {
                    tableName = "pdf_export"
                }
            ) {
                PdfExporter.exportAndShare(
                    context = context,
                    person = person,
                    category = category,
                    records = records,
                    allPhotos = if (includePhotos) photos else emptyList(),
                    range = range,
                    order = order,
                    customStartDate = start,
                    customEndDate = end,
                    password = password
                )
            }
        }
    }
}
