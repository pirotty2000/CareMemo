package jp.mydns.fujiwara.carememo.logic.common

/**
 * Logic：AlertLogic
 *
 * 【役割】
 * 健康データから「異常（アラート）」を判定するための共通ロジックを提供します。
 * 単一レコードの閾値判定（HealthLogic の活用）に加え、時系列的な変化の判定も担当します。
 *
 * 【判定基準】
 * 1. 基本異常値：AppSpecifications に基づく各項目の閾値超え。
 * 2. 時系列異常：前回比 -3.0kg 以上の急激な体重減少。
 */
object AlertLogic {

    /**
     * 体重の急激な変化を判定します。
     * 前回値と比較して 3.0kg 以上減少している場合に ALERT を返します。
     *
     * @param currentWeight 今回の体重(kg)
     * @param previousWeight 前回の体重(kg)
     * @return 警告レベルと減少量(diff)のペア。異常なしなら NORMAL と 0.0
     */
    fun evaluateWeightLoss(currentWeight: Double?, previousWeight: Double?): Pair<HealthAlertLevel, Double> {
        if (currentWeight == null || previousWeight == null) return HealthAlertLevel.NORMAL to 0.0
        
        val diff = currentWeight - previousWeight
        // 3.0kg以上の減少（diff が -3.0 以下）
        return if (diff <= -3.0) {
            HealthAlertLevel.ALERT to diff
        } else {
            HealthAlertLevel.NORMAL to 0.0
        }
    }
}
