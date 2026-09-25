# 安大通插件开发指南

> 面向插件开发者与 AI Agent。插件 = 小工具页里的独立功能模块，与主程序**插拔解耦**：
> 拔掉插件模块（settings.gradle.kts 一行），主工程零改动；加回来，插件即出现。
> 参考实现：`feature/circle`（校园圈子，只读）。

## 1. 架构总览

```
┌─ 宿主（app）─────────────────────────────────────────┐
│  PluginRegistry（ServiceLoader 发现，零静态引用）      │
│  PluginHostServicesImpl（能力供给：HTTP/存储）          │
│  Main.kt：plugin/<id> 路由 → plugin.Entry(host)        │
│  HomeWidgetRegistry：入口卡片 = 内置小工具 + 插件       │
└──────────────▲───────────────────────────────────────┘
               │ 唯一契约
┌─ core/plugin-api（叶子模块，唯一词汇表）──────────────┐
│  AhuPlugin / PluginMeta / PluginCapability            │
│  PluginHostServices / PluginStorage                   │
└──────────────▲───────────────────────────────────────┘
               │ 实现
┌─ feature/<你的插件> ─────────────────────────────────┐
│  class XxxPlugin : AhuPlugin                         │
│  META-INF/services 索引 + 自己的 UI/网络/存储          │
└──────────────────────────────────────────────────────┘
```

**为什么不做运行时动态加载（APK 插件）**：第三方代码进主进程就能摸到教务/一卡通/学习通的全部会话凭据——安全上是灾难。编译期插拔 + ServiceLoader 发现是安全与灵活的平衡点。

## 2. 契约速览

| 契约 | 作用 |
|------|------|
| `AhuPlugin` | 插件本体：`meta` + `capabilities` + `@Composable Entry(host)` |
| `PluginMeta` | id（小写字母/数字/下划线）、标题、简介、图标、tint、版本、作者 |
| `PluginCapability` | `NETWORK` / `PLUGIN_STORAGE`——声明什么给什么，没声明调用即抛 `PluginCapabilityDeniedException` |
| `PluginHostServices` | `appContext` / `http()`（裸客户端，无全局会话）/ `storage()`（隔离 KV） |
| `PluginStorage` | `getString/putString/...`，命名空间 `plugin_<id>`，插件间互不可见 |

## 3. 十分钟写一个插件

### 3.1 建模块

```
feature/<name>/build.gradle.kts     ← 复制 feature/circle 的，改 namespace
feature/<name>/src/main/AndroidManifest.xml  ← 空 manifest
```

`settings.gradle.kts` 加一行：`include (":feature:<name>")`
`app/build.gradle.kts` 加一行：`implementation(project(":feature:<name>"))`

### 3.2 实现契约

```kotlin
package com.ahu.ahutong.feature.myplugin

class MyPlugin : AhuPlugin {  // 必须是 class + public 无参构造（ServiceLoader 要求，不能用 object）
    override val meta = PluginMeta(
        id = "my_plugin",
        title = "我的小工具",
        summary = "一句话简介",
        iconRes = R.drawable.ic_my_plugin,
        tint = 0xFF009688,          // 与其他小工具同款硬编码色
        version = "0.1.0",
        author = "你的名字"
    )
    override val capabilities = setOf(PluginCapability.NETWORK, PluginCapability.PLUGIN_STORAGE)

    @Composable
    override fun Entry(host: PluginHostServices) {
        MyPluginHome(host)
    }
}
```

### 3.3 服务索引（ServiceLoader 发现的关键）

`feature/<name>/src/main/resources/META-INF/services/com.ahu.ahutong.core.plugin.AhuPlugin`：

```
com.ahu.ahutong.feature.myplugin.MyPlugin
```

忘了这文件 = 插件永远不会被发现（宿主只记日志不报错）。

### 3.4 宿主侧：零改动

路由 `plugin/<id>`、小工具页入口卡片、能力供给全部由宿主自动完成。你的插件名会出现在小工具页，点击即进入 `Entry`。

## 4. 硬约束（模块边界测试会拦）

插件模块**只许依赖**：`:core:plugin-api`、`:core:designsystem`、`:core:common`、`:core:network`。

**禁止**伸手的地方（违者 `ModuleBoundaryTest` R30 系规则直接红）：
- `ui.screen.*` / `ui.state.*`（宿主页面与状态）
- `data.dao.*` / `data.crawler.*` / `data.repository.*`（主 App 数据层）
- `data.session.*` / `data.security.*`（**会话与凭据——插件永远拿不到登录态**）
- `AHUApplication`、小组件、通知、提醒、个性化、原生 SDK

**网络纪律**：
- 只能用 `host.http()` 拿到的裸客户端；要自定义 Header 用 `client.newBuilder().addInterceptor{}`
- 禁止自己 `OkHttpClient.Builder()`（R8 规则全局唯一构造点）
- 裸客户端**不带任何主 App Cookie**——插件访问第三方服务自带鉴权，不许顺走校园会话

**UI 纪律**：
- 一律用设计系统（`AppCard`/`AppButton`/`AppDialog`/`AppToggle`…），自动获得三主题（曜光/Miuix/Material）+ 全局背景适配
- 不自己画主题分支；插件内部的页面跳转自己管（状态切换或自建 NavHost），宿主只给 `Entry` 一扇门

## 5. 能力边界（现在给什么、故意不给什么）

| 能力 | 状态 |
|------|------|
| 网络（裸客户端） | ✅ `NETWORK` |
| 隔离存储 | ✅ `PLUGIN_STORAGE` |
| 应用上下文 | ✅ `appContext`（只读用途） |
| 学号/教务/一卡通/学习通会话 | 🚫 故意不给——插件不需要知道用户是谁 |
| 主页面/课表数据 | 🚫 不给——需要就先评审加能力，不许绕 |
| 后台任务/通知 | 🚫 暂不给（有真实需求再议） |

## 6. 参考实现：feature/circle（校园圈子）

第一个插件，也是契约的验收测试：

- **需求面恰好是最小完备集**：网络 + 存储 + UI——证明契约够用
- **只读设计**：全程零鉴权 GET（列表 `topics?page=`、详情 `topics/read_only/{id}`、评论 `comments?topic_id=`），不接发帖（发帖要身份 token，超出只读定位）
- **防御式 JSON 解析**：BBS 接口包裹层不稳定，`extractArray` 兼容 `{data:{list:[]}}` / `{data:[]}` 等多种形态
- **页面内导航**：列表 ↔ 详情用密封类状态切换，不占宿主路由
- **免责条**：第三方社区内容提示常驻列表顶部

## 7. 调试与验收清单

- [ ] `settings.gradle.kts` + `app/build.gradle.kts` 各一行
- [ ] `META-INF/services` 索引文件内容与类全限定名一致
- [ ] `assembleDebug` 通过 + `ModuleBoundaryTest` 全绿
- [ ] 小工具页出现入口卡片（图标/标题/tint 正确）
- [ ] 点击进入、返回正常；服务挂掉时优雅降级（错误态 + 重试），不崩宿主
- [ ] release 构建后插件仍在（proguard 规则已保活，回归一次）

## 8. 常见问题

**Q：插件没出现在小工具页？**
九成是 META-INF/services 索引写错（类名、路径、文件名三处必须严格一致）；logcat 搜 `PluginRegistry` 看发现日志。

**Q：能拿到当前登录的学号吗？**
不能，这是故意的。插件活在「不需要知道用户是谁」的世界里。真有个性化需求，走评审往契约加能力。

**Q：插件之间能通信吗？**
不能，存储和网络都是隔离的。插件间有依赖说明该合并成一个插件。
