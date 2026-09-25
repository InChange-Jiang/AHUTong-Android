package com.ahu.ahutong.feature.circle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator

/** 列表/详情的内部页面态：插件自建导航，不占宿主路由。 */
private sealed interface CirclePage {
    data object Feed : CirclePage
    data class Detail(val topic: CircleTopic) : CirclePage
}

@Composable
internal fun CircleRoot(host: com.ahu.ahutong.core.plugin.PluginHostServices) {
    val api = remember { CircleApi(host.http()) }
    var page by remember { mutableStateOf<CirclePage>(CirclePage.Feed) }

    when (val current = page) {
        CirclePage.Feed -> CircleFeedPage(
            api = api,
            onOpen = { page = CirclePage.Detail(it) }
        )
        is CirclePage.Detail -> CircleDetailPage(
            api = api,
            topic = current.topic,
            onBack = { page = CirclePage.Feed }
        )
    }
}

/* ==================== 列表页 ==================== */

@Composable
private fun CircleFeedPage(api: CircleApi, onOpen: (CircleTopic) -> Unit) {
    var topics by remember { mutableStateOf<List<CircleTopic>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextPage by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    suspend fun load(page: Int, append: Boolean) {
        try {
            val result = api.fetchTopics(page)
            topics = if (append) topics + result.topics else result.topics
            hasMore = result.hasMore
            nextPage = page + 1
            error = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
    }

    LaunchedEffect(Unit) { loading = true; load(1, append = false); loading = false }

    // 滚到底自动翻页
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasMore && !loadingMore && !loading && info.totalItemsCount > 0 &&
                last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadingMore = true
            load(nextPage, append = true)
            loadingMore = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CircleHeader(title = "校园圈子", onBack = null) {
            IconButton(onClick = {
                topics = emptyList(); nextPage = 1; hasMore = true
            }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
            }
        }

        // 第三方内容免责条
        Text(
            text = "内容为第三方社区用户发布，不代表本应用立场 · 只读浏览",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        when {
            loading && topics.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { AppCircularProgressIndicator() }

            error != null && topics.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text("圈子服务暂不可用", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    error.orEmpty(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppButton(onClick = { nextPage = 1; topics = emptyList() }, variant = AppButtonVariant.Secondary) {
                    Text("重试")
                }
                Spacer(Modifier.weight(1f))
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(topics, key = { it.id }) { topic ->
                    TopicCard(topic = topic, onClick = { onOpen(topic) })
                }
                if (loadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) { AppCircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp) }
                    }
                }
                if (!hasMore && topics.isNotEmpty()) {
                    item {
                        Text(
                            "到底啦",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // 列表为空时触发重试的状态复位：topics 清空后由 LaunchedEffect(shouldLoadMore) 或手动刷新重载
    if (topics.isEmpty() && !loading && error == null) {
        LaunchedEffect(topics) {
            loading = true
            load(1, append = false)
            loading = false
        }
    }
}

@Composable
private fun TopicCard(topic: CircleTopic, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (topic.isTop) {
                Text(
                    "置顶",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
            }
            if (topic.nodeName.isNotBlank()) {
                Text(
                    topic.nodeName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                topic.createTime.take(10),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (topic.title.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(topic.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            topic.content,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "💬 ${topic.commentCount}   👍 ${topic.likeCount}   👀 ${topic.viewCount}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ==================== 详情页 ==================== */

@Composable
private fun CircleDetailPage(api: CircleApi, topic: CircleTopic, onBack: () -> Unit) {
    var detail by remember { mutableStateOf(topic) }
    var comments by remember { mutableStateOf<List<CircleComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(topic.id) {
        detail = runCatching { api.fetchTopicDetail(topic.id) }.getOrDefault(topic)
        comments = runCatching { api.fetchComments(topic.id) }.getOrDefault(emptyList())
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CircleHeader(title = detail.title.ifBlank { "帖子详情" }, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AppCard {
                    Text(
                        "${if (detail.isAnon) "匿名" else detail.authorName} · ${detail.createTime.take(16)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(detail.content, fontSize = 14.sp)
                    detail.imageUrls.forEach { url ->
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                Text(
                    "评论 ${comments.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        AppCircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp)
                    }
                }
            }
            items(comments, key = { it.id }) { comment ->
                AppCard {
                    Text(
                        "${comment.authorName} · ${comment.createTime.take(16)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(comment.content, fontSize = 13.sp)
                }
            }
        }
    }
}

/* ==================== 公共头 ==================== */

@Composable
private fun CircleHeader(
    title: String,
    onBack: (() -> Unit)?,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
