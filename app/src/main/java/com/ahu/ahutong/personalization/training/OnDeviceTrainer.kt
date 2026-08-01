package com.ahu.ahutong.personalization.training

import com.ahu.ahutong.personalization.action.ActionFamily
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.context.PredictionInput
import com.ahu.ahutong.personalization.inference.AdamWState
import com.ahu.ahutong.personalization.inference.TinyMlpBackprop
import com.ahu.ahutong.personalization.model.ModelStateStore
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.LearningStateEntity
import com.ahu.ahutong.personalization.storage.TrainingSampleEntity
import com.ahu.ahutong.personalization.storage.TrainingBatchJournalEntity
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

data class OrganicTrainingSample(
    val sampleId: String = UUID.randomUUID().toString(),
    val input: PredictionInput,
    val targetOutputId: String,
    val actionFamily: ActionFamily,
    val labelSource: String
)

data class TrainingSliceResult(
    val trained: Boolean,
    val profileKey: String?,
    val batches: Int,
    val samples: Int,
    val averageLoss: Float?,
    val gradientNorm: Float?,
    val elapsedNanos: Long,
    val reason: String
)

interface OnDeviceTrainer {
    suspend fun enqueue(sample: OrganicTrainingSample)
    suspend fun runIdleSlice(budgetMillis: Long): TrainingSliceResult
    fun resumeProfile(profileKey: String)
    suspend fun cancelProfile(profileKey: String)
}

