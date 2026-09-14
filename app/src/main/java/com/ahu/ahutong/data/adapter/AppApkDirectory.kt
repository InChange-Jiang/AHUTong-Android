package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.update.ApkDirectory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ApkDirectory] 的生产实现：应用的外部私有目录（没有外部存储时退回内部目录）。
 *
 * 与迁移前 MainViewModel 里的取法逐字一致。
 */
@Singleton
class AppApkDirectory @Inject constructor() : ApkDirectory {

    override fun dir(): File {
        val context = AppEnvironmentHolder.context()
        context.getExternalFilesDir(null)?.let { return it }
        return File(context.filesDir, "updates").also { it.mkdirs() }
    }
}
