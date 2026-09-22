package jp.mydns.fujiwara.carememo.viewmodel

/**
 * 非同期処理の性質を分類するカテゴリ。
 * [BaseUiStateViewModel] の非同期制御基盤において、ロード状態の意味を ViewModel へ伝えるために使用します。
 */
sealed interface LoadingCategory {
    /** [ 構造的 ] 画面構築に必須のデータが未利用で、画面を構築できない状態。 */
    data object Structural : LoadingCategory

    /** [ 操作的 ] 保存、削除、追加など、ユーザー操作に伴う一時的な処理。 */
    data object Operation : LoadingCategory

    /** [ 更新的 ] すでに Content はあるが、最新状態との同期を行うための取得。 */
    data object Refresh : LoadingCategory

    /** [ 暫定 ] 既存画面との互換性のための移行期間限定カテゴリ。新規開発では非推奨。 */
    data object Default : LoadingCategory
}
