package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.inference.NextActionProbabilityVector
import com.ahu.ahutong.personalization.ui.SuggestionPolicy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionPolicyTest {
    @Test
    fun zeroOrganicSamplesNeverProduceRandomSuggestion() {
        assertTrue(SuggestionPolicy.rankedCandidates(vector(), emptySet()).isEmpty())
    }

    @Test
    fun firstOrganicSampleIsEligibleWithoutProbabilityOrMarginGate() {
        val candidates = SuggestionPolicy.rankedCandidates(
            vector(AppActionId.VIEW_SCHEDULE to 0.01f),
            setOf(AppActionId.VIEW_SCHEDULE.stableId)
        )

        assertEquals(AppActionId.VIEW_SCHEDULE, candidates.single().action)
        assertEquals(0.01f, candidates.single().probability)
    }

    @Test
    fun ranksOnlyPreviouslyUsedSuggestibleNonTransactionActions() {
        val candidates = SuggestionPolicy.rankedCandidates(
            vector(
                AppActionId.REFRESH_PAYMENT_QR to 0.40f,
                AppActionId.SUBMIT_CARD_RECHARGE to 0.30f,
                AppActionId.VIEW_GRADES to 0.02f,
                AppActionId.VIEW_SCHEDULE to 0.01f
            ),
            setOf(
                AppActionId.REFRESH_PAYMENT_QR.stableId,
                AppActionId.SUBMIT_CARD_RECHARGE.stableId,
                AppActionId.VIEW_GRADES.stableId
            )
        )

        assertEquals(listOf(AppActionId.VIEW_GRADES), candidates.map { it.action })
    }

    @Test
    fun zeroAvailabilityProbabilityRemainsIneligible() {
        val candidates = SuggestionPolicy.rankedCandidates(
            vector(),
            setOf(AppActionId.VIEW_SCHEDULE.stableId)
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun suggestionClickIsOwnedByParentAndDismissHasNoPenalty() {
        val sourceRoot = File(repositoryRoot(), "app/src/main/java")
        val host = File(
            sourceRoot,
            "com/ahu/ahutong/personalization/ui/SmartSuggestionHost.kt"
        ).readText()
        val main = File(sourceRoot, "com/ahu/ahutong/ui/screen/Main.kt").readText()
        val runtime = File(
            sourceRoot,
            "com/ahu/ahutong/personalization/runtime/PredictionRuntime.kt"
        ).readText()

        assertFalse(host.contains("rememberCoroutineScope"))
        assertTrue(host.contains("onClick = { onSuggestionClick(suggestion) }"))
        assertTrue(
            Regex("onSuggestionClick\\s*=\\s*\\{ suggestion ->[\\s\\S]*?scope\\.launch[\\s\\S]*?acceptSuggestion")
                .containsMatchIn(main)
        )
        val dismissBody = Regex("fun dismissSuggestionByUser\\(\\) \\{([\\s\\S]*?)\\n    }")
            .find(runtime)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        assertEquals("hideSuggestion()", dismissBody.trim())
        assertTrue(
            Regex("acceptSuggestion[\\s\\S]*?recordActionIntent\\([\\s\\S]*?deferNextOpportunity = true")
                .containsMatchIn(runtime)
        )
    }

    private fun vector(vararg actionProbabilities: Pair<AppActionId, Float>): NextActionProbabilityVector {
        val values = FloatArray(AppActionCatalog.outputIds.size)
        actionProbabilities.forEach { (action, probability) ->
            values[AppActionCatalog.outputIndex.getValue(action.stableId)] = probability
        }
        values[AppActionCatalog.outputIndex.getValue(AppActionCatalog.NONE_OUTPUT_ID)] =
            1f - actionProbabilities.sumOf { it.second.toDouble() }.toFloat()
        return NextActionProbabilityVector(AppActionCatalog.outputIds, values, modelVersion = 1)
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
