package com.ahu.ahutong.personalization.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "behavior_event",
    indices = [
        Index(value = ["profileKey", "sequenceNo"]),
        Index(value = ["profileKey", "occurredAtEpochMs"]),
        Index(value = ["resolvedDecisionId"], unique = true),
        Index(value = ["profileKey", "actionInstanceId", "eventType"], unique = true)
    ]
)
data class BehaviorEventEntity(
    @PrimaryKey val eventId: String,
    val actionInstanceId: String,
    val profileKey: String,
    val sessionId: String,
    val processInstanceId: String,
    val sequenceNo: Long,
    val eventType: String,
    val actionId: String?,
    val source: String,
    val occurredAtEpochMs: Long,
    val occurredAtElapsedMs: Long,
    val sessionElapsedMs: Long,
    val triggerDecisionId: String?,
    val resolvedDecisionId: String?,
    val timeBucket: Int,
    val dayType: String,
    val balanceBucket: String,
    val daysToExamBucket: String,
    val contextSchemaVersion: Int
)

@Entity(
    tableName = "pending_prediction",
    indices = [
        Index(value = ["profileKey", "resolutionStatus", "labelDeadlineElapsedMs"]),
        Index(value = ["profileKey", "sequenceNo"]),
        Index(value = ["profileKey", "sessionId", "triggerEventId"], unique = true)
    ]
)
data class PendingPredictionEntity(
    @PrimaryKey val decisionId: String,
    val profileKey: String,
    val sessionId: String,
    val processInstanceId: String,
    val sequenceNo: Long,
    val triggerEventId: String,
    val previousAction: String?,
    val createdAtEpochMs: Long,
    val createdAtElapsedMs: Long,
    val labelDeadlineElapsedMs: Long,
    val labelWindowPolicyVersion: Int,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val actionCatalogVersion: Int,
    val features: ByteArray,
    val availabilityMask: ByteArray,
    val inputDigest: String,
    val contextSnapshotJson: String,
    val preparationState: String,
    val preparationFailure: String?,
    val statProbabilities: ByteArray?,
    val tinyProbabilities: ByteArray?,
    val recentBaselineProbabilities: ByteArray?,
    val timeBaselineProbabilities: ByteArray?,
    val statModelVersion: Int?,
    val tinyModelVersion: Int?,
    val activeCheckpointId: String?,
    val activeCheckpointChecksum: String?,
    val candidateCheckpointId: String?,
    val candidateCheckpointChecksum: String?,
    val candidateProbabilities: ByteArray?,
    val candidateInferenceNanos: Long?,
    val statInferenceNanos: Long?,
    val tinyInferenceNanos: Long?,
    val promotionStageAtDecision: String,
    val effectiveDecisionTierAtDecision: String,
    val mixedLambda: Float,
    val isPromotionHoldout: Boolean,
    val interventionState: String,
    val resolutionStatus: String,
    val finalOrganicTarget: String?,
    val resolvedByEventId: String?
)

@Entity(
    tableName = "product_execution_lease",
    indices = [
        Index(value = ["profileKey", "decisionId"], unique = true),
        Index(value = ["profileKey", "executionId"], unique = true)
    ]
)
data class ProductExecutionLeaseEntity(
    @PrimaryKey val executionId: String,
    val decisionId: String,
    val profileKey: String,
    val sessionId: String,
    val processInstanceId: String,
    val actionId: String,
    val interventionType: String,
    val source: String,
    val route: String?,
    val profileGeneration: Long,
    val loginGeneration: Long,
    val preparedAtSequenceNo: Long,
    val executionEpoch: Long,
    val createdAtElapsedMs: Long,
    val expiresAtElapsedMs: Long,
    val state: String
)

@Entity(
    tableName = "action_stat",
    primaryKeys = ["profileKey", "contextKey", "actionId"],
    indices = [Index(value = ["profileKey", "actionId"])]
)
data class ActionStatEntity(
    val profileKey: String,
    val contextKey: String,
    val actionId: String,
    val positiveMass: Double,
    val exposureMass: Double,
    val updatedAtEpochDay: Long
)

@Entity(
    tableName = "training_sample",
    indices = [
        Index(value = ["profileKey", "sampleId"], unique = true),
        Index(value = ["profileKey", "decisionId"], unique = true),
        Index(value = ["profileKey", "targetIndex"]),
        Index(value = ["profileKey", "occurredEpochDay"])
    ]
)
data class TrainingSampleEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sampleId: String,
    val profileKey: String,
    val decisionId: String,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val actionCatalogVersion: Int,
    val features: ByteArray,
    val targetIndex: Int,
    val targetActionId: String,
    val actionFamily: String,
    val occurredEpochDay: Long,
    val replayPriority: Float,
    val trainingCount: Int,
    val labelSource: String
)

