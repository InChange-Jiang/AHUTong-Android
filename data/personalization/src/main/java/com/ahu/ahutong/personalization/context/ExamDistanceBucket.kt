package com.ahu.ahutong.personalization.context

/**
 * 下一场考试离现在有多远——界面能观察到、模型用得上的一个业务上下文。
 *
 * 原先和 PredictionInput（特征向量与快照）挤在同一个文件里，于是界面想上报"距离考试还有几天"
 * 就必须依赖端侧模型的模块。词汇搬到这里之后，界面只认识桶本身。
 */
enum class ExamDistanceBucket { UNKNOWN, NONE, WITHIN_ONE_DAY, WITHIN_THREE_DAYS, WITHIN_SEVEN_DAYS, LATER }