@Singleton
class KotlinOnDeviceTrainer @Inject constructor(
    private val dao: BehaviorDao,
    private val stateStore: ModelStateStore
) : OnDeviceTrainer {
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tiny-mlp-trainer").apply { priority = Thread.MIN_PRIORITY }
    }.asCoroutineDispatcher()
    private val pendingProfiles = ConcurrentHashMap.newKeySet<String>()
    private val cancelledGenerations = ConcurrentHashMap<String, Long>()

    override suspend fun enqueue(sample: OrganicTrainingSample) {
        require(sample.labelSource == "ORGANIC_ACTION" || sample.labelSource == "INTERVENTION_FREE_TIMEOUT")
        val targetIndex = AppActionCatalog.outputIndex.getValue(sample.targetOutputId)
        dao.insertTrainingSample(
            TrainingSampleEntity(
                sampleId = sample.sampleId,
                profileKey = sample.input.profileKey,
                decisionId = sample.input.decisionId,
                featureSchemaVersion = sample.input.featureSchemaVersion,
                outputSchemaVersion = sample.input.outputSchemaVersion,
                actionCatalogVersion = sample.input.actionCatalogVersion,
                features = sample.input.features.toBytes(),
                targetIndex = targetIndex,
                targetActionId = sample.targetOutputId,
                actionFamily = sample.actionFamily.name,
                occurredEpochDay = sample.input.snapshot.epochDay,
                replayPriority = replayPriority(sample.input.decisionId, sample.targetOutputId),
                trainingCount = 0,
                labelSource = sample.labelSource
            )
        )
        if (dao.trainingSampleCount(sample.input.profileKey) > REPLAY_LIMIT) {
            check(dao.evictOneReplaySample(sample.input.profileKey) == 1)
        }
        pendingProfiles += sample.input.profileKey
    }

    override fun resumeProfile(profileKey: String) {
        pendingProfiles += profileKey
    }

    override suspend fun runIdleSlice(budgetMillis: Long): TrainingSliceResult = withContext(dispatcher) {
        val profileKey = pendingProfiles.firstOrNull()
            ?: return@withContext TrainingSliceResult(false, null, 0, 0, null, null, 0, "NO_PENDING_PROFILE")
        val generation = cancelledGenerations[profileKey] ?: 0L
        val total = dao.trainingSampleCount(profileKey)
        val nonNone = dao.organicNonNoneTrainingSampleCount(profileKey)
        val families = dao.trainingActionFamilyCount(profileKey)
        val qualifiedActions = dao.qualifiedTrainingActionCount(profileKey, MIN_PER_ACTION)
        if (total < MIN_SAMPLES || nonNone < MIN_NON_NONE || families < MIN_FAMILIES || qualifiedActions < MIN_QUALIFIED_ACTIONS) {
            return@withContext TrainingSliceResult(false, profileKey, 0, total, null, null, 0, "MINIMUM_SAMPLE_GATE")
        }

        val started = System.nanoTime()
        val deadline = started + budgetMillis.coerceIn(1, MAX_SLICE_MS) * 1_000_000L
        var batches = 0
        var lastLoss: Float? = null
        var lastNorm: Float? = null
        recoverPreparedBatch(profileKey)?.let { recovered ->
            batches++
            lastLoss = recovered.averageLoss
            lastNorm = recovered.gradientNorm
        }
        while (batches < MAX_BATCHES && System.nanoTime() < deadline) {
            if ((cancelledGenerations[profileKey] ?: 0L) != generation) break
            val candidates = (
                dao.recentTrainingSamples(profileKey, RECENT_CANDIDATES) +
                    dao.historicalReplayCandidates(profileKey, HISTORICAL_CANDIDATES)
                ).distinctBy(TrainingSampleEntity::rowId)
            val selected = balancedBatch(candidates, BATCH_SIZE)
            if (selected.size < BATCH_SIZE) break
            val state = stateStore.state(profileKey)
            val parameters = state.training.parameters.deepCopy(state.optimizer.step)
            val optimizer = state.optimizer.deepCopy()
            val result = TinyMlpBackprop.trainBatch(
                parameters,
                optimizer,
                selected.map { BinaryCodec.floats(it.features) },
                selected.map(TrainingSampleEntity::targetIndex).toIntArray()
            )
            require(result.averageLoss.isFinite() && result.gradientNorm.isFinite())
            val rowIds = selected.map(TrainingSampleEntity::rowId)
            val batchId = batchId(profileKey, state.training.trainingRevision, rowIds)
            check(
                dao.insertTrainingBatchJournal(
                    TrainingBatchJournalEntity(
                        batchId = batchId,
                        profileKey = profileKey,
                        expectedTrainingRevision = state.training.trainingRevision,
                        selectedRowIds = rowIds.joinToString(","),
                        state = "PREPARED",
                        createdAtEpochMs = System.currentTimeMillis(),
                        committedAtEpochMs = null
                    )
                ) != -1L
            ) { "training batch journal already exists" }
            stateStore.commitTrainingBatch(
                profileKey,
                state.training.trainingRevision,
                batchId,
                parameters.deepCopy(result.steps),
                optimizer
            )
            dao.completeTrainingBatch(batchId, rowIds, System.currentTimeMillis())
            batches++
            lastLoss = result.averageLoss
            lastNorm = result.gradientNorm
        }
        val elapsed = System.nanoTime() - started
        if (batches > 0) {
            val previous = dao.learningState(profileKey)
            dao.upsertLearningState(
                LearningStateEntity(
                    profileKey,
                    previous?.statLearningStartedEpochDay,
                    previous?.tinyTrainingStartedEpochDay ?: LocalDate.now(ZoneOffset.UTC).toEpochDay(),
                    stateStore.state(profileKey).lastAppliedBatchId,
                    elapsed,
                    lastLoss,
                    lastNorm
                )
            )
            val committed = stateStore.state(profileKey)
            if (committed.candidate == null &&
                committed.training.trainingRevision - committed.lastConsumedCandidateRevision >= CANDIDATE_MIN_STEPS &&
                total - committed.lastConsumedCandidateSampleCount >= CANDIDATE_MIN_NEW_SAMPLES &&
                dao.promotionState(profileKey)?.consecutivePassingWindows == 0
            ) {
                stateStore.createCandidate(profileKey, committed.training.trainingRevision, total)
            }
            pendingProfiles.remove(profileKey)
        }
        TrainingSliceResult(batches > 0, profileKey, batches, total, lastLoss, lastNorm, elapsed, if (batches > 0) "COMMITTED" else "BUDGET_OR_BATCH_GATE")
    }

    override suspend fun cancelProfile(profileKey: String) {
        cancelledGenerations.merge(profileKey, 1L, Long::plus)
        pendingProfiles.remove(profileKey)
        // A dispatcher barrier ensures no in-flight model/Room commit can race a profile wipe.
        withContext(dispatcher) { Unit }
    }

    private suspend fun recoverPreparedBatch(profileKey: String): TrainingStep? {
        val journal = dao.preparedTrainingBatch(profileKey) ?: return null
        val rowIds = journal.selectedRowIds.split(',').mapNotNull(String::toLongOrNull)
        if (rowIds.isEmpty()) {
            dao.abandonTrainingBatchJournal(journal.batchId, System.currentTimeMillis())
            return null
        }
        val state = stateStore.state(profileKey)
        if (state.lastAppliedBatchId == journal.batchId) {
            dao.completeTrainingBatch(journal.batchId, rowIds, System.currentTimeMillis())
            return TrainingStep(null, null)
        }
        if (state.training.trainingRevision != journal.expectedTrainingRevision) {
            dao.abandonTrainingBatchJournal(journal.batchId, System.currentTimeMillis())
            error("training revision diverged from prepared journal")
        }
        val byId = dao.trainingSamplesByIds(profileKey, rowIds).associateBy(TrainingSampleEntity::rowId)
        val samples = rowIds.mapNotNull(byId::get)
        if (samples.size != rowIds.size) {
            dao.abandonTrainingBatchJournal(journal.batchId, System.currentTimeMillis())
            error("prepared training samples are missing")
        }
        val parameters = state.training.parameters.deepCopy(state.optimizer.step)
        val optimizer = state.optimizer.deepCopy()
        val result = TinyMlpBackprop.trainBatch(
            parameters,
            optimizer,
            samples.map { BinaryCodec.floats(it.features) },
            samples.map(TrainingSampleEntity::targetIndex).toIntArray()
        )
        require(result.averageLoss.isFinite() && result.gradientNorm.isFinite())
        stateStore.commitTrainingBatch(
            profileKey,
            journal.expectedTrainingRevision,
            journal.batchId,
            parameters.deepCopy(result.steps),
            optimizer
        )
        dao.completeTrainingBatch(journal.batchId, rowIds, System.currentTimeMillis())
        return TrainingStep(result.averageLoss, result.gradientNorm)
    }

    private fun batchId(profileKey: String, revision: Long, rowIds: List<Long>): String {
        val source = "$profileKey|$revision|${rowIds.joinToString(",")}".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) }
    }

    private data class TrainingStep(val averageLoss: Float?, val gradientNorm: Float?)

    private fun balancedBatch(values: List<TrainingSampleEntity>, size: Int): List<TrainingSampleEntity> {
        val nonNoneGroups = values.filter { it.targetActionId != AppActionCatalog.NONE_OUTPUT_ID }
            .groupBy(TrainingSampleEntity::targetActionId)
            .values
            .map { group ->
                ArrayDeque(group.sortedWith(
                    compareBy<TrainingSampleEntity> { it.trainingCount }
                        .thenByDescending { it.replayPriority }
                        .thenByDescending { it.rowId }
                ))
            }
            .sortedBy { it.size }
        val none = values.filter { it.targetActionId == AppActionCatalog.NONE_OUTPUT_ID }
            .sortedWith(
                compareBy<TrainingSampleEntity> { it.trainingCount }
                    .thenByDescending { it.replayPriority }
                    .thenByDescending { it.rowId }
            )
        val result = ArrayList<TrainingSampleEntity>(size)
        val nonNoneTarget = size - min(none.size, size / 2)
        while (result.size < nonNoneTarget && nonNoneGroups.any(ArrayDeque<TrainingSampleEntity>::isNotEmpty)) {
            nonNoneGroups.forEach { group ->
                if (result.size < nonNoneTarget && group.isNotEmpty()) result += group.removeFirst()
            }
        }
        result += none.take(min(size / 2, size - result.size))
        if (result.size < size) {
            result += nonNoneGroups.flatMap(ArrayDeque<TrainingSampleEntity>::toList).take(size - result.size)
        }
        return result.distinctBy(TrainingSampleEntity::rowId).take(size)
    }

    private fun replayPriority(decisionId: String, targetOutputId: String): Float {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$decisionId|$targetOutputId".toByteArray(Charsets.UTF_8))
        val reservoir = (((digest[0].toInt() and 0xff) shl 8) or (digest[1].toInt() and 0xff)) / 65_535f
        return reservoir + if (targetOutputId == AppActionCatalog.NONE_OUTPUT_ID) 0f else 1f
    }

    private fun AdamWState.deepCopy() = AdamWState(
        firstMoments.map(FloatArray::copyOf),
        secondMoments.map(FloatArray::copyOf),
        step
    )

    private companion object {
        const val MIN_SAMPLES = 64
        const val MIN_NON_NONE = 32
        const val MIN_FAMILIES = 3
        const val MIN_QUALIFIED_ACTIONS = 2
        const val MIN_PER_ACTION = 8
        const val BATCH_SIZE = 16
        const val MAX_BATCHES = 4
        const val MAX_SLICE_MS = 50L
        const val REPLAY_LIMIT = 2_048
        const val RECENT_CANDIDATES = 128
        const val HISTORICAL_CANDIDATES = 256
        const val CANDIDATE_MIN_STEPS = 32L
        const val CANDIDATE_MIN_NEW_SAMPLES = 64
    }
}
