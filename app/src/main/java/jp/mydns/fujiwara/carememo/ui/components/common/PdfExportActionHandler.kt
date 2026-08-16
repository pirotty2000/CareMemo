package jp.mydns.fujiwara.carememo.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import jp.mydns.fujiwara.carememo.data.Category
import jp.mydns.fujiwara.carememo.data.ConditionPhoto
import jp.mydns.fujiwara.carememo.data.HistoryRecord
import jp.mydns.fujiwara.carememo.data.Person
import jp.mydns.fujiwara.carememo.utils.PdfExporter
import jp.mydns.fujiwara.carememo.viewmodel.BaseUiStateViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Component：PdfExportActionHandler
 *
 * 【役割】
 * PDF出力（帳票生成）および共有アクションを共通で処理するハンドラーコンポーネントです。
 * UIを持たない論理的なコンポーネントですが、設定ダイアログの表示と実際の出力処理の橋渡しを行います。
 * 
 * ※ UIを持たないため、modifier 引数は持ちません。
 *
 * 【主な機能】
 * ・PDF出力設定ダイアログ（PdfSettingsDialog）の表示管理。
 * ・生体認証等による保護が必要な場合のフック機能。
 * ・PDF生成処理のバックグラウンド実行と、システム共有シートの呼び出し。
 * ・共有シートからの復帰時にアプリロックがかからないようバイパス設定を制御。
 *
 * 【想定する利用場所】
 * 各利用者詳細画面（健康記録、所見メモ等）のツールバーまたはメニューにある「PDF出力」アクション。
 *
 * 【注意点】
 * PDF生成は重い処理であるため、ViewModel の safeLaunch を通じて実行され、
 * 進捗状況は ViewModel の共通エラーハンドリング機構によって管理されます。
 */

/**
 * 全体像：PDF出力フロー（PDF Export Flow）
 *
 * ■ 各 Screen (PersonHealthScreen 等) の TopAppBar アクション
 * │
 * └─ [1] PdfExportActionHandler (★本コンポーネント：制御ロジックの集約)
 *      ├─ [2] PdfSettingsDialog (設定 UI：期間、並び順、パスワード)
 *      │    └─ [3] PdfSettingsContent (DatePicker, TextField 等)
 *      └─ [4] PdfExporter.exportAndShare (出力実行 ➔ システム共有シート)
 *           └─ ViewModel.safeLaunch (非同期実行と例外捕捉)
 */

/**
 * PDF出力アクションをハンドリングします。
 *
 * @param showDialog ダイアログを表示するかどうか
 * @param onDismiss ダイアログを閉じる際のコールバック
 * @param category 出力対象のカテゴリ（健康記録、所見等）
 * @param person 出力対象の利用者情報
 * @param records 出力対象の履歴データ
 * @param viewModel 非同期処理とバイパス設定を管理するための ViewModel
 * @param onRequireAuthentication 認証が必要な場合に呼び出されるコールバック
 * @param photos 所見メモ等の場合に含まれる写真データのリスト
 */
@Composable
fun PdfExportActionHandler(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    category: Category,
    person: Person?,
    records: ImmutableList<HistoryRecord>,
    viewModel: BaseUiStateViewModel<*, *>,
    onRequireAuthentication: (titleResId: Int?, subtitleResId: Int?, onSuccess: () -> Unit) -> Unit = { _, _, onSuccess -> onSuccess() },
    photos: ImmutableList<ConditionPhoto> = persistentListOf()
) {
    val context = LocalContext.current

    if (showDialog && person != null) {
        // PDFの出力範囲やパスワードを設定するダイアログを表示
        PdfSettingsDialog(
            category = category,
            onDismiss = onDismiss,
            onRequireAuthentication = onRequireAuthentication
        ) { range, order, start, end, includePhotos, password ->
            onDismiss()

            // 【重要】PDF出力後の共有UI（システムの共有シート）から戻ってきたときに、
            // アプリが「バックグラウンドから復帰した」と判定されてロックがかからないよう、
            // 一時的にロックバイパスを有効化する。
            viewModel.setLockBypassEnabled(true)
            
            // ViewModel の安全なコルーチン起動を使用して PDF 出力を実行
            viewModel.safeLaunch(
                operation = "exportAndShare",
                contextBuilder = {
                    tableName = "pdf_export"
                }
            ) {
                // PDF 生成およびシステムの共有シート呼び出し
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
