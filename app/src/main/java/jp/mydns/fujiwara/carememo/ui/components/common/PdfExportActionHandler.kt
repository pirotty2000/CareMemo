package jp.mydns.fujiwara.carememo.ui.components.common

/**
 * Component：PdfExportActionHandler
 *
 * 【役割】：
 * PDF出力に関連する一連のユーザーアクション（設定ダイアログ表示、出力処理実行、結果通知）を共通で処理する。
 *
 * 【主な機能】：
 * ・PdfSettingsDialog の表示制御。
 * ・PdfExporter と連携した PDF 生成および共有インテントの呼び出し。
 * ・出力成功/失敗時のスナックバーによるフィードバック。
 * ・外部共有画面からの復帰時におけるアプリロックのバイパス制御。
 *
 * 【想定する利用場所】：
 * 各カテゴリ（健康、所見、服薬）の詳細画面。
 *
 * 【このコンポーネントでは行わないこと】：
 * PDFの描画ロジック自体（PdfExporter が担当）や、設定ダイアログの内部 UI 定義（PdfSettingsDialog が担当）。
 *
 * 【公開composable】：
 * PdfExportActionHandler
 */

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
    snackbarHostState: SnackbarHostState,
    viewModel: BaseViewModel,
    onRequireAuthentication: (titleResId: Int?, subtitleResId: Int?, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    photos: List<ConditionPhoto> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            
            scope.launch {
                val success = PdfExporter.exportAndShare(
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
                
                if (!success) {
                    snackbarHostState.showSnackbar("PDFの出力に失敗しました。対象データがない可能性があります。")
                }
            }
        }
    }
}
