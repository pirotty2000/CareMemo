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

    // バイタル判定ラベル
    const val VITAL_LABEL_NORMAL = "正常"
    const val VITAL_LABEL_HIGH_BP = "高血圧"
    const val VITAL_LABEL_LOW_BP = "低血圧"
    const val VITAL_LABEL_TACHYCARDIA = "頻脈"
    const val VITAL_LABEL_BRADYCARDIA = "徐脈"
    const val VITAL_LABEL_FEVER = "発熱"
    const val VITAL_LABEL_HYPOTHERMIA = "低体温"

    // 体温の判定基準
    const val TEMP_HIGH = 37.5          // 発熱
    const val TEMP_LOW = 35.5           // 低体温

    // 血糖値・HbA1cの判定基準
    const val GLUCOSE_NORMAL_HIGH = 99.0
    const val GLUCOSE_NORMAL_LOW = 70.0

    // 血糖値判定ラベル
    const val GLUCOSE_LABEL_LOW = "低血糖"
    const val GLUCOSE_LABEL_NORMAL = "良好"
    const val GLUCOSE_LABEL_HIGH = "高血糖"

    const val HBA1C_GOOD = 5.5
    const val HBA1C_PREDIABETES = 6.0
    const val HBA1C_DIABETES = 6.5

    // HbA1c判定ラベル
    const val HBA1C_LABEL_NORMAL = "正常"
    const val HBA1C_LABEL_NORMAL_HIGH = "正常高値"
    const val HBA1C_LABEL_PREDIABETES = "糖尿病予備軍"
    const val HBA1C_LABEL_DIABETES = "糖尿病型"

    // BMIの判定基準
    const val BMI_NORMAL_LOW = 18.5
    const val BMI_NORMAL_HIGH = 25.0
    const val BMI_OBESITY_1 = 30.0
    const val BMI_OBESITY_2 = 35.0
    const val BMI_OBESITY_3 = 40.0

    // 健康指標項目ラベル
    const val HEALTH_LABEL_HEIGHT = "身長"
    const val HEALTH_LABEL_WEIGHT = "体重"
    const val HEALTH_LABEL_BMI = "BMI"
    const val HEALTH_LABEL_BP = "血圧"
    const val HEALTH_LABEL_BP_SYSTOLIC = "血圧(上)"
    const val HEALTH_LABEL_BP_DIASTOLIC = "血圧(下)"
    const val HEALTH_LABEL_SYSTOLIC_SHORT = "上"
    const val HEALTH_LABEL_DIASTOLIC_SHORT = "下"
    const val HEALTH_LABEL_PULSE = "脈拍"
    const val HEALTH_LABEL_PULSE_SHORT = "脈"
    const val HEALTH_LABEL_BODY_TEMP = "体温"
    const val HEALTH_LABEL_GLUCOSE = "血糖値"
    const val HEALTH_LABEL_HBA1C = "HbA1c"
    const val HEALTH_LABEL_STATUS = "判定"

    // BMI判定ラベル
    const val BMI_LABEL_UNDERWEIGHT = "低体重"
    const val BMI_LABEL_NORMAL = "普通体重"
    const val BMI_LABEL_OBESITY_1 = "肥満(１度)"
    const val BMI_LABEL_OBESITY_2 = "肥満(２度)"
    const val BMI_LABEL_OBESITY_3 = "肥満(３度)"
    const val BMI_LABEL_OBESITY_4 = "肥満(４度)"

    // 指標の説明文
    val BP_EXPLANATION = """
        血圧グラフの見方：
        ・赤い線（血圧・上）が、上の薄い緑色の範囲（${BP_LOW_SYSTOLIC.toInt()}〜${BP_HIGH_SYSTOLIC.toInt()}）にあれば正常です。
        ・青い線（血圧・下）が、下の薄い緑色の範囲（${BP_LOW_DIASTOLIC.toInt()}〜${BP_HIGH_DIASTOLIC.toInt()}）にあれば正常です。
        
        ※ いずれかの線が緑色の範囲より上にあれば「$VITAL_LABEL_HIGH_BP」、下にあれば「$VITAL_LABEL_LOW_BP」の目安となります。
    """.trimIndent()

    val PULSE_EXPLANATION = """
        脈拍グラフの見方：
        ・線が薄い緑色の範囲（${PULSE_LOW.toInt()}〜${PULSE_HIGH.toInt()}）にあれば正常です。
        
        ※ 範囲より上にあれば「$VITAL_LABEL_TACHYCARDIA」、下にあれば「$VITAL_LABEL_BRADYCARDIA」の目安となります。
    """.trimIndent()

    val TEMP_EXPLANATION = """
        体温グラフの見方：
        ・線が薄い緑色の範囲（$TEMP_LOW〜$TEMP_HIGH）にあれば平熱の目安です。
        
        ※ 37.5℃以上は「$VITAL_LABEL_FEVER」、35.5℃未満は「$VITAL_LABEL_HYPOTHERMIA」の目安となります。
    """.trimIndent()

    val GLUCOSE_EXPLANATION = """
        血糖値グラフの見方：
        ・$GLUCOSE_LABEL_NORMAL：${GLUCOSE_NORMAL_LOW.toInt()}〜${GLUCOSE_NORMAL_HIGH.toInt()} mg/dL
        
        ※ 範囲より上にあれば「$GLUCOSE_LABEL_HIGH」、下にあれば「$GLUCOSE_LABEL_LOW」の目安となります。
    """.trimIndent()

    val HBA1C_EXPLANATION = """
        HbA1cグラフの見方：
        ・$HBA1C_LABEL_NORMAL：$HBA1C_GOOD％以下
        ・$HBA1C_LABEL_NORMAL_HIGH：$HBA1C_GOOD％超〜$HBA1C_PREDIABETES％未満
        ・$HBA1C_LABEL_PREDIABETES：$HBA1C_PREDIABETES％以上〜$HBA1C_DIABETES％未満
        ・$HBA1C_LABEL_DIABETES：$HBA1C_DIABETES％以上
    """.trimIndent()

    val BMI_EXPLANATION = """
        BMIグラフの見方：
        ・薄い緑色（${BMI_NORMAL_LOW}〜${BMI_NORMAL_HIGH}未満）：$BMI_LABEL_NORMAL
        ・薄い青色（${BMI_NORMAL_LOW}未満）：$BMI_LABEL_UNDERWEIGHT（低栄養への注意）
        ・薄い赤色（${BMI_OBESITY_2}以上）：高度な肥満（３度以上）
        
        【判定基準（WHO）】
        ・${BMI_NORMAL_LOW}未満：$BMI_LABEL_UNDERWEIGHT
        ・${BMI_NORMAL_LOW}〜${BMI_NORMAL_HIGH}未満：$BMI_LABEL_NORMAL
        ・${BMI_NORMAL_HIGH}〜${BMI_OBESITY_1}未満：$BMI_LABEL_OBESITY_1
        ・${BMI_OBESITY_1}〜${BMI_OBESITY_2}未満：$BMI_LABEL_OBESITY_2
        ・${BMI_OBESITY_2}〜${BMI_OBESITY_3}未満：$BMI_LABEL_OBESITY_3
        ・${BMI_OBESITY_3}以上：$BMI_LABEL_OBESITY_4
    """.trimIndent()
}
