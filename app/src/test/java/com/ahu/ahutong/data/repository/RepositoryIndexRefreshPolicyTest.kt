package com.ahu.ahutong.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryIndexRefreshPolicyTest {
    @Test
    fun `fresh compatible index is reused even when UI observes progress`() {
        val now = 50_000_000L

        assertTrue(
            RepositoryIndexRefreshPolicy.canReuse(
                cachedAtMillis = now - RepositoryIndexRefreshPolicy.AUTO_REFRESH_INTERVAL_MS + 1L,
                cachedVersion = 7,
                expectedVersion = 7,
                hasRootContents = true,
                nowMillis = now
            )
        )
    }

    @Test
    fun `stale incompatible or incomplete index is rebuilt`() {
        val now = 50_000_000L
        val staleAt = now - RepositoryIndexRefreshPolicy.AUTO_REFRESH_INTERVAL_MS

        assertFalse(RepositoryIndexRefreshPolicy.canReuse(staleAt, 7, 7, true, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now, 6, 7, true, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now, 7, 7, false, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now + 1L, 7, 7, true, now))
    }

    @Test
    fun `index construction does not eagerly fetch every LFS candidate`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/com/ahu/ahutong/data/repository/RepositoryManager.kt"
        ).readText()

        assertFalse(source.contains("resolveGitLfsDisplaySizes"))
        assertTrue(source.contains("size = child.size"))
    }

    @Test
    fun `cold root renders before the full repository index finishes`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/com/ahu/ahutong/data/repository/RepositoryManager.kt"
        ).readText()
        val getContents = source.substring(
            source.indexOf("suspend fun getContents"),
            source.indexOf("suspend fun warmUpAllContentCaches")
        )

        val immediateRootReturn = getContents.indexOf("fallbackRootItems?.let { return@withContext it }")
        val indexWarmUp = getContents.indexOf("warmUpAllContentCaches(")
        assertTrue(immediateRootReturn in 0 until indexWarmUp)
    }

    @Test
    fun `repository cache parsing stays off the composition thread`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/com/ahu/ahutong/ui/state/RepositoryViewModel.kt"
        ).readText()
        val stateGetter = source.substring(
            source.indexOf("fun getInitialDirectoryState"),
            source.indexOf("fun getSharedState")
        )

        assertFalse(stateGetter.contains("RepositoryManager.getCachedContents"))
        assertTrue(source.contains("withContext(Dispatchers.IO) { refreshDownloadedSet() }"))
        assertTrue(source.contains("val resolvedState = withContext(Dispatchers.IO)"))
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
