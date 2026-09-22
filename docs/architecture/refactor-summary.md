# AHUTong 大重构纪要（2026-09）

> 基线：`origin/master` @ `0492acc`（2026-09-13）
> 成果：`ui-decouple` @ `5da6791`
> 规模：**43 个提交、518 个文件变更、+31,765 / −13,804 行、1 模块 → 21 模块**
> 配套阅读：`CONTEXT.md`（模块职责）、`decoupling-plan.md`（执行计划与逐轮记录）、`adr/`（关键决策）

---

## 0. 三条工作线

本次重构不是一件事，是三条线并行推进后在 `d220e88` 汇合：

| 工作线 | 内容 | 来源 |
|---|---|---|
| **UI 解耦** | 组件契约化、统一页面壳、主题公园（槽位混搭） | 本仓库 `ui-decouple` 分支（29 个提交） |
| **数据解耦** | 单模块拆为 21 个 Gradle 模块、接缝与端口化 | `merge d220e88` 合入 OpenAHU `Develop` 分支 |
| **工程治理** | 边界门禁、测试归属、依赖锁、架构文档 | 随数据线一并合入，本地继续加固 |

合并后另有 8 个提交做整合与收敛（导航、主页、设置页、外观页命名等）。

---

## 1. 构建架构：从单模块到 21 模块

### 1.1 结构对比

| | 重构前（远端 master） | 重构后（本地 HEAD） |
|---|---|---|
| Gradle 模块 | 1 个（`:app`） | **21 个** |
| 源码文件 | 292（`app/src/main/java`）+ 53 测试 | **543**（含 103 个测试文件） |
| 分层方式 | 包名约定，无强制 | Gradle 模块 + 29 条门禁规则强制 |
| 数据层位置 | 全部在 `app/.../data/`（14 个子目录） | 拆到 `:core:*`（6）+ `:data:*`（7） |

### 1.2 模块清单

```text
:app                    组合根：绑定、导航、MainActivity、RustSDK 装配、登录页、主页 widget（283 源文件）
│
├─ :core:model          领域模型、值对象（22）              —— 零 IO、零 Android 依赖
├─ :core:common         错误模型、Dispatcher、时钟、扩展（13）
├─ :core:designsystem   主题、形状、通用组件、动效、组件契约（31）
├─ :core:network        HTTP 传输、Cookie、日志脱敏、续期接缝（11）
├─ :core:storage        设置分级读写、失败策略（6）
├─ :core:auth           登录态、凭据保险箱、会话刷新（5）
│
├─ :data:schedule       课表端口 + 纯策略（11）
├─ :data:grade          成绩/考试端口（3）
├─ :data:recharge       缴费模型、支付签名、源端口（27）
├─ :data:chaoxing       学习通协议、凭据、提醒存储（12）
├─ :data:repository-index  仓库索引与文档（2）
├─ :data:update         版本检查、下载、校验、安装意图（13）
├─ :data:personalization   行为词汇表、记录端口、设置（10）
│
├─ :feature:settings    设置/偏好/外观页 + ViewModel（16）
├─ :feature:schedule    课表页与 ViewModel（11）
├─ :feature:grade       成绩/考试页与 ViewModel（10）
├─ :feature:xuexiaotong 学习通页与 ViewModel（12）
├─ :feature:recharge    缴费页与 ViewModel（20）
├─ :feature:repository-index  仓库页与 ViewModel（8）
│
└─ :background          小组件、通知、提醒（17）—— 只读，禁止登录与写请求
```

### 1.3 依赖规则（硬约束，由 `ModuleBoundaryTest` 检查）

```text
:app（组合根：只做绑定与导航）
 ├─> :feature:*   ─> :core:* / :data:*
 ├─> :background  ─> 只读接口（不得触发登录、不得发起写请求）
 └─> :integration:native-sdk

禁止  :core:* / :data:*  ──> :feature:*
禁止  :feature:a         ──> :feature:b
禁止  网络层 ──> 业务层（AHURepository / dao）
禁止  `utils` ──> `data`（utils 是叶子）
禁止  在 `data/network` 之外构造 HTTP 日志拦截器
禁止  在 `data/network` 之外自行装配 AutoLoginInterceptor / TokenAuthenticator
禁止  在 `data/network` 之外构造 OkHttp 客户端
```

