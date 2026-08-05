package com.ahu.ahutong.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingLifecycleStore
import com.ahu.ahutong.personalization.storage.BootstrapTrainingConsentEntity
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootstrapTrainingLifecycleStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: BootstrapTrainingLifecycleStore

    @Before
    fun setUp() {
        context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = BootstrapTrainingLifecycleStore(context, STORE_NAME)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun missingRoomLifecycleBecomesDurableDeletionWork() {
        val participantId = UUID.randomUUID().toString()
        store.persistActive(consent(participantId))

        assertTrue(store.reconcileRoomLifecycles(emptySet(), 100L))
        val deletion = store.dueDeletion(100L)
        assertNotNull(deletion)
        assertEquals(participantId, deletion?.participantId)
        assertNotNull(deletion?.deletionId)

        store.retryDeletion(participantId, 200L, "NETWORK_PENDING")
        assertEquals(null, store.dueDeletion(199L))
        assertEquals(participantId, store.dueDeletion(200L)?.participantId)
        store.acknowledgeDeletion(participantId)
        assertEquals(0, store.pendingDeletionCount())
    }

    @Test
    fun malformedSiblingRecordIsNeverErasedByAnUnrelatedLifecycleWrite() {
        val preferences = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
        preferences.edit().putString("lifecycle.corrupt", "{").commit()
        val participantId = UUID.randomUUID().toString()

        store.persistActive(consent(participantId))
        store.reconcileRoomLifecycles(emptySet(), 100L)
        store.acknowledgeDeletion(participantId)

        assertTrue(preferences.contains("lifecycle.corrupt"))
    }

    private fun consent(participantId: String) = BootstrapTrainingConsentEntity(
        profileKey = "profile",
        consentLifecycleId = UUID.randomUUID().toString(),
        participantId = participantId,
        secretAlias = "alias",
        encryptedRevocationCapability = "ciphertext",
        consentSchemaVersion = 1,
        includeHistorical = true,
        historicalBackfillCompleted = false,
        nextSequenceNo = 1,
        contributedExampleCount = 0,
        lastUploadAtEpochMs = null,
        state = "ACTIVE",
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1
    )

    private companion object {
        const val STORE_NAME = "bootstrap_training_lifecycle_outbox_instrumentation_test"
    }
}
