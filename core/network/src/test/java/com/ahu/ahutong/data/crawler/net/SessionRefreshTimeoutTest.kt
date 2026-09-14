package com.ahu.ahutong.data.crawler.net

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * ADR 0002 的「有界刷新」：一次续期带总超时，超时即当失败——不重试，也不推进代号。
 *
 * 超时值走参数，测试因此不必真等 30 秒；生产调用点用默认值。
 */
class SessionRefreshTimeoutTest {

    /** 协调器是进程内的单例；每个用例从「刚登录过」的状态开始，免得失败记忆跨用例生效。 */
    @BeforeTest
    fun forgetPreviousFailures() = runBlocking {
        SessionRefreshCoordinator.onAuthenticated()
    }

    @Test
    fun aFailedRefreshIsNotRetriedByEveryWaiter() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0

        val first = SessionRefreshCoordinator.refreshIfNeeded(generation, timeoutMillis = 5_000L) {
            attempts += 1
            false
        }
        val second = SessionRefreshCoordinator.refreshIfNeeded(generation, timeoutMillis = 5_000L) {
            attempts += 1
            true
        }

        assertFalse(first)
        assertFalse(second)
        assertEquals(1, attempts, "一次会话失效只许自动续期一次（ADR 0002）")
    }

    @Test
    fun aNewLoginLetsAutomaticRefreshHappenAgain() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.refreshIfNeeded(generation, timeoutMillis = 5_000L) { false }
        SessionRefreshCoordinator.onAuthenticated()
        val authenticatedGeneration = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            authenticatedGeneration,
            timeoutMillis = 5_000L
        ) {
            attempts += 1
            true
        }

        assertTrue(refreshed)
        assertEquals(1, attempts)
    }

    @Test
    fun aResponseFromBeforeManualLoginCannotRefreshTheNewSession() = runBlocking {
        val oldGeneration = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.onAuthenticated()
        var attempts = 0

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            oldGeneration,
            timeoutMillis = 5_000L
        ) {
            attempts += 1
            false
        }

        assertTrue(refreshed)
        assertEquals(0, attempts)
        var staleCommitRan = false
        assertFalse(
            SessionRefreshCoordinator.commitIfCurrent(oldGeneration) { staleCommitRan = true }
        )
        assertFalse(staleCommitRan)
    }

    @Test
    fun signingOutDisablesRefreshUntilTheNextAuthentication() = runBlocking {
        val requestBeforeSignOut = SessionRefreshCoordinator.currentGeneration()
        SessionRefreshCoordinator.onSignedOut()
        val signedOutGeneration = SessionRefreshCoordinator.currentGeneration()
        var attempts = 0

        val oldRequestRefreshed = SessionRefreshCoordinator.refreshIfNeeded(requestBeforeSignOut) {
            attempts += 1
            true
        }
        val newRequestRefreshed = SessionRefreshCoordinator.refreshIfNeeded(signedOutGeneration) {
            attempts += 1
            true
        }

        assertFalse(oldRequestRefreshed)
        assertFalse(newRequestRefreshed)
        assertEquals(0, attempts)
    }

    @Test
    fun refreshThatNeverReturnsIsCutOffAndCountedAsFailed() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            observedGeneration = generation,
            timeoutMillis = 50L
        ) {
            delay(10_000L)
            true
        }

        assertFalse(refreshed)
        assertEquals(generation, SessionRefreshCoordinator.currentGeneration())
    }

    @Test
    fun refreshInsideTheBudgetStillReportsSuccess() = runBlocking {
        val generation = SessionRefreshCoordinator.currentGeneration()

        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(
            observedGeneration = generation,
            timeoutMillis = 5_000L
        ) { true }

        assertTrue(refreshed)
        assertEquals(generation + 1, SessionRefreshCoordinator.currentGeneration())
    }
}
