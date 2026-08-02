package com.ahu.ahutong.personalization.ui

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.inference.NextActionProbabilityVector

internal data class SuggestionCandidate(
    val action: AppActionId,
    val probability: Float
)

internal object SuggestionPolicy {
    fun remainingVisibilityFraction(
        shownAtElapsedMs: Long,
        expiresAtElapsedMs: Long,
        nowElapsedMs: Long
    ): Float {
        val lifetime = expiresAtElapsedMs - shownAtElapsedMs
        if (lifetime <= 0L) return 0f
        val remaining = expiresAtElapsedMs - nowElapsedMs
        return (remaining.toDouble() / lifetime.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun isDisplayIntervalElapsed(
        lastShownElapsedMs: Long,
        nowElapsedMs: Long,
        minimumIntervalMs: Long
    ): Boolean {
        require(minimumIntervalMs >= 0L)
        if (lastShownElapsedMs == 0L) return true
        return nowElapsedMs >= lastShownElapsedMs &&
            nowElapsedMs - lastShownElapsedMs >= minimumIntervalMs
    }

    fun rankedCandidates(
        prediction: NextActionProbabilityVector,
        organicActionIds: Set<String>,
        requireOrganicHistory: Boolean = true
    ): List<SuggestionCandidate> = prediction.rankedIndices().mapNotNull { index ->
        val probability = prediction.probabilities[index]
        if (probability <= 0f) return@mapNotNull null
        val outputId = prediction.outputIds[index]
        if (requireOrganicHistory && outputId !in organicActionIds) return@mapNotNull null
        val action = AppActionId.fromStableId(outputId) ?: return@mapNotNull null
        val spec = AppActionCatalog.spec(action)
        if (!spec.suggestible || spec.sideEffect == SideEffect.TRANSACTION) return@mapNotNull null
        SuggestionCandidate(action, probability)
    }
}
