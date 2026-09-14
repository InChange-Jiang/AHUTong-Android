package com.ahu.ahutong.personalization.settings

import com.ahu.ahutong.personalization.bootstrap.BootstrapContributionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置界面需要的那部分个性化能力（计划 §3.2 的“:data:personalization 对外收窄”）。
 *
 * 界面只表达“用户改了哪一项设置”“别再推荐了”“清掉学习记录”“看一下贡献状态”；
 * 端侧如何训练、如何推理、上报什么，全在实现里。生产实现是 :app 的端侧运行时适配器。
 */
interface PersonalizationSettings {

    /** 本档位的训练贡献状态：设置页只显示这个，不认识训练管线。 */
    val contributionStatus: StateFlow<BootstrapContributionStatus>

    /** 建议已经被处理掉，不要再显示。 */
    fun dismissSuggestion()

    /** 取消正在进行的预取（关闭预测式预取时）。 */
    suspend fun cancelPredictivePrefetch()

    /** 清空本档位的学习记录。 */
    suspend fun clearLearningRecord()

    /** 贡献开关；[includeHistorical] 只在首次开启时有效。 */
    suspend fun setBootstrapTrainingContribution(enabled: Boolean, includeHistorical: Boolean)
}
