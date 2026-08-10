package jp.mydns.fujiwara.carememo.ui.preview

import jp.mydns.fujiwara.carememo.data.*
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * プレビューおよびテスト用のモックデータ集約クラス
 */
object MockData {
    val person = Person(
        id = "person-1",
        lastName = "山田",
        firstName = "太郎",
        lastNameFurigana = "ヤマダ",
        firstNameFurigana = "タロウ",
        birthday = Instant.parse("1950-01-01T00:00:00Z")
    )

    val bpAndPulse = BpAndPulse(
        id = "record-1",
        personId = "person-1",
        recordTime = Instant.now(),
        bpSystolic = 125,
        bpDiastolic = 82,
        sat = 98,
        pulse = 68,
        bodyTemperature = 36.5
    )

    val glucose = GlucoseAndHbA1c(
        id = "record-3",
        personId = "person-1",
        recordTime = Instant.now().minus(2, ChronoUnit.HOURS),
        glucose = 110,
        hba1c = 5.8
    )

    val heightWeight = HeightAndWeight(
        id = "record-4",
        personId = "person-1",
        recordTime = Instant.now().minus(1, ChronoUnit.DAYS),
        height = 165.0,
        weight = 60.5
    )

    val condition = ConditionAtVisit(
        id = "record-5",
        personId = "person-1",
        recordTime = Instant.now(),
        title = "経過報告",
        condition = "顔色も良く、食欲も安定しています。午後は庭の散歩を楽しまれました。",
        author = "看護 花子"
    )

    val healthRecords = persistentListOf(
        bpAndPulse,
        glucose,
        heightWeight
    )

    val conditionRecords = persistentListOf(
        condition,
        condition.copy(id = "record-6", recordTime = Instant.now().minus(1, ChronoUnit.DAYS), title = "昨日の様子", condition = "少し疲れ気味でしたが、夕食後はゆっくり休まれました。")
    )
}
