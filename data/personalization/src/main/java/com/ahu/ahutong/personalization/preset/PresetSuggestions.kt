package com.ahu.ahutong.personalization.preset

import com.ahu.ahutong.personalization.semantic.SemanticDomain

/**
 * 本地预设建议（计划 §3.2 的 SuggestionProvider）：排序、曝光、应用与提交。
 *
 * 界面只表达"给我几个建议""用户看了这个""用户用了这个""用户自己改成了这样"；
 * 怎么排序、怎么训练、上报什么，全在实现里（:app 的端侧适配器）。
 *
 * 与 [com.ahu.ahutong.personalization.recorder.BehaviorRecorder] 的分工：那个记录"发生了什么"，
 * 这个把发生的事变成建议——两者都由界面调用，但方向相反。
 */
interface PresetSuggestions {

    /** 当前值得推荐的候选；没有可推荐的时候返回空列表。 */
    suspend fun rank(domain: SemanticDomain): List<PresetCandidate>

    /** 用户看到了这一条：开一次曝光交互；返回令牌供后续提交或过期使用。 */
    suspend fun markExposed(candidate: PresetCandidate): PresetInteractionToken?

    /** 用户采纳了这一条。 */
    suspend fun apply(candidate: PresetCandidate): AppliedPreset?

    /** 建议界面被销毁（无论是采纳、替换还是离开）。 */
    fun expire(token: PresetInteractionToken?)

    /** 用户自己完成了同样的选择：这是最可靠的一类反馈。 */
    suspend fun recordNaturalSubmission(
        submission: PresetSubmission,
        interactionToken: PresetInteractionToken?,
        candidatesAtOpportunity: List<PresetCandidate>
    )
}

