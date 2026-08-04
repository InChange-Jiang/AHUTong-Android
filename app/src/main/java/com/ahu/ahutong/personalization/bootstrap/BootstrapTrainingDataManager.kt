package com.ahu.ahutong.personalization.bootstrap

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.context.V3ToV4FeatureAdapter
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.BootstrapTrainingBatchEntity
import com.ahu.ahutong.personalization.storage.BootstrapTrainingConsentEntity
import com.ahu.ahutong.personalization.storage.BootstrapTrainingDeletionTombstoneEntity
import com.ahu.ahutong.personalization.storage.BootstrapTrainingExampleEntity
import com.ahu.ahutong.personalization.storage.JourneyTrainingSampleEntity
import com.ahu.ahutong.personalization.storage.PresetTrainingSampleEntity
import com.ahu.ahutong.personalization.storage.transaction
import com.ahu.ahutong.personalization.training.OrganicTrainingSample
import com.ahu.ahutong.personalization.training.TrainingFeedbackPolicy
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class BootstrapTrainingDataManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BehaviorDatabase,
    private val dao: BehaviorDao,
    private val secretStore: BootstrapTrainingSecretStore
) {
    private val mutex = Mutex()
    private val random = SecureRandom()
    private val gson = Gson()
    private val _status = MutableStateFlow(BootstrapContributionStatus())
    val status: StateFlow<BootstrapContributionStatus> = _status.asStateFlow()
    @Volatile private var activeProfile: String? = null

    suspend fun reconcileProfile(profileKey: String, enabled: Boolean, includeHistorical: Boolean) {
        activeProfile = profileKey
        val existing = dao.bootstrapTrainingConsent(profileKey)
        when {
            enabled && existing == null -> setConsent(profileKey, true, includeHistorical)
            enabled && existing?.state == "ACTIVE" -> {
                refreshStatus(profileKey)
                schedulePeriodicUpload()
            }
            !enabled && existing != null -> revoke(profileKey)
            else -> _status.value = BootstrapContributionStatus()
        }
    }

    suspend fun setConsent(profileKey: String, enabled: Boolean, includeHistorical: Boolean = false) {
        if (!enabled) {
            revoke(profileKey)
            return
        }
        mutex.withLock {
            if (dao.bootstrapTrainingConsent(profileKey)?.state == "ACTIVE") return@withLock
            val now = System.currentTimeMillis()
            val lifecycleId = UUID.randomUUID().toString()
            val capability = randomBytesBase64(32)
            val encrypted = secretStore.createAndEncrypt(lifecycleId, capability)
            var state = BootstrapTrainingConsentEntity(
                profileKey = profileKey,
                consentLifecycleId = lifecycleId,
                participantId = UUID.randomUUID().toString(),
                secretAlias = encrypted.alias,
                encryptedRevocationCapability = encrypted.ciphertext,
                consentSchemaVersion = BOOTSTRAP_CONSENT_SCHEMA_VERSION,
                includeHistorical = includeHistorical,
                historicalBackfillCompleted = !includeHistorical,
                nextSequenceNo = 1L,
                contributedExampleCount = 0,
                lastUploadAtEpochMs = null,
                state = "ACTIVE",
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            dao.upsertBootstrapTrainingConsent(state)
            if (includeHistorical) {
                state = backfillHistorical(state)
                dao.upsertBootstrapTrainingConsent(state)
            }
        }
        refreshStatus(profileKey)
        schedulePeriodicUpload()
        scheduleUpload(includeHistorical)
    }

    suspend fun revoke(profileKey: String) {
        val alias = mutex.withLock {
            val consent = dao.bootstrapTrainingConsent(profileKey) ?: return@withLock null
            val now = System.currentTimeMillis()
            val tombstone = BootstrapTrainingDeletionTombstoneEntity(
                deletionId = UUID.randomUUID().toString(),
                participantId = consent.participantId,
                consentLifecycleId = consent.consentLifecycleId,
                secretAlias = consent.secretAlias,
                encryptedRevocationCapability = consent.encryptedRevocationCapability,
                state = "READY",
                attemptCount = 0,
                nextAttemptAtEpochMs = now,
                lastErrorCode = null,
                createdAtEpochMs = now,
                acknowledgedAtEpochMs = null
            )
            database.transaction {
                check(dao.insertBootstrapTrainingDeletionTombstone(tombstone) != -1L)
                dao.deleteBootstrapTrainingProfileState(profileKey)
            }
            consent.secretAlias
        }
        if (alias != null) scheduleUpload(false, immediate = true)
        if (activeProfile == profileKey) _status.value = BootstrapContributionStatus()
    }

    suspend fun captureNextAction(sample: OrganicTrainingSample) {
        if (sample.deliveryLane == "TARGETED") return
        val weight = TrainingFeedbackPolicy.sampleWeight(sample.labelSource)
        if (weight <= 0f) return
        append(
            profileKey = sample.input.profileKey,
            task = BootstrapTrainingTask.NEXT_ACTION,
            completeness = if (sample.availabilityMask == null) {
                BootstrapExampleCompleteness.LEGACY_PARTIAL
            } else {
                BootstrapExampleCompleteness.COMPLETE
            },
            featureSchemaVersion = sample.input.featureSchemaVersion,
            outputSchemaVersion = sample.input.outputSchemaVersion,
            actionCatalogVersion = sample.input.actionCatalogVersion,
            features = sample.input.features.toBytes(),
            availabilityMask = sample.availabilityMask,
            targetLabel = sample.targetOutputId,
            feedbackSource = sample.labelSource,
            sampleWeight = weight,
            deliveryLane = sample.deliveryLane,
            naturalHoldoutEligible = sample.naturalHoldoutEligible,
            occurredEpochDay = sample.input.snapshot.epochDay
        )
    }

    suspend fun captureJourney(sample: JourneyTrainingSampleEntity) {
        append(
            profileKey = sample.profileKey,
            task = BootstrapTrainingTask.JOURNEY_GOAL,
            completeness = BootstrapExampleCompleteness.COMPLETE,
            featureSchemaVersion = sample.featureSchemaVersion,
            outputSchemaVersion = sample.journeyOutputSchemaVersion,
            actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
            features = sample.features,
            availabilityMask = null,
            targetLabel = sample.targetActionId,
            feedbackSource = sample.labelSource,
            sampleWeight = 1f,
            journeyLengthBucket = journeyLengthBucket(sample.journeyLength),
            naturalHoldoutEligible = true,
            occurredEpochDay = sample.occurredEpochDay
        )
    }

    suspend fun capturePreset(sample: PresetTrainingSampleEntity, candidateOrdinal: Int) {
        appendPreset(
            profileKey = sample.profileKey,
            domainId = sample.domainId,
            rawOpportunityId = sample.opportunityId,
            candidateOrdinal = candidateOrdinal,
            features = sample.features,
            label = sample.label,
            occurredEpochDay = sample.occurredEpochDay,
            feedbackSource = sample.feedbackSource,
            sampleWeight = sample.sampleWeight,
            naturalHoldoutEligible = sample.naturalHoldoutEligible,
            historical = false
        )
    }

    suspend fun capturePresetOpportunity(
        profileKey: String,
        domainId: String,
        rawOpportunityId: String,
        candidateOrdinal: Int,
        features: ByteArray,
        label: Boolean,
        occurredEpochDay: Long,
        naturalHoldoutEligible: Boolean
    ) {
        appendPreset(
            profileKey,
            domainId,
            rawOpportunityId,
            candidateOrdinal,
            features,
            label,
            occurredEpochDay,
            "NATURAL_COMMIT",
            1f,
            naturalHoldoutEligible,
            false
        )
    }

    suspend fun prepareDueBatches() {
        dao.activeBootstrapTrainingConsents().forEach { consent ->
            mutex.withLock { prepareOneBatch(consent) }
        }
    }

    suspend fun refreshStatus(profileKey: String? = activeProfile) {
        val resolvedProfile = profileKey ?: return
        val consent = dao.bootstrapTrainingConsent(resolvedProfile)
        _status.value = if (consent?.state == "ACTIVE") {
            BootstrapContributionStatus(
                enabled = true,
                pendingExamples = dao.pendingBootstrapTrainingExampleCount(resolvedProfile),
                contributedExamples = consent.contributedExampleCount,
                lastUploadAtEpochMs = consent.lastUploadAtEpochMs,
                includeHistorical = consent.includeHistorical
            )
        } else {
            BootstrapContributionStatus()
        }
    }

    private suspend fun appendPreset(
        profileKey: String,
        domainId: String,
        rawOpportunityId: String,
        candidateOrdinal: Int,
        features: ByteArray,
        label: Boolean,
        occurredEpochDay: Long,
        feedbackSource: String,
        sampleWeight: Float,
        naturalHoldoutEligible: Boolean,
        historical: Boolean
    ) {
        append(
            profileKey = profileKey,
            task = BootstrapTrainingTask.PRESET_RANKING,
            completeness = BootstrapExampleCompleteness.COMPLETE,
            featureSchemaVersion = PRESET_FEATURE_SCHEMA_VERSION,
            outputSchemaVersion = PRESET_OUTPUT_SCHEMA_VERSION,
            actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
            features = features,
            availabilityMask = null,
            targetLabel = if (label) "1" else "0",
            feedbackSource = feedbackSource,
            sampleWeight = sampleWeight,
            domainId = domainId,
            rawOpportunityId = rawOpportunityId,
            candidateOrdinal = candidateOrdinal,
            naturalHoldoutEligible = naturalHoldoutEligible,
            occurredEpochDay = occurredEpochDay,
            historical = historical
        )
    }

    private suspend fun append(
        profileKey: String,
        task: BootstrapTrainingTask,
        completeness: BootstrapExampleCompleteness,
        featureSchemaVersion: Int,
        outputSchemaVersion: Int,
        actionCatalogVersion: Int,
        features: ByteArray,
        availabilityMask: ByteArray?,
        targetLabel: String,
        feedbackSource: String,
        sampleWeight: Float,
        deliveryLane: String? = null,
        domainId: String? = null,
        opportunityGroupId: String? = null,
        rawOpportunityId: String? = null,
        candidateOrdinal: Int? = null,
        journeyLengthBucket: Int? = null,
        naturalHoldoutEligible: Boolean,
        occurredEpochDay: Long,
        historical: Boolean = false
    ) {
        mutex.withLock {
            val consent = dao.bootstrapTrainingConsent(profileKey)?.takeIf { it.state == "ACTIVE" } ?: return
            val now = System.currentTimeMillis()
            val resolvedOpportunityGroupId = if (task == BootstrapTrainingTask.PRESET_RANKING && rawOpportunityId != null) {
                val capability = secretStore.decrypt(
                    consent.secretAlias,
                    consent.encryptedRevocationCapability
                )
                hmacSha256(capability, rawOpportunityId)
            } else {
                opportunityGroupId
            }
            val value = BootstrapTrainingExampleEntity(
                exampleId = UUID.randomUUID().toString(),
                profileKey = profileKey,
                consentLifecycleId = consent.consentLifecycleId,
                participantId = consent.participantId,
                sequenceNo = consent.nextSequenceNo,
                task = task.name,
                completeness = completeness.name,
                featureSchemaVersion = featureSchemaVersion,
                outputSchemaVersion = outputSchemaVersion,
                actionCatalogVersion = actionCatalogVersion,
                features = features.copyOf(),
                availabilityMask = availabilityMask?.copyOf(),
                targetLabel = targetLabel,
                feedbackSource = feedbackSource,
                sampleWeight = sampleWeight,
                deliveryLane = deliveryLane,
                domainId = domainId,
                opportunityGroupId = resolvedOpportunityGroupId,
                candidateOrdinal = candidateOrdinal,
                journeyLengthBucket = journeyLengthBucket,
                naturalHoldoutEligible = naturalHoldoutEligible,
                occurredEpochDay = occurredEpochDay,
                historical = historical,
                state = "PENDING",
                batchId = null,
                createdAtEpochMs = now
            )
            val payload = value.toPayload()
            BootstrapTrainingPayloadValidator.requireValidExample(payload)
            if (dao.insertBootstrapTrainingExample(value) != -1L) {
                dao.upsertBootstrapTrainingConsent(
                    consent.copy(nextSequenceNo = consent.nextSequenceNo + 1, updatedAtEpochMs = now)
                )
                enforceTaskCap(profileKey, task)
            }
        }
        if (activeProfile == profileKey) refreshStatus(profileKey)
        scheduleUpload(historical)
    }

    private suspend fun backfillHistorical(initial: BootstrapTrainingConsentEntity): BootstrapTrainingConsentEntity {
        var state = initial
        val minimumDay = LocalDate.now(ZoneOffset.UTC).minusDays(29).toEpochDay()
        dao.recentTrainingSamples(initial.profileKey, NEXT_ACTION_LIMIT)
            .asReversed()
            .filter { it.occurredEpochDay >= minimumDay }
            .forEach { sample ->
                val adapted = V3ToV4FeatureAdapter.adapt(BinaryCodec.floats(sample.features), sample.featureSchemaVersion)
                state = insertHistorical(
                    state,
                    BootstrapTrainingTask.NEXT_ACTION,
                    featureSchemaVersion = com.ahu.ahutong.personalization.context.FeatureExtractor.FEATURE_SCHEMA_VERSION,
                    outputSchemaVersion = sample.outputSchemaVersion,
                    actionCatalogVersion = sample.actionCatalogVersion,
                    features = BinaryCodec.floats(adapted),
                    targetLabel = sample.targetActionId,
                    feedbackSource = sample.labelSource,
                    sampleWeight = TrainingFeedbackPolicy.sampleWeight(sample.labelSource),
                    naturalHoldoutEligible = false,
                    occurredEpochDay = sample.occurredEpochDay
                )
            }
        dao.recentJourneyTrainingSamples(initial.profileKey, JOURNEY_LIMIT)
            .asReversed()
            .filter { it.occurredEpochDay >= minimumDay }
            .forEach { sample ->
                state = insertHistorical(
                    state,
                    BootstrapTrainingTask.JOURNEY_GOAL,
                    featureSchemaVersion = sample.featureSchemaVersion,
                    outputSchemaVersion = sample.journeyOutputSchemaVersion,
                    actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
                    features = sample.features,
                    targetLabel = sample.targetActionId,
                    feedbackSource = sample.labelSource,
                    sampleWeight = 1f,
                    naturalHoldoutEligible = false,
                    occurredEpochDay = sample.occurredEpochDay,
                    journeyLengthBucket = journeyLengthBucket(sample.journeyLength)
                )
            }
        val presetRows = dao.recentPresetTrainingSamples(initial.profileKey, PRESET_LIMIT)
            .filter { it.occurredEpochDay >= minimumDay }
            .groupBy { it.opportunityId }
        val capability = secretStore.decrypt(state.secretAlias, state.encryptedRevocationCapability)
        presetRows.values.forEach { group ->
            group.sortedBy { it.rowId }.forEachIndexed { ordinal, sample ->
                state = insertHistorical(
                    state,
                    BootstrapTrainingTask.PRESET_RANKING,
                    featureSchemaVersion = PRESET_FEATURE_SCHEMA_VERSION,
                    outputSchemaVersion = PRESET_OUTPUT_SCHEMA_VERSION,
                    actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
                    features = sample.features,
                    targetLabel = if (sample.label) "1" else "0",
                    feedbackSource = sample.feedbackSource,
                    sampleWeight = sample.sampleWeight,
                    naturalHoldoutEligible = sample.naturalHoldoutEligible,
                    occurredEpochDay = sample.occurredEpochDay,
                    domainId = sample.domainId,
                    opportunityGroupId = hmacSha256(capability, sample.opportunityId),
                    candidateOrdinal = ordinal
                )
            }
        }
        return state.copy(
            historicalBackfillCompleted = true,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private suspend fun insertHistorical(
        consent: BootstrapTrainingConsentEntity,
        task: BootstrapTrainingTask,
        featureSchemaVersion: Int,
        outputSchemaVersion: Int,
        actionCatalogVersion: Int,
        features: ByteArray,
        targetLabel: String,
        feedbackSource: String,
        sampleWeight: Float,
        naturalHoldoutEligible: Boolean,
        occurredEpochDay: Long,
        journeyLengthBucket: Int? = null,
        domainId: String? = null,
        opportunityGroupId: String? = null,
        candidateOrdinal: Int? = null
    ): BootstrapTrainingConsentEntity {
        if (sampleWeight <= 0f) return consent
        val now = System.currentTimeMillis()
        val entity = BootstrapTrainingExampleEntity(
            exampleId = UUID.randomUUID().toString(),
            profileKey = consent.profileKey,
            consentLifecycleId = consent.consentLifecycleId,
            participantId = consent.participantId,
            sequenceNo = consent.nextSequenceNo,
            task = task.name,
            completeness = if (task == BootstrapTrainingTask.NEXT_ACTION) {
                BootstrapExampleCompleteness.LEGACY_PARTIAL.name
            } else {
                BootstrapExampleCompleteness.COMPLETE.name
            },
            featureSchemaVersion = featureSchemaVersion,
            outputSchemaVersion = outputSchemaVersion,
            actionCatalogVersion = actionCatalogVersion,
            features = features.copyOf(),
            availabilityMask = null,
            targetLabel = targetLabel,
            feedbackSource = feedbackSource,
            sampleWeight = sampleWeight,
            deliveryLane = if (task == BootstrapTrainingTask.NEXT_ACTION) "ORDINARY_NEXT_ACTION" else null,
            domainId = domainId,
            opportunityGroupId = opportunityGroupId,
            candidateOrdinal = candidateOrdinal,
            journeyLengthBucket = journeyLengthBucket,
            naturalHoldoutEligible = naturalHoldoutEligible,
            occurredEpochDay = occurredEpochDay,
            historical = true,
            state = "PENDING",
            batchId = null,
            createdAtEpochMs = now
        )
        if (runCatching {
                BootstrapTrainingPayloadValidator.requireValidExample(entity.toPayload())
            }.isFailure
        ) return consent
        return if (dao.insertBootstrapTrainingExample(entity) != -1L) {
            consent.copy(nextSequenceNo = consent.nextSequenceNo + 1, updatedAtEpochMs = now)
        } else {
            consent
        }
    }

    private suspend fun prepareOneBatch(consent: BootstrapTrainingConsentEntity) {
        val pending = dao.pendingBootstrapTrainingExamples(consent.profileKey, MAX_PENDING_SCAN)
            .filter { it.consentLifecycleId == consent.consentLifecycleId }
        if (pending.isEmpty()) return
        val task = pending.first().task
        val sameTask = pending.filter { it.task == task }
        val selected = selectBalanced(task, sameTask)
        if (selected.isEmpty()) return
        val capability = runCatching {
            secretStore.decrypt(consent.secretAlias, consent.encryptedRevocationCapability)
        }.getOrNull() ?: return
        var batchRows = selected
        var request: BootstrapTrainingBatchRequest
        var body: ByteArray
        do {
            request = BootstrapTrainingBatchRequest(
                batchId = UUID.randomUUID().toString(),
                participantId = consent.participantId,
                consentLifecycleId = consent.consentLifecycleId,
                consentSchemaVersion = consent.consentSchemaVersion,
                revocationCapabilityHash = sha256(capability.toByteArray(Charsets.UTF_8)),
                appVersionCode = BuildConfig.VERSION_CODE,
                examples = batchRows.map(BootstrapTrainingExampleEntity::toPayload)
            )
            BootstrapTrainingPayloadValidator.requireValid(request)
            body = gson.toJson(request).toByteArray(Charsets.UTF_8)
            if (body.size > MAX_BODY_BYTES) batchRows = batchRows.dropLast(maxOf(1, batchRows.size / 8))
        } while (body.size > MAX_BODY_BYTES && batchRows.isNotEmpty())
        if (batchRows.isEmpty()) return
        val now = System.currentTimeMillis()
        val batch = BootstrapTrainingBatchEntity(
            batchId = request.batchId,
            profileKey = consent.profileKey,
            consentLifecycleId = consent.consentLifecycleId,
            participantId = consent.participantId,
            protocolVersion = BOOTSTRAP_PROTOCOL_VERSION,
            body = body,
            bodySha256 = sha256(body),
            exampleCount = batchRows.size,
            containsHistorical = batchRows.any { it.historical },
            state = "READY",
            attemptCount = 0,
            nextAttemptAtEpochMs = now,
            lastErrorCode = null,
            createdAtEpochMs = now,
            acknowledgedAtEpochMs = null
        )
        database.transaction {
            dao.insertBootstrapTrainingBatch(batch)
            check(dao.markBootstrapTrainingExamplesBatched(batchRows.map { it.rowId }, batch.batchId) == batchRows.size)
        }
    }

    private fun selectBalanced(task: String, rows: List<BootstrapTrainingExampleEntity>): List<BootstrapTrainingExampleEntity> {
        val natural = rows.filter { it.sampleWeight >= 0.999f }
        val weak = rows.filter { it.sampleWeight < 0.999f }
        val naturalSelected = when (task) {
            BootstrapTrainingTask.NEXT_ACTION.name -> balancedNextActionNatural(natural, 192)
            BootstrapTrainingTask.JOURNEY_GOAL.name -> balancedLabels(natural, "NONE", 192)
            BootstrapTrainingTask.PRESET_RANKING.name -> wholePresetGroups(natural, 192)
            else -> emptyList()
        }
        if (naturalSelected.isEmpty()) return emptyList()
        val weakLimit = minOf(
            MAX_EXAMPLES_PER_BATCH / 4,
            MAX_EXAMPLES_PER_BATCH - naturalSelected.size,
            naturalSelected.size / 3
        )
        val weakSelected = when (task) {
            BootstrapTrainingTask.PRESET_RANKING.name -> wholePresetGroups(weak, weakLimit)
            else -> balancedLabels(weak, if (task == BootstrapTrainingTask.NEXT_ACTION.name) AppActionCatalog.NONE_OUTPUT_ID else "NONE", weakLimit)
        }
        return (naturalSelected + weakSelected).sortedBy { it.sequenceNo }.take(MAX_EXAMPLES_PER_BATCH)
    }

    private fun balancedNextActionNatural(
        rows: List<BootstrapTrainingExampleEntity>,
        limit: Int
    ): List<BootstrapTrainingExampleEntity> {
        val actionGroups = rows.filter { it.targetLabel != AppActionCatalog.NONE_OUTPUT_ID }
            .groupBy { it.targetLabel }
            .values
            .sortedByDescending { it.size }
        if (actionGroups.size < 3) return emptyList()
        val selectedGroups = actionGroups.take(minOf(actionGroups.size, limit))
        val perAction = minOf(selectedGroups.minOf { it.size }, limit / selectedGroups.size)
        if (perAction <= 0) return emptyList()
        val nonNone = selectedGroups.flatMap { group -> group.sortedBy { it.sequenceNo }.take(perAction) }
        val none = rows.filter { it.targetLabel == AppActionCatalog.NONE_OUTPUT_ID }
            .sortedBy { it.sequenceNo }
            .take(minOf(nonNone.size, limit - nonNone.size))
        return (nonNone + none).sortedBy { it.sequenceNo }.take(limit)
    }

    private fun balancedLabels(
        rows: List<BootstrapTrainingExampleEntity>,
        noneLabel: String,
        limit: Int
    ): List<BootstrapTrainingExampleEntity> {
        if (limit <= 0) return emptyList()
        val none = ArrayDeque(rows.filter { it.targetLabel == noneLabel }.sortedBy { it.sequenceNo })
        val groups = rows.filter { it.targetLabel != noneLabel }
            .groupBy { it.targetLabel }
            .values
            .map { ArrayDeque(it.sortedBy { row -> row.sequenceNo }) }
            .sortedBy { it.size }
        val result = mutableListOf<BootstrapTrainingExampleEntity>()
        val perLabelLimit = maxOf(1, (limit * 0.4f).toInt())
        while (result.size < limit && groups.any { it.isNotEmpty() }) {
            groups.forEach { group ->
                if (result.size < limit && group.isNotEmpty()) {
                    val labelCount = result.count { it.targetLabel == group.first().targetLabel }
                    if (labelCount < perLabelLimit) result += group.removeFirst()
                }
            }
        }
        result += none.take(minOf(none.size, limit / 2, limit - result.size))
        return result
    }

    private fun wholePresetGroups(rows: List<BootstrapTrainingExampleEntity>, limit: Int): List<BootstrapTrainingExampleEntity> {
        if (limit <= 0) return emptyList()
        val result = mutableListOf<BootstrapTrainingExampleEntity>()
        rows.groupBy { it.opportunityGroupId }
            .values
            .sortedBy { group -> group.minOf { it.sequenceNo } }
            .forEach { group ->
                if (group.size <= limit - result.size) result += group.sortedBy { it.candidateOrdinal }
            }
        return result
    }

    private suspend fun enforceTaskCap(profileKey: String, task: BootstrapTrainingTask) {
        val limit = when (task) {
            BootstrapTrainingTask.NEXT_ACTION -> NEXT_ACTION_LIMIT
            BootstrapTrainingTask.JOURNEY_GOAL -> JOURNEY_LIMIT
            BootstrapTrainingTask.PRESET_RANKING -> PRESET_LIMIT
        }
        val count = dao.pendingBootstrapTrainingTaskCount(profileKey, task.name)
        if (count > limit) dao.evictOldestPendingBootstrapTrainingExamples(profileKey, task.name, count - limit)
    }

    private fun schedulePeriodicUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BootstrapTrainingDataWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleUpload(historical: Boolean, immediate: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (historical) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(historical)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<BootstrapTrainingDataWorker>()
            .setConstraints(constraints)
            .setInitialDelay(if (immediate) 0 else 1, if (immediate) TimeUnit.MILLISECONDS else TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            when {
                immediate -> DELETION_WORK_NAME
                historical -> HISTORICAL_WORK_NAME
                else -> REGULAR_WORK_NAME
            },
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun randomBytesBase64(size: Int): String = ByteArray(size)
        .also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun hmacSha256(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }

    private fun journeyLengthBucket(length: Int): Int = when {
        length <= 0 -> 0
        length == 1 -> 1
        length == 2 -> 2
        length <= 4 -> 3
        else -> 4
    }

    private companion object {
        const val NEXT_ACTION_LIMIT = 2_048
        const val JOURNEY_LIMIT = 1_024
        const val PRESET_LIMIT = 2_048
        const val MAX_PENDING_SCAN = 4_096
        const val MAX_BODY_BYTES = 512 * 1024
        const val PRESET_FEATURE_SCHEMA_VERSION = 1
        const val PRESET_OUTPUT_SCHEMA_VERSION = 1
        const val PERIODIC_WORK_NAME = "bootstrap_training_periodic"
        const val REGULAR_WORK_NAME = "bootstrap_training_regular"
        const val HISTORICAL_WORK_NAME = "bootstrap_training_historical"
        const val DELETION_WORK_NAME = "bootstrap_training_deletion"
    }
}