`docs/architecture/boundary-allowlist.txt` 已于 2026-09-14 **清空**——零豁免，任何新增违规直接让 CI 失败。

---

## 2. UI 层重构

### 2.1 组件契约与主题包（`:core:designsystem`）

**问题**：重构前每页写 `if (radiant) 一套壳 else 另一套壳`，主题差异渗透进每个页面；加一个主题要改所有页面，改一处忘一处就会出现行为漂移。

**方案**：`ui/theme/pack/` 引入契约与实现分离：

```text
AppComponentPack（open 契约，13 个成员，默认 Material3 兜底实现）
 ├─ MaterialComponentPack   （空实现，全走默认）
 ├─ MiuixComponentPack      （override 13 个成员）
 └─ RadiantComponentPack    （override 11 个成员）
        ↑ 经 AHUTheme 注入 LocalComponentPack（staticCompositionLocalOf）
AppUiTheme.componentPack 注册表 —— 新增主题 = 一个子类 + 一行注册
```

契约成员：`Toggle / SelectField / Button / Card / CircularProgressIndicator / FloatingActionButton / TextField / SearchField / HeaderIconButton / PageHeader / FilterChip / ModalBottomSheet / PageScaffold`。

**约定**：契约成员一律不声明默认参数——`open` 成员 + 默认值 + 值类型参数会触发 Kotlin 2.2 编译器 `JvmInlineClassLowering` 的 NPE（`getTypeSubstitutionMap`）。由 `App*` 包装层显式传全。

### 2.2 统一页面壳 `AppPageScaffold`

消灭「双壳分叉」：页面只声明语义参数（标题/副标题/操作/搜索态）+ 内容三选一（`content` 滚动流 / `lazyContent` 懒列表 / `freeContent` 自管滚动），**头部形态由当前主题的 Pack 决定**：

- Radiant：固定渐变悬浮标题栏，内容从其下穿过；
- Miuix：大标题收起式 TopAppBar（滚动联动）；
- 其他：内联标题行。

当前 **15 个文件**使用该壳；旧壳（`AppScrollablePageLayout` / `SecondaryPageScaffold` / `AppPageLayout`）引用清零。

### 2.3 组件族统一

| 组件 | 作用 |
|---|---|
| `AppDialog` | 弹窗族统一出口（长内容可滚动时内容区 `weight(1f, fill=false)`，修隐私政策首启卡死） |
| `AppStateCard` | 加载/空/错误三态标准化（替代各页手写 `Box + CircularProgressIndicator`） |
| `AppSectionCard` | 内容分区卡统一出口（消灭 `if (radiant) GlassCard else 裸 Column`） |
| `AppTitleIconButton` | 标题栏图标按钮 |
| `AppToggle` / `SettingsToggleRow` | 设置族开关行解耦，控件走槽位 |
| `RefreshState` | 手动刷新三态枚举（IDLE / LOADING / UPDATED） |

### 2.4 主题公园（Theme Park）

**目标**：主题不再是枚举，而是可组合、可持久化的数据——用户可组件级混搭（如 Radiant 主页 + Miuix 按钮）。

```text
ComponentSlotId（13 槽位）
    ×  SlotSource（Material 3 / Miuix / 曜光 Radiant）
    ↓
MixedComponentPack(base, overrides)  —— 以套装为底、逐槽委托，无覆盖时零包装开销
    ↓
DataStore: component_slot_overrides（"Button=radiant;Card=miuix"）+ startup mirror
    ↓
外观页（ThemeLab）：套装预设 + 逐槽混搭 + 实时预览（预览区全走 App* 组件）
```

**设计要点**：

- 槽位实现若依赖宿主环境（如 Radiant 的玻璃 backdrop），在非对应套装下按其既有降级路径渲染（实色回落），不做跨环境搬运；
- `LocalComponentPack` 保留 `staticCompositionLocalOf`：值变化触发 provider 内容整树重组，正是全局主题切换要的语义；
- 设置页名称经迭代定为「外观」，主题色选择器（含自定义色）从偏好设置迁移至该页顶部。

