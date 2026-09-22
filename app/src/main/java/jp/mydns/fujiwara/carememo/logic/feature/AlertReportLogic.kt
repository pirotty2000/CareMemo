package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.R
import jp.mydns.fujiwara.carememo.data.*
import jp.mydns.fujiwara.carememo.data.repository.*
import jp.mydns.fujiwara.carememo.logic.common.*
import jp.mydns.fujiwara.carememo.ui.mapping.HealthDisplayMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Logic：AlertReportLogic
 *
 * 【役割】
 * アプリ内の全利用者・全健康データから異常（アラート）をスキャンして抽出するエンジンです。
 * 判定には AlertLogic および HealthLogic を使用します。
 */
class AlertReportLogic(
    private val personRepository: PersonRepository,
    private val healthRepository: HealthRepository
) {

    /**
     * 直近の全データからアラートをスキャンします。
     *
     * @param sinceDays 指定された日数以内のデータのみを対象とする（nullなら全期間の最新）
     * @param isNameMaskingEnabled 名前をマスクするかどうか
     * @return 抽出された AlertItem のリスト（日時の降順）
     */
    suspend fun scanAlerts(
        sinceDays: Int? = null,
        isNameMaskingEnabled: Boolean = true
    ): List<AlertItem> = withContext(Dispatchers.IO) {
        val allAlerts = mutableListOf<AlertItem>()
        val cutoff = sinceDays?.let { Instant.now().minus(it.toLong(), ChronoUnit.DAYS) }

        // 1. 全利用者の取得 (Flow の最新値を1回だけ取得)
        val persons = personRepository.getAllPersons().first()

        for (person in persons) {
            val maskedName = person.getMaskedName(isNameMaskingEnabled)

            // --- A. 身長・体重 (直近の BMI と 前回比の体重減少) ---
            val hwList = healthRepository.getHeightAndWeightByPersonId(person.id).first()
                .filter { it.deletedAt == null } // 念のため有効データのみ

            if (hwList.isNotEmpty()) {
                val latest = hwList[0] // 降順なので [0] が最新

                if (cutoff == null || !latest.recordTime.isBefore(cutoff)) {
                    // 最新データが判定対象
                    // 1. 体重減少の判定 (直近 vs その一つ前)
                    if (hwList.size >= 2) {
                        val previous = hwList[1]
                        val (level, diff) = AlertLogic.evaluateWeightLoss(latest.weight, previous.weight)
                        if (level != HealthAlertLevel.NORMAL) {
                            allAlerts.add(
                                latest.toAlertItem(
                                    personName = maskedName,
                                    category = Category.HEIGHT_AND_WEIGHT,
                                    itemName = "体重",
                                    itemNameResId = R.string.health_label_weight,
                                    valueText = "${HealthLogic.formatWeight(latest.weight)} ${AppSpecifications.Health.Weight.UNIT}",
                                    alertLevel = level,
                                    messageResId = R.string.alert_report_weight_loss,
                                    messageArgs = listOf("%.1f".format(diff))
                                )
                            )
                        }
                    }

                    // 2. BMI の判定 (最新データのみ)
                    val bmi = latest.calculateBMI()
                    val (status, level) = HealthLogic.evaluateBMI(bmi)
                    if (level == HealthAlertLevel.ALERT || level == HealthAlertLevel.WARNING) {
                        val bmiLabelRes = HealthDisplayMapper.getBmiLabel(status)
                        allAlerts.add(
                            latest.toAlertItem(
                                personName = maskedName,
                                category = Category.HEIGHT_AND_WEIGHT,
                                itemName = "BMI",
                                itemNameResId = R.string.health_label_bmi,
                                valueText = HealthLogic.formatBmi(bmi),
                                alertLevel = level,
                                messageResId = bmiLabelRes
                            )
                        )
                    }
                }
            }

            // --- B. バイタル (直近の血圧、脈拍、体温、SAT) ---
            val latestBp = healthRepository.getBpAndPulseByPersonId(person.id).first()
                .firstOrNull { it.deletedAt == null }

            if (latestBp != null && (cutoff == null || !latestBp.recordTime.isBefore(cutoff))) {
                val results = HealthLogic.evaluateVitalItems(
                    latestBp.bpSystolic, latestBp.bpDiastolic, latestBp.sat, latestBp.pulse, latestBp.bodyTemperature
                )
                for (res in results) {
                    if (res.second == HealthAlertLevel.ALERT || res.second == HealthAlertLevel.WARNING) {
                        val labelRes = HealthDisplayMapper.getVitalLabel(res.first)
                        val itemNameRes = when (res.first) {
                            VitalStatus.HIGH_BP, VitalStatus.LOW_BP -> R.string.health_label_bp
                            VitalStatus.TACHYCARDIA, VitalStatus.BRADYCARDIA -> R.string.health_label_pulse
                            VitalStatus.LOW_SAT -> R.string.health_label_sat
                            VitalStatus.FEVER, VitalStatus.HYPOTHERMIA -> R.string.health_label_body_temp
                            else -> R.string.common_category_vital
                        }
                        allAlerts.add(
                            latestBp.toAlertItem(
                                personName = maskedName,
                                category = Category.BP_AND_PULSE,
                                itemName = "バイタル",
                                itemNameResId = itemNameRes,
                                valueText = buildVitalValueText(latestBp, res.first),
                                alertLevel = res.second,
                                messageResId = labelRes
                            )
                        )
                    }
                }
            }

            // --- C. 血糖値・HbA1c (直近データ) ---
            val latestG = healthRepository.getGlucoseAndHbA1cByPersonId(person.id).first()
                .firstOrNull { it.deletedAt == null }

            if (latestG != null && (cutoff == null || !latestG.recordTime.isBefore(cutoff))) {
                // 血糖値
                val (gStatus, gLevel) = HealthLogic.evaluateGlucose(latestG.glucose)
                if (gLevel == HealthAlertLevel.ALERT || gLevel == HealthAlertLevel.WARNING) {
                    val labelRes = gStatus?.let { HealthDisplayMapper.getGlucoseLabel(it) }
                    allAlerts.add(
                        latestG.toAlertItem(
                            personName = maskedName,
                            category = Category.GLUCOSE_AND_HBA1C,
                            itemName = "血糖値",
                            itemNameResId = R.string.health_label_glucose,
                            valueText = "${HealthLogic.formatGlucose(latestG.glucose)} ${AppSpecifications.Health.BloodGlucose.UNIT}",
                            alertLevel = gLevel,
                            messageResId = labelRes
                        )
                    )
                }
                // HbA1c
                val (hStatus, hLevel) = HealthLogic.evaluateHbA1c(latestG.hba1c)
                if (hLevel == HealthAlertLevel.ALERT || hLevel == HealthAlertLevel.WARNING) {
                    val labelRes = hStatus?.let { HealthDisplayMapper.getHbA1cLabel(it) }
                    allAlerts.add(
                        latestG.toAlertItem(
                            personName = maskedName,
                            category = Category.GLUCOSE_AND_HBA1C,
                            itemName = "HbA1c",
                            itemNameResId = R.string.health_label_hba1c,
                            valueText = "${HealthLogic.formatHbA1c(latestG.hba1c)} ${AppSpecifications.Health.HbA1c.UNIT}",
                            alertLevel = hLevel,
                            messageResId = labelRes
                        )
                    )
                }
            }
        }

        // 全てを日時の降順でソートして返す
        allAlerts.sortedByDescending { it.recordTime }
    }

    /**
     * HistoryRecord から AlertItem への変換補助
     */
    private fun HistoryRecord.toAlertItem(
        personName: String,
        category: Category,
        itemName: String,
        itemNameResId: Int? = null,
        valueText: String,
        alertLevel: HealthAlertLevel,
        messageResId: Int? = null,
        messageArgs: List<String> = emptyList()
    ): AlertItem {
        return AlertItem(
            id = this.id,
            personId = this.personId,
            personName = personName,
            category = category,
            recordTime = this.recordTime,
            itemName = itemName,
            itemNameResId = itemNameResId,
            valueText = valueText,
            alertLevel = alertLevel,
            message = "",
            messageResId = messageResId,
            messageArgs = messageArgs
        )
    }

    /**
     * バイタルの異常項目に応じた値の文字列を構築します
     */
    private fun buildVitalValueText(bp: BpAndPulse, status: VitalStatus): String {
        return when (status) {
            VitalStatus.HIGH_BP, VitalStatus.LOW_BP -> 
                "${HealthLogic.formatBpValue(bp.bpSystolic)}/${HealthLogic.formatBpValue(bp.bpDiastolic)} ${AppSpecifications.Health.BloodPressure.UNIT}"
            VitalStatus.TACHYCARDIA, VitalStatus.BRADYCARDIA -> 
                "${HealthLogic.formatPulse(bp.pulse)} ${AppSpecifications.Health.Pulse.UNIT}"
            VitalStatus.LOW_SAT -> 
                "${HealthLogic.formatSat(bp.sat)} ${AppSpecifications.Health.OxygenSaturation.UNIT}"
            VitalStatus.FEVER, VitalStatus.HYPOTHERMIA -> 
                "${HealthLogic.formatBodyTemp(bp.bodyTemperature)} ${AppSpecifications.Health.BodyTemperature.UNIT}"
            else -> ""
        }
    }
}
