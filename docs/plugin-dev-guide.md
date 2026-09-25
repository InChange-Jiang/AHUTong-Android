# 安大通插件开发指南（.ahup 运行期插件）

> 面向插件开发者与 AI Agent。插件 = `.ahup` 文件，**用户在 App 内手动安装、运行时装载**，
> 与主程序完全解耦：插件不打进宿主 APK，宿主对插件零静态引用。
> 参考实现：`feature/circle`（校园圈子，只读）。

## 1. 架构总览

```
┌─ 插件作者侧 ─────────────────────────────────────────┐
│  feature/<name> 模块（开发态仍是 Gradle 模块）         │
│  gradlew :feature:<name>:packageAhup                 │
│    → AAR classes.jar → D8 → plugin.dex               │
│    → RSA 签名（团队密钥）→ manifest.json + icon.png    │
│    → dist/ahup/<id>.ahup                             │
└──────────────▲───────────────────────────────────────┘
               │ 分发 .ahup 文件
┌─ 用户侧（宿主 app）──────────────────────────────────┐
│  小工具页 → 插件管理 → 安装插件包 → 确认弹窗            │
│  （元数据 + 能力声明 + 签名状态三态）                   │
│  AhupInstaller：解包校验/签名校验/落盘 filesDir/ahup    │
│  RuntimePluginLoader：DexClassLoader 装载入口类        │
│  PluginRegistry：内置 + 运行期合并视图（StateFlow）     │
└──────────────────────────────────────────────────────┘
```

## 2. .ahup 包格式（ZIP）

```
campus_circle.ahup
├── manifest.json   展示用元数据（id/标题/作者/入口类/能力/签名）
├── plugin.dex      插件代码（D8 产物，只打插件自己的类）
└── icon.png        入口图标（运行期插件无 R 资源体系，图标只能随包携带）
```

**安全模型：签名是唯一闸门。** 插件与宿主同进程运行，契约接口管得住守规矩的插件、
管不住故意的——所以宿主只完全信任钉死的团队公钥（`assets/ahup_trusted_pubkey.txt`）：

| 签名状态 | 安装弹窗表现 |
|---|---|
| 团队签名（可信） | ✅ 正常安装 |
| 未签名 | ⚠️ 强警告，用户自担风险确认 |
| 签名无效 | 🚫 安装按钮变红色危险样式 |

签名载荷 = `sha256(plugin.dex + icon.png)`，RSA/SHA256withRSA。
manifest.json 不参与签名（纯展示元数据；能力门控读的是已签名代码里的声明）。

## 3. 契约速览（core/plugin-api）

| 契约 | 作用 |
|------|------|
| `AhuPlugin` | `meta` + `capabilities` + `@Composable Entry(host)` |
| `PluginMeta` | id（小写/数字/下划线）、标题、简介、`icon: PluginIcon`、tint、版本、作者 |
| `PluginIcon` | `Resource(resId)` 编译期 / `Bytes(png)` 运行期——装载器自动用包内 PNG 覆写 |
| `PluginCapability` | `NETWORK` / `PLUGIN_STORAGE`——未声明调用即抛 `PluginCapabilityDeniedException` |
| `PluginHostServices` | `appContext` / `http()`（裸客户端，零全局会话）/ `storage()`（隔离 KV） |

## 4. 十分钟写一个 .ahup 插件

### 4.1 建模块（开发态还是普通 Gradle 模块）

复制 `feature/circle` 全套：`build.gradle.kts`（改 namespace）、空 manifest、源码。
依赖只许：`:core:plugin-api`、`:core:designsystem`、`:core:common`、`:core:network`。

### 4.2 实现契约

```kotlin
class MyPlugin : AhuPlugin {  // class + public 无参构造（装载器反射实例化）
    override val meta = PluginMeta(
        id = "my_plugin",
        title = "我的小工具",
        summary = "一句话简介",
        icon = PluginIcon.Resource(R.drawable.ic_my_plugin), // 仅编译期兜底，运行期被包内 PNG 覆写
        tint = 0xFF009688,
        version = "0.1.0",
        author = "你的名字"
    )
    override val capabilities = setOf(PluginCapability.NETWORK)

    @Composable
    override fun Entry(host: PluginHostServices) { MyPluginHome(host) }
}
```

### 4.3 打包

在模块 `build.gradle.kts` 里复制 circle 的 `packageAhup` 任务（改 id/标题/entry），然后：

```
gradlew :feature:myplugin:packageAhup
```

产物在 `dist/ahup/my_plugin.ahup`。有团队签名密钥（`~/Documents/ahutong-plugin-signing/`）自动签名。

### 4.4 安装验证

把 .ahup 发到手机（QQ/文件传输），App 内：小工具页 → 插件管理 → 安装插件包 → 选文件 → 确认。

## 5. 硬约束（边界测试会拦）

- 插件模块只许依赖契约/设计系统/common/network 四个模块
- 禁止 import：`ui.screen.*` / `ui.state.*` / `data.dao.*` / `data.crawler.*` / `data.session.*` / `data.security.*` / `AHUApplication`（**插件永远拿不到主 App 的登录态与凭据**）
- 网络只能用 `host.http()` 裸客户端；禁止自己构造 OkHttpClient（R8 全局唯一构造点）
- UI 用设计系统组件（自动三主题 + 全局背景适配）；插件内部导航自己管，宿主只给 `Entry` 一扇门

## 6. 参考实现：feature/circle（校园圈子）

第一个运行期插件，契约验收测试：

- 只读 feed（列表 `topics?page=` / 详情 `topics/read_only/{id}` / 评论），全程零鉴权
- 防御式 JSON 解析（BBS 包裹层多形态兼容）
- 页面内导航（列表 ↔ 详情密封类状态切换），不占宿主路由
- 第三方内容免责条常驻

## 7. 常见问题

**Q：装了插件但小工具页没出现？**
logcat 搜 `RuntimePluginLoader`——九成是入口类名与 manifest.entryClass 不符，或入口类没有 public 无参构造。

**Q：插件里能用 R 资源吗？**
编译期引用不崩（R 类打进 dex，字段是内联常量），但**运行期宿主里没有这些资源**——图片/图标一律放包里读字节流，或直接用设计系统组件。

**Q：能拿学号/登录态吗？**
不能，故意的。宿主给的裸客户端零 Cookie，存储按插件 id 隔离。

**Q：插件 dex 里的 Compose 代码怎么跑的？**
父 ClassLoader 是宿主——Compose 运行时、设计系统、契约类全部共享宿主的，插件 dex 只打自己的业务类。