### 2.5 主页重建

- **全主题唯一布局族**：主页排版统一为 Radiant 方案，主题只换材质——「主页形态」这条第二轴的需求因此消失，`HomeWidgetRegistry` 不再读主题枚举；
- 小工具槽位改为流动排列（「更多」不再钉死第二行末尾；编辑态 7 槽几何不变）；
- 网格编辑六点迭代：阻尼阈值变形、弧形手柄、压实对齐、拖拽排序回归、高度 2/3、二维码原位弹开；
- 校园卡玻璃效果按液态能力分发（Radiant 恢复 ambient 真玻璃）。

### 2.6 导航 IA 收敛

- 学习通提级为第三 Tab，一级页转场方向表补齐；
- 学小通 Dock 逻辑统一（抽屉开关统一走 `AppToggle`）；
- 课表课程弹窗补钟表时间（`ScheduleSectionTimes.getCourseClockRange`）；
- 修复 `forEach` 标签返回导致的 D8 dex 失败（改 `for` 循环）。

### 2.7 视觉分叉的处理原则

分类收敛，而不是一刀切：

| 类型 | 处理 | 例子 |
|---|---|---|
| 结构分叉 | 统一（照主页重建的方子） | `SecondaryPageScaffold` 271 → 123 行并最终退场 |
| 形状/间距分叉 | 取 Radiant 正典值 | 平滑圆角、页面内边距 |
| 语义分叉 | 统一到语义组件 | `AppSectionCard`、`AppStateCard` |
| 材质分叉 | 留在 Pack 内，按能力降级 | `GlassCard` 的玻璃/实色 |

### 2.8 UI 层残留（诚实登记）

`isRadiantUi` 仍有 13 处（非 import），全工程分布：

| 位置 | 处数 | 性质 |
|---|---|---|
| `feature/schedule/Schedule.kt` | 4 | 布局+样式分叉（**课程表布局仍随主题变化**） |
| `feature/schedule/schedule/CourseCard.kt` | 3 | 排版策略、字号 |
| `feature/schedule/schedule/ScheduleColors.kt` | 1 | 配色板 |
| `feature/xuexiaotong/XuexiaotongScreen.kt` | 1 | 子页导航 IA（底部导航轮换 vs 页内 Dock） |
| `feature/settings/Settings.kt` | 1 | 图标风格 |
| `app/.../screen/BottomNavBar.kt` | 1 | 导航栏整体结构（Radiant vs Classic 两套） |
| `core/designsystem`（6 文件） | 6 | Radiant 实现内部降级机制 + 槽位粒度外的主题级语义组件 |
| 架构测试 | 2 | 断言用 |

**待收敛方向**（用户已明确构想：**主题只管样式和材质，布局不随主题变**）：

1. 度量（图标尺寸、字号、padding、maxLines）令牌化 → `ScheduleMetrics` 之类的主题度量对象；
2. 课表总览格与课程卡排版统一为唯一布局族；
3. `BottomNavBar` 升格为槽位（与 `PageScaffold` 同性质）；
4. 学习通子页导航 IA 统一（IA 决策不该挂在主题上）。

---

## 3. 数据层重构

### 3.1 五处「没有单一定义者」的地方 → 接缝

重构前的核心矛盾：需要各司其职的五处（网络、会话与验证、存储、更新下载、后台组件）都没有单一定义者，谁都能直接调用谁。

