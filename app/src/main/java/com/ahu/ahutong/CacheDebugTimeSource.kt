package com.ahu.ahutong

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugTimeSource

/** 生产实现：调试用的 mock 时间仍然存在业务缓存里，这里只把它接到 [DebugTimeSource] 上。 */
object CacheDebugTimeSource : DebugTimeSource {

    override fun mockedNowMillis(): Long? = AHUCache.getMockCurrentTimeMillis()
}
