package com.ahu.ahutong.personalization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.context.FeatureExtractor
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BehaviorEventEntity
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.PendingPredictionEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingResolutionRecoveryTest {
    private lateinit var database: BehaviorDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BehaviorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pendingAlreadyLinkedToAnEventIsCensoredBeforeAnotherEventCanClaimIt() = runBlocking {
        val profile = "profile"
        val decisionId = UUID.randomUUID().toString()
        val resolvingEventId = UUID.randomUUID().toString()
        database.behaviorDao().insertPending(pending(profile, decisionId))
        database.behaviorDao().insertEvent(event(profile, decisionId, resolvingEventId))

        assertEquals(1, database.behaviorDao().censorPendingWithExistingResolutionEvent(profile))

        val recovered = requireNotNull(database.behaviorDao().pending(decisionId))
        assertEquals("CENSORED_ORPHANED_RESOLUTION_EVENT", recovered.resolutionStatus)
        assertEquals(resolvingEventId, recovered.resolvedByEventId)
        assertNull(database.behaviorDao().latestPending(profile))
    }

    @Test
    fun pendingWithoutAResolutionEventRemainsEligible() = runBlocking {
        val profile = "profile"
        val decisionId = UUID.randomUUID().toString()
        database.behaviorDao().insertPending(pending(profile, decisionId))

        assertEquals(0, database.behaviorDao().censorPendingWithExistingResolutionEvent(profile))
        assertEquals(decisionId, database.behaviorDao().latestPending(profile)?.decisionId)
    }

    private fun pending(profile: String, decisionId: String) = PendingPredictionEntity(
        decisionId = decisionId,
        profileKey = profile,
        sessionId = "session-old",
        processInstanceId = "process-old",
        sequenceNo = 1,
        triggerEventId = UUID.randomUUID().toString(),
        previousAction = AppActionId.OPEN_HOME.stableId,
        createdAtEpochMs = 1,
        createdAtElapsedMs = 1,
        labelDeadlineElapsedMs = Long.MAX_VALUE,
        labelWindowPolicyVersion = 1,
        featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
        outputSchemaVersion = AppActionCatalog.OUTPUT_SCHEMA_VERSION,
        actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
        features = BinaryCodec.floats(FloatArray(FeatureExtractor.INPUT_DIMENSION)),
        availabilityMask = BinaryCodec.booleans(BooleanArray(AppActionCatalog.outputIds.size) { true }),
        inputDigest = "digest",
        contextSnapshotJson = "{}",
        preparationState = "PENDING",
        preparationFailure = null,
        statProbabilities = null,
        tinyProbabilities = null,
        recentBaselineProbabilities = null,
        timeBaselineProbabilities = null,
        statModelVersion = 1,
        tinyModelVersion = 1,
        activeCheckpointId = "active",
        activeCheckpointChecksum = "checksum",
        candidateCheckpointId = null,
        candidateCheckpointChecksum = null,
        candidateProbabilities = null,
        candidateInferenceNanos = null,
        statInferenceNanos = 1,
        tinyInferenceNanos = 1,
        promotionStageAtDecision = "SHADOW",
        effectiveDecisionTierAtDecision = "STAT_ONLY",
        mixedLambda = 0f,
        isPromotionHoldout = false,
        interventionState = "NONE",
        resolutionStatus = "PENDING",
        finalOrganicTarget = null,
        resolvedByEventId = null
    )

    private fun event(profile: String, decisionId: String, eventId: String) = BehaviorEventEntity(
        eventId = eventId,
        actionInstanceId = UUID.randomUUID().toString(),
        profileKey = profile,
        sessionId = "session-old",
        processInstanceId = "process-old",
        sequenceNo = 2,
        eventType = "ACTION_INTENT_ACCEPTED",
        actionId = AppActionId.VIEW_SCHEDULE.stableId,
        source = ActionSource.ORGANIC.name,
        occurredAtEpochMs = 2,
        occurredAtElapsedMs = 2,
        sessionElapsedMs = 1,
        triggerDecisionId = null,
        resolvedDecisionId = decisionId,
        timeBucket = 22,
        dayType = "WEEKDAY",
        balanceBucket = "UNKNOWN",
        daysToExamBucket = "UNKNOWN",
        contextSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION
    )
}