| 接缝 | 位置 | 解决的问题 |
|---|---|---|
| `SessionExpiryHook` | `:core:network` 声明，`:core:auth` 实现 | 网络层曾直接调 `AHURepository.loginWithCrawler()`，形成 `data → data.AHURepository` 环 |
| `NetworkLogging` | `:core:network` | 日志拦截器曾分散在 4 个客户端各自写 `if (BuildConfig.DEBUG)` 与脱敏名单，漏一处即 release 泄露；现 release 返回 `null`（代码路径根本不存在），统一脱敏 Authorization / Synjones-Auth / Cookie / Set-Cookie |
| `CampusHttp` | `:core:network` | 4 个第一方客户端各自重复「Cookie + 重定向 + 登录跳转识别 + 会话续期」四行装配，漏配不报错、只表现为某接口不自动重登 |
| `AhuHttp` / `AhuRetrofit` | `:core:network` | 收敛 9 个 OkHttp 构造点 |
| `CredentialVault` | `:core:auth` | 智慧安大密码原混在 900 行的 `AHUCache` 里，看不出「丢了会怎样」 |

### 3.2 端口 + 纯策略的模块化模式

以课表为例（其余域同构）：

```text
:data:schedule  —— 两件东西
  ├─ 纯策略：TeachingWeekPolicy（周次计算）、ScheduleSnapshotComparator（快照比较）
  │           无 IO，带自己的 JVM 测试一起搬过来
  └─ 数据端口：ScheduleSource（界面只问「有没有、变没变、什么时候取的」）
               协议与缓存分层留在 :app 的实现里
```

同类：`GradeSource` / `ExamSource`、`BathroomDepositSource` 等四个缴费源、`ChaoxingGateway` 系、`RepositoryIndex`、`ApkUpdateChecker` + `ApkDownloader`、`PresetSuggestions` + `BehaviorRecorder`。

### 3.3 会话模型（ADR 0002）

- 删除 `AHUApplication.sessionExpired` 与 `reLoginMutex` 两个公开静态字段（前者 6 处读写、后者声明后从未使用）；
- `AhuSession` 成为登录态唯一真相（`StateFlow<SessionState>`：Unknown / Anonymous / Authenticated / Expired）；
- **有界刷新**：一代会话只自动续期一次、带 30s 总超时，失败即 `Unauthorized`；禁止后台用存储凭据无限重试；
- 凭据只经 `CredentialVault`（SecureStorage 实现）。

### 3.4 存储分级与失败策略（ADR 0003）

- `SettingsStore` 落地读写失败策略：读失败回落保守值（个性化关、预取关、仅 Wi-Fi 开），写失败重试一次；
- `PreferencesManager` 的 18 处读、19 处写全部接线，`SettingsFailurePolicyTest` 四条用例（含两个反例）钉住；
- `StartupThemePreferences` 提供启动期快照（冷启动不闪旧主题）。

### 3.5 错误模型统一（ADR 0001）

- 收敛 `AHUResponse` / `Result` 双轨为 `AhuResult` + `AhuError`；
- 位于 `:core:common`，仓储与全部调用点完成迁移。

### 3.6 后台只读约束

`:background`（小组件、通知、提醒）禁止触发登录与发起写请求。曾发现小组件冷启动走网络并写缓存（`provideGlance` 调 `ScheduleWeekConfig.resolveLocalFirst()`），修复为只读 `ScheduleReadModel.cachedConfig()`，入口点不再发放带写入口的 `SettingsStore`，并用 R28 追加 import 禁令与内容正则看守。

---

## 4. 工程治理

### 4.1 边界门禁

- `ModuleBoundaryTest`：**29 条规则**，扫描跨模块源码根（不写死 `app` 路径）；
- `boundary-allowlist.txt`：存量违规清单，**只减不增，现已清空**；
- 门禁有效性做过反向验证：临时移除一条 allowlist → 测试立即失败并指出违规文件。

### 4.2 测试

| 指标 | 重构前 | 重构后 |
|---|---|---|
| 测试文件 | 53 | **103** |
| 套件 / 用例 | 54 / 243 | **78 / 378**，0 失败 |
| 归属守卫 | 无 | `FeatureTestOwnershipTest`：每个 `:feature:*` 必须自带测试源集与至少一个 `*Test.kt`；`:app` 不许留 feature 模块 ViewModel 的测试（模块清单从 `settings.gradle.kts` 读，新增 feature 自动纳入） |

搬迁过程中的通用规律：**任何模型跨模块搬迁都必须走一次真实编译**——跨模块属性无法智能转换（3 处报错）、新模块缺传递依赖（`gson`）这类问题静态检查发现不了。

