package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.ahu.ahutong.data.update.ApkUpdateInfoSource
import javax.inject.Inject
import javax.inject.Singleton

/** [ApkUpdateInfoSource] 的生产实现：转给服务端的检查更新接口。 */
@Singleton
class AhuTongApkUpdateInfoSource @Inject constructor() : ApkUpdateInfoSource {

    override suspend fun latest(): ApkUpdateInfo = AhuTong.API.getApkUpdateInfo()
}

