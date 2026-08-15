package jp.mydns.fujiwara.carememo.data.spec

/**
 * Spec：HealthSpecifications
 *
 * 【役割】
 * 健康記録（カテゴリA, C, D）に関連する閾値、バリデーションルール、およびグラフ表示設定を定義します。
 */
object HealthSpecifications {

    // ----- 身長・体重 ------------------------------------------------------------------------------
    /** BMI */
    object BodyMassIndex {
        /**
         * BMIの判断基準
         *   (1)低体重：18.5未満
         *   (2)普通体重：18.5以上、25.0未満
         *   (3)肥満(1度)：25.0以上、30.0未満
         *   (4)肥満(2度)：30.0以上、35.0未満
         *   (5)肥満(3度)：35.0以上、40.0未満
         *   (6)肥満(4度)：40.0以上
         */
        const val THRESHOLD_UNDERWEIGHT = 18.5
        const val THRESHOLD_NORMAL_UPPER = 25.0
        const val THRESHOLD_OBESITY_1 = 30.0
        const val THRESHOLD_OBESITY_2 = 35.0
        const val THRESHOLD_OBESITY_3 = 40.0

        const val THRESHOLD_GRAPH_NORMAL_UPPER = 25.0
        const val THRESHOLD_GRAPH_NORMAL_LOWER = 18.5

        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 2.0         // Y軸の目盛りの刻み幅
            const val DEFAULT_MIN = 20.0        // データ空時のデフォルト最小値
            const val DEFAULT_MAX = 25.0        // データ空時のデフォルト最大値
            const val VIEW_PADDING = 1.0        // データの最大/最小値から表示端までの余白
            const val RANGE_MAX = 100.0         // ハイライトの終点
            const val RANGE_MIN = 0.0           // ハイライトの始点
        }
    }

    // ----- バイタル --------------------------------------------------------------------------------
    /** 血圧 */
    object BloodPressure {
        /**
         * 血圧の判断基準
         *   (1)高血圧：血圧(上)が140以上、または血圧(下)が90以上
         *   (2)低血圧：血圧(上)が100未満、または血圧(下)が60未満
         */
        const val THRESHOLD_HIGH_SYSTOLIC = 140.0
        const val THRESHOLD_HIGH_DIASTOLIC = 90.0
        const val THRESHOLD_LOW_SYSTOLIC = 100.0
        const val THRESHOLD_LOW_DIASTOLIC = 60.0

        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 10.0        // Y軸の目盛りの刻み幅
            const val Y_MIN_VIEW_LIMIT = 70.0   // データが少なくても最低限表示したい下限値
            const val Y_MAX_VIEW_LIMIT = 160.0  // データが少なくても最低限表示したい上限値
            const val RANGE_MAX = 300.0         // ハイライト範囲（ALERT等）の描画上の終点
            const val RANGE_MIN = 0.0           // ハイライト範囲（INFO等）の描画上の始点
        }

        const val DIGITS_INT = 3
        const val MAX_VALUE = 300.0
        const val MIN_VALUE = 20.0
        const val UNIT = "mmHg"
    }

    /** 脈拍 */
    object Pulse {
        /**
         * 脈拍の判断基準
         *   (1)頻脈：100回/分 以上
         *   (2)徐脈： 50回/分 以下
         */
        const val THRESHOLD_HIGH = 100.0
        const val THRESHOLD_LOW = 50.0

        const val THRESHOLD_GRAPH_NORMAL_UPPER = 100.0
        const val THRESHOLD_GRAPH_NORMAL_LOWER = 50.0

        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 10.0        // Y軸の目盛りの刻み幅
            const val Y_MIN_VIEW_LIMIT = 40.0   // 最低限表示したい下限値
            const val Y_MAX_VIEW_LIMIT = 110.0  // 最低限表示したい上限値
            const val RANGE_MAX = 300.0         // ハイライトの終点
            const val RANGE_MIN = 0.0           // ハイライトの始点
        }

        const val DIGITS_INT = 3
        const val MAX_VALUE = 300.0
        const val MIN_VALUE = 20.0
        const val UNIT = "bpm"
    }

    /** 酸素飽和度(SAT) */
    object OxygenSaturation {
        /**
         * 酸素飽和度の判断基準
         *   (1)呼吸不全：90.0%以下
         */
        const val THRESHOLD_LOW = 90.0

        const val THRESHOLD_GRAPH_NORMAL_LOWER = 90.0

        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 2.0         // Y軸の刻み幅（SATは変化が小さいため細かく設定）
            const val Y_MIN_VIEW_LIMIT = 85.0   // 最低限表示したい下限値
            const val Y_MAX_VIEW_LIMIT = 100.0  // 最低限表示したい上限値（100%固定）
            const val RANGE_MIN = 0.0           // 異常範囲(ALERT)の始点
            const val RANGE_MAX = 100.0         // 正常範囲の終点
        }

        const val DIGITS_INT = 3
        const val MAX_VALUE = 100.0
        const val MIN_VALUE = 50.0
        const val UNIT = "%"
    }

    /** 体温 */
    object BodyTemperature {
        /**
         * 体温の判断基準
         *   (1)熱発：37.5℃以上
         *   (2)低体温：35.0℃以下
         */
        const val THRESHOLD_HIGH = 37.5
        const val THRESHOLD_LOW = 35.0

        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 0.5         // Y軸の刻み幅（体温は0.1単位の変化が重要なため細かく設定）
            const val Y_MIN_VIEW_LIMIT = 34.0   // 最低限表示したい下限値
            const val Y_MAX_VIEW_LIMIT = 40.0   // 最低限表示したい上限値
            const val RANGE_MAX = 50.0          // ハイライトの終点
            const val RANGE_MIN = 0.0           // ハイライトの始点
        }

        const val DIGITS_INT = 2
        const val DIGITS_DEC = 1
        const val MAX_VALUE = 45.0
        const val MIN_VALUE = 30.0
        const val UNIT = "℃"
    }

    // ----- 血糖値・HbA1c ---------------------------------------------------------------------------
    /** 血糖値 */
    object BloodGlucose {
        /**
         * 空腹時血糖値の判断基準
         *   (1)低血糖  ： 70mg/dL未満：INFO
         *   (2)正常型  ： 70mg/dL以上、99mg/dL以下(アプリでは100mg/dL未満)：NORMAL
         *   (3)正常高値：100mg/dL以上、109mg/dL以下(アプリでは110mg/dL未満)：INFO
         *   (4)予備群  ：110mg/dL以上、125mg/dL以下(アプリでは126mg/dL未満)：WARNING
         *   (5)糖尿病型：126mg/dL以上：ALERT
         */
        const val THRESHOLD_HIGH = 126.0
        const val THRESHOLD_PREDIABETES = 110.0
        const val THRESHOLD_NORMAL_UPPER = 100.0
        const val THRESHOLD_LOW = 70.0

        const val THRESHOLD_GRAPH_NORMAL_UPPER = 100.0
        const val THRESHOLD_GRAPH_NORMAL_LOWER = 70.0

        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 50.0        // Y軸の目盛りの刻み幅
            const val DEFAULT_MIN = 70.0        // データ空時のデフォルト最小値
            const val DEFAULT_MAX = 110.0       // データ空時のデフォルト最大値
            const val VIEW_PADDING = 10.0       // データの最大/最小値から表示端までの余白
            const val RANGE_MAX = 1000.0        // ハイライトの終点（非常に高い値をカバー）
            const val RANGE_MIN = 0.0           // ハイライトの始点
        }

        const val DIGITS_INT = 3
        const val MAX_VALUE = 999.0
        const val MIN_VALUE = 10.0
        const val UNIT = "mg/dL"
    }

    /** HbA1c */
    object HbA1c {
        /**
         * HbA1c
         *   (1)正常値：5.5%以下
         *   (2)予備群：5.6%以上、6.4％以下(アプリでは6.5％未満)
         *   (3)糖尿病型：6.5％以上
         */
        const val THRESHOLD_NORMAL_UPPER = 5.5
        const val THRESHOLD_DIABETES = 6.5

        const val THRESHOLD_GRAPH_NORMAL_UPPER = 5.5

        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 0.5         // Y軸の目盛りの刻み幅
            const val DEFAULT_MIN = 5.0         // データ空時のデフォルト最小値
            const val DEFAULT_MAX = 6.0         // データ空時のデフォルト最大値
            const val VIEW_PADDING = 0.5        // データの最大/最小値から表示端までの余白
            const val RANGE_MAX = 20.0          // ハイライトの終点
            const val RANGE_MIN = 0.0           // ハイライトの始点
        }

        const val DIGITS_INT = 2
        const val DIGITS_DEC = 1
        const val MAX_VALUE = 20.0
        const val MIN_VALUE = 3.0
        const val UNIT = "%"
    }


    /** 身長 */
    object Height {
        const val DIGITS_INT = 3
        const val DIGITS_DEC = 1
        const val MAX_VALUE = 250.0
        const val MIN_VALUE = 50.0
        const val UNIT = "cm"
    }

    /** 体重 */
    object Weight {
        /** グラフ描画設定 */
        object Graph {
            const val Y_AXIS_STEP = 5.0         // Y軸の目盛りの刻み幅
            const val DEFAULT_MIN = 50.0        // データ空時のデフォルト最小値
            const val DEFAULT_MAX = 60.0        // データ空時のデフォルト最大値
            const val VIEW_PADDING = 2.0        // データの最大/最小値から表示端までの余白
        }

        const val DIGITS_INT = 3
        const val DIGITS_DEC = 1
        const val MAX_VALUE = 300.0
        const val MIN_VALUE = 1.0
        const val UNIT = "kg"
    }
}
