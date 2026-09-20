package com.ahu.ahutong.ui.state

/**
 * 应用自身的版本与更新说明。
 *
 * 只包含设置页现在就能切干净的两件事：当前版本名与更新说明。
 * 「手动检查更新」没有在这里——它驱动的是主界面的更新对话框状态机（`apkUpdateInfo` /
 * `showApkUpdateDialog` 那一套），按计划属于 P4 的 `:data:update` 收口：把下载器与状态
 * 一起从 MainViewModel 抽出来之后，它才会变成一个真正的端口。
 */
interface AppUpdateGateway {

    /** 当前版本名（设置页展示用）；读不到时为 null。 */
    val currentVersionName: String?

    /** 更新说明；失败与空内容由调用方转成给用户看的文案。 */
    suspend fun changelog(): String
}

