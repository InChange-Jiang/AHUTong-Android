package com.ahu.ahutong.personalization.telemetry

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.ShadowEvaluationEntity
import com.ahu.ahutong.personalization.storage.TelemetryAggregateWindowEntity
import com.ahu.ahutong.personalization.storage.TelemetryStateEntity
import com.google.gson.Gson
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredActionMetric(
    val actionId: String,
    val eligibleSampleCount: Int,
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
    val ties: Int
)

data class StoredPerActionMetrics(val values: List<StoredActionMetric> = emptyList())

/**
 * The only component allowed to turn a per-decision evaluation into telemetry state.
 * It is invoked in the same Room transaction as label resolution and stores sums only;
 * report construction never reads behavior, feature, probability, replay, or model tables.
 */
@Singleton
class TelemetryAggregateStore @Inject constructor(
    private val dao: BehaviorDao
) {
    private val gson = Gson()

    suspend fun contribute(evaluation: ShadowEvaluationEntity) {
        if (!evaluation.telemetryEligible) return
        val lifecycle = dao.telemetryState(evaluation.profileKey)
            ?.takeIf { it.lifecycleState == "ACTIVE" }
            ?: return
        var open = dao.openTelemetryAggregateWindow(evaluation.profileKey, lifecycle.consentLifecycleId)
        if (open != null && !sameBinding(open, lifecycle, evaluation)) {
            dao.transitionTelemetryAggregateWindow(
                open.windowId,
                expectedState = "OPEN",
                state = "SUPPRESSED",
                updatedAtEpochMs = System.currentTimeMillis()
            )
            open = null
        }
        val current = open ?: emptyWindow(lifecycle, evaluation).also { created ->
            dao.insertTelemetryAggregateWindow(created)
        }
        val perAction = readPerAction(current.perActionJson).associateBy(StoredActionMetric::actionId).toMutableMap()
        if (evaluation.trueLabel != AppActionCatalog.NONE_OUTPUT_ID &&
            evaluation.trueLabel != AppActionCatalog.OTHER_OUTPUT_ID &&
            evaluation.trueLabel in AppActionCatalog.outputIndex
        ) {
            val old = perAction[evaluation.trueLabel] ?: emptyAction(evaluation.trueLabel)
            perAction[evaluation.trueLabel] = old.add(evaluation)
        }
        val paired = evaluation.paired
        val eligibleCount = current.eligibleSampleCount + 1
        val now = System.currentTimeMillis()
        dao.updateTelemetryAggregateWindow(
            current.copy(
                endEvaluationSeq = evaluation.evaluationSeq,
                windowEndEpochDay = evaluation.occurredEpochDay,
                eligibleSampleCount = eligibleCount,
                organicNonNoneSampleCount = current.organicNonNoneSampleCount + if (evaluation.isOrganicNonNone) 1 else 0,
                pairedSampleCount = current.pairedSampleCount + if (paired) 1 else 0,
                statTop1Correct = current.statTop1Correct + if (paired) evaluation.statTop1 else 0,
                statTop3Hit = current.statTop3Hit + if (paired) evaluation.statTop3 else 0,
                statReciprocalRankSum = current.statReciprocalRankSum + if (paired) evaluation.statReciprocalRank else 0.0,
                statBrierSum = current.statBrierSum + if (paired) evaluation.statBrier else 0.0,
                statLogLossSum = current.statLogLossSum + if (paired) evaluation.statLogLoss else 0.0,
                tinyTop1Correct = current.tinyTop1Correct + if (paired) evaluation.tinyTop1 else 0,
                tinyTop3Hit = current.tinyTop3Hit + if (paired) evaluation.tinyTop3 else 0,
                tinyReciprocalRankSum = current.tinyReciprocalRankSum + if (paired) evaluation.tinyReciprocalRank else 0.0,
                tinyBrierSum = current.tinyBrierSum + if (paired) evaluation.tinyBrier else 0.0,
                tinyLogLossSum = current.tinyLogLossSum + if (paired) evaluation.tinyLogLoss else 0.0,
                tinyWins = current.tinyWins + if (paired && evaluation.winner == "TINY") 1 else 0,
                statWins = current.statWins + if (paired && evaluation.winner == "STAT") 1 else 0,
                ties = current.ties + if (paired && evaluation.winner == "TIE") 1 else 0,
                statInferenceNanosSum = current.statInferenceNanosSum + evaluation.statInferenceNanos.coerceAtLeast(0),
                tinyInferenceNanosSum = current.tinyInferenceNanosSum + evaluation.tinyInferenceNanos.coerceAtLeast(0),
                trainingNanosSum = current.trainingNanosSum + evaluation.trainingNanos.coerceAtLeast(0),
                modelSizeBytesMax = maxOf(current.modelSizeBytesMax, evaluation.modelSizeBytes),
                perActionJson = gson.toJson(StoredPerActionMetrics(perAction.values.sortedBy(StoredActionMetric::actionId))),
                state = if (eligibleCount >= WINDOW_SAMPLE_COUNT) "CLOSED" else "OPEN",
                updatedAtEpochMs = now
            )
        )
    }

    fun readPerAction(json: String): List<StoredActionMetric> =
        runCatching { gson.fromJson(json, StoredPerActionMetrics::class.java)?.values.orEmpty() }
            .getOrDefault(emptyList())

    private fun emptyWindow(
        lifecycle: TelemetryStateEntity,
        evaluation: ShadowEvaluationEntity
    ): TelemetryAggregateWindowEntity {
        val now = System.currentTimeMillis()
        return TelemetryAggregateWindowEntity(
            windowId = UUID.randomUUID().toString(),
            profileKey = evaluation.profileKey,
            consentLifecycleId = lifecycle.consentLifecycleId,
            telemetryId = lifecycle.telemetryId,
            modelGenerationId = lifecycle.modelGenerationId,
            activeCheckpointId = evaluation.activeCheckpointId,
            startEvaluationSeq = evaluation.evaluationSeq,
            endEvaluationSeq = evaluation.evaluationSeq,
            windowStartEpochDay = evaluation.occurredEpochDay,
            windowEndEpochDay = evaluation.occurredEpochDay,
            eligibleSampleCount = 0,
            organicNonNoneSampleCount = 0,
            pairedSampleCount = 0,
            statTop1Correct = 0,
            statTop3Hit = 0,
            statReciprocalRankSum = 0.0,
            statBrierSum = 0.0,
            statLogLossSum = 0.0,
            tinyTop1Correct = 0,
            tinyTop3Hit = 0,
            tinyReciprocalRankSum = 0.0,
            tinyBrierSum = 0.0,
            tinyLogLossSum = 0.0,
            tinyWins = 0,
            statWins = 0,
            ties = 0,
            statInferenceNanosSum = 0,
            tinyInferenceNanosSum = 0,
            trainingNanosSum = 0,
            modelSizeBytesMax = 0,
            perActionJson = gson.toJson(StoredPerActionMetrics()),
            appVersionCode = evaluation.appVersionCode,
            statModelVersion = evaluation.statModelVersion,
            tinyModelVersion = evaluation.tinyModelVersion,
            featureSchemaVersion = evaluation.featureSchemaVersion,
            outputSchemaVersion = evaluation.outputSchemaVersion,
            actionCatalogVersion = evaluation.actionCatalogVersion,
            trainingConfigVersion = evaluation.trainingConfigVersion,
            metricSchemaVersion = evaluation.metricSchemaVersion,
            state = "OPEN",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }

    private fun sameBinding(
        window: TelemetryAggregateWindowEntity,
        lifecycle: TelemetryStateEntity,
        evaluation: ShadowEvaluationEntity
    ): Boolean = window.telemetryId == lifecycle.telemetryId &&
        window.modelGenerationId == lifecycle.modelGenerationId &&
        window.activeCheckpointId == evaluation.activeCheckpointId &&
        window.appVersionCode == evaluation.appVersionCode &&
        window.statModelVersion == evaluation.statModelVersion &&
        window.tinyModelVersion == evaluation.tinyModelVersion &&
        window.featureSchemaVersion == evaluation.featureSchemaVersion &&
        window.outputSchemaVersion == evaluation.outputSchemaVersion &&
        window.actionCatalogVersion == evaluation.actionCatalogVersion &&
        window.trainingConfigVersion == evaluation.trainingConfigVersion &&
        window.metricSchemaVersion == evaluation.metricSchemaVersion

    private fun emptyAction(actionId: String) = StoredActionMetric(
        actionId, 0, 0, 0, 0, 0.0, 0.0, 0.0,
        0, 0, 0.0, 0.0, 0.0, 0, 0, 0
    )

    private fun StoredActionMetric.add(value: ShadowEvaluationEntity): StoredActionMetric {
        val paired = value.paired
        return copy(
            eligibleSampleCount = eligibleSampleCount + 1,
            pairedSampleCount = pairedSampleCount + if (paired) 1 else 0,
            statTop1Correct = statTop1Correct + if (paired) value.statTop1 else 0,
            statTop3Hit = statTop3Hit + if (paired) value.statTop3 else 0,
            statReciprocalRankSum = statReciprocalRankSum + if (paired) value.statReciprocalRank else 0.0,
            statBrierSum = statBrierSum + if (paired) value.statBrier else 0.0,
            statLogLossSum = statLogLossSum + if (paired) value.statLogLoss else 0.0,
            tinyTop1Correct = tinyTop1Correct + if (paired) value.tinyTop1 else 0,
            tinyTop3Hit = tinyTop3Hit + if (paired) value.tinyTop3 else 0,
            tinyReciprocalRankSum = tinyReciprocalRankSum + if (paired) value.tinyReciprocalRank else 0.0,
            tinyBrierSum = tinyBrierSum + if (paired) value.tinyBrier else 0.0,
            tinyLogLossSum = tinyLogLossSum + if (paired) value.tinyLogLoss else 0.0,
            tinyWins = tinyWins + if (paired && value.winner == "TINY") 1 else 0,
            statWins = statWins + if (paired && value.winner == "STAT") 1 else 0,
            ties = ties + if (paired && value.winner == "TIE") 1 else 0
        )
    }

    private companion object {
        const val WINDOW_SAMPLE_COUNT = 64
    }
}
