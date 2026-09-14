package com.ahu.ahutong.ui.state

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.server.AhuTong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppUpdateGateway] 的生产实现：版本名读 PackageManager，更新说明读服务端。
 *
 * 版本名的读法与迁移前的 AboutViewModel 相同，只是多了 runCatching：
 * 读不到时展示空字符串，而不是让界面在构造时崩掉。
 */
@Singleton
class AndroidAppUpdateGateway @Inject constructor() : AppUpdateGateway {

    private val context: Context get() = AppEnvironmentHolder.context()

    override val currentVersionName: String? =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()

    override suspend fun changelog(): String =
        AhuTong.API.getApkUpdateInfo().changelog.orEmpty()
}

