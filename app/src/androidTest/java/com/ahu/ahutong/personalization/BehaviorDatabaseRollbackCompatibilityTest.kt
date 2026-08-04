package com.ahu.ahutong.personalization

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BehaviorDatabaseFactory
import com.ahu.ahutong.personalization.storage.BehaviorDatabaseFiles
import com.ahu.ahutong.personalization.storage.PendingPredictionEntity
import com.ahu.ahutong.personalization.storage.ProductExecutionLeaseEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehaviorDatabaseRollbackCompatibilityTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BehaviorDatabase::class.java
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanUp() {
        context.deleteDatabase(BehaviorDatabaseFiles.LEGACY_V1)
        context.deleteDatabase(BehaviorDatabaseFiles.CURRENT_V2)
    }

    @Test
    fun versionOneIsCopiedAndLeftReadableForAnOldBinary() {
        cleanUp()
        helper.createDatabase(BehaviorDatabaseFiles.LEGACY_V1, 1).apply {
            execSQL(
                "INSERT INTO learning_state " +
                    "(profileKey, statLearningStartedEpochDay, tinyTrainingStartedEpochDay, " +
                    "lastCommittedBatchId, lastTrainingNanos, lastTrainingLoss, lastGradientNorm) " +
                    "VALUES ('rollback-profile', 2, 3, 'batch', 4, 0.5, 0.25)"
            )
            close()
        }

        val database = BehaviorDatabaseFactory.open(context)
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(3, sqlite.version)
            sqlite.query(
                "SELECT COUNT(*) FROM learning_state WHERE profileKey = 'rollback-profile'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            database.close()
        }

        val legacy = SQLiteDatabase.openDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.LEGACY_V1).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            assertEquals(1, legacy.version)
        } finally {
            legacy.close()
        }
    }

    @Test
    fun unknownLegacyVersionIsPreservedAndPredictionStorageRecoversFresh() {
        cleanUp()
        SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.LEGACY_V1),
            null
        ).use { database ->
            database.version = 99
        }

        val database = BehaviorDatabaseFactory.open(context)
        try {
            assertEquals(3, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }

        val legacy = SQLiteDatabase.openDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.LEGACY_V1).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            assertEquals(99, legacy.version)
        } finally {
            legacy.close()
        }
        assertTrue(context.getDatabasePath(BehaviorDatabaseFiles.CURRENT_V2).exists())
    }

    @Test
    fun futureCurrentVersionFallsBackToFreshVersionThreeStorage() {
        cleanUp()
        SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(BehaviorDatabaseFiles.CURRENT_V2),
            null
        ).use { database ->
            database.execSQL("CREATE TABLE future_only (id INTEGER PRIMARY KEY)")
            database.version = 99
        }

        val database = BehaviorDatabaseFactory.open(context)
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(3, sqlite.version)
            sqlite.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'behavior_event'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun targetedInterventionCanAtomicallyClaimAStillPreparingOpportunity() = runBlocking {
        cleanUp()
        val database = BehaviorDatabaseFactory.open(context)
        try {
            val now = SystemClock.elapsedRealtime()
            val pending = PendingPredictionEntity(
                decisionId = "targeted-preparing",
                profileKey = "profile",
                sessionId = "session",
                processInstanceId = "process",
                sequenceNo = 1L,
                triggerEventId = "event",
                previousAction = null,
                createdAtEpochMs = System.currentTimeMillis(),
                createdAtElapsedMs = now,
                labelDeadlineElapsedMs = now + 60_000L,
                labelWindowPolicyVersion = 1,
                featureSchemaVersion = 1,
                outputSchemaVersion = 1,
                actionCatalogVersion = 1,
                features = byteArrayOf(),
                availabilityMask = byteArrayOf(),
                inputDigest = "digest",
                contextSnapshotJson = "{}",
                preparationState = "PREPARING",
                preparationFailure = null,
                statProbabilities = null,
                tinyProbabilities = null,
                recentBaselineProbabilities = null,
                timeBaselineProbabilities = null,
                statModelVersion = null,
                tinyModelVersion = null,
                activeCheckpointId = "active",
                activeCheckpointChecksum = "checksum",
                candidateCheckpointId = null,
                candidateCheckpointChecksum = null,
                candidateProbabilities = null,
                candidateInferenceNanos = null,
                statInferenceNanos = null,
                tinyInferenceNanos = null,
                promotionStageAtDecision = "SHADOW",
                effectiveDecisionTierAtDecision = "STAT_ONLY",
                mixedLambda = 0f,
                isPromotionHoldout = false,
                interventionState = "NONE",
                resolutionStatus = "PENDING",
                finalOrganicTarget = null,
                resolvedByEventId = null
            )
            database.behaviorDao().insertPending(pending)
            val lease = ProductExecutionLeaseEntity(
                executionId = "execution",
                decisionId = pending.decisionId,
                profileKey = pending.profileKey,
                sessionId = pending.sessionId,
                processInstanceId = pending.processInstanceId,
                actionId = "OPEN_HOME",
                interventionType = "SUGGESTION_TARGETED",
                source = "SUGGESTION",
                route = "home",
                profileGeneration = 1L,
                loginGeneration = 1L,
                preparedAtSequenceNo = pending.sequenceNo,
                executionEpoch = 1L,
                createdAtElapsedMs = now,
                expiresAtElapsedMs = now + 10_000L,
                state = "PREPARED"
            )

            assertFalse(
                database.behaviorDao().prepareProductExecution(
                    pending.decisionId,
                    pending.profileKey,
                    "PREPARED_SUGGESTION",
                    lease,
                    allowPreparing = false
                )
            )
            assertTrue(
                database.behaviorDao().prepareProductExecution(
                    pending.decisionId,
                    pending.profileKey,
                    "PREPARED_SUGGESTION_TARGETED",
                    lease,
                    allowPreparing = true
                )
            )
            assertEquals(
                "INVALIDATED_INTERVENTION_PREPARED",
                database.behaviorDao().pending(pending.decisionId)?.resolutionStatus
            )
            assertEquals("PREPARED", database.behaviorDao().lease(lease.executionId)?.state)
        } finally {
            database.close()
        }
    }
}
