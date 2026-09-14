package com.ahu.ahutong.personalization.semantic

/**
 * 界面语义的粗粒度词汇：feature 上报"界面处于什么状态"，模型侧只消费这些取值。
 *
 * 与动作 id 一样属于 :data:personalization 的对外词汇——界面不必认识端侧模型，
 * 但需要能说出自己上报的是什么。
 */
enum class SemanticDomain {
    HOME,
    PAYMENT,
    SCHEDULE,
    WEATHER,
    FREE_CLASSROOM,
    GRADE,
    EXAM,
    LOST_FOUND,
    REPOSITORY,
    ELECTRICITY,
    EVALUATION,
    APPEARANCE,
    UNKNOWN
}

enum class ContentStateBucket { UNKNOWN, FRESH, STALE, EMPTY, LOADING, READY, ERROR }

enum class ResultCountBucket { UNKNOWN, ZERO, ONE_TO_FIVE, SIX_TO_TWENTY, TWENTY_ONE_PLUS }

enum class ErrorTypeBucket { NONE, UNKNOWN, NETWORK, AUTHENTICATION, SERVER, PARSE, LOCAL }
