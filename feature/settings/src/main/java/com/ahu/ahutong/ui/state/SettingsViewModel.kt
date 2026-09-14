package com.ahu.ahutong.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 设置枢纽页的状态与动作。
 *
 * 迁移前这些东西散在界面与三个 :app 的 ViewModel 里（`AboutViewModel.versionName`、
 * 内联的 9 步「清除所有数据」、直接调 `AhuTong` 取更新说明）。现在它们各自走接口，
 * 于是这一页的逻辑第一次可以脱离设备验证。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val reset: AppDataReset,
    private val updates: AppUpdateGateway
) : ViewModel() {

    /** 迁移前由 AboutViewModel 提供，读取方式与它逐字一致（PackageManager）。 */
    val versionName: String? = updates.currentVersionName

    /**
     * 一次性提示位。
     *
     * 迁移前它由 AboutViewModel 持有、界面消费后置空；当前没有任何写入方，
     * 保留是为了让这次搬迁不改变任何行为。
     */
    var tip: String? by mutableStateOf(null)

    /**
     * 更新说明：与迁移前逐字一致——取不到是「获取失败」，取到空串是「暂无更新说明」。
     */
    suspend fun changelog(): String =
        runCatching { updates.changelog() }
            .getOrElse { CHANGELOG_FAILED }
            .ifBlank { CHANGELOG_EMPTY }

    /** 「清除所有数据」：顺序与范围见 [AppDataReset]。 */
    suspend fun clearAllData() = reset.clearAll()

    private companion object {
        const val CHANGELOG_FAILED = "获取失败"
        const val CHANGELOG_EMPTY = "暂无更新说明"
    }
}

