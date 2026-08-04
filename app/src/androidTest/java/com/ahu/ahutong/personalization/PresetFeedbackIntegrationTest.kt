package com.ahu.ahutong.personalization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.ahu.ahutong.personalization.context.ContextSnapshot
import com.ahu.ahutong.personalization.context.DayType
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.preset.PresetFeedbackSource
import com.ahu.ahutong.personalization.preset.PresetInteractionState
import com.ahu.ahutong.personalization.preset.PresetModelStateStore
import com.ahu.ahutong.personalization.preset.PresetRankingEngine
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.LocalParameterPresetEntity
import com.ahu.ahutong.personalization.telemetry.TelemetryAggregateStore
import com.ahu.ahutong.personalization.telemetry.TelemetryV3AggregateStore
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetFeedbackIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: BehaviorDatabase
    private lateinit var store: PresetModelStateStore
    private lateinit var engine: PresetRankingEngine
    private val profile = UUID.randomUUID().toString().replace("-", "")

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BehaviorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PresetModelStateStore(context)
        store.reset(profile)
        val dao = database.behaviorDao()
        engine = PresetRankingEngine(
            dao,
            store,
            TelemetryAggregateStore(dao, TelemetryV3AggregateStore(dao))
        )
        database.behaviorDao().upsertLocalPreset(
            LocalParameterPresetEntity(
                presetId = "natural-preset",
                profileKey = profile,
                domainId = SemanticDomain.FREE_CLASSROOM.name,
                fingerprint = sha256(SAME_FINGERPRINT_SOURCE),
                localPayloadJson = "{\"campus\":1}",
                coarseFeaturesJson = "{\"campus\":\"PRIMARY\"}",
                createdAtEpochMs = 1,
                lastOrganicUsedAtEpochMs = 1,
                organicUseCount = 3,
                source = "ORGANIC_COMMIT",
                schemaVersion = 1
            )
        )
    }

    @After
    fun tearDown() = runBlocking {
        store.reset(profile)
        database.close()
    }

    @Test
    fun exposureApplyConfirmationAndReplacementUseExactlyOnceWeightedFeedback() = runBlocking {
        val dao = database.behaviorDao()
        val firstCandidate = engine.rank(profile, SemanticDomain.FREE_CLASSROOM, snapshot(), holdout = false).single()
        val exposed = requireNotNull(engine.markRecommendationExposed(profile, firstCandidate))
        assertEquals(PresetInteractionState.EXPOSED.name, dao.presetInteraction(profile, exposed.interactionId)?.state)
        assertEquals(0, dao.presetTrainingSampleCount(profile))

        val applied = requireNotNull(engine.applyRecommendation(profile, firstCandidate))
        assertEquals(PresetInteractionState.APPLIED.name, dao.presetInteraction(profile, exposed.interactionId)?.state)
        assertEquals(0, dao.presetTrainingSampleCount(profile))

        engine.recordNaturalSubmission(profile, submission(SAME_FINGERPRINT_SOURCE), snapshot(), applied.interactionToken, listOf(firstCandidate))
        val positive = dao.recentPresetTrainingSamples(profile, 10).single()
        assertTrue(positive.label)
        assertEquals(0.20f, positive.sampleWeight)
        assertEquals(PresetFeedbackSource.ASSISTED_QUERY_CONFIRMED.name, positive.feedbackSource)
        assertEquals(false, positive.naturalHoldoutEligible)
        assertEquals(PresetInteractionState.QUERY_CONFIRMED.name, dao.presetInteraction(profile, exposed.interactionId)?.state)
        assertTrue(dao.recentPresetShadowEvaluations(profile, 10).isEmpty())

        engine.recordNaturalSubmission(profile, submission(SAME_FINGERPRINT_SOURCE), snapshot(), applied.interactionToken, listOf(firstCandidate))
        assertEquals("CAS must prevent duplicate feedback", 1, dao.presetTrainingSampleCount(profile))

        val replacementCandidate = engine.rank(profile, SemanticDomain.FREE_CLASSROOM, snapshot(), holdout = false).first()
        engine.markRecommendationExposed(profile, replacementCandidate)
        val replacementApplied = requireNotNull(engine.applyRecommendation(profile, replacementCandidate))
        engine.recordNaturalSubmission(profile, submission("changed-parameters"), snapshot(), replacementApplied.interactionToken, listOf(replacementCandidate))
        val negative = dao.recentPresetTrainingSamples(profile, 10).first { !it.label }
        assertEquals(0.10f, negative.sampleWeight)
        assertEquals(PresetFeedbackSource.ASSISTED_REPLACED.name, negative.feedbackSource)
        assertEquals(PresetInteractionState.REPLACED.name, dao.presetInteraction(profile, replacementApplied.interactionToken.interactionId)?.state)

        val organicPreset = requireNotNull(dao.localPreset(profile, "natural-preset"))
        assertEquals("assisted feedback must not alter natural use count", 3, organicPreset.organicUseCount)
        assertEquals(1L, organicPreset.lastOrganicUsedAtEpochMs)
    }

    @Test
    fun ignoredRecommendationProducesNoLabelAndProfileCleanupRemovesInteraction() = runBlocking {
        val dao = database.behaviorDao()
        val candidate = engine.rank(profile, SemanticDomain.FREE_CLASSROOM, snapshot(), holdout = false).single()
        val token = requireNotNull(engine.markRecommendationExposed(profile, candidate))

        engine.recordNaturalSubmission(profile, submission(SAME_FINGERPRINT_SOURCE), snapshot(), token, listOf(candidate))

        assertEquals(0, dao.presetTrainingSampleCount(profile))
        assertEquals(PresetInteractionState.EXPIRED_NO_LABEL.name, dao.presetInteraction(profile, token.interactionId)?.state)
        dao.deleteProfileLearningState(profile)
        assertTrue(dao.recentPresetInteractions(profile, 10).isEmpty())
    }

    @Test
    fun promotionEvaluationUsesNaturalHoldoutWithoutTrainingOnTheSameRows() = runBlocking {
        val dao = database.behaviorDao()
        assertTrue(engine.rank(profile, SemanticDomain.FREE_CLASSROOM, snapshot(), holdout = true).isEmpty())

        engine.recordNaturalSubmission(
            profile,
            submission(SAME_FINGERPRINT_SOURCE),
            snapshot(),
            interactionToken = null,
            candidatesAtOpportunity = emptyList()
        )

        assertEquals(0, dao.presetTrainingSampleCount(profile))
        val evaluations = dao.recentPresetShadowEvaluations(profile, 10)
        assertTrue(evaluations.isNotEmpty())
        assertTrue(evaluations.all { it.evaluationSource == "ORGANIC" && it.naturalHoldoutEligible })
    }

    private fun submission(source: String) = PresetSubmission(
        domain = SemanticDomain.FREE_CLASSROOM,
        localPayloadJson = "{\"campus\":1}",
        coarseFeaturesJson = "{\"campus\":\"PRIMARY\"}",
        stableFingerprintSource = source
    )

    private fun snapshot() = ContextSnapshot(
        epochDay = LocalDate.now().toEpochDay(),
        minuteOfDay = 9 * 60,
        dayType = DayType.WEEKDAY,
        route = "free_classroom",
        previousAction = null,
        recentActions = emptyList(),
        balanceBucket = BalanceBucket.UNKNOWN,
        balanceFresh = false,
        examDistanceBucket = ExamDistanceBucket.UNKNOWN,
        sessionDurationBucket = 0
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SAME_FINGERPRINT_SOURCE = "same-parameters"
    }
}
