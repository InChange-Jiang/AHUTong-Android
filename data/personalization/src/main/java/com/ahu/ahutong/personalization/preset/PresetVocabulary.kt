package com.ahu.ahutong.personalization.preset

import com.ahu.ahutong.personalization.semantic.SemanticDomain

/*
 * 本地预设建议的词汇：候选、提交、交互令牌与它们的枚举。
 *
 * 原先和 PresetRankingEngine（781 行，带推理与存储）挤在同一个文件里，于是界面能看见候选、
 * 却必须依赖端侧实现所在的模块。词汇搬到这里之后，feature 只认识候选与端口，
 * 引擎仍留在 :app。
 */

data class PresetCandidate(
    val opportunityId: String,
    val presetId: String,
    val domain: SemanticDomain,
    val localPayloadJson: String,
    val coarseFeaturesJson: String,
    val statScore: Float,
    val tinyScore: Float,
    val effectiveScore: Float,
    val reason: String,
    val candidateFingerprint: String = "",
    val checkpointId: String? = null,
    val featureVector: FloatArray = FloatArray(0),
    val recentBaselineScore: Float = 0f,
    val frequencyBaselineScore: Float = 0f,
    val promotionHoldout: Boolean = false,
    val candidateOrdinal: Int = 0,
    val occurredEpochDay: Long? = null
)

data class PresetSubmission(
    val domain: SemanticDomain,
    val localPayloadJson: String,
    val coarseFeaturesJson: String,
    val stableFingerprintSource: String
)

enum class PresetInteractionState {
    EXPOSED,
    APPLIED,
    QUERY_CONFIRMED,
    REPLACED,
    REMOVED,
    EXPIRED_NO_LABEL
}

enum class PresetFeedbackSource {
    NATURAL_COMMIT,
    ASSISTED_QUERY_CONFIRMED,
    ASSISTED_REPLACED,
    ASSISTED_REMOVED
}

data class PresetInteractionToken(
    val interactionId: String,
    val domain: SemanticDomain,
    val opportunityId: String,
    val candidateId: String,
    val candidateFingerprint: String
)

data class AppliedPreset(
    val localPayloadJson: String,
    val interactionToken: PresetInteractionToken
)

