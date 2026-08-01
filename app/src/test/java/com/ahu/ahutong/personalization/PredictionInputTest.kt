package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.ahu.ahutong.personalization.context.ContextSnapshot
import com.ahu.ahutong.personalization.context.DayType
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.context.FeatureExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PredictionInputTest {
    private val snapshot = ContextSnapshot(
        epochDay = 20_000,
        minuteOfDay = 22 * 60,
        dayType = DayType.WEEKDAY,
        route = "home",
        previousAction = AppActionId.OPEN_HOME,
        recentActions = listOf(AppActionId.VIEW_SCHEDULE, AppActionId.OPEN_HOME),
        balanceBucket = BalanceBucket.TEN_TO_TWENTY,
        balanceFresh = true,
        examDistanceBucket = ExamDistanceBucket.WITHIN_THREE_DAYS,
        sessionDurationBucket = 2
    )

    @Test
    fun featureVectorIsFixedAndDefensive() {
        val input = FeatureExtractor.build("profile", "decision", snapshot)
        val first = input.features.copy()
        first[0] = 999f
        assertEquals(FeatureExtractor.INPUT_DIMENSION, input.features.size)
        assertNotEquals(999f, input.features[0])
    }

    @Test
    fun digestChangesWithContext() {
        val first = FeatureExtractor.build("profile", "decision-a", snapshot)
        val second = FeatureExtractor.build(
            "profile",
            "decision-b",
            snapshot.copy(minuteOfDay = snapshot.minuteOfDay + 60)
        )
        assertNotEquals(first.inputDigest, second.inputDigest)
        val differentRouteWithSameFeaturePresence = FeatureExtractor.build(
            "profile",
            "decision-c",
            snapshot.copy(route = "settings")
        )
        assertNotEquals(first.inputDigest, differentRouteWithSameFeaturePresence.inputDigest)
    }

    @Test
    fun missingBusinessValuesHaveExplicitMaskBits() {
        val first = FeatureExtractor.build("profile", "decision-a", snapshot)
        val second = FeatureExtractor.build(
            "profile",
            "decision-b",
            snapshot.copy(
                balanceBucket = BalanceBucket.UNKNOWN,
                balanceFresh = false,
                examDistanceBucket = ExamDistanceBucket.UNKNOWN,
                semesterWeek = null
            )
        )
        assertTrue(first.features[58] == 0f && second.features[58] == 1f)
        assertTrue(first.features[59] == 0f && second.features[59] == 1f)
        assertTrue(first.features[60] == 0f && second.features[60] == 1f)
        assertTrue(second.features[61] == 1f)
    }
}
