package com.ahu.ahutong.feature.circle

/** 帖子。字段名按 BBS 接口 JSON 防御式解析（多别名兜底）。 */
data class CircleTopic(
    val id: String,
    val title: String,
    val content: String,
    val nodeName: String,
    val authorName: String,
    val commentCount: Int,
    val likeCount: Int,
    val viewCount: Int,
    val isAnon: Boolean,
    val isTop: Boolean,
    val createTime: String,
    val imageUrls: List<String>
)

/** 评论。 */
data class CircleComment(
    val id: String,
    val authorName: String,
    val content: String,
    val createTime: String
)

/** 一页帖子。hasMore 由「本页数量达到页大小」推断（接口无显式总页数字段时够用）。 */
data class CircleTopicPage(
    val topics: List<CircleTopic>,
    val page: Int,
    val hasMore: Boolean
)
