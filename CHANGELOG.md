v3.3.0

1.新增了miuix主题和RadiantUI主题

2.新增了学习通日历/学习通课程页面

3.新增了电控缴费A、B，将招商银行充值校园卡逻辑统一

4.修改了部分二级页面的排版布局，优化使用体验

未发布（插件系统）

1.宿主开放插件相机能力（PluginCapability.CAMERA + PluginHostV2：camera/pluginFilesDir/pickImage），CAMERA 权限由宿主持有并统一处理运行时授权

2..ahup 包格式升级 v2：支持携带原生库（lib/<abi>/*.so，如 OpenCV），签名载荷纳入全部 .so；v1 旧包校验行为不变

3.插件管理页能力标签中文化（网络/插件存储/相机）