### 4.3 供应链

- **21 份 `gradle.lockfile`**：覆盖 debug / test / lint 配置（不只有 release），锁定传递依赖版本；
- `gradle/verification-metadata.xml`：校验和钉住（新模块引入 19 个传递构件时，纯新增 160 行）；
- 依赖锁与依赖不一致时构建直接失败——版本漂移必须是一次显式提交。

### 4.4 文档

| 文档 | 内容 |
|---|---|
| `docs/architecture/CONTEXT.md` | 模块职责表、依赖规则、五条判定问题、现状基线 |
| `docs/architecture/decoupling-plan.md` | P0–P5 完整执行记录（30+ 轮），含两轮外部审查发现与修复、已知偏差登记 |
| `docs/architecture/adr/0001` | 统一错误模型 |
| `docs/architecture/adr/0002` | 会话模型（登录态唯一真相 + 有界刷新） |
| `docs/architecture/adr/0003` | 存储分级 |
| `docs/architecture/boundary-allowlist.txt` | 已清空 |
| `docs/on-device-behavior-prediction-plan.md` | 端侧行为预测方案 |
| `docs/sentry.md` | 崩溃与性能上报 |

另：Bugly appid 从 Java 源码移入 `buildConfigField`，使密钥扫描能盯住「标识不该散落在代码里」；Sentry 上传令牌只从环境变量读。

---

## 5. 已知偏差（计划书自报，已核实）

1. `AhuSession.state` 在生产环境**没有读者**：写路径齐全（登录、过期、复位），UI 问的是 `SessionIdentity`（本机有没有登录用户）。ADR 0002 所述「`Unauthorized` 全屏引导」重建未做；
2. 凭据未 100% 收口：`SecureCredentialVault` 仍有 4 处直接调用（`LoginViewModel` 存、`EvaluationRepository` 与 `TokenManager` 读、`AHUCache.clearAll` 清）——给两个 Kotlin object 找注入点是独立的一刀；
3. **五条主流程真机回归未做**（登录 / 课表 / 充值 / 学习通 / 更新），另加小组件冷启动与支付灰度；
4. APK 下载器缺事件级测试缝（`ApkDownloadApi` 是懒加载伴生对象，客户端不可注入），部分时序回归只能读代码发现；
5. 「承载状态或 IO 的 object 数量为 0」在本项目不可达（`AHUCache`、Rust SDK 桥、`CurrentWeekResolver`、`SessionRefreshCoordinator`、`AhuSessionState` 均属此类），应读作「object 不得成为跨模块的可变状态入口」；
6. UI 层 `isRadiantUi` 残留 13 处（见 §2.8），其中课程表布局仍随主题变化，与「主题只管样式材质」的目标尚未完全对齐。

---

## 6. 风险与建议

| 级别 | 事项 |
|---|---|
| **高** | **本地 43 个提交未 push**。数据层血统在 OpenAHU `Develop`（ff31246）有公开备份，但 UI 收敛成果、合并本身与合并后 8 个提交**无任何远端备份** |
| 高 | 五条主流程真机回归未做——门禁与单测覆盖不了登录时序、拦截器顺序、小组件冷启动这类行为 |
| 中 | 与上游 `OpenAHU/master` 的合流成本会随时间上升（远端仍在 0492acc，本次变更面 518 文件） |
| 中 | `AhuSession.state` 有接口无使用者，违反项目自己的「接口由使用者定义」原则，应补读者或降级文档 |
| 低 | 课表度量令牌化、导航栏槽位化等收尾工作（§2.8） |

---

## 7. 附录：核查命令

```bash
# 与远端基线的差异规模
git diff --shortstat 0492acc HEAD

# 门禁（边界规则）
./gradlew :app:testDebugUnitTest --tests "*ModuleBoundaryTest*"

# 全模块单测
./gradlew testDebugUnitTest

# 发布构建
./gradlew :app:assembleRelease

# 残留主题分支盘点
grep -rn "isRadiantUi" --include="*.kt" . | grep -v ":.*import " | grep -v "/build/"
```
