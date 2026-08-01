package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.ahu.ahutong.personalization.context.ContextSnapshot
import com.ahu.ahutong.personalization.context.ContextSnapshotCodec
import com.ahu.ahutong.personalization.context.DayType
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContextSnapshotCodecTest {
    @Test
    fun roundTripUsesStableActionIdsAndPreservesEveryField() {
        val snapshot = snapshot(
            previousAction = AppActionId.OPEN_HOME,
            recentActions = AppActionId.entries,
            recentActionSources = ActionSource.entries
        )

        val encoded = ContextSnapshotCodec.encode(snapshot)

        assertTrue(encoded.contains("\"schemaVersion\":${ContextSnapshotCodec.SCHEMA_VERSION}"))
        assertTrue(encoded.contains("\"recentActions\":[\"OPEN_HOME\",\"VIEW_SCHEDULE\""))
        assertTrue(encoded.contains("\"recentActionSources\":[\"organic\",\"suggestion\""))
        assertEquals(snapshot, ContextSnapshotCodec.decode(encoded))
    }

    @Test
    fun everyContextEnumUsesAnExplicitWireToken() {
        DayType.entries.forEach { value ->
            val snapshot = snapshot(dayType = value)
            assertEquals(snapshot, ContextSnapshotCodec.decode(ContextSnapshotCodec.encode(snapshot)))
        }
        BalanceBucket.entries.forEach { value ->
            val snapshot = snapshot(balanceBucket = value)
            assertEquals(snapshot, ContextSnapshotCodec.decode(ContextSnapshotCodec.encode(snapshot)))
        }
        ExamDistanceBucket.entries.forEach { value ->
            val snapshot = snapshot(examDistanceBucket = value)
            assertEquals(snapshot, ContextSnapshotCodec.decode(ContextSnapshotCodec.encode(snapshot)))
        }
    }

    @Test
    fun unknownActionsAndSchemasAreRejectedInsteadOfBecomingTrainingInput() {
        val encoded = ContextSnapshotCodec.encode(
            snapshot(previousAction = null, recentActions = listOf(AppActionId.VIEW_SCHEDULE))
        )
        assertFailsWith<IllegalArgumentException> {
            ContextSnapshotCodec.decode(encoded.replace("VIEW_SCHEDULE", "UNKNOWN_ACTION"))
        }
        assertFailsWith<IllegalArgumentException> {
            ContextSnapshotCodec.decode(encoded.replace("\"schemaVersion\":1", "\"schemaVersion\":999"))
        }
    }

    private fun snapshot(
        dayType: DayType = DayType.WEEKDAY,
        previousAction: AppActionId? = AppActionId.OPEN_TOOLS,
        recentActions: List<AppActionId> = listOf(AppActionId.OPEN_HOME, AppActionId.VIEW_SCHEDULE),
        balanceBucket: BalanceBucket = BalanceBucket.TEN_TO_TWENTY,
        examDistanceBucket: ExamDistanceBucket = ExamDistanceBucket.WITHIN_THREE_DAYS,
        recentActionSources: List<ActionSource> = listOf(ActionSource.ORGANIC, ActionSource.SYSTEM)
    ) = ContextSnapshot(
        epochDay = 20_307,
        minuteOfDay = 22 * 60 + 15,
        dayType = dayType,
        route = "schedule/details",
        previousAction = previousAction,
        recentActions = recentActions,
        balanceBucket = balanceBucket,
        balanceFresh = true,
        examDistanceBucket = examDistanceBucket,
        sessionDurationBucket = 4,
        semesterWeek = 18,
        foregroundGapBucket = 2,
        sessionDepth = recentActions.size,
        pageDwellBucket = 3,
        recentActionSources = recentActionSources,
        personalFamilyFrequencies = listOf(0.1f, 0.25f, 0.65f),
        personalFamilyRecencies = listOf(1f, 0.5f, 0.125f)
    )
}
