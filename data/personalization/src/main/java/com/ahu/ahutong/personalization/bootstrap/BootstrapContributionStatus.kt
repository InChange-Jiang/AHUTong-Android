package com.ahu.ahutong.personalization.bootstrap

/**
 * 本档位的训练贡献状态：设置页与遥测界面只读这个。
 *
 * 与装载结构（BootstrapTrainingExamplePayload 等）分开：那些是实现细节，
 * 而界面要看的词汇跟着接口一起待在 :data:personalization。
 */
data class BootstrapContributionStatus(
    val enabled: Boolean = false,
    val pendingExamples: Int = 0,
    val contributedExamples: Long = 0,
    val lastUploadAtEpochMs: Long? = null,
    val includeHistorical: Boolean = false
)

