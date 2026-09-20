package com.ahu.ahutong.personalization.recorder

import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.context.ExamDistanceBucket

/**
 * 个性化层对外的行为记录入口（计划 §3.2 的 BehaviorRecorder）。
 *
 * feature 只报告"用户做了什么、界面处于什么状态"；是否训练、如何推理、上报什么，全由实现决定。
 * 生产实现是 :app 里的端侧运行时适配器，测试可以用记录型 fake。
 *
 * 之所以先立这个接口：feature 模块不能依赖 :app，而界面又确实需要上报行为——
 * 于是把"上报"收成一个 feature 能看见、又不需要认识端侧模型的窄接口。
 */
interface BehaviorRecorder {

    /** 用户主动发起的动作，区别于由建议促成的动作。 */
    fun recordOrganicAction(action: AppActionId)

    /** 界面内容状态变化。 */
    fun recordContentState(
        domain: SemanticDomain,
        contentState: ContentStateBucket,
        freshnessBucket: Int,
        resultCount: ResultCountBucket,
        errorType: ErrorTypeBucket
    )

    /**
     * 用户提交了一次变更（设置项、课表周次、学期……）。
     *
     * 与 [recordOrganicAction] 的分工：那个是"点了什么"，这个是"改成了什么"——
     * 后者带着旧值与新值，个性化层才有得学。[coarseValueBucket] 给不适合直接上报的取值
     * （例如颜色）。
     */
    fun reportCommittedMutation(
        mutationId: MutationId,
        oldValue: Any?,
        newValue: Any?,
        coarseValueBucket: String? = null
    )

    /**
     * 用户停在考试页、而下一场考试在这个距离内。
     *
     * 这是"界面当下看到什么"的一类事实，与 [recordContentState] 同源：
     * 由界面在状态变化时报告，而不是自己去算。
     */
    fun recordExamDistance(bucket: ExamDistanceBucket)
}
