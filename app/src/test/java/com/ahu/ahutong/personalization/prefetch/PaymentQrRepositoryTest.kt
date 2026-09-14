package com.ahu.ahutong.personalization.prefetch

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentQrRepositoryTest {
    @Test
    fun `foreground QR loads before prediction profile is ready`() = runTest {
        val repository = PaymentQrRepository {
            Result.success("https://example.invalid/payment-qr")
        }

        assertEquals(
            "https://example.invalid/payment-qr",
            repository.getForDisplay().getOrThrow()
        )
    }

    @Test
    fun `profile activation does not cancel an early foreground request`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()
        val repository = PaymentQrRepository {
            requestStarted.complete(Unit)
            finishRequest.await()
            Result.success("https://example.invalid/payment-qr")
        }

        val result = async { repository.getForDisplay() }
        requestStarted.await()
        repository.activateProfile("profile", profileGeneration = 1, loginGeneration = 1)
        finishRequest.complete(Unit)

        assertEquals("https://example.invalid/payment-qr", result.await().getOrThrow())
    }
}
