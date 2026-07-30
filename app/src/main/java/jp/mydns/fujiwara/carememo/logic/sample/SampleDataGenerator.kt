package jp.mydns.fujiwara.carememo.logic.sample

import jp.mydns.fujiwara.carememo.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.Random
import kotlin.math.roundToLong

/**
 * サンプルデータ（12名分）を生成するロジック
 */
object SampleDataGenerator {

    fun generate(): CareMemoBackup {
        val random = Random(42) // シード固定で再現性を確保しつつランダム化
        
        val persons = mutableListOf<PersonBackupDto>()
        val heightAndWeights = mutableListOf<HeightAndWeightBackupDto>()
        val bpAndPulses = mutableListOf<BpAndPulseBackupDto>()
        val glucoseAndHbA1cs = mutableListOf<GlucoseAndHbA1cBackupDto>()
        val conditionAtVisits = mutableListOf<ConditionAtVisitBackupDto>()
        val conditionPhotos = mutableListOf<ConditionPhotoBackupDto>()
        val medicationRecords = mutableListOf<MedicationRecordBackupDto>()

        val wafumei = listOf(
            "睦月" to "むつき", "如月" to "きさらぎ", "弥生" to "やよい", "卯月" to "うづき",
            "皐月" to "さつき", "水無月" to "みなづき", "文月" to "ふみづき", "葉月" to "はづき",
            "長月" to "ながつき", "神無月" to "かんなづき", "霜月" to "しもつき", "師走" to "しわす"
        )

        val names = listOf(
            "太郎" to "たろう", "はな" to "はな", "節子" to "せつこ", "二郎" to "じろう",
            "幸子" to "さちこ", "涼子" to "りょうこ", "健三" to "けんぞう", "恵美" to "えみ",
            "陽子" to "ようこ", "きく" to "きく", "四郎" to "しろう", "まり" to "まり"
        )

        val ages = listOf(80, 75, 92, 68, 85, 70, 77, 82, 65, 88, 90, 73)

        val categories = listOf(
            setOf("HW", "BP", "GL", "CO", "ME"), // 1: ALL
            setOf("HW", "BP", "GL", "CO", "ME"), // 2: ALL
            setOf("HW", "BP", "GL", "CO", "ME"), // 3: ALL
            setOf("HW"),                         // 4
            setOf("BP"),                         // 5
            setOf("GL"),                         // 6
            setOf("ME"),                         // 7
            setOf("CO"),                         // 8
            setOf("HW", "BP"),                   // 9
            setOf("BP", "GL"),                   // 10
            setOf("GL", "CO"),                   // 11
            setOf("CO", "ME")                    // 12
        )

        val startDate = LocalDate.of(2025, 1, 1)
        val endDate = LocalDate.of(2026, 7, 31)

        for (i in 0 until 12) {
            val personId = UUID.randomUUID().toString()
            val month = i + 1
            val birthday = LocalDate.of(2025 - ages[i], month, 1)
                .atTime(LocalTime.NOON)
                .toInstant(ZoneOffset.UTC)

            persons.add(
                PersonBackupDto(
                    id = personId,
                    lastName = wafumei[i].first,
                    firstName = names[i].first,
                    lastNameFurigana = wafumei[i].second,
                    firstNameFurigana = names[i].second,
                    birthday = birthday,
                    note = "サンプルデータ",
                    updatedAt = Instant.now()
                )
            )

            val cats = categories[i]

            // --- 個人ごとのベース値設定 ---
            val baseHeight = 145.0 + random.nextDouble() * 25.0
            var currentWeight = 40.0 + random.nextDouble() * 35.0
            val weightTrend = (random.nextDouble() - 0.5) * 0.1 // 緩やかな体重増減トレンド
            
            val baseSystolic = 110 + random.nextInt(40) // 110〜150
            val baseDiastolic = 60 + random.nextInt(30) // 60〜90
            val basePulse = 60 + random.nextInt(20)     // 60〜80
            
            val baseGlucose = 85 + random.nextInt(60)   // 85〜145
            var currentHbA1c = baseGlucose / 20.0

            // データ生成ループ
            var currentMonthDate = startDate.withDayOfMonth(1)
            while (!currentMonthDate.isAfter(endDate)) {
                
                // 月に1件（月初め付近にジッターを加える）
                val dayOffsets = listOf(0)
                for (offset in dayOffsets) {
                    val jitter = random.nextInt(4) // 0〜3日のズレ
                    val recordDate = currentMonthDate.plusDays(offset.toLong() + jitter)
                    if (recordDate.isAfter(endDate)) break
                    
                    // 記録時間もランダム化 (8:00〜11:00)
                    val recordTime = recordDate.atTime(LocalTime.of(8 + random.nextInt(3), random.nextInt(60)))
                        .toInstant(ZoneOffset.UTC)

                    if (cats.contains("HW")) {
                        currentWeight += weightTrend + (random.nextDouble() - 0.5) * 0.4
                        heightAndWeights.add(
                            HeightAndWeightBackupDto(
                                id = UUID.randomUUID().toString(),
                                personId = personId,
                                height = (baseHeight * 10).roundToLong() / 10.0,
                                weight = (currentWeight * 10).roundToLong() / 10.0,
                                recordTime = recordTime
                            )
                        )
                    }

                    if (cats.contains("BP")) {
                        val sys = baseSystolic + random.nextInt(15) - 7
                        val dia = baseDiastolic + random.nextInt(10) - 5
                        bpAndPulses.add(
                            BpAndPulseBackupDto(
                                id = UUID.randomUUID().toString(),
                                personId = personId,
                                bpSystolic = sys,
                                bpDiastolic = dia,
                                pulse = basePulse + random.nextInt(10) - 5,
                                sat = if (random.nextInt(10) == 0) 95 + random.nextInt(3) else 97 + random.nextInt(3),
                                bodyTemperature = 36.0 + random.nextInt(8) * 0.1,
                                recordTime = recordTime
                            )
                        )
                    }

                    if (cats.contains("GL")) {
                        val glu = baseGlucose + random.nextInt(40) - 20
                        // HbA1cは血糖値に相関するが、過去数ヶ月の平均なので動きを鈍くする(慣性を持たせる)
                        val targetHbA1c = glu / 20.0
                        currentHbA1c = (currentHbA1c * 0.8) + (targetHbA1c * 0.2)
                        glucoseAndHbA1cs.add(
                            GlucoseAndHbA1cBackupDto(
                                id = UUID.randomUUID().toString(),
                                personId = personId,
                                glucose = glu,
                                hba1c = (currentHbA1c * 10).roundToLong() / 10.0,
                                recordTime = recordTime
                            )
                        )
                    }

                    if (cats.contains("CO")) {
                        val conditionId = UUID.randomUUID().toString()
                        val conditions = listOf(
                            "お変わりなく過ごされています。食欲もあり、元気に活動されました。",
                            "少し疲れ気味のご様子でしたが、レクリエーションには参加されました。",
                            "本日は足取りも軽く、笑顔が多く見られました。",
                            "食事の進みが少し遅いようでしたが、完食されました。"
                        )
                        conditionAtVisits.add(
                            ConditionAtVisitBackupDto(
                                id = conditionId,
                                personId = personId,
                                title = "${recordDate.monthValue}月${recordDate.dayOfMonth}日の記録",
                                condition = conditions[random.nextInt(conditions.size)],
                                author = "サンプル記録者",
                                recordTime = recordTime
                            )
                        )
                    }

                    if (cats.contains("ME")) {
                        val dosageDateStr = recordDate.toString()
                        for (slot in 0..3) {
                            medicationRecords.add(
                                MedicationRecordBackupDto(
                                    id = UUID.randomUUID().toString(),
                                    personId = personId,
                                    dosageDate = dosageDateStr,
                                    timeSlot = slot,
                                    status = if (random.nextInt(10) == 0) 1 else 2, // ほとんど服用、たまに介助
                                    recordTime = recordTime
                                )
                            )
                        }
                    }
                }
                currentMonthDate = currentMonthDate.plusMonths(1)
            }
        }

        return CareMemoBackup(
            version = 5,
            appVersionCode = 0,
            persons = persons,
            heightAndWeights = heightAndWeights,
            bpAndPulses = bpAndPulses,
            glucoseAndHbA1cs = glucoseAndHbA1cs,
            conditionAtVisits = conditionAtVisits,
            conditionPhotos = conditionPhotos,
            medicationRecords = medicationRecords
        )
    }
}
