package com.ahu.ahutong.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionArchitectureTest {

    @Test
    fun `the production UI observes the session and web verification commits it`() {
        val activity = source("app/src/main/java/com/ahu/ahutong/MainActivity.kt")
        val login = source("app/src/main/java/com/ahu/ahutong/ui/state/LoginViewModel.kt")

        assertTrue(activity.contains("session.state.collectAsState()"))
        assertTrue(login.contains("session.completeWebVerification()"))
    }

    @Test
    fun `sign out clears evaluation state and the login screen does not mutate session globals`() {
        val session = source("app/src/main/java/com/ahu/ahutong/data/session/DefaultAhuSession.kt")
        val screen = source("app/src/main/java/com/ahu/ahutong/ui/screen/setup/Login.kt")
        val reset = source("app/src/main/java/com/ahu/ahutong/ui/state/DeviceDataReset.kt")

        assertTrue(session.contains("EvaluationRepository.clearSession()"))
        assertFalse(screen.contains("AhuSessionState.mark"))
        assertFalse(screen.contains("TokenManager.clear()"))
        assertFalse(reset.contains("AhuSessionState.markExpired()"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot(), relativePath).readText()

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