@Entity(
    tableName = "training_batch_journal",
    indices = [Index(value = ["profileKey", "state", "createdAtEpochMs"])]
)
data class TrainingBatchJournalEntity(
    @PrimaryKey val batchId: String,
    val profileKey: String,
    val expectedTrainingRevision: Long,
    val selectedRowIds: String,
    val state: String,
    val createdAtEpochMs: Long,
    val committedAtEpochMs: Long?
)

@Entity(
    tableName = "shadow_evaluation",
    indices = [
        Index(value = ["profileKey", "evaluationSeq"], unique = true),
        Index(value = ["profileKey", "decisionId"], unique = true),
        Index(value = ["profileKey", "occurredEpochDay"])
    ]
)
data class ShadowEvaluationEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val profileKey: String,
    val evaluationSeq: Long,
    val decisionId: String,
    val occurredEpochDay: Long,
    val trueLabel: String,
    val isOrganicNonNone: Boolean,
    val statTop1: Int,
    val statTop3: Int,
    val statReciprocalRank: Double,
    val statBrier: Double,
    val statLogLoss: Double,
    val statTop1Confidence: Double,
    val tinyTop1: Int,
    val tinyTop3: Int,
    val tinyReciprocalRank: Double,
    val tinyBrier: Double,
    val tinyLogLoss: Double,
    val tinyTop1Confidence: Double,
    val recentReciprocalRank: Double,
    val recentTop3: Int,
    val timeReciprocalRank: Double,
    val timeTop3: Int,
    val winner: String,
    val paired: Boolean,
    val tinyPredictionStatus: String,
    val statInferenceNanos: Long,
    val tinyInferenceNanos: Long,
    val trainingNanos: Long,
    val modelSizeBytes: Long,
    val promotionEligible: Boolean,
    val telemetryEligible: Boolean,
    val rejectionReason: String?,
    val stage: String,
    val tier: String,
    val activeCheckpointId: String?,
    val isHoldout: Boolean,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val actionCatalogVersion: Int,
    val appVersionCode: Int,
    val statModelVersion: Int,
    val tinyModelVersion: Int,
    val trainingConfigVersion: Int,
    val metricSchemaVersion: Int
)

@Entity(
    tableName = "candidate_shadow_evaluation",
    indices = [Index(value = ["profileKey", "evaluationSeq"], unique = true)]
)
data class CandidateShadowEvaluationEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val profileKey: String,
    val evaluationSeq: Long,
    val decisionId: String,
    val activeCheckpointId: String,
    val candidateCheckpointId: String,
    val activeChecksum: String,
    val candidateChecksum: String,
    val trueLabel: String,
    val activeTop3: Int,
    val candidateTop3: Int,
    val activeMrr: Double,
    val candidateMrr: Double,
    val activeBrier: Double,
    val candidateBrier: Double,
    val activeLogLoss: Double,
    val candidateLogLoss: Double,
    val activeConfidence: Double,
    val candidateConfidence: Double,
    val activeInferenceNanos: Long,
    val candidateInferenceNanos: Long,
    val candidateStatus: String,
    val consumed: Boolean
)

@Entity(tableName = "tiny_promotion_state")
data class TinyPromotionStateEntity(
    @PrimaryKey val profileKey: String,
    val holdoutSeed: String,
    val stage: String,
    val mixedLambda: Float,
    val modelGenerationVersion: Long,
    val stageGeneration: Long,
    val transitionSequence: Long,
    val enteredEpochDay: Long,
    val evidenceHighWatermark: Long,
    val activeCheckpointId: String?,
    val activeChecksum: String?,
    val candidateCheckpointId: String?,
    val trainingRevision: Long,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val actionCatalogVersion: Int,
    val promotionConfigVersion: Int,
    val consecutivePassingWindows: Int,
    val consecutiveFailingWindows: Int,
    val cooldownUntilEpochDay: Long,
    val minimumNewEvidenceSeq: Long,
    val healthState: String,
    val lastTransitionReason: String,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "tiny_runtime_health_state")
data class TinyRuntimeHealthStateEntity(
    @PrimaryKey val profileKey: String,
    val recentAttemptBits: Long,
    val recentAttemptCount: Int,
    val failureEpochDay: Long,
    val failuresToday: Int,
    val totalFailures: Long,
    val lastCheckpointId: String?,
    val lastFailureCode: String?,
    val lastFailureEpochMs: Long?,
    val lastSuccessEpochMs: Long?
)

@Entity(
    tableName = "promotion_evaluation_window",
    indices = [Index(value = ["profileKey", "windowId"], unique = true)]
)
data class PromotionEvaluationWindowEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val profileKey: String,
    val windowId: String,
    val purpose: String,
    val stage: String,
    val checkpointId: String?,
    val startEvaluationSeq: Long,
    val endEvaluationSeq: Long,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val eligibleSampleCount: Int,
    val organicNonNoneSampleCount: Int,
    val pairedSampleCount: Int,
    val metricsJson: String,
    val ece: Double,
    val perActionDigest: String,
    val inferenceP95Nanos: Long,
    val trainingP95Nanos: Long,
    val status: String,
    val qualified: Boolean,
    val consumedTransitionSequence: Long?
)

