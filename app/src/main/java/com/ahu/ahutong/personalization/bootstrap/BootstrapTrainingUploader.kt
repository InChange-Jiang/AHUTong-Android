package com.ahu.ahutong.personalization.bootstrap

import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.google.gson.Gson
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class BootstrapTrainingUploader @Inject constructor(
    private val dao: BehaviorDao,
    private val manager: BootstrapTrainingDataManager,
    private val secretStore: BootstrapTrainingSecretStore
) {
    private val random = SecureRandom()
    private val gson = Gson()

    /** Returns true while durable upload or deletion work remains. */
    suspend fun uploadDue(): Boolean {
        uploadDeletions()
        repeat(MAX_BATCH_ROUNDS_PER_RUN) {
            manager.prepareDueBatches()
            uploadBatches()
        }
        return dao.pendingBootstrapTrainingBatchCount() > 0 ||
            dao.pendingBootstrapTrainingDeletionCount() > 0
    }

    private suspend fun uploadBatches() {
        repeat(MAX_ITEMS_PER_RUN) {
            val now = System.currentTimeMillis()
            val batch = dao.dueBootstrapTrainingBatch(now) ?: return
            val consent = dao.bootstrapTrainingConsent(batch.profileKey)
            if (consent == null || consent.state != "ACTIVE" ||
                consent.consentLifecycleId != batch.consentLifecycleId ||
                consent.participantId != batch.participantId
            ) {
                dao.quarantineBootstrapTrainingBatch(batch.batchId, "CONSENT_LIFECYCLE_MISMATCH")
                return@repeat
            }
            if (sha256(batch.body) != batch.bodySha256 || batch.body.size > MAX_BODY_BYTES) {
                dao.quarantineBootstrapTrainingBatch(batch.batchId, "IMMUTABLE_BODY_INVALID")
                return@repeat
            }
            val immutableRequest = runCatching {
                gson.fromJson(batch.body.toString(Charsets.UTF_8), BootstrapTrainingBatchRequest::class.java)
                    .also(BootstrapTrainingPayloadValidator::requireValid)
                    .also {
                        require(
                            it.batchId == batch.batchId &&
                                it.participantId == batch.participantId &&
                                it.consentLifecycleId == batch.consentLifecycleId
                        )
                    }
            }.getOrElse {
                dao.quarantineBootstrapTrainingBatch(batch.batchId, "IMMUTABLE_BODY_INVALID")
                return@repeat
            }
            val attempts = batch.attemptCount + 1
            dao.retryBootstrapTrainingBatch(
                batch.batchId,
                now + backoffMillis(attempts),
                "NETWORK_PENDING"
            )
            val credentialResponse = runCatching {
                BootstrapTrainingApi.API.credential(
                    BootstrapTrainingCredentialRequest(
                        batchId = batch.batchId,
                        bodySha256Hex = batch.bodySha256,
                        appVersionCode = immutableRequest.appVersionCode
                    )
                )
            }.getOrNull()
            val credentialCode = credentialResponse?.code()
            if (credentialCode in PERMANENT_FAILURE_CODES) {
                credentialResponse?.errorBody()?.close()
                dao.quarantineBootstrapTrainingBatch(batch.batchId, "CREDENTIAL_$credentialCode")
                return@repeat
            }
            val credential = credentialResponse?.takeIf { it.isSuccessful }?.body()
            credentialResponse?.errorBody()?.close()
            if (credential == null || credential.expiresAtEpochMs <= now) return@repeat
            val response = runCatching {
                BootstrapTrainingApi.API.upload(
                    credential = "Bootstrap ${credential.credential}",
                    bodySha256Hex = batch.bodySha256,
                    exactBody = batch.body.toRequestBody(JSON_MEDIA_TYPE)
                )
            }.getOrNull()
            val code = response?.code()
            response?.body()?.close()
            response?.errorBody()?.close()
            when {
                response?.isSuccessful == true -> {
                    dao.acknowledgeBootstrapTrainingBatch(batch.batchId, System.currentTimeMillis())
                    manager.refreshStatus(batch.profileKey)
                }
                code in PERMANENT_FAILURE_CODES ->
                    dao.quarantineBootstrapTrainingBatch(batch.batchId, "UPLOAD_$code")
                else -> Unit
            }
        }
    }

    private suspend fun uploadDeletions() {
        repeat(MAX_ITEMS_PER_RUN) {
            val now = System.currentTimeMillis()
            val tombstone = dao.dueBootstrapTrainingDeletion(now) ?: return
            val attempts = tombstone.attemptCount + 1
            dao.retryBootstrapTrainingDeletion(
                tombstone.deletionId,
                now + backoffMillis(attempts),
                "NETWORK_PENDING"
            )
            val capability = runCatching {
                secretStore.decrypt(tombstone.secretAlias, tombstone.encryptedRevocationCapability)
            }.getOrNull() ?: return@repeat
            val response = runCatching {
                BootstrapTrainingApi.API.delete(
                    BootstrapTrainingDeletionRequest(
                        deletionId = tombstone.deletionId,
                        participantId = tombstone.participantId,
                        consentLifecycleId = tombstone.consentLifecycleId,
                        revocationCapability = capability
                    )
                )
            }.getOrNull()
            response?.body()?.close()
            response?.errorBody()?.close()
            if (response?.isSuccessful == true) {
                dao.acknowledgeBootstrapTrainingDeletion(tombstone.deletionId, System.currentTimeMillis())
                secretStore.delete(tombstone.secretAlias)
            }
        }
    }

    private fun backoffMillis(attempt: Int): Long {
        val index = (attempt - 1).coerceIn(0, BACKOFF_MINUTES.lastIndex)
        return BACKOFF_MINUTES[index] * 60_000L + random.nextInt(MAX_JITTER_MS + 1)
    }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val PERMANENT_FAILURE_CODES = setOf(400, 409, 410, 413, 422)
        val BACKOFF_MINUTES = longArrayOf(15, 60, 360, 1_440, 4_320, 10_080)
        const val MAX_JITTER_MS = 15 * 60_000
        const val MAX_ITEMS_PER_RUN = 4
        const val MAX_BATCH_ROUNDS_PER_RUN = 3
        const val MAX_BODY_BYTES = 512 * 1024
    }
}
