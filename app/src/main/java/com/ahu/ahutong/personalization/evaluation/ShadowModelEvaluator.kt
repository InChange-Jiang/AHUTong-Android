package com.ahu.ahutong.personalization.evaluation

import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.model.ModelStateStore
import com.ahu.ahutong.personalization.model.TRAINING_CONFIG_VERSION
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.CandidateShadowEvaluationEntity
import com.ahu.ahutong.personalization.storage.PendingPredictionEntity
import com.ahu.ahutong.personalization.storage.ShadowEvaluationEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

data class MetricContribution(
    val top1: Int,
    val top3: Int,
    val reciprocalRank: Double,
    val brier: Double,
    val logLoss: Double
)

interface ShadowModelEvaluator {
    suspend fun resolve(pending: PendingPredictionEntity, targetOutputId: String): ShadowEvaluationEntity?
}

@Singleton
class PairedShadowModelEvaluator @Inject constructor(
    private val dao: BehaviorDao,
    private val modelStateStore: ModelStateStore
) : ShadowModelEvaluator {

    override suspend fun resolve(
        pending: PendingPredictionEntity,
        targetOutputId: String
    ): ShadowEvaluationEntity? {
        val target = AppActionCatalog.outputIndex[targetOutputId] ?: return null
        val statBytes = pending.statProbabilities ?: return null
        val tinyBytes = pending.tinyProbabilities
        val recentBytes = pending.recentBaselineProbabilities ?: return null
        val timeBytes = pending.timeBaselineProbabilities ?: return null
        val stat = BinaryCodec.floats(statBytes)
        val tiny = tinyBytes?.let(BinaryCodec::floats)
        val recent = BinaryCodec.floats(recentBytes)
        val time = BinaryCodec.floats(timeBytes)
        val statMetric = metric(stat, target)
        val tinyMetric = tiny?.let { metric(it, target) }
        val recentMetric = metric(recent, target)
        val timeMetric = metric(time, target)
        val paired = tinyMetric != null
        val winner = when {
            tinyMetric == null -> "UNPAIRED"
            tinyMetric.logLoss + WIN_EPSILON < statMetric.logLoss -> "TINY"
            statMetric.logLoss + WIN_EPSILON < tinyMetric.logLoss -> "STAT"
            else -> "TIE"
        }
        val seq = dao.maxEvaluationSeq(pending.profileKey) + 1
        val value = ShadowEvaluationEntity(
            profileKey = pending.profileKey,
            evaluationSeq = seq,
            decisionId = pending.decisionId,
            occurredEpochDay = pending.createdAtEpochMs / MILLIS_PER_DAY,
            trueLabel = targetOutputId,
            isOrganicNonNone = targetOutputId != AppActionCatalog.NONE_OUTPUT_ID,
            statTop1 = statMetric.top1,
            statTop3 = statMetric.top3,
            statReciprocalRank = statMetric.reciprocalRank,
            statBrier = statMetric.brier,
            statLogLoss = statMetric.logLoss,
            statTop1Confidence = stat.maxOrNull()?.toDouble() ?: 0.0,
            tinyTop1 = tinyMetric?.top1 ?: 0,
            tinyTop3 = tinyMetric?.top3 ?: 0,
            tinyReciprocalRank = tinyMetric?.reciprocalRank ?: 0.0,
            tinyBrier = tinyMetric?.brier ?: 0.0,
            tinyLogLoss = tinyMetric?.logLoss ?: 0.0,
            tinyTop1Confidence = tiny?.maxOrNull()?.toDouble() ?: 0.0,
            recentReciprocalRank = recentMetric.reciprocalRank,
            recentTop3 = recentMetric.top3,
            timeReciprocalRank = timeMetric.reciprocalRank,
            timeTop3 = timeMetric.top3,
            winner = winner,
            paired = paired,
            tinyPredictionStatus = if (paired) "OK" else "FAILED",
            statInferenceNanos = pending.statInferenceNanos ?: 0L,
            tinyInferenceNanos = pending.tinyInferenceNanos ?: 0L,
            trainingNanos = dao.learningState(pending.profileKey)?.lastTrainingNanos ?: 0L,
            modelSizeBytes = modelStateStore.modelSizeBytes(pending.profileKey),
            promotionEligible = pending.isPromotionHoldout && pending.interventionState == "NONE",
            telemetryEligible = pending.interventionState == "NONE",
            rejectionReason = if (paired) null else "TINY_PREDICTION_FAILED",
            stage = pending.promotionStageAtDecision,
            tier = pending.effectiveDecisionTierAtDecision,
            activeCheckpointId = pending.activeCheckpointId,
            isHoldout = pending.isPromotionHoldout,
            featureSchemaVersion = pending.featureSchemaVersion,
            outputSchemaVersion = pending.outputSchemaVersion,
            actionCatalogVersion = pending.actionCatalogVersion,
            appVersionCode = BuildConfig.VERSION_CODE,
            statModelVersion = pending.statModelVersion ?: 0,
            tinyModelVersion = pending.tinyModelVersion ?: 0,
            trainingConfigVersion = TRAINING_CONFIG_VERSION,
            metricSchemaVersion = 1
        )
        dao.insertShadowEvaluation(value)
        val candidate = pending.candidateProbabilities?.let(BinaryCodec::floats)
        val candidateId = pending.candidateCheckpointId
        val candidateChecksum = pending.candidateCheckpointChecksum
        val activeId = pending.activeCheckpointId
        val activeChecksum = pending.activeCheckpointChecksum
        if (candidate != null && candidateId != null && candidateChecksum != null &&
            activeId != null && activeChecksum != null && tinyMetric != null
        ) {
            val candidateMetric = metric(candidate, target)
            dao.insertCandidateEvaluation(
                CandidateShadowEvaluationEntity(
                    profileKey = pending.profileKey,
                    evaluationSeq = seq,
                    decisionId = pending.decisionId,
                    activeCheckpointId = activeId,
                    candidateCheckpointId = candidateId,
                    activeChecksum = activeChecksum,
                    candidateChecksum = candidateChecksum,
                    trueLabel = targetOutputId,
                    activeTop3 = tinyMetric.top3,
                    candidateTop3 = candidateMetric.top3,
                    activeMrr = tinyMetric.reciprocalRank,
                    candidateMrr = candidateMetric.reciprocalRank,
                    activeBrier = tinyMetric.brier,
                    candidateBrier = candidateMetric.brier,
                    activeLogLoss = tinyMetric.logLoss,
                    candidateLogLoss = candidateMetric.logLoss,
                    activeConfidence = tiny.maxOrNull()?.toDouble() ?: 0.0,
                    candidateConfidence = candidate.maxOrNull()?.toDouble() ?: 0.0,
                    activeInferenceNanos = pending.tinyInferenceNanos ?: 0L,
                    candidateInferenceNanos = pending.candidateInferenceNanos ?: 0L,
                    candidateStatus = "OK",
                    consumed = false
                )
            )
        }
        return value
    }

    companion object {
        fun metric(probabilities: FloatArray, target: Int): MetricContribution {
            require(target in probabilities.indices)
            val ranking = probabilities.indices.sortedWith(compareByDescending<Int> { probabilities[it] }.thenBy { it })
            val rank = ranking.indexOf(target) + 1
            val brier = probabilities.indices.sumOf { index ->
                val expected = if (index == target) 1.0 else 0.0
                val delta = probabilities[index].toDouble() - expected
                delta * delta
            }
            return MetricContribution(
                top1 = if (rank == 1) 1 else 0,
                top3 = if (rank <= 3) 1 else 0,
                reciprocalRank = 1.0 / rank,
                brier = brier,
                logLoss = -ln(probabilities[target].coerceAtLeast(1e-7f).toDouble())
            )
        }

        private const val WIN_EPSILON = 1e-6
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