@Entity(tableName = "promotion_action_qualification", primaryKeys = ["profileKey", "actionId"])
data class PromotionActionQualificationEntity(
    val profileKey: String,
    val actionId: String,
    val highestQualifiedTier: String,
    val checkpointId: String?,
    val sampleCount: Int,
    val evidenceWindowId: String?,
    val lastRegressionReason: String?
)

@Entity(tableName = "promotion_transition_journal")
data class PromotionTransitionJournalEntity(
    @PrimaryKey val journalId: String,
    val profileKey: String,
    val expectedGeneration: Long,
    val fromStage: String,
    val toStage: String,
    val checkpointId: String?,
    val checksum: String?,
    val state: String,
    val preparedAtEpochMs: Long,
    val committedAtEpochMs: Long?
)

@Entity(tableName = "learning_state")
data class LearningStateEntity(
    @PrimaryKey val profileKey: String,
    val statLearningStartedEpochDay: Long?,
    val tinyTrainingStartedEpochDay: Long?,
    val lastCommittedBatchId: String?,
    val lastTrainingNanos: Long,
    val lastTrainingLoss: Float?,
    val lastGradientNorm: Float?
)

@Entity(
    tableName = "telemetry_report",
    indices = [
        Index(value = ["reportId"], unique = true),
        Index(value = ["batchId"], unique = true),
        Index(value = ["profileKey", "windowId"], unique = true),
        Index(value = ["consentLifecycleId", "state"])
    ]
)
data class TelemetryReportEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val reportId: String,
    val batchId: String,
    val profileKey: String,
    val consentLifecycleId: String,
    val telemetryId: String,
    val modelGenerationId: String,
    val windowId: String,
    val payloadJson: String,
    val payloadSha256Hex: String,
    val exactRequestBodyJson: String,
    val bodySha256Hex: String,
    val state: String,
    val attemptCount: Int,
    val nextAttemptEpochMs: Long,
    val lastAttemptEpochDay: Long?,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long
)

@Entity(
    tableName = "telemetry_state",
    indices = [Index(value = ["consentLifecycleId"], unique = true)]
)
data class TelemetryStateEntity(
    @PrimaryKey val profileKey: String,
    val consentLifecycleId: String,
    val telemetryId: String,
    val modelGenerationId: String,
    val localModelGenerationVersion: Long,
    val encryptedRevocationCapability: String,
    val revocationKeyAlias: String,
    val consentGeneration: Long,
    val aggregationStartEvaluationSeq: Long,
    val lastReportedEvaluationSeq: Long,
    val lastReportCreatedEpochDay: Long?,
    val lifecycleState: String,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "telemetry_aggregate_window",
    indices = [
        Index(value = ["profileKey", "consentLifecycleId", "state", "startEvaluationSeq"]),
        Index(value = ["profileKey", "consentLifecycleId", "startEvaluationSeq"], unique = true)
    ]
)
data class TelemetryAggregateWindowEntity(
    @PrimaryKey val windowId: String,
    val profileKey: String,
    val consentLifecycleId: String,
    val telemetryId: String,
    val modelGenerationId: String,
    val activeCheckpointId: String?,
    val startEvaluationSeq: Long,
    val endEvaluationSeq: Long,
    val windowStartEpochDay: Long,
    val windowEndEpochDay: Long,
    val eligibleSampleCount: Int,
    val organicNonNoneSampleCount: Int,
    val pairedSampleCount: Int,
    val statTop1Correct: Long,
    val statTop3Hit: Long,
    val statReciprocalRankSum: Double,
    val statBrierSum: Double,
    val statLogLossSum: Double,
    val tinyTop1Correct: Long,
    val tinyTop3Hit: Long,
    val tinyReciprocalRankSum: Double,
    val tinyBrierSum: Double,
    val tinyLogLossSum: Double,
    val tinyWins: Int,
    val statWins: Int,
    val ties: Int,
    val statInferenceNanosSum: Long,
    val tinyInferenceNanosSum: Long,
    val trainingNanosSum: Long,
    val modelSizeBytesMax: Long,
    val perActionJson: String,
    val appVersionCode: Int,
    val statModelVersion: Int,
    val tinyModelVersion: Int,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val actionCatalogVersion: Int,
    val trainingConfigVersion: Int,
    val metricSchemaVersion: Int,
    val state: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "telemetry_deletion_tombstone")
data class TelemetryDeletionTombstoneEntity(
    @PrimaryKey val deletionId: String,
    val consentLifecycleId: String,
    val telemetryId: String,
    val modelGenerationId: String,
    val encryptedRevocationCapability: String,
    val revocationKeyAlias: String,
    val attemptCount: Int,
    val nextAttemptEpochMs: Long,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val state: String
)
