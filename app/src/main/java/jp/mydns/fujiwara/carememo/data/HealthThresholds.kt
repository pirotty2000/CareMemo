package jp.mydns.fujiwara.carememo.data

// 健康指標の判定基準
object HealthThresholds {
    // 高血圧・低血圧・頻脈・徐脈の判定ルールとなる数値
    const val BP_HIGH_SYSTOLIC = 140.0  // 高血圧：血圧（上）
    const val BP_HIGH_DIASTOLIC = 90.0  // 高血圧：血圧（下）
    const val BP_LOW_SYSTOLIC = 100.0   // 低血圧：血圧（上）
    const val BP_LOW_DIASTOLIC = 60.0   // 低血圧：血圧（下）
    const val PULSE_HIGH = 100.0        // 頻脈
    const val PULSE_LOW = 50.0          // 徐脈

    // 血糖値・HbA1cの判定基準
    const val GLUCOSE_NORMAL_HIGH = 99.0
    const val GLUCOSE_NORMAL_LOW = 70.0
    const val HBA1C_GOOD = 5.5
    const val HBA1C_PREDIABETES = 6.0
    const val HBA1C_DIABETES = 6.5

    // BMIの判定基準
    const val BMI_NORMAL_LOW = 18.5
    const val BMI_NORMAL_HIGH = 25.0
    const val BMI_OBESITY_1 = 30.0
    const val BMI_OBESITY_2 = 35.0
    const val BMI_OBESITY_3 = 40.0

    // 指標の説明文
    val BP_EXPLANATION = """
        血圧グラフの見方：
        ・赤い線（血圧・上）が、上の薄い緑色の範囲（${BP_LOW_SYSTOLIC.toInt()}〜${BP_HIGH_SYSTOLIC.toInt()}）にあれば正常です。
        ・青い線（血圧・下）が、下の薄い緑色の範囲（${BP_LOW_DIASTOLIC.toInt()}〜${BP_HIGH_DIASTOLIC.toInt()}）にあれば正常です。
        
        ※ いずれかの線が緑色の範囲より上にあれば「高血圧」、下にあれば「低血圧」の目安となります。
    """.trimIndent()

    val PULSE_EXPLANATION = """
        脈拍グラフの見方：
        ・線が薄い緑色の範囲（${PULSE_LOW.toInt()}〜${PULSE_HIGH.toInt()}）にあれば正常です。
        
        ※ 範囲より上にあれば「頻脈」、下にあれば「徐脈」の目安となります。
    """.trimIndent()

    val GLUCOSE_EXPLANATION = """
        血糖値グラフの見方：
        ・線が薄い緑色の範囲（${GLUCOSE_NORMAL_LOW.toInt()}〜${GLUCOSE_NORMAL_HIGH.toInt()}）にあれば正常範囲（空腹時などの目安）です。
    """.trimIndent()

    val HBA1C_EXPLANATION = """
        HbA1cグラフの見方：
        ・薄い緑色（$HBA1C_GOOD％以下）：良好
        ・薄い黄色（$HBA1C_PREDIABETES％〜6.4％）：糖尿病予備軍
        ・薄い赤色（$HBA1C_DIABETES％以上）：糖尿病が強く疑われる値
    """.trimIndent()

    val BMI_EXPLANATION = """
        BMIグラフの見方：
        ・薄い緑色（${BMI_NORMAL_LOW}〜${BMI_NORMAL_HIGH}未満）：普通体重
        ・薄い青色（${BMI_NORMAL_LOW}未満）：低体重（低栄養への注意）
        ・薄い赤色（${BMI_OBESITY_2}以上）：高度な肥満（３度以上）
        
        【判定基準（WHO）】
        ・${BMI_NORMAL_LOW}未満：低体重
        ・${BMI_NORMAL_LOW}〜${BMI_NORMAL_HIGH}未満：普通体重
        ・${BMI_NORMAL_HIGH}〜${BMI_OBESITY_1}未満：肥満(１度)
        ・${BMI_OBESITY_1}〜${BMI_OBESITY_2}未満：肥満(２度)
        ・${BMI_OBESITY_2}〜${BMI_OBESITY_3}未満：肥満(３度)
        ・${BMI_OBESITY_3}以上：肥満(４度)
    """.trimIndent()
}
