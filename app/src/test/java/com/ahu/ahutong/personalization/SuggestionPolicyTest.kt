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
    fun suggestionDisplayIntervalIsInclusiveAndHandlesClockRegression() {
        assertTrue(SuggestionPolicy.isDisplayIntervalElapsed(0L, 1L, 30_000L))
        assertFalse(SuggestionPolicy.isDisplayIntervalElapsed(10_000L, 39_999L, 30_000L))
        assertTrue(SuggestionPolicy.isDisplayIntervalElapsed(10_000L, 40_000L, 30_000L))
        assertFalse(SuggestionPolicy.isDisplayIntervalElapsed(40_000L, 10_000L, 30_000L))
    }

    @Test
    fun suggestionVisibilityTracksItsRemainingLifetime() {
        assertEquals(1f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 13_000L, 1_000L))
        assertEquals(0.5f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 13_000L, 7_000L))
        assertEquals(0f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 13_000L, 13_000L))
        assertEquals(0f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 1_000L, 1_000L))
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
        val prefetch = File(
            sourceRoot,
            "com/ahu/ahutong/personalization/prefetch/PrefetchCoordinator.kt"
        ).readText()
        val preferences = File(
            sourceRoot,
            "com/ahu/ahutong/data/dao/PreferencesManager.kt"
        ).readText()

        assertTrue(host.contains("val animationScope = rememberCoroutineScope()"))
        assertTrue(host.contains("onClick = { onSuggestionClick(suggestion) }"))
        assertTrue(host.contains("highlightRadiusMultiplier = 0.9f"))
        assertTrue(host.contains(".then(interactiveHighlight.modifier)"))
        assertTrue(host.contains(".then(interactiveHighlight.gestureModifier)"))
        assertTrue(host.contains("interactiveHighlight.pressProgress"))
        assertTrue(host.contains("runtime.pauseSuggestionVisibility(suggestion.executionId)"))
        assertTrue(host.contains("runtime.restartSuggestionVisibility(suggestion.executionId)"))
        assertTrue(host.contains("if (suggestion.visibilityPaused)"))
        assertTrue(host.contains(".clip(suggestionShape)"))
        assertFalse(host.contains("interaction.pressPosition"))
        assertFalse(host.contains("drawCircle("))
        assertFalse(host.contains("waveVisible"))
        assertTrue(host.contains("lifetimeOpacity.animateTo"))
        assertTrue(host.contains("Modifier.drawBackdrop"))
        assertTrue(host.contains("vibrancy()"))
        assertTrue(host.contains("blur(8f.dp.toPx())"))
        assertTrue(host.contains("lens(24f.dp.toPx(), 24f.dp.toPx())"))
        assertTrue(host.contains("opacity(lifetimeOpacity.value)"))
        assertTrue(host.contains("val suggestionShape = ContinuousCapsule"))
        assertFalse(host.contains("layerBlock = { alpha = lifetimeOpacity.value }"))
        assertFalse(host.contains("graphicsLayer { alpha = lifetimeOpacity.value }"))
        assertFalse(host.contains("Surface("))
        assertFalse(host.contains("LocalIndication"))
        assertFalse(host.contains("DoNotDisturbOn"))
        assertFalse(host.contains("suppressSuggestedActionByUser"))
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
        assertTrue(runtime.contains("SUGGESTION_MIN_INTERVAL_MS = 30_000L"))
        assertTrue(runtime.contains("fun pauseSuggestionVisibility(executionId: String)"))
        assertTrue(runtime.contains("fun restartSuggestionVisibility(executionId: String)"))
        assertTrue(runtime.contains("suggestionVisibilityGeneration.incrementAndGet()"))
        assertTrue(runtime.contains("shownAtElapsedMs = restartedAtElapsedMs"))
        assertTrue(runtime.contains("prefetchCoordinator.prefetchSuggestedAction"))
        assertTrue(prefetch.contains("suspend fun prefetchSuggestedAction"))
        assertTrue(prefetch.contains("FileUtils.saveResponseBodyToFile(context, body, \"xiaoli.jpg\")"))
        assertTrue(prefetch.contains("AHUCache.saveLostFoundList(1, response.data.list)"))
        assertFalse(preferences.contains("SUGGESTION_ACTION_SUPPRESSIONS"))
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
