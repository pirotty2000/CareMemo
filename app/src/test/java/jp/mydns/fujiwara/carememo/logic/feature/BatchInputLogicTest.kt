@file:Suppress("NonAsciiCharacters")

package jp.mydns.fujiwara.carememo.logic.feature

import jp.mydns.fujiwara.carememo.data.BpAndPulse
import jp.mydns.fujiwara.carememo.data.GlucoseAndHbA1c
import jp.mydns.fujiwara.carememo.data.HeightAndWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * LOG-PH-003 BatchInputLogic のテスト
 * 
 * 仕様書: doc/test/logic/TEST_SPEC_LOG-PH-003_BatchInputLogic.md に準拠
 */
class BatchInputLogicTest {

    // ======================================================================================
    // 3.1. バリデーション (validate / isValid)
    // ======================================================================================

    @Test
    fun vl_01_全項目が空_invalid() {
        val state = BatchInputUiState()
        assertEquals(BatchInputValidationResult.EMPTY_ALL, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun vl_02_体重のみ正常入力_success() {
        val state = BatchInputUiState(weight = "60.5")
        assertEquals(BatchInputValidationResult.SUCCESS, BatchInputLogic.validate(state))
        assertTrue(BatchInputLogic.isValid(state))
    }

    @Test
    fun vl_03_不正な値が含まれる_invalid() {
        val state = BatchInputUiState(height = "160.0.1", weight = "60.0")
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    @Test
    fun vl_04_範囲外の値_invalid() {
        val state = BatchInputUiState(bodyTemperature = "50.0") // 業務ルール違反
        assertEquals(BatchInputValidationResult.INVALID_VALUE, BatchInputLogic.validate(state))
        assertFalse(BatchInputLogic.isValid(state))
    }

    // ======================================================================================
    // 3.2. カテゴリ抽出 (getEffectiveCategories)
    // ======================================================================================

    @Test
    fun ex_01_体重と血圧を入力() {
        val state = BatchInputUiState(weight = "60.0", bpSystolic = "120")
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertEquals(2, categories.size)
        assertTrue(categories.contains(BatchInputCategory.HEIGHT_WEIGHT))
        assertTrue(categories.contains(BatchInputCategory.VITAL))
    }

    @Test
    fun ex_02_血糖値のみ入力() {
        val state = BatchInputUiState(glucose = "100")
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertEquals(1, categories.size)
        assertEquals(BatchInputCategory.GLUCOSE, categories[0])
    }

    @Test
    fun ex_03_全項目空() {
        val state = BatchInputUiState()
        val categories = BatchInputLogic.getEffectiveCategories(state)
        assertTrue(categories.isEmpty())
    }

    // ======================================================================================
    // 3.3. Entity 生成 (createEntities)
    // ======================================================================================

    @Test
    fun cp_01_体重と血圧を入力() {
        val state = BatchInputUiState(weight = "60.0", bpSystolic = "120")
        val entities = BatchInputLogic.createEntities(1, Instant.now(), state)
        
        assertEquals(2, entities.size)
        assertTrue(entities.any { it is HeightAndWeight })
        assertTrue(entities.any { it is BpAndPulse })
    }

    @Test
    fun cp_02_血糖値のみ入力() {
        val state = BatchInputUiState(glucose = "100")
        val entities = BatchInputLogic.createEntities(1, Instant.now(), state)
        
        assertEquals(1, entities.size)
        assertTrue(entities[0] is GlucoseAndHbA1c)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cp_03_不正な値を含む() {
        val state = BatchInputUiState(height = "abc")
        BatchInputLogic.createEntities(1, Instant.now(), state)
    }

    // ======================================================================================
    // 4. 変更検知 (isChanged) - フェーズ 2 追加
    // ======================================================================================

    @Test
    fun chg_01_初期状態は変更なし() {
        val now = Instant.now()
        val state = BatchInputUiState(recordTime = now, initialRecordTime = now)
        assertFalse(BatchInputLogic.isChanged(state))
    }

    @Test
    fun chg_02_数値入力があれば変更あり() {
        val now = Instant.now()
        val state = BatchInputUiState(weight = "60", recordTime = now, initialRecordTime = now)
        assertTrue(BatchInputLogic.isChanged(state))
    }

    @Test
    fun chg_03_日時の変更があれば変更あり() {
        val now = Instant.now()
        val later = now.plusSeconds(60)
        val state = BatchInputUiState(recordTime = later, initialRecordTime = now)
        assertTrue(BatchInputLogic.isChanged(state))
    }
}
