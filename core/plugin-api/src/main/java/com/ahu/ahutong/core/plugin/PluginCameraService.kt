package com.ahu.ahutong.core.plugin

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import java.io.File

/**
 * 插件相机服务：宿主统一持有 CAMERA 权限，插件经此取景与拍照。
 * 一切方法内部已按声明能力门控；线程模型见各方法注释。
 *
 * 安全纪律：相机**只**经 [PluginHostV2.camera] 开放，插件无自取权限路径；
 * 拍到的 Bitmap 只回给插件本地使用，宿主不做任何自动上传/云端处理。
 * 契约只用 Android 框架类型（View/Bitmap），CameraX 由宿主吸收、对插件可替换。
 */
interface PluginCameraService {
    /** CAMERA 权限当前是否已授予（宿主发起过授权且用户同意）。 */
    fun hasPermission(): Boolean

    /** 发起系统运行时权限弹窗（由宿主 Activity 承接），结果一次性回调。可在任意线程调用。 */
    fun requestPermission(onResult: (granted: Boolean) -> Unit)

    /** 创建取景预览 View（实现为 CameraX PreviewView）。插件在 Compose 里用 AndroidView { it } 嵌入。 */
    fun createPreviewView(): View

    /**
     * 启动相机会话：把预览绑到给定 View，默认后置相机。
     * @param analyzer 可选的逐帧回调，供插件做实时检测框：回调降采样 Bitmap（短边 ≤ 480px），
     *                 **后台线程**调用，只保最新帧（ImageAnalysis STRATEGY_KEEP_ONLY_LATEST 语义）。
     */
    fun startSession(previewView: View, analyzer: ((Bitmap) -> Unit)? = null): CameraSession

    interface CameraSession {
        /** 拍一张全分辨率照片（EXIF 旋转已校正），Bitmap 在**主线程**回调。 */
        fun capture(onResult: (Result<Bitmap>) -> Unit)

        /** 手电筒；无闪光灯设备为空操作。 */
        fun setTorch(on: Boolean)

        /** 解绑相机。插件必须在离开组合时调用（DisposableEffect）——宿主 Activity 的相机被插件独占。 */
        fun close()
    }
}

/**
 * 宿主扩展能力集：旧宿主未实现本接口，插件用 `host is PluginHostV2` 探测后优雅降级
 * （直接调新方法在旧宿主上会 NoSuchMethodError，探测则安全）。未来再加扩展服务优先挂这里。
 */
interface PluginHostV2 {
    /** 相机服务。门控：插件必须声明 [PluginCapability.CAMERA]，否则抛 [PluginCapabilityDeniedException]。 */
    fun camera(): PluginCameraService

    /**
     * 插件专属二进制文件目录（KV 存储装不下扫描页/PDF 等大块数据）。
     * 门控：[PluginCapability.PLUGIN_STORAGE]。路径约定 `filesDir/ahup_plugins/<插件id>/`，
     * 宿主卸载插件时连带清理；插件间按 id 隔离，互不可见。
     */
    fun pluginFilesDir(): File

    /**
     * 系统照片选择器（Photo Picker，**无需任何存储权限**），选中内容以只读 Uri 回调；
     * 用户取消回调 null。用于扫描插件的"从相册导入"。
     */
    fun pickImage(onResult: (Uri?) -> Unit)

    /**
     * 关闭插件自己：宿主 popBackStack 退出插件页（返回小工具页）。
     * 无门控——插件退出自己的页面不属于任何能力声明。
     * 返回前的二次确认由插件 UI 自理（BackHandler 里弹自己的确认弹窗，确认后调本方法）。
     */
    fun close()
}
