package jp.mydns.fujiwara.carememo.data

import androidx.compose.runtime.Immutable
import jp.mydns.fujiwara.carememo.logic.common.HealthAlertLevel
import java.time.Instant

/**
 * Data：AlertItem
 *
 * 【役割】
 * 健康データから抽出された「アラート（異常値や急激な変化）」一件の情報を保持します。
 * レポート画面での表示および、詳細画面への遷移に必要な情報を集約します。
 */
@Immutable
data class AlertItem(
    val id: String,          // 元データのID（遷移用）
    val personId: String,    // 利用者ID
    val personName: String,  // 利用者名（表示用：マスク済み）
    val category: Category,  // データのカテゴリ（健康、所見、服薬、血糖 等）
    val recordTime: Instant, // 記録日時
    val itemName: String,    // 項目名（フォールバック用）
    val itemNameResId: Int? = null, // 項目名の表示に使用するリソースID
    val valueText: String,   // 値と単位の文字列（例：「150/95 mmHg」「-3.5 kg」）
    val alertLevel: HealthAlertLevel, // 警告レベル（ALERT, WARNING 等）
    val message: String,     // 直接表示するメッセージ（リソースIDがない場合のフォールバック）
    val messageResId: Int? = null, // 表示に使用するリソースID
    val messageArgs: List<String> = emptyList() // リソースIDに使用する引数
)
