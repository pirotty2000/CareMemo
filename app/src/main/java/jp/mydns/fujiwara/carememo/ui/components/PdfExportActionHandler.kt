package jp.mydns.fujiwara.carememo.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * PDF出力アクションを共通で処理するハンドラーコンポーネント。
 * 各画面での重複コードを排除するために作成。
 */
@Composable
fun PdfExportActionHandler(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    category: Category,
    person: Person?,
    records: List<HistoryRecord>,
    isNameMaskingEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: BaseViewModel,
    photos: List<ConditionPhoto> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showDialog && person != null) {
        PdfSettingsDialog(
            category = category,
            onDismiss = onDismiss
        ) { range, order, start, end, includePhotos, password ->
            onDismiss()
            // PDF出力後の共有UI（システムの共有シート）から戻ってきたときに
            // アプリロックがかからないようにバイパスを設定
            viewModel.setLockBypassEnabled(true)
            
            scope.launch {
                val success = PdfExporter.exportAndShare(
                    context = context,
                    person = person,
                    isNameMaskingEnabled = isNameMaskingEnabled,
                    category = category,
                    records = records,
                    allPhotos = if (includePhotos) photos else emptyList(),
                    range = range,
                    order = order,
                    customStartDate = start,
                    customEndDate = end,
                    password = password
                )
                
                if (!success) {
                    snackbarHostState.showSnackbar("PDFの出力に失敗しました。対象データがない可能性があります。")
                }
            }
        }
    }
}
