# AHUTong 解耦与重构计划（各司其职 · 最小风险）

> 审查基线：`upstream/master` @ `aa40c6a4`（2026-09-11，PR #20 合并后）
> 审查方式：只读静态审查（依赖统计、接口与生命周期核对、构建与 CI 配置核对）
> 文档状态：计划稿。执行前请先确认第 4 节的阶段顺序与第 8 节的"明确不做"。

---

## 执行状态（截至 2026-09-14）

### P0 冻结与守护 —— 已完成并验证

| 产出 | 路径 |
|---|---|
| 架构上下文（模块职责表） | `docs/architecture/CONTEXT.md` |
| 3 条 ADR | `docs/architecture/adr/0001-error-model.md`、`0002-session-model.md`、`0003-storage-tiers.md` |
| 存量违规清单（只减不增） | `docs/architecture/boundary-allowlist.txt` |
| 边界门禁测试 | `app/src/test/java/com/ahu/ahutong/architecture/ModuleBoundaryTest.kt` |

验证方式（已实际执行）：

1. 门禁通过：`:app:testDebugUnitTest --tests ModuleBoundaryTest` 绿。
2. 门禁有效：临时移除 allowlist 中一条 → 测试立即失败并指出违规文件 → 恢复后重新变绿。
3. 该测试属于既有 CI 任务 `:app:testDebugUnitTest`，无需改 CI 配置即可生效。

### P1 纯搬运 —— 第一块（`:core:model`）已完成

- 新增 `:core:model`（`core/model/build.gradle.kts`，Android library，namespace `com.ahu.ahutong.core.model`）。
- `app/src/main/java/com/ahu/ahutong/data/model/` 全部 19 个文件迁入新模块；**包名保持不变**（`com.ahu.ahutong.data.model`），因此 69 处引用零改动 —— 这是"最小风险"的取舍：先换物理模块，包名重命名留作独立一步。
- 修掉一处真实的反向依赖：`CampusDataItem` 原先声明在 `ui.state.ElectricityDepositViewModel` 内，被模型 `RoomSelectionInfo` 引用（模型 → UI）。现已抽到 `core/model/.../data/model/CampusDataItem.kt`，R1 违规归零。
- 新模块依赖：`androidx.annotation`（@NonNull）+ `gson`（@SerializedName）——后者是编译验证阶段发现的遗漏。

验证结果（已实际执行）：

| 检查 | 结果 |
|---|---|
| `:core:model:compileDebugKotlin` | BUILD SUCCESSFUL（19 个搬迁文件真实编译通过） |
| app/test 中 `data.model.*` 引用解析 | 0 处悬空（46 个类型全部可解析） |
| 边界规则复算 | 8 处存量违规，与 allowlist **完全一致**；0 条失效条目 |
| `:app:compileDebugKotlin` / `:app:testDebugUnitTest` | **未能执行**，原因见下 |

补充（同日完成）：`:app:compileDebugKotlin` 与 `:app:testDebugUnitTest` 已在本机跑通 ——
**编译 BUILD SUCCESSFUL；单测 54 套件 / 243 用例 / 0 失败**（改动前基线 53 套件 / 241 用例，新增的正是边界门禁）。

搬运过程中编译器抓出并已修复的 3 类问题（**跨模块搬迁的通用规律，后续每一步都会遇到**）：

| 现象 | 位置 | 处理方式 |
|---|---|---|
| 跨模块属性无法智能转换 | `ui/screen/main/PhoneBook.kt`（2 处，6 个错误） | 先取局部变量再做判空 |
| 跨模块属性无法智能转换 | `data/EvaluationRepository.kt#requireData` | 同上 |
| 跨模块属性无法智能转换 | `ui/screen/main/BathroomDeposit.kt`（3 个错误） | 同上 |
| 新模块缺少传递依赖 | `core/model/build.gradle.kts` | 补 `gson`（模型用 `@SerializedName`） |

结论：**任何模型跨模块搬迁都必须走一次真实编译**，静态检查发现不了这类问题。

### P1 剩余部分与已知阻塞点

**已完成（2026-09-13）。** 当时的阻塞点都靠"先抽接缝、再搬运"解决，过程本身也印证了这份计划的判断：

| 阻塞点 | 解决办法 |
|---|---|
| `:core:common`：`ext/CoroutineScope.kt` 弹 Toast、`data/debug/DebugClock.kt` 读 `AHUCache` | 抽出 `UserNotice`（异常提示）与 `DebugTimeSource`（时间源）两个可安装接缝，生产实现留在 `:app`；4 个 `ext` 文件与 `DebugClock` 随后搬入 |
| `:core:designsystem`：主题依赖 `PreferencesViewModel` 与设置常量、付款组件依赖业务状态 | `AHUTheme` 改为接收 `AhuThemeConfig`（宿主读 ViewModel 后传入）；`DEFAULT_THEME_COLOR` 收进 `:core:model`；两个付款组件留在 `:app` |

结果是三个模块都建起来了：`:core:model`、`:core:common`、`:core:designsystem`（21 个文件，含主题、组件、动效工具与形状）。

新增/更新两条门禁：

- **R11 `core/designsystem` 是叶子**：不得 import `ui.screen` / `ui.state` / `ui.component` / `personalization` / `sdk` / `data.*` / 后台组件；
- `ModuleBoundaryTest` 的扫描范围加入 `core/designsystem`；`LiquidGlassArchitectureTest` 改为跨模块源码根查找，不再写死 `app` 路径（写死路径的断言在文件搬家时会以"文件不存在"的形式失效）。

⚠️ 安全相关变更：新模块带来 19 个传递构件，`gradle/verification-metadata.xml` 增加 **160 行校验和**（纯新增，0 删除/修改，`git diff -U0` 可核对）。

✅ 已处理（P5 收尾）：`ui/components/PayCapsuleConfirmButton.kt` 全仓库零引用，确认是死代码后删除；
它用到的 `PaymentState` 仍由 `:feature:recharge` 的校园卡流程使用，因此只删这一个文件。

### P2 接缝 —— 第一个切片已完成（会话续期接缝）

目标：消除"网络层依赖业务层"这条反向边（CONTEXT.md 第 2 节 R4）。

| 新增/修改 | 说明 |
|---|---|
| `data/crawler/net/SessionExpiryHook.kt`（新增） | 网络层唯一允许调用的会话续期接缝，只有 `refresh(observedGeneration): Boolean` 一个方法 |
| `data/session/RepositorySessionExpiryHook.kt`（新增） | 业务实现：用存储凭据重新登录、清理 token；内容由 `TokenAuthenticator` 原样搬出，行为不变 |
| `TokenAuthenticator` | 改为构造注入 `SessionExpiryHook`；删除对 `AHURepository`/`AHUCache`/`AHUApplication` 的 import，只保留 HTTP 关注点（重试计数、加解密无关） |
| `AdwmhApi` / `JwxtApi` | 构造点改为 `TokenAuthenticator(RepositorySessionExpiryHook)` |
| `boundary-allowlist.txt` | **删除 R4 条目**，该约束从此由门禁强制，无豁免 |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` 54 套件 / 243 用例 / 0 失败；
门禁的"无失效条目"断言同时通过，证明 R4 已真正消除而不是被绕过。

P2 后续（尚未开始，按计划顺序）：统一 `HttpTransport` 工厂收敛 9 个 OkHttp 构建点、落地 `AhuError` 并删除 `AHUResponse`/`Result` 双轨、`AhuSession`/`CredentialVault`、`AHUCache` 按凭据/设置/缓存三分。

### P2 接缝 —— 第二个切片已完成（会话状态收口）

勘察结论：`AHUApplication.sessionExpired` 有 4 处写入、**没有任何读取方**；`reLoginMutex` 声明后从未使用。两者都是只写不读的全局状态。

| 新增/修改 | 说明 |
|---|---|
| `data/session/AhuSessionState.kt`（新增） | 登录态单一真相：`Status`（Anonymous/Authenticated/Expired）+ `StateFlow`，暴露 `markExpired()` / `markAuthenticated()` |
| `SessionRefreshCoordinator` | 两处写入改为调用 `AhuSessionState`，移除对 `AHUApplication` 的 import |
| `RepositorySessionExpiryHook` | 同上；刷新失败标记为 Expired |
| `Login.kt` / `Settings.kt` | 登出与"清除所有数据"改为 `AhuSessionState.markExpired()`；这两处对 `AHUApplication` 的引用**仅为此一行**，import 一并移除 |
| `AHUApplication.java` | **删除** `sessionExpired` 与 `reLoginMutex` 两个公开静态字段 |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` 54 套件 / 243 用例 / 0 失败；
`rg sessionExpired app/src` 仅剩新文件里的说明性注释，代码引用为 0。

顺带核对（无需改动）：`HttpLoggingInterceptor` 在 `YcardApi` / `WeatherApi` 中**已经是 `if (BuildConfig.DEBUG)` 条件接入且脱敏**（Authorization / Synjones-Auth / Cookie / Set-Cookie），release 不会输出请求日志。这一项标记为已完成。

### P2 接缝 —— 第三个切片已完成（HTTP 日志收口 + 门禁）

勘察发现日志拦截器分散在 4 处（`JwxtApi` / `AdwmhApi` / `YcardApi` / `WeatherApi`），每处各自写 `if (BuildConfig.DEBUG)` 且各自维护脱敏名单——漏改一处就是 release 泄露。

| 新增/修改 | 说明 |
|---|---|
| `data/network/NetworkLogging.kt`（新增） | HTTP 日志拦截器的**唯一**构造点：release 返回 `null`（日志代码路径根本不存在），统一脱敏 Authorization / Synjones-Auth / Cookie / Set-Cookie |
| 四个 API 客户端 | 改用 `NetworkLogging.debugInterceptor(...)`；`YcardApi` 浴室客户端继续移除同一实例（其 URL 带 bearer token） |
| `ModuleBoundaryTest` | 规则支持"例外前缀"，新增 **R6**：`data/network` 之外不得 import `okhttp3.logging` |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` 54 套件 / 243 用例 / 0 失败；
`rg "import okhttp3.logging" app/src/main` 只剩 `NetworkLogging.kt`，且 4 处 `if (BuildConfig.DEBUG)` 守卫全部消失。

### P2 接缝 —— 第四个切片已完成（第一方客户端共享装配）

问题：4 个第一方客户端各自重复"Cookie + 重定向 + 登录跳转识别 + 会话续期"四行装配。**漏配不会编译报错**，只会在运行时表现为某个接口不会自动重登——最难发现的一类缺陷。

| 新增/修改 | 说明 |
|---|---|
| `data/network/CampusHttp.kt`（新增） | `campusCookies` / `campusAutoLogin` / `campusSessionRefresh` / `withoutCampusSessionRefresh`（登录客户端专用） |
| `JwxtApi` / `AdwmhApi` / `EvaluationApi` / `YcardApi` | 改用共享装配，**网络拦截器顺序保持不变**（仍是 [自动重登, 日志]） |
| `CampusHttpAssemblyTest`（新增） | 纯 JVM 契约测试：断言共享装配真的把 Cookie jar、自动重登网络拦截器、会话 authenticator 装进客户端 |
| `ModuleBoundaryTest` | 新增 **R7**：`data/network` 之外不得自行装配 `AutoLoginInterceptor` / `TokenAuthenticator` |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` **55 套件 / 245 用例 / 0 失败**（新增 2 个装配契约用例）；
`rg "import com.ahu.ahutong.data.crawler.net.(TokenAuthenticator|AutoLoginInterceptor)"` 只剩 `CampusHttp.kt`。

⚠️ 运行时验证待办：拦截器顺序虽已逐行保持，但登录/重登属于真机行为，合并前需要在设备上回归一次（登录 → 课表 → 借阅/一卡通 → 评价）。

### P2 接缝 —— 第五个切片已完成（凭据保险箱）

问题：智慧安大密码原先经 `AHUCache` 读写，混在 900 行无关缓存访问器里——从调用点看不出"这份数据丢了会怎样"，而这恰恰是 ADR 0003 的核心分类。

| 新增/修改 | 说明 |
|---|---|
| `data/security/SecureBoxStore.kt`（新增） | init box 的统一读写，**迁移链原样保留**（SecureStorage → Rust SDK → 旧 MMKV 明文，边读边迁移）；由 `AHUCache` 与凭据保险箱共用，不再是 AHUCache 私有实现 |
| `data/session/CredentialVault.kt`（新增） | `CredentialVault` 接口 + `SecureCredentialVault`（SecureStorage 实现）：`saveWisdomPassword` / `wisdomPassword` / `clearWisdomPassword` |
| 4 个调用点 | `LoginViewModel`、`EvaluationRepository`、`RepositorySessionExpiryHook`、`TokenManager` 改经保险箱 |
| `AHUCache` | 删除 `saveWisdomPassword` / `getWisdomPassword`；`clearAll()` 改调 `SecureCredentialVault.clearWisdomPassword()` |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` 55 套件 / 245 用例 / 0 失败；
`rg "getWisdomPassword|saveWisdomPassword" app/src` 只剩 `CredentialVault` 自身与一处注释。

⚠️ 运行时验证待办：凭据读写涉及 Keystore 与旧数据迁移，合并前需在设备上回归（已登录用户直接使用、会话过期后自动重登、清除数据后要求重新登录）。

### P2 第 3 项（统一错误模型）—— 第 A 段已完成

勘察结论：旧错误表达的实际规模比预估小得多——`AHUResponse` 只被 12 个文件、211 行引用；`Result<` 13 个文件、53 行。因此本项可以做完，分三段推进（A 落地类型 → B 适配器与仓储 → C UI 调用点并删除旧类型）。

| 新增 | 说明 |
|---|---|
| `:core:common` 模块 | 共享基础设施（Android library，无三方依赖） |
| `core/common/.../AhuError.kt` | 封闭错误模型：Network / Timeout / Unauthorized(message) / ProtocolChanged(detail) / Server(code, message) |
| `core/common/.../AhuResult.kt` | `AhuResult` + `map`，替代 Kotlin `Result` 作为跨模块结果类型 |
| `data/AhuResponseMapping.kt` | **唯一**允许新旧模型并存处：`AHUResponse.toAhuResult()`，规则与旧语义逐条对应 |
| `data/AhuResponseMappingTest` | 映射契约测试（4 例）：0+数据→Success、0 无数据→ProtocolChanged、401→Unauthorized、其它→Server |

验证：`compileDebugKotlin` 无错误；**56 套件 / 249 用例 / 0 失败**。

### P2 第 3 项（统一错误模型）—— 第 B 段已完成（仓储与全部调用点）

改动范围：`AHURepository` 全部公开方法、4 个 ViewModel、4 个界面、小组件与预取协调器（共 20 个文件）。

| 关键决策 | 说明 |
|---|---|
| 仓储边界统一为 `AhuResult` | 适配器仍返回 `AHUResponse`，由 `toAhuResult(missingDataMessage = …)` 桥接，保证"课表响应缺少数据"这类**上游文案不丢失** |
| 登录结果显式建模 | 新增 `LoginOutcome`（Success / JwxtWebVerificationRequired）："校园网已过、教务还要安全验证"是域状态而非错误码，旧的 `WEB_VERIFICATION_REQUIRED_CODE` 约定在 UI 侧消失 |
| 异常边界统一 | `Throwable.toAhuError()` + `Result.toAhuResult()`（Rust SDK 等仍以 Kotlin `Result` 暴露失败） |
| 调用点改写 | 不再有 `.isSuccessful` / `.data` / `.msg` / `.getOrNull()` / `.exceptionOrNull()`；用户可见文案经 `AhuError.toUserMessage()` 保持不变 |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` **57 套件 / 254 用例 / 0 失败**。

### P2 第 3 项（统一错误模型）—— 已完成 ✅

C 段：适配器与最后两个使用旧类型的 ViewModel 全部迁移，旧类型已删除。

| 改动 | 说明 |
|---|---|
| `BaseDataSource` + 两个真实适配器 + 两个 Mock | 全部返回 `AhuResult`；ADWMH 的两个接口改用**协议层 DTO** `AdwmhApiResponse`（在适配器内映射），旧类型不再兼任应用级返回类型 |
| `NetworkRechargeViewModel` / `ElectricityDepositViewModel` | 内部支付/选项查询助手改为直接构建 `AhuResult`（`parseJsonResponse` 不再依赖可变包装对象）；7 个选项/查询方法与调用点同步迁移 |
| **删除** `AHUResponse.java`、`AhuResponseMapping.kt`、`AhuResponseMappingTest.kt` | 迁移桥接的使命结束；`rg AHUResponse app/src` 现在为零引用 |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` **56 套件 / 249 用例 / 0 失败**
（套件数由 57 降为 56，减少的正是随旧类型一起删除的桥接测试）。

### P2 第 1 项（网络接缝）—— 已完成 ✅

收尾部分：把剩余 9 处 OkHttp 构造点全部收进统一工厂，并加门禁防止回退。

| 改动 | 说明 |
|---|---|
| `data/network/AhuHttp.kt`（新增） | `plain(...)` 成为唯一构造点；参数保留（各链路数值本就不同：探测 5 秒、下载 15 分钟），默认值对齐 OkHttp 默认，因此不改变既有行为 |
| 迁移 | Weather / GitHub / 学习通 / 本地服务 / 仓库下载 / 遥测 / 训练数据上报 / openahu 服务端 / 四个第一方校园客户端 |
| `ModuleBoundaryTest` | 新增 **R8**：`data/network` 之外不得出现 `OkHttpClient.Builder(`；规则引擎增加"整文件正则"能力，跨行的 `OkHttpClient\n .Builder()` 写法也拦得住 |

验证：`:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` **56 套件 / 249 用例 / 0 失败**；
R8 首次运行即拦下 4 处遗漏（4 个第一方客户端），补完后零豁免通过 —— 说明门禁确实在起作用。

**收尾补充（本轮）**：计划要求"统一 9 个 OkHttp/Retrofit 构建点"，此前只完成了 OkHttp 侧——Retrofit 仍在 9 个文件里各自装配。现在收口到 `data/network/AhuRetrofit.kt` 的 `retrofit(baseUrl, client)`，并加门禁 **R9**（`data/network` 之外不得出现 `Retrofit.Builder(`）。各 API 仍各自持有 baseUrl 与 client，因为它们本来就不一样。

**R9 有效性已验证**：临时在 `data/repository/GitHubApi.kt` 加一行含 `Retrofit.Builder(` 的注释，`ModuleBoundaryTest` 立刻失败并指名 R9 与该文件；删掉后又变绿（与 P0 验证 R1/R8 的方式一致）。

### P2 第 2 项（网关契约测试）—— 已完成 ✅

用 fixture 驱动 `CrawlerDataSource`，把"上游返回什么 → 领域结果是什么"钉死。打通了测试所需的两处结构性改动：

| 改动 | 说明 |
|---|---|
| `createJwxtApi` / `createAdwmhApi` | 改为**顶层函数**：契约测试可以在纯 JVM 里把请求指向本地 fixture 服务；若写成伴随对象方法，调用会触发依赖 Android 的客户端初始化（实测报 `ExceptionInInitializerError`） |
| `CrawlerDataSource` | 两个协议客户端改为构造参数（默认值 = 生产实例），行为不变 |
| `CrawlerDataSourceContractTest`（新增） | 4 条契约：服务端渲染表格格式、旧 JS 变量格式、"无考试"= 成功空结果、上游 5xx = `Server(-1, "请求失败")` |
| `gradle/verification-metadata.xml` | 新增测试依赖 `mockwebserver` 的 sha256 校验和（项目开启了依赖校验，新依赖必须显式受信）——**这是安全相关改动，需要审阅** |

验证：`:app:testDebugUnitTest` **57 套件 / 253 用例 / 0 失败**。

契约测试自身的价值已经体现：第一版 fixture 把课程名/类型 span 放进了第一个 `td`，测试立刻报出位置字段被污染——这类"fixture 与真实页面结构不符"正是它要拦住的问题。

**第二段（补齐 SDK 侧缺口）**：原以为"Rust SDK 网关需要 native 库，只能靠 instrumentation"，写测试时发现两条路径消费的是**同一份 `GradeResponse` 结构**（爬虫侧由 `/student/for-std/grade/sheet/info/{id}` 直出，原生侧由本地服务 JSON 反序列化得到），因此"同一份上游数据 → 同一份领域成绩"完全可以在 JVM 上钉住，需要设备的只剩"本地服务的传输与鉴权"。

| 改动 | 说明 |
|---|---|
| `data/crawler/GradeMapper.kt`（新增） | 成绩聚合的**唯一**实现。总学分 / 总学分绩点 / 加权平均绩点三段算术原先在四处各写了一遍（爬虫单档案构建、爬虫多档案合并、SDK 响应映射、SDK 多档案合并），"两个网关结果一致"只是巧合而非结构保证；现在四处共用同一份 |
| `CrawlerDataSource` / `SdkDataSource` | 五处内联算术删除，改为调用 `GradeMapper`；每学期的构建逻辑**刻意不合并**——爬虫侧对缺字段是直接取值（`credits.toString()`、`semesterId!!`），SDK 侧回落为 0，统一它们属于行为变更 |
| `GradeMapperContractTest`（新增，5 例） | 爬虫侧经 MockWebServer、原生侧经同一份 JSON，**同一套断言跑两遍**；另覆盖空响应、学期名被改（"2024"）、课程字段缺失、同名学期 |
| `CrawlerDataSourceContractTest`（+2 例） | 契约扩展到安大智慧 JSON 端点：失物招领列表的分页结构、写接口 `{code,msg,data}` 包装的四个分支（成功 / 401→Unauthorized / code 0 无数据→ProtocolChanged / 其它→Server） |

写这套测试时抓到的真实差异（**只记录、未修改**，因为改动会改变学生看到的内容）：

- 原生侧以学期名为 HashMap 键，**学期顺序不保证**与上游一致；
- 同名学期在原生侧会互相覆盖（只留后一个），爬虫侧两份都保留——若校方把同一学期的不同修读类型拆到两个 `semesterId` 下，原生侧会静默少一学期；
- 爬虫侧的 `semesterId!!` 在字段缺失时抛 NPE（被上层 catch 吞掉，表现为该档案无成绩），SDK 侧回落为 0。

契约测试的价值再次体现：第一版写接口 fixture 用了 `"object"` 作为载荷键（那是 `LostFoundResponse` 的写法），测试立刻失败——`AdwmhApiResponse` 映射的是 `"data"`。

验证：`:app:testDebugUnitTest` **58 套件 / 260 用例 / 0 失败**。

### P2 第 4 项（会话接缝）—— 已完成 ✅（含一处有依据的范围修正）

| 改动 | 说明 |
|---|---|
| `data/session/AhuSession.kt`（新增） | 会话唯一入口：`state` / `signIn` / `signOut` / `ensureFresh`（有界刷新） |
| `data/session/RepositoryAhuSession.kt`（新增） | 行为与原流程逐条对应：登录沿用仓库实现（爬虫/原生/回退不变），新增"登录成功后标记已认证"；登出清理会话态、token、第一方 Cookie；续期沿用 generation 机制、失败即过期 |
| `RepositorySessionExpiryHook` | 退化为一行委托（网络层只认识接缝） |
| `LoginViewModel` | 登录改经 `RepositoryAhuSession.signIn` |
| `AhuSessionState` | 增加 `markAnonymous()`（区分"主动登出"与"会话过期"） |

**范围修正（有证据）**：`HumanChallenge` 取消。核查发现验证码流程是"取校方验证码图片 → 交 `AhuTong.API.getCaptchaResult`（OCR 服务）识别 → 最多重试 5 次"，`ui/` 下没有任何人工输入验证码的代码。也就是说它只有一个适配器，按计划自身的接缝纪律（两个适配器才算真接缝）不应凭空造接口。将来登录流程要写契约测试时，该建立的接缝是 `CaptchaSolver`（生产 = OCR 服务，测试 = 假实现）。

验证：`:app:testDebugUnitTest` **57 套件 / 253 用例 / 0 失败**。

⚠️ 运行时验证待办：登录/续期属真机行为，合并前需在设备上回归（首次登录、会话过期后自动重登、登出）。

### P2 验证项（登录失败分类）—— 已完成 ✅

计划 §4 的验证条目要求"登录态在断网 / 密码错 / 协议变更三种情况下给出不同 `AhuError`"。核对结果是**没有达成**：密码错与校方改版都落到 `Server(-1, "登录失败")`，断网则把异常原文抛给 UI（用户看到的是 `Unable to resolve host ...`）。

| 改动 | 说明 |
|---|---|
| `data/crawler/login/CrawlerLoginFlow.kt`（新增） | 爬虫登录的协议动作与分类，返回 `Succeeded` / `WebVerificationRequired` / `CredentialsRejected` / `ProtocolChanged` / `Upstream` / `TransportFailure`。教务侧不再只有一个 `Failed`，而是记下"没有登录表单（改版）/ 响应为空 / 非 2xx / 凭据被拒" |
| `data/crawler/login/CaptchaSolver.kt` + `AhuTongCaptchaSolver.kt`（新增） | 上一批预判的验证码接缝：生产 = OCR 服务，测试 = 固定答案 |
| `AHURepository.loginWithCrawler` | 保留原生优先与 Cookie 同步，爬虫分支交给流程，每种结局映射到各自的 `AhuError`；归类不了的异常照旧抛出，不把程序错误伪装成"网络不可用" |
| `CrawlerLoginFlowTest`（新增，6 例） | 断网 / 密码错 / 校方改版 / 安大智慧业务拒绝 / 需要安全验证 / 成功（CAS 跳转回教务主页） |

写测试时暴露并修掉的两个隐形依赖：

- **Kotlin 默认参数的桥接方法**：`JwxtApi.device(...)` / `login(...)` 原先靠默认参数省略字段，而那个桥接是接口的**静态成员**——一碰就会初始化整套 Android 客户端（`CookieManager` → `AppEnvironment` → 测试环境直接失败）。现在协议字段显式写出：wire 契约在流程里可见，流程也能在 JVM 上跑。
- **`android.util.Log` 在单测里抛 "not mocked"**：登录流程每个分支都打日志，这正是它一直没被测试的原因。已打开 `unitTests.isReturnDefaultValues`（影响说明见该提交），让带日志的路径可被契约测试覆盖。

用户可见变化（**这就是需求本身**，不是顺手改）：密码错 → "用户名或密码错误，请重新输入"；校方改版 → 说明页面哪里变了；断网 → "网络连接失败，请检查网络后重试"。

验证：`:app:testDebugUnitTest` **61 套件 / 275 用例 / 0 失败**；`:app:lintRelease` 0 error；`:app:assembleRelease` 成功。

⚠️ 运行时验证待办：登录**成功路径**（真实校方页面 → CAS 跳转 → Cookie 回填）必须在设备上回归一次；分类已由 fixture 钉住，但真实页面结构仍需现场确认。

### P2 验证项（fake 适配器与"单测不联网"）—— 已核对

| 接缝 | fake 适配器 | 现状 |
|---|---|---|
| `BaseDataSource` | `MockDataSource`（debug / release 源集） | ✅ 早已存在 |
| `SecureBoxStoreCore` 的三个协作者 | `FakeSecureStore` / `FakeBoxStore` | ✅ 本轮新增（迁移链契约） |
| `CaptchaSolver` | `FixedCaptchaSolver` | ✅ 本轮新增（登录分类契约） |
| `SessionExpiryHook` / `CredentialVault` / `AhuSession` | 无 | ⚠️ 目前只有生产实现。它们的消费者是应用运行时与 Compose 界面，现在造 fake 没有测试会用到——按计划自己的纪律（出现第二个真实实现才抽接缝），这三个 fake 随 P3 的 feature 单测一起落地 |

"单测不联网"已核对：61 个套件全部跑在 JVM 上；涉及 HTTP 的三个套件一律指向 `MockWebServer`（loopback）；`ApkDownloadCancellationTest` 由应用层拦截器直接短路响应，不发起真实请求。

### P2 第 5 项（存储三分）—— 已完成 ✅

勘察结论：三档里的两档其实早已就位——**设置**在 `PreferencesManager`（DataStore），**凭据**在上一批已迁到 `CredentialVault`（Keystore）。真正混在业务缓存里的是**会话域数据**：身份（`current_user`）与原生服务的会话 Cookie（`rust_cookies_json`）——它们的丢失后果是"登出/原生接口立即失效"，与"缓存可重新拉取"完全不同。

| 改动 | 说明 |
|---|---|
| `data/session/SessionStore.kt`（新增） | 会话域唯一读写点：`currentUser` / `isLoggedIn` / `persistCurrentUser` / `clearPersistedCurrentUser` / `saveRustCookies` / `rustCookies`；**键名与 JSON 格式保持不变，无需数据迁移** |
| `AHUCache` | 移除 `getCurrentUser` / `isLogin` / `saveRustCookies` / `getRustCookies`；`save/clearCurrentUser` 保留为"委托给 SessionStore + 失效小组件槽位缓存"的薄封装（该副作用属于缓存层） |
| 调用点 | 18 个文件迁移到 `SessionStore`（身份读取 26 处、会话 Cookie 17 处） |

至此：设置 → DataStore；凭据 → Keystore；会话 → SessionStore；业务缓存 → AHUCache。四者的失败语义可以各自回答。

验证：`:app:testDebugUnitTest` **57 套件 / 253 用例 / 0 失败**。

⚠️ 运行时验证待办：身份与会话 Cookie 属真机行为（登录后重启、原生接口鉴权、登出清理），合并前需设备回归。


**最后一块（`eval_token`）**：评教服务令牌原先按用户分箱留在 `AHUCache` 里，现已迁入 `SessionStore`（与身份同层，因为它的丢失后果同样是"要重新登录"）。`SecureBoxStore` 增加分箱参数，分箱命名与"SecureStorage → Rust SDK → 旧 MMKV"迁移链都收口到一处，缓存层只做委托；键名与箱名保持不变，**无需数据迁移**。`EvaluationRepository` 读写令牌改经 `SessionStore`，并去掉对缓存层的 import。

验证：`:app:testDebugUnitTest` **57 套件 / 253 用例 / 0 失败**。

⚠️ 运行时验证待办（累积）：`eval_token` 的复用与跨重启持久化属真机行为。

**收尾两块**（本轮完成）：

| 改动 | 说明 |
|---|---|
| `data/security/SecureBoxStoreCore.kt`（新增） | 把"可信来源 → 原生 KV → 旧明文"的读取顺序与**边读边迁移**规则从 Android 依赖里剥离；`SecureBoxStore` 退化为纯装配。计划 §4 P2.3 明确要求的"迁移完成即清除明文"测试由此落地：`SecureBoxStoreCoreTest` 用内存 fake 驱动 8 条契约（明文被提升后删除、空值不写回但副本照样删、缺失值不动任何存储、写入不留旧副本、分箱命名与缓存层同一规则……） |
| `core/common/.../AppEnvironment.kt`（新增） | 三档存储依赖的 Context 从 `AHUApplication.getApp()` 挪到可安装、可 fake 的接口后面（详见下文"完成判据"表） |

⚠️ 运行时验证待办：迁移链现在有 JVM 契约测试，但**真实 Keystore / MMKV 行为**（首次迁移、删明文、解密失败不降级）仍需设备回归。

### P2 五项完成情况 vs 计划第 4 节的完成判据

| 计划完成判据 | 现状 | 说明 |
|---|---|---|
| `rg "AHUApplication.sessionExpired"` 为 0 | ✅ 0 | 仅剩 `AhuSessionState` 注释里的说明文字 |
| `TokenAuthenticator` 无业务 import | ✅ | 只依赖 `SessionExpiryHook` 接缝 |
| `AHUApplication.getApp()` 引用数从 20 降至 0 | ✅ 0（实际 22 处、11 个文件） | 新增 `AppEnvironment` 接缝：接口在 `:core:common`，生产实现在 `:app` 的 `Application.onCreate()` 首行安装，测试可安装 fake。静态字段与 `getApp()` 已彻底删除；未安装时立即抛错并指明安装点，而不是悄悄给出空 Context。这也是模块抽取的前置条件：`:data:*` / `:core:*` 从此不再依赖 `:app` 的 Application 类 |
| ^ | 同上 | 说明：代码引用为 **0**；仅 `AppEnvironmentHolderTest` 的 KDoc 里提到该符号（说明这条判据的由来），因此 `rg` 仍会命中一处注释 |

§7 的验收清单是项目**终态**（P5 收尾）而不是 P2 的交付物，其中已经达成的两条：

- `app/src/main/java/com/ahu/ahutong/data` 下 import `com.ahu.ahutong.ui` 为 **0 处**（R1 已由门禁强制）；
- `OkHttpClient.Builder` 的构造点只剩 `data/network/AhuHttp.kt` 一处（`CampusHttp.kt` 里是 builder 扩展，属于装配；loopback 的 `LocalServiceClient` 也走同一个工厂）；`Retrofit.Builder` 同样只剩 `data/network/AhuRetrofit.kt` 一处。§7 第 4 条"收敛为 1 个工厂 + 1 个 loopback 专用实例"至此达成，两条各由一个门禁（R8 / R9）守着。

结论：**P2 第 1–5 项全部完成**，每项都有编译与单测验证（当前基线 58 套件 / 260 用例 / 0 失败）。P2 作为整体的收尾——三个 Gradle 模块的实际抽取（`:core:network` / `:core:auth` / `:core:storage`）以及随之而来的 `getApp()` 归零——是计划里独立的下一步；它的前置条件（接缝先行、两个真实适配器、契约测试）现在已经具备。
结论：**P2 第 1–5 项与 §4 的验证条目全部完成**——三条完成判据全部达成，当前基线 **61 套件 / 275 用例 / 0 失败**，`:app:lintRelease` 0 error，`:app:assembleRelease` 成功。
结论：**P2 第 1–5 项与 §4 的验证条目全部完成**——三条完成判据全部达成，§7 的"网络构建点收敛"也随之达成（R8 / R9 两个门禁守着）。当前基线 **61 套件 / 275 用例 / 0 失败**，`:app:lintRelease` 0 error，`:app:assembleRelease` 成功。

P2 还剩一件事：**三个 Gradle 模块的实际抽取**（`:core:network` / `:core:auth` / `:core:storage`）。前置条件现在都具备了——接缝先行、两个真实适配器、契约测试、Context 不再依赖 `:app`——所以这一步是可控的；但它会改动 Gradle 结构与 Hilt 跨模块生成代码，仍属独立的一步，且按计划 §8"不为模块化而模块化"的取舍，需要先确认收益是否值得这次结构变动。

> 订正（2026-09-14）：上一段把三个模块的抽取记为"待决策"；P2.1 的 `:core:network` 已经做完（见下），`:core:auth` / `:core:storage` 的阻塞点也已查清并记录。

### P2 收尾 —— 第 1 刀：`:core:network` 已抽取 ✅（2026-09-14）

P2.1 的标题本身就是模块名，此前只落地了接缝。本轮把它做成真正的 Gradle 模块：`data/network`（AhuHttp / AhuRetrofit / CampusHttp / NetworkLogging）与 `data/crawler/net`（SessionExpiryHook / SessionRefreshCoordinator / AutoLoginInterceptor / TokenAuthenticator）共 8 个文件搬入，**包名不变**，因此调用点一处 import 都没改。

搬迁前必须先切断三条"网络层 → 业务层"的硬边：

| 硬边 | 处理 |
|---|---|
| `AutoLoginInterceptor` 直接写 `AhuSessionState` | `SessionExpiryHook` 增加 `onExpired()`：拦截器只通知，登录态由会话层写（`RepositorySessionExpiryHook.onExpired()`） |
| `SessionRefreshCoordinator.refreshIfNeeded` 成功后写 `markAuthenticated()` | 协调器只保留"请求代号"（generation + 互斥）；两个调用方（`RepositoryAhuSession.ensureFresh`、`TokenManager.refreshStoredSession`）各自写状态。语义等价：协调器原本也只在 `refresh()` 返回 true 时才写 |
| `campusCookies()` / `campusSessionRefresh()` 默认取 `CookieManager.cookieJar`、`RepositorySessionExpiryHook` | 改为显式参数，由 4 个第一方客户端注入 |

`NetworkLogging` 改读 `:core:network` 自己的 `BuildConfig.DEBUG`——library 的 debug 变体跟随宿主构建类型，release 仍然完全没有日志代码路径。

新增门禁 **R15**（`core/network` 不得 import app 内部实现与业务包），**并已验证它会拦**：临时给 `:core:network` 加一条 Gradle 允许、架构不允许的依赖（`:data:repository-index`）并 import 其类型，`ModuleBoundaryTest` 立即失败并指名 R15 与 `AhuHttp.kt`；撤销后变绿。顺带确认编译器本身也拦得住 `:app` 的 import——模块根本看不见那些类。

**这一刀还查出一个潜伏的破绽（已修）**：`7a9a2f61` 把 `RepositoryDeletion.kt` 搬进 `:feature:repository-index`，但它的测试留在 `:app`。`internal` 跨模块不可见，本地增量缓存让编译"看起来"通过，而**干净构建（也就是 CI）必然失败**。测试已移到被测代码旁边；CI 的单元测试命令也相应从 `:app:testDebugUnitTest` 改成 `testDebugUnitTest`，否则各模块自己的测试根本不会跑。

验证：`testDebugUnitTest`（全模块）**63 套件 / 274 用例 / 0 失败**（`:app` 61/267、`:core:network` 1/3、`:feature:repository-index` 1/4）；`CampusHttpAssemblyTest` 新增一例，用 MockWebServer 跑通"重定向 → 通知接缝 → 401"整条路径。`gradle/verification-metadata.xml` 仅 +3 行（一个构件），无删除、无修改。

### P2 收尾 —— 还剩两刀（`:core:auth` / `:core:storage`），阻塞点已查清

| 模块 | 阻塞点 | 需要的接缝 |
|---|---|---|
| `:core:auth` | `RepositoryAhuSession` 是 `object`，直接调 `AHURepository.loginWithCrawler`、`TokenManager.clear()`、`CookieManager.cookieJar.clear()`，并经 `SessionStore` 读 Keystore/MMKV | 登录动作端口（`SessionSignIn`）、本机残留清理端口、身份读取端口。不先反转这些，`AhuSession` 的 fake 造出来也没有测试能用它——这正是 P2 §4 那条"每个接缝都有 fake"至今没兑现的原因 |
| `:core:storage` | `SecureBoxStore` 直接用 `:app` 的 `RustSDK`；`AHUCache` 还用 `:app` 的 `BuildConfig` 与 crawler 模型 | 原生 KV 端口（对应计划 §3.2 的 `:integration:native-sdk`）。`AHUCache` 按计划只作为 `ProfileCache` 的实现留在 `:app`，不搬 |
| 两者共同 | `SettingsStore` / `ProfileCache` 的形态 | 通用 `String` KV 端口会把 16 个调用点全部改写，风险大于收益；按 `:data:repository-index` 的先例，端口应当是**被 feature 真正使用的那部分具名能力**，实现留在 `:app` |

**2026-09-14 更新：第 2 刀已落地**——`AhuSession` 的静态依赖反转完成。新增 `SessionSignIn` / `SessionAccount` / `SessionResidue` 三个端口（`CredentialVault` 本来就有），`RepositoryAhuSession` 由 `object` 变成可注入的类，生产装配收进一处（`DefaultAhuSession`）；`RepositorySessionExpiryHook` 也改为持有 `AhuSession` 实例，于是"网络层通知 → 会话层动作"这一跳同样可测。

`app/src/test/java/com/ahu/ahutong/testing/SessionFakes.kt` 集中了六个 fake（`FakeCredentialVault` / `FakeSessionAccount` / `FakeSessionResidue` / `FakeSessionSignIn` / `FakeSessionExpiryHook` / `FakeAhuSession`）。**P2 §4 的"每个接缝都有 fake 适配器"至此兑现**，而且每个 fake 都有测试在用（不留死代码）：`AhuSessionContractTest`（8 例）与 `SessionExpiryHookContractTest`（2 例）把 ADR 0002 的行为钉住——登录成功标记已认证、被拒时不动状态、安全验证算成功而非失败、登出清本机残留、没有账号或没有存过凭据时不打扰网关、续期用存储凭据且 `preferNative = false`、续期失败即过期而不重试、旧代号复用别人已完成的续期。

验证：全模块 **65 套件 / 284 用例 / 0 失败**（`:app` 63/277、`:core:network` 1/3、`:feature:repository-index` 1/4）。`:core:auth` 因此只剩"把 `SecureBoxStore` 的原生依赖反转成端口"这一件事。

### P2.3 的 `SettingsStore` 已落地（2026-09-14，随 P3 的第二个 feature 一起）

计划 §3.2 要求 `:core:storage` 暴露 `SettingsStore`，这一条现在兑现了——而且是**先有使用者、再定接口**：

| 改动 | 说明 |
|---|---|
| `:core:storage`（新增） | 只放 `SettingsStore` 与 `StartupThemePreferences`。接口是设置页已经在说的语义（`showQRCode`、`courseReminderEnabled`……），不是泛化的 String KV：泛化会把 16 个调用点全部改写，而键名与默认值本就属于实现 |
| `PreferencesManager` | 实现该接口：文件只多了一个父类型与 40 处 `override`，调用点一处没动 |
| `ProfileCache` | **暂不建**。它的使用者在课表与成绩两个 feature 里，现在造端口等于凭空发明需求 |
| 门禁 **R16** | `core/storage` 是叶子。已验证会拦：加一条 Gradle 允许、架构不允许的依赖，门禁立即指名规则与文件 |

`ProfileCache` 之外的存储侧工作（把 `SecureBoxStore` 对 `RustSDK` 的依赖反转、再把安全存储原语搬进模块）仍待做，理由不变：那一步动的是设备侧实现，收益要等 P4 的 `:integration:native-sdk` 一起评估。

### P3 第二个 feature（`:feature:settings`）—— 第一刀已完成 ✅（2026-09-14）

先做 ViewModel，界面随后：设置界面还依赖提醒调度与 app 资源，那两块要先立端口或搬家，属于下一刀。

| 改动 | 说明 |
|---|---|
| `:feature:settings`（新增） | `PreferencesViewModel` 搬入，协作方换成 `SettingsStore` + `PersonalizationSettings`。构造函数之外不再出现 DataStore、端侧运行时或任何 Android 类型（除 ViewModel 本身），11 条用例因此不需要设备 |
| `PersonalizationSettings`（`:data:personalization` 新增） | 上报一次设置变更、收掉建议、取消预取、清学习记录、读贡献状态；适配器 `BehaviorPredictionSettings` 留在 `:app` |
| 词汇一起搬 | `SemanticEventCatalog`（含 `MutationId`）、`BootstrapContributionStatus`、`ActionSource`——包名不变，`:app` 里的引用零改动 |
| `di/SettingsWiringModule`（新增） | 组合根是唯一同时认识两侧的地方 |
| 门禁 **R17** | feature 不得 import app 内部实现。已验证会拦 |
| `PreferencesViewModelTest`（11 例） | 两个端口全用 fake 驱动：界面读到什么、上报的旧值取自哪里、关个性化会收掉建议、关预取会连带清 wifi-only、wifi-only 不能在预取关闭时打开、Miuix 的默认色、启动镜像的两帧 |

ViewModel 的**公开面一字未改**，所以 `Preferences.kt` / `RepositorySettings.kt` / `MainActivity` 一处都不用动——这是「先搬不改」的收益。

验证：全模块 **66 套件 / 295 用例 / 0 失败**（`:app` 63/277、`:core:network` 1/3、`:feature:repository-index` 1/4、`:feature:settings` 1/11）；`:feature:settings` 的依赖图带来 87 行纯新增校验和。
**设置 feature 的进度与剩余范围**（2026-09-14 第二轮）：界面已搬入 `Preferences.kt` 与 `RepositorySettings.kt`，两者包名不变，所以 `Main.kt` 一处没改。剩余三块的阻碍都是具体的：

| 剩余 | 阻碍 | 需要的端口 |
|---|---|---|
| `Settings.kt`（设置枢纽） | ✅ **逻辑已切走**（第四轮）：9 步「清除所有数据」收成 `AppDataReset`，版本名与更新说明收成 `AppUpdateGateway`，两者由新的 `SettingsViewModel` 持有并对接 fake。剩下的是**文件本身**搬不动：更新那一行仍要调 `MainViewModel.checkApkUpdateManually`（它驱动主界面的更新对话框状态机，属 P4 的 `:data:update`），账户那一行仍要 `SessionStore` + `ScheduleViewModel` 的学年学期，另有 9 个字符串与 8 个 drawable 留在 `:app` | 更新流程（P4）、课表摘要读模型（P3 的 schedule / P4 的 `ScheduleReadModel`）；资源可整体搬或由宿主传入 |
| `License.kt` / `Contributors.kt` | ✅ 已搬入（第三轮）。两个 ViewModel 本就没有耦合（一个只依赖 `:core:model`，一个无外部依赖），直接搬；界面用的 7 个字符串里 5 个独占、进了 feature 自己的 `res/values/strings.xml`，剩下 2 个标题与枢纽页共享，改为**由宿主传入**——依赖方向因此是 app → feature 的 API，而不是 app → feature 的 R | — |
| 提醒的后台动作 | ✅ 已由 `CourseReminderControl` 收口（本次） | — |

**R17 的订正（重要）**：它的禁止清单里曾误含 `data.repository.`——那是 `:data:repository-index`，feature 本来就允许依赖；这个错误是搬 `RepositorySettings.kt` 时被门禁自己抓出来的。修正后要如实说明它的效力：R17 目前列举的包都在 `:app` 里，Gradle 的可见性会先拦下这些 import，所以它的作用是**把依赖方向写成可执行的规则**（哪天某个包被移进可达模块，它立刻生效），而不是当下能拦下某个可达违规。这与 R15 / R16 不同——那两个可以被真实触发（临时加一条 Gradle 允许的依赖即可复现）。

### P3 第三个 feature（`:feature:schedule`）—— 前置已就位（2026-09-14 第五轮）

课表是 P3 里最大的一块：`Schedule.kt` 1164 行、`ScheduleViewModel` 242 行，而且它的依赖散得比设置页广。这一轮先把**跨 feature 与跨层**的那几件事拆开，feature 模块本身与界面搬迁仍在后面。

| 改动 | 为什么必须先做 |
|---|---|
| `CourseReminderControl` 从 `:feature:settings` 搬到 `:core:common` | 课表变化后同样要重算提醒。端口长在某个 feature 里时，另一个 feature 够不着——feature 之间不允许互相依赖。`:core:common` 是 `UserNotice` 已经在用的位置：能力接缝的接口在核心层，实现留在 `:app` |
| 新增 `:core:auth`（接口半边） | 课表页只问「本机有没有登录用户」。P2 当初没建这个模块，是因为凭据实现要经 `SecureBoxStore` 到 RustSDK；但 feature 从不需要那一半。现在 `AhuSession` / `AhuSessionState` 搬进模块（包名不变，`:app` 零改动），`CredentialVault` 拆成接口（模块）+ `SecureCredentialVault`（`:app`），并新增 `SessionIdentity` 回答「存在哪个用户」——那是 `AhuSession.state` 从来没回答的半个问题 |
| 新增 `:data:schedule` | 课表的纯策略（`TeachingWeekPolicy`、`ScheduleSnapshotComparator`）带着各自的测试一起搬进来，第一次能在模块内被回归；`ScheduleSource` 端口定义数据入口（缓存 / 取到时间 / 拉取 / 后台刷新 / 下一学期 / 假数据开关），实现仍是 `AHURepository` |
| `ScheduleSectionTimes` 从 `ScheduleViewModel` 的伴生对象搬进 `:data:schedule` | 节次时间表的使用者从来不只是课表页：小组件、课前提醒、首页时间线、课程卡片都在 import 那个 ViewModel。这既让课表 feature 无法抽取，也让四处非界面代码依赖了一个界面类。现在它们 import 的是数据模块，`ScheduleTimeRangeTest` 跟着被测代码走 |

新增两条门禁：**R18**（`core/auth` 是 API 叶子）、**R19**（`data/schedule` 是 API 叶子）。

验证：全模块 **67 套件 / 304 用例 / 0 失败**（`:app` 60/266、`:core:network` 1/3、`:data:schedule` 3/11、`:feature:repository-index` 1/4、`:feature:settings` 2/20）；另外做过一次 `:app:clean` 的干净重建——类跨模块移动后第一次增量构建在 KSP 里报了过时状态错误，干净重建证明那只是缓存而非真的损坏。

**`:feature:schedule` 的进展（第六轮）**：周次/学期配置的端口补上了——`ScheduleWeekConfig` + `SemesterKey` / `ResolvedConfig`（后者原先嵌在 `CurrentWeekResolver` 内部，feature 连名字都拿不到）；`:app` 侧两个适配器分别转给 `AHURepository` 与 `CurrentWeekResolver` + `AHUCache`，由新的 `di/ScheduleWiringModule` 接线。`ScheduleViewModel` 随之改成 `@HiltViewModel`，四个协作方全是接口，并**第一次有了 fake 驱动的 12 条用例**（`app/src/test/.../ScheduleViewModelTest.kt`），其中两条是界面上看不出来的差别：刷新失败时不要重排提醒、本地已确认过就不再问远端。随后 ViewModel 搬入 `:feature:schedule`（**R20** 看守）。

搬之前顺手删掉一处死代码：ViewModel 用 `toUserMessage()` 把刷新失败翻成文案，而 `Schedule.kt` 只判断该字段是否为 null、并写自己的措辞——那句翻译从来没有被显示过。按 ADR 0001 规则 3（文案由 UI 层决定），字段改为携带 `AhuError`，最后一个 `:app` 依赖随之消失。

**已知待修（记在案）**：R14 与 R17 的禁止清单里还有 `data.session.` 这一条按包名的规则。`:core:auth` 建起来之后，这个包同时装着 feature 合法依赖的接口（`SessionIdentity`、`AhuSession`）与只属于 `:app` 的实现，因此按包名禁止是错的——R20 的第一次运行就是这么误报的，已改成按实现类点名。R14/R17 今天仍然通过，只是因为那两个 feature 还没 import 任何会话类型；设置枢纽页搬进来时就会用到 `SessionIdentity`，届时要一并订正。

**`:feature:schedule` 的进展（第七轮）**：界面的构件搬完了——`CourseCard` / `CourseCardSpec` / `CourseDetailDialog` / `ScheduleTextFormatters` / `ScheduleAccessibility` 共 409 行，只有课表页主体引用它们，因此是一个自包含的簇（`ScheduleAccessibilityTest` 跟着走）。包的路径不变，所以课表页一行 import 都没改。

依旧留在 `:app` 的那一处只有文件本身了（`Schedule.kt`，1164 行），它还需要三样东西：

| 阻碍 | 需要的接缝/动作 |
|---|---|
| `BehaviorPredictionRuntime` 的两个方法（`onContentStateChanged` ×2、`recordCommittedMutationAsync` ×4）与 `rememberBehaviorActionReporter()` | 行为上报的 Compose 接缝；`:data:personalization` 已有 `BehaviorRecorder` 与 `PersonalizationSettings`（后者名字偏窄，届时可考虑更中性的命名） |
| `R.drawable.ic_aiming` | 只有课表页用，搬进 feature 自己的 `res/`；另两个 `ic_config` / `ic_refresh` 来自设计系统，改用 `DesignSystemR` 即可（项目里已有先例） |
| `toUserMessage()` ×2（失败提示） | ADR 0001 说它应随展示侧搬走；feature 不能依赖 `:app`，因此要么搬进共享模块，要么由界面自己措辞（课表页的刷新失败提示本来就没用它） |

另外顺手修掉一处门禁误报隐患（见下）：R11 / R14 / R15 / R17 等 7 处规则按包名禁止 `data.session.`，而 `:core:auth` 建起来之后，这个包同时装着接口与实现——已改成按实现类点名，与 R20 的订正一致。

**`:feature:schedule` 已完成 ✅（第八轮）**：界面主体（1164 行）也搬进来了，模块现在装着 ViewModel、课表页、五个界面构件与无障碍测试。搬之前清掉最后两块：

- `AhuError.toUserMessage()` 从 `:app` 搬到 `:core:common`。ADR 0001 早就写明「等 P3 抽出 feature 模块时，它应随展示侧一起搬走」——四个 feature 都要用它，而没有一个能依赖 `:app`。13 个调用点改了 import，它仍然是**唯一**的翻译点（ADR 说这一点比位置更重要）。
- `ic_aiming` 只有课表页用，随界面搬进 feature；`ic_config` / `ic_refresh` 本来就在 `:core:designsystem`，界面改成显式写 `DesignSystemR`，不再依赖 app 的 R 恰好是传递的。

R20 这一轮又抓到我自己写错的一条规则（禁止 `import ui.screen.`，而 feature 自己的界面就在这个命名空间里——包名不变是每次搬迁的刻意选择），已删。

课表 feature 至此没有遗留项：小组件与提醒调度读的是 `:data:schedule` 的 `ScheduleSectionTimes` 与 `ScheduleSource`，不再 import 任何 ViewModel。

### P3 第四个 feature（`:feature:grade`）—— 第一刀：建议机制的端口（第九轮）

成绩页的 ViewModel（403 行）同时用了 `BehaviorPredictionRuntime` 的两类能力：记录发生过什么（已由 `BehaviorRecorder` 覆盖）与**本地预设建议**。后者的词汇原本嵌在 `PresetRankingEngine`（781 行、带推理与存储）里，界面想说出「一个候选」就得依赖引擎所在的模块。

这一刀：6 个词汇类型（`PresetCandidate` / `PresetSubmission` / `PresetInteractionToken` / `AppliedPreset` + 两个枚举）搬进 `:data:personalization`（包名不变，引擎只少 55 行声明）；新增 `PresetSuggestions` 端口（rank / markExposed / apply / expire / recordNaturalSubmission）与 `:app` 适配器 `AppPresetSuggestions`，绑定在 `PredictionBindingModule`；`GradeViewModel` 改为依赖两个端口。

它**还不能搬**：数据侧仍直接用 `AHURepository`、`AHUCache`、`CurrentWeekResolver` 与 `SessionStore`。下一步是按 `:data:schedule` 的先例建 `:data:grade` 放成绩数据端口（成绩本身、绩点排名、学生档案、按档案缓存的成绩与假数据开关），学年学期那部分可以直接复用已存在的 `ScheduleWeekConfig`。

**`:feature:grade` 第一刀完成（第十轮）**：`GradeSource`（成绩、绩点排名、学生档案、按档案缓存与假数据开关）落在新的 `:data:grade`，`:app` 适配器转给 `AHURepository` 与 `AHUCache`，门禁 **R21** 看守；`GradeViewModel` 随之只依赖五个端口，并搬进 `:feature:grade`（**R22** 看守），`GradeTermSelectionPolicy` 与它的测试一起走（纯策略、同模块，因此 `internal` 不必放宽——四次搬迁里第一次）。学年学期没有重复造端口：它复用 `:data:schedule` 的 `ScheduleWeekConfig`。

成绩界面（`Grade.kt`，567 行）与考试界面（`Exam.kt`，558 行）还在 `:app`；考试是否并入本 feature 尚未决定（计划里只写了 `:feature:grade`）。

**`:feature:grade` 第二刀（第十一轮）**：界面（`Grade.kt`，567 行）也搬进来了。补上的最后两块前置：`GradeSource.mockRefreshRevisions()`（调试用的 mock 刷新信号——`MockScenarioController` 是 debug/release **按变体分源集**的一份同名实现，这正是不可能把它搬进模块的原因，所以把信号放到端口上）；`R.string.grade` 只有这一处用，随界面搬进 feature 自己的 `strings.xml`。界面那唯一一处行为上报改经 ViewModel（`onManualRefresh()`），与课表页的处理一致。

**考试页（`Exam.kt`，558 行）待定**：它需要自己的数据端口（考试缓存与刷新）、`ExamViewModel` 的端口化、`RefreshState` 与 `ExamDistanceBucket` 两个类型的归位，以及三处行为上报改经 ViewModel。工作量与成绩页相当；建议并入 `:feature:grade`（两者共用学期与档案概念），但这是个可以晚一步再确认的选择。

**考试页并入 `:feature:grade`（第十二轮）**：按上一轮的建议执行，不再为它单开模块。它带来的三处结构调整值得记下：

1. **假数据信号独立成 `MockDataSignals`**（`usesMockData` + `mockRefreshRevisions`），`GradeSource` 与 `ExamSource` 都继承它。它的实现是按构建变体分源集的（debug / release 各一份同名实现），端口因此是界面唯一能看见它的方式。
2. **`ExamDistanceBucket` 从 `PredictionInput` 搬进 `:data:personalization`**：界面想上报"下一场考试还有多远"，不该因此依赖特征向量所在的模块。`BehaviorRecorder` 同时新增 `recordExamDistance`。
3. **`ExamViewModel` 的三处行为上报改经 ViewModel**（两次手动刷新 + 一次考试距离），与成绩页、课表页一致。`RefreshState`、`ExamRefreshPolicy` 与它的测试随 ViewModel 一起走（同模块，`internal` 保住）。

搬字符串时抓到一处**会改变用户可见文案**的错误：app 里 `exam` 的值是「考场查询」，我按语义写成了「考试安排」。已改回原值——搬资源必须逐字对齐，不能照着语义重写。

### P3 第五个 feature（`:feature:xuexiaotong`）—— 第一刀：数据层成模块（第十三轮）

计划里点名了 `:data:chaoxing`，而学习通的数据层正好够格：7 个文件只依赖 `:app` 之外的一件事——`:core:network` 的 `AhuHttp` 工厂。

结果是一次**纯重命名式的搬迁**（git 对 7 个文件全部识别为 100% 相似），包名不变，所以界面的 8 个文件、`reminder/` 的 3 个文件与两个测试一处 import 都没改。`WorkDeadlineParserTest` 跟着被测代码进了模块，门禁 **R23** 看守。计划里的 `ChaoxingGateway` 窄接口**故意没建**：此刻没有任何 feature 之外的使用者，接口应由使用者定义。

**学习通第二刀（第十四轮）**：ViewModel 端口化。它原先同时拿着 `ChaoxingApi` 与一个 **Context**（VM 持有 Context 是反模式，而且这个 Context 只被提醒调用用到），并由界面用 `LocalContext.current` 手工装配工厂。三个端口替掉了这两样：

| 端口 | 覆盖 |
|---|---|
| `ChaoxingSession` | 登录态、账密登录、清会话、静默重登、同步作业与课程进度（进度从 API 的监听器接口改成普通 lambda） |
| `ChaoxingStore` | VM 用到的十六个读写，按"存的是什么"命名而不是照抄 `Store` 的 getter 名 |
| `ChaoxingReminders` | `cancelAll` / `scheduleAll` / `rescheduleAll` / `sendTest`——那四个命令正是 Context 的真实用途 |

44 处调用点机械替换，两处进度监听器手工改成 lambda；VM 改为 `@HiltViewModel`，界面用 `hiltViewModel()` 取，工厂类删除。登录界面仍直接用 API——那是本 feature 的下一块，之后才是界面搬迁。





另外记一笔流程上的失误：`1442402c` 的提交信息里写了「新增门禁 R21」，而那一条其实漏加了——下一笔提交 `90077724` 把规则补上并明确说明了这一点。提交信息描述的必须是已经存在的改动。







这三项是 P2 的最后一段，纪律与 P3 的 feature 抽取相同：先立端口，再搬模块。

**阶段收尾验证（§6 要求 lintRelease + assembleRelease）**：已在本机跑完（2026-09-13）。

| 检查 | 命令 | 结果 |
|---|---|---|
| 单测 | `:app:testDebugUnitTest` | 58 套件 / 260 用例 / 0 失败 |
| 静态检查 | `:app:lintRelease` | BUILD SUCCESSFUL |
| Debug 构建 | `:app:assembleDebug` | BUILD SUCCESSFUL（`app-debug.apk` 41.9 MB，供真机回归用） |
| 发布构建 | `:app:assembleRelease` | BUILD SUCCESSFUL，产出 `app-release-unsigned.apk`（本机无 `keystore.properties`，因此未签名；R8 已实际运行） |

`lintRelease` 首跑抓出 **6 处 `SuspiciousIndentation`**，全部是本轮错误模型迁移（`edb56f42` / `4175366d`）留下的缩进错位：`ElectricityDepositViewModel` 4 处、`GradeViewModel` 1 处、`LostFoundViewModel` 1 处。它们不影响编译与运行（Kotlin 按花括号而非缩进解析），但会让 CI 的 lint 步骤失败，因此已修复——修复是纯空白改动（`git diff -w` 为空），字节码与行为完全不变。这条也说明：`lintRelease` 值得进每个 PR 的门禁清单，而不是只在阶段收尾跑。

仍未完成的验证是**五条主流程的真机回归**（登录 / 课表 / 充值 / 学习通 / 更新），需要设备；本机 `adb devices` 为空。

### 本地验证环境阻塞（需要你决策）

### P3 逐个抽出 feature 模块 —— 第一个（:feature:repository-index）已完成 ✅

这是第一个真正抽出去的界面 feature，也是第一次出现"**不依赖设备就能测的 ViewModel**"。它的前置在三轮里陆续就位：设计系统模块（P1 收尾）、行为上报端口（:data:personalization）、仓库索引端口（:data:repository-index）。

| 改动 | 说明 |
|---|---|
| :feature:repository-index（新增） | 浏览页、下载页、路由与 ViewModel；**包名不变**，所以宿主的 import 一处没改 |
| RepositoryViewModel | 去掉 AndroidViewModel、全局 RepositoryManager、Toast 与 FileProvider/Intent 代码：改为注入 RepositoryIndex + RepositoryFileAccess，提示走一次性 StateFlow 由界面消费。这正是它能被 JVM 测试的原因 |
| AndroidRepositoryFileAccess（:app） | 承接内容 URI 读取、FileProvider 授权与系统查看器打开；用**带类型的返回值**把失败交给 ViewModel，文案不变 |
| di/RepositoryWiringModule.kt（:app） | 装配点：数据层的 ManagerRepositoryIndex 与界面侧的 AndroidRepositoryFileAccess 只有组合根同时认识。**放进 data/ 会被 R1 拦下**（data 不得依赖 ui）——门禁确实在做事 |
| IO 调度器限定符 | 生产注入 Dispatchers.IO，测试注入测试调度器，用例因此完全确定，不必和真实 IO 抢时序 |
| 门禁 **R14** | feature 不得 import app 内部实现（dao / crawler / session / security / server / sdk / 个性化运行时 / AHUApplication……） |
| RepositoryViewModelTest（7 例） | 用 FakeRepositoryIndex + RepositoryFileAccess 驱动：命中缓存不取网络、刷新与排序、失败回落缓存、无缓存时报错、下载、打开、markdown 被删 |

保留在 :app 的：RepositorySettings.kt——它通过 app 的 PreferencesViewModel 改设置，属于第二个 feature（:feature:settings）的范围。

模块现状（7 个）：:app、:core:model、:core:common、:core:designsystem、:data:personalization、:data:repository-index、:feature:repository-index。

验证：:app:testDebugUnitTest **63 套件 / 273 用例 / 0 失败**（R1–R14 全绿）；:app:assembleRelease 通过。

⚠️ 安全相关：gradle/verification-metadata.xml 增加 97 行校验和（纯新增），来自 feature 自己的依赖图。

下一个：:feature:settings（计划的风险顺序），随后是 schedule / grade / 学习通 / recharge（支付单独灰度）。

### 与 master 合流（2026-09-13）

上游 master 在本轮推进期间前进了两笔：APK 分片下载器删除（`0492acc0`）与历史校历浏览（`9365d74d`）。合并只有 5 个文件冲突，但性质不同：

| 冲突 | 处理 |
|---|---|
| `BaseDataSource` / `AHURepository` / `CrawlerDataSource` / `SdkDataSource` / 两个 `MockDataSource` | 校历功能是用**旧的 `AHUResponse`** 写的，而本分支已删除该类型——所以不是"选一边"，而是把这批新接口移植到 `AhuResult` / `AhuError`（HTTP 失败保留真实状态码，旧代码一律折叠成 `code = -1`） |
| `SchoolCalendar.kt` | 取 master 的版本（年份目录 + 选择器），只把两处仓库读取改成 `valueOrNull()` |
| `AhuTong.kt` | 保留本分支的 Retrofit 工厂 import；`@Header` 的 import 随 master 删除 Range 请求一并消失 |

这次合流顺带印证了 P2 的取舍：**统一错误模型把"上游新增一个功能"从"逐个判断该返回什么类型"变成了一次机械移植**。

测试基线：275 → 265（master 删掉的 12 个分片下载用例 + 新增的 2 个校历策略用例）。顺带清理了 `proguard-rules.pro` 里已删除的 `AHUResponse` keep 规则。

验证：`:app:compileDebugKotlin`、`:app:testDebugUnitTest`（61 套件 / 265 用例 / 0 失败）、`:app:assembleRelease` 全部通过。

本机 `~/.gradle/init.d/aliyun-mirrors.init.gradle.kts` 会用 Aliyun 镜像替换仓库，并把 JitPack 声明为 `exclusiveContent`。实测结果是两个 JitPack 依赖（`com.github.franmontiel:PersistentCookieJar`、`com.github.Kyant0:Monet`）在本机始终解析失败：Gradle 既不查缓存也不发网络请求（debug 日志中 0 条 jitpack 记录），因此 `:app` 的编译与单测无法在本地完成。CI（GitHub Actions，不加载该 init 脚本）不受影响。

**2026-09-13 更新：绕行方法已稳定可用**——本次全部验证（编译 + 58 套件 / 260 用例 / 0 失败）都是这样跑出来的：

```powershell
$f = 'C:\\Users\\MuxYang\\.gradle\\init.d\\aliyun-mirrors.init.gradle.kts'
$code = 1
try {
  Rename-Item -LiteralPath $f -NewName 'aliyun-mirrors.init.gradle.kts.bak' -ErrorAction Stop
  .\gradlew --no-configuration-cache :app:testDebugUnitTest *> build\verify.log
  $code = $LASTEXITCODE
} finally {
  if (Test-Path -LiteralPath ($f + '.bak')) { Rename-Item -LiteralPath ($f + '.bak') -NewName 'aliyun-mirrors.init.gradle.kts' }
}
"gradle exit=$code"; exit $code
```

三个必须注意的点（都实际踩过）：

- **`exit $code` 不能省**：`try/finally` 的退出码取自最后执行的那条命令，恢复文件名的动作会把 Gradle 的失败掩盖成成功——第一次就是这样误判成"通过"的。判断是否真的通过，看日志里的 `BUILD SUCCESSFUL`，不要只看退出码。
- **必须带 `--no-configuration-cache`**：该项目的 configuration cache 与 AGP 不兼容。
- 若某次运行被中断，`init.d` 下可能残留 `.bak`，需要手工改回原名；否则后续所有构建都会走镜像，重新变成依赖解析失败。

诊断结论（供修复参考）：

- 用隔离 GRADLE_USER_HOME 时，配置阶段又会出现插件类路径缺失——该项目的插件解析依赖 init 脚本里的镜像配置，因此"换 home"不是可行绕行。
- 绕开镜像的临时手段已写成 `<scratch>/fix-repos.init.gradle`（在 `beforeSettings` 中延迟注册 `settingsEvaluated`，最后覆盖依赖仓库为 mavenCentral + google + jitpack）。它能修正"仓库被替换"的问题，但**修不了这两个 JitPack 依赖**：实测即使把仓库换成项目自身的声明、并清空 `caches/modules-2/metadata-2.107`，Gradle 依然不发请求直接判定 not found。
- `:core:model` 的编译验证是在**默认 Gradle home** 下完成的（该模块只依赖 Maven Central 的 androidx.annotation 与 gson，不涉及 JitPack）。
- 排查过程中修改过的缓存：`~/.gradle/caches/modules-2/metadata-2.107`（可再生的依赖描述缓存，已由后续构建自动重建）、以及停止过若干 Gradle daemon；未改动你的任何配置文件。

---

### P1/P2 收尾与 P3 完成（2026-09-14 第十五～二十二轮）

这一段把 P1/P2 的遗留项与 P3 的最后一个 feature 一起做完了。

**P1/P2 遗留项**（“有没修的接着修完”）：

| 项 | 处理 |
|---|---|
| R3 / R5 按包名整包禁止（`data.` / `ui.`），而模型与设计系统早已搬进 :core:* | 改成点名实现所在的包；三条失效的 allowlist 条目随之删除（清单从 7 条降到 2 条） |
| R23 仍按包名禁止 `data.session.` | 与 R14 / R17 / R20 / R22 对齐，按实现类点名 |
| `:core:auth` 缺“安全存储原语” | `SecureStorage`（AES-GCM + Keystore）搬进模块，文件与包名不变，调用点零改动。`SecureBoxStore`（Rust SDK KV + 旧 MMKV 迁移链）与 `SecureCredentialVault` 仍留 :app：在现场反转那两处依赖，在盒子搬走之前没有使用者，归属等 P4 的 `:integration:native-sdk` |
| `ProfileCache` | 维持“暂不建”：仍没有 feature 需要它 |

**P3 的最后一个 feature（`:feature:recharge`）**：

- 新增 `:data:recharge`：ycard 的协议词汇（11 个 DTO）与签名辅助按原包名搬入，`PayState` 一并搬来；四个端口 `CardRechargeSource` / `BathroomDepositSource` / `NetworkRechargeSource` / `ElectricityDepositSource` 都由使用者定义（端点返回响应体文本或 `RechargeCall`，协议顺序与解析留在调用方）。
- 四个 ViewModel 全部端口化并搬进 `:feature:recharge`；四个界面（校园卡、浴室、网费、电费）与共用的 `SecurePaymentPasswordDialog` 随后一起搬入。行为上报一律改经 ViewModel（`onRechargeSubmitted` / `onPaymentSubmitted`），手机号、充值方式与房间选择经端口读写。
- 门禁 **R25**（`:data:recharge` 是数据叶子）与 **R26**（`:feature:recharge` 不得伸手进 :app）在这一轮各抓到一次真事：协议客户端必须先有落点（`:app` 的 `data/adapter`），R2 的豁免条目也随之一条条消失——现在清单里只剩 `JwxtWebLogin` 与 `FreeClassroomViewModel` 两条。
- 键盘设置走新的窄接口 `PaymentKeyboardSetting`（`:core:storage`，一个成员）：付费页只问这一个问题，不该为了测试去实现近四十个成员的 `SettingsStore`。
- `MockDataSignals` 从 `:data:grade` 搬进 `:core:common`：成绩、考试、充值三处都要它，而 feature 之间不能互相依赖。

**`:feature:settings` 的枢纽页也搬完了**（P3 的最后一个卡点）：页面先长出宿主外壳（`SettingsHub`，住 :app），把导航目标、更新检查、账户名与学年学期摘要、调试开关、应用名与图标、许可/贡献入口标题全部由宿主传入；随后页面连同 6 个字符串与 8 个 drawable 搬进 feature，R17 通过。

**外部审查（gpt-5.6-sol, xhigh）与修出的真实缺陷**：

- **[P1] 续期成功却把刚建立的 Cookie 一起清了**：`RepositoryAhuSession.ensureFresh` 调 `residue.clear()`，而生产实现会清 `TokenManager` **与** `CookieManager.cookieJar`；master 的 `TokenAuthenticator` 只清派生令牌。重试要用的正是刚建立的 Cookie，清掉它会让重试立刻再失败。已把 `SessionResidue` 拆成 `clearDerivedToken()`（续期）与 `clear()`（登出/清数据），契约测试同步钉住（续期只清令牌；失败的续期什么都不清）。
- 审查的其余 P2 项记录在案，尚未处理：单次续期没有总超时（ADR 0002 要求）；`AhuSession.state` 在生产环境无人读取（“单一真相”名不副实）；`SettingsStore` 未实现 ADR 0003 的“读失败回默认值、写失败重试一次”；`:feature:grade` 只有策略测试、没有 ViewModel 契约测试（P3“每个 feature 自带 fake 驱动的 JVM 测试”这一条还差它）；学习通登录界面仍直接用具体存储；R4 有一条永远匹配不上的类名规则。

验证基线：**70 套件 / 338 用例 / 0 失败**（新增学习通 ViewModel 的 8 例与充值域的 20 例）。真机回归仍未做（本机无设备）。

### P3 完成（2026-09-14 第二十三轮）

六个 feature 模块全部抽出：`repository-index` / `settings`（含枢纽页）/ `schedule` / `grade`（含考试页）/ `xuexiaotong` / `recharge`。最后补齐的两块：

- **grade 的 ViewModel 契约测试**（`GradeViewModelTest` 8 例、`ExamViewModelTest` 6 例，配 feature 自己的 fakes）。P3 那条「每个 feature 自带 fake 驱动的 JVM 测试」至此对六个 feature 都成立——repository-index / schedule / recharge 的 ViewModel 测试沿用早先放在 `:app` 测试源集的做法（fakes 集中在那里的约定）。
  写这两个套件时纠正了我自己的两处误解：没有登录用户又没有假数据时成绩页**打不开**（学年列表由登录身份算出，构造即抛）；手动刷新**不会**把缓存推上屏，它只留下原本显示的内容。
- **学习通登录不再绕过 ViewModel**：凭据保存随登录一起进了端口实现（`AppChaoxingSession`），界面只调 `viewModel.login(...)`；`XuexiaotongScreen` 因此不再需要宿主传会话，`Main.kt` 里的 Chaoxing 入口点删除，R24 增加「禁止具体 Store」一条。

阶段收尾验证（§6 要求）：

| 检查 | 命令 | 结果 |
|---|---|---|
| 单测（全模块） | `testDebugUnitTest` | **72 套件 / 354 用例 / 0 失败** |
| 静态检查 | `:app:lintRelease` | BUILD SUCCESSFUL（需 `-Dorg.gradle.jvmargs=-Xmx4096m`：2G 堆下 lint 会让 daemon 因 GC 抖动退出） |
| 发布构建 | `:app:assembleRelease` | BUILD SUCCESSFUL，`app-release-unsigned.apk` 14.9 MB（R8 已运行） |

仍未完成、且不属于 P3 代码交付物的：五条主流程的真机回归（登录 / 课表 / 充值 / 学习通 / 更新，本机无设备）；支付路径的单独灰度（发版动作，需真机验证后才谈得上）；审查列出的 P2 项（续期没有总超时、`AhuSession.state` 无人读取、`SettingsStore` 的读写失败策略、R4 那条永不匹配的规则）留给 P4/P5。

### P4 完成（2026-09-14 第二十四～二十七轮）

**`:data:update`（三刀）**：先把「谁都不能绕过」的两块搬进模块——更新元数据的策略（URL 可信、摘要形状、版本单调）与安装前校验（体积、路径可信、包名、版本、签名指纹，原先在 Activity 的私有方法里）；再把下载器从 MainViewModel 抽出（`DefaultApkDownloader` + `ApkDownloadApi`：独立 dispatcher、逐跳校验重定向、200MB 上限、part 文件 sha256、原子重命名与校验过的复制兜底、镜像建议；日志字段与用户文案按 P4 的原样保留——审查发现搬迁时丢过三处诊断信息（入口标签、镜像标记、断点状态），已在同一天的修复里补齐）；最后把版本检查收成一个（原先启动检查与手动检查各写一遍）三态结果 `UpdateCheck`。门禁 **R27**，新增 11 例测试（完整性规则、下载器两条源码级约定、检查分类）。

**`:background`**：提醒包、课程提醒通知包与小组件全部搬入。过程中消掉的正是这一阶段要消的耦合：`PreferencesManager(context)` → `SettingsStore`；`CurrentWeekResolver` 与学期缓存读 → 新的只读接缝 `ScheduleReadModel`（三条读、零写、零网络）；`Intent(context, MainActivity::class.java)` → 包管理器给出的启动 Intent；小组件布局随码搬。门禁 **R28**（后台只许读）。

**一处刻意的行为变化**：Glance 小组件原先在没有缓存课表时会回退到仓库拉取（会走到登录与网络），现在渲染空——这正是本阶段的验收标准：**小组件不得成为触发登录的东西**。

**个性化收窄**：`SmartSuggestionHost`（Compose 宿主）搬出领域层到 `ui/suggestion`；顺带删掉 `BehaviorActionReporter`——充值那轮把付款页的上报改经 ViewModel 之后，它已经没有任何调用点。

**范围修正（有证据）**：计划里的 `TelemetrySink` 没有建。核对结果是非个性化层今天没有任何地方需要上报遥测——遥测的调用点全在层内（`TelemetryUploader` / `ModelQualityTelemetryManager` / 其 Worker），而模块对外已经只暴露 `BehaviorRecorder` + `PresetSuggestions` + 词汇表。凭空造一个没有使用者的接口违反本项目「接口应由使用者定义」（与 `HumanChallenge` 的取消同一条纪律）；等真出现第二个使用者再抽。

**静默降级的证据**（计划要求：模型损坏、训练失败、遥测失败都不得影响主流程）：

| 失败面 | 现状 |
|---|---|
| 遥测 | `TelemetryUploader` 每个网络调用都包 `runCatching`；Worker 整体 `runCatching` 后 `Result.retry()`——失败只是下次再来 |
| 模型损坏 | `ModelStateStore.read()` 的解码（含迁移日志恢复）包在 `runCatching` 里，坏文件当「没有模型」 |
| 训练失败 | 运行时作用域是 `SupervisorJob + CoroutineExceptionHandler`：记日志并写进诊断，单切片失败既不波及兄弟协程，也不冒泡成崩溃 |

验证：**74 套件 / 365 用例 / 0 失败**（每个切片后都跑），依赖校验新增 369 行纯新增校验和。真机回归（登录 / 课表 / 充值 / 学习通 / 更新 / 小组件冷启动）仍未做——本机无设备。

## 0. 一页摘要
### P3 判据补齐、P4 审查修复与 P5 补记（2026-09-14 第二十八～三十轮）

**P3 的判据落到实处（「每个 feature 自带 fake 驱动的 JVM 测试」）**

上一轮把这条记成「对六个 feature 都成立」，但 repository-index / schedule / recharge 的 ViewModel 测试仍在 `:app` 测试源集里——那种状态下 `gradlew :feature:x:testDebugUnitTest` 根本跑不到该模块自己的行为。这一轮把它们连同 fake 一起搬回各自模块：

| 测试 | 从 | 到 | 备注 |
|---|---|---|---|
| `RepositoryViewModelTest` | `:app` | `:feature:repository-index` | 它本来就用假端口，搬完即可跑 |
| `ScheduleViewModelTest` + `ScheduleFakes` | `:app` | `:feature:schedule` | fake 按模块各带一份（与 grade / settings 同一约定） |
| 浴室与校园卡两个 ViewModel 测试 + 四个 fake | `:app` | `:feature:recharge` | 模块补上 `isReturnDefaultValues`：缴费流程经过 LiveData 与 `android.util.Log`，缺了它会以 `Method getMainLooper in android.os.Looper not mocked` 失败 |
| `ElectricityControllerTest` | `:app` | `:core:model` | 测试跟着被测代码走 |
| `CardPayRequestTest`、`BathroomPaymentRequestTest` | `:app` | `:data:recharge` | 同上；前者顺带把包名从 `ui.screen.main` 改成 `data.crawler.model.ycard` |

新增守卫 `FeatureTestOwnershipTest`：模块清单从 `settings.gradle.kts` 读（新增 feature 自动纳入），两条规则——每个 `:feature:*` 必须有自己的测试源集与至少一个 `*Test.kt`；`:app` 的测试源集里不许出现住在 feature 模块里的 ViewModel 的测试。

**P4 外部审查（gpt-5.6-sol, xhigh）与修复**

审查的证据链完整，五项全部有据。按严重度记录处理方式：

| 级别 | 发现 | 处理 |
|---|---|---|
| P1 | 小组件冷启动**仍会走网络并写缓存**：`provideGlance` 调 `ScheduleWeekConfig.resolveLocalFirst()`，本地未经今天确认时它去问教务并写学期状态，而那个客户端带着校园自动登录与会话续期 | 小组件改读 `ScheduleReadModel.cachedConfig()`；入口点不再发放 `SettingsStore`（带写入口）与 `ScheduleWeekConfig`（会问远端），换成 `CourseReminderSettings`（两条提醒开关）+ `ScheduleReadModel`；`ScheduleReadModel` 增加 `cachedSemesterKey()` 顶替提醒排期原先从 `ScheduleWeekConfig` 取的那一项；R28 追加两条 import 禁令与 `resolveLocalFirst\|syncRemoteConfig` 的内容正则 |
| P1 | 强制重下已把本地包删掉，界面仍认为「可安装」 | `startApkDownload` 在 `forceRedownload` 时同步清掉 `apkLocalReady`（迁移前的代码本来就这么做，搬迁时漏了） |
| P2 | 镜像提示读的 `lastProgress` 从不复位，上一次下载的进度会让下一次的提示永不出现 | 两次尝试入口都复零。**没有**补事件级测试——下载器缺测试缝（见「已知偏差」第 3 条），因此这一条按「读代码验证」记录 |
| P3 | 「日志与文案逐字不变」不成立：起始日志丢了 `mirror` 与断点字段，启动检查与手动检查共用同一个失败标签，手动检查的摘要标签也变了 | 新增 `UpdateCheckEntry`（STARTUP / MANUAL）携带两个入口各自的标签，检查规则仍只有一份；两处起始日志补齐 `mirror=` 与 `partExists` / `partBytes`；新增用例钉住「两个入口分类一致」 |
| P3 | 权威文档仍在承诺不存在的 `TelemetrySink` 与 `SuggestionProvider` | `CONTEXT.md` 与本计划 §3.2 改为 `BehaviorRecorder` + `PresetSuggestions` + `PersonalizationSettings`，并注明 `TelemetrySink` 是刻意未建及其理由 |

**P3 审查遗留的 P2 项**

| 项 | 处理 |
|---|---|
| R4 有一条永远匹配不上的规则（`AHURepository.` 末尾带点，只能匹配嵌套类） | 去掉末尾的点，两种写法都覆盖 |
| 单次续期没有总超时（ADR 0002 明确要求） | `SessionRefreshCoordinator.refreshIfNeeded` 用 `withTimeout` 包住续期，超时即当失败（不重试、不推进代号）；预算走参数（默认 30 s），`SessionRefreshTimeoutTest` 两侧都钉 |
| `SettingsStore` 未实现 ADR 0003 的读写失败策略 | 策略落进 `:core:storage`（`fallbackToDefaultOnReadFailure` / `retryOnceOnWriteFailure`），`PreferencesManager` 的 18 处读、19 处写全部接线；`SettingsFailurePolicyTest` 四条用例（含两个反例） |

**P5 补记**（代码此前已提交，这里补上执行记录）：`AHUApplication.startLocalService()` 与未用 import 删除；`ui/component`（1 文件）与 `ui/components` 的双包合并完成（那个文件现在住在 :feature:recharge 的 `ui/component`，:app 只剩 `ui/components`）；`ui/components/PayCapsuleConfirmButton.kt` 零引用，按 P5 的待决事项删除；`boundary-allowlist.txt` 清空（末两条 R2 豁免由 `FreeClassroomGateway` 与浏览器 UA 收口消掉）；CI 增补模块边界测试、密钥扫描（Bugly appid 移入 `buildConfigField`）与依赖锁（21 份 `gradle.lockfile`，现已覆盖 debug / test / lint 配置）；`NetworkLogging` 的四条脱敏与 release 早退由 `NetworkLoggingTest` 钉住（§7 第 8 条的守卫；脱敏后的输出在单测里拿不到，因此级别映射按行为断言、脱敏清单按源码形状断言）。

**第二次审查（P5 阶段，gpt-5.6-sol xhigh）与修复**

范围 `b59033d9..f5217340`，即 P5 的两个提交加上本轮 P3 补齐与 P4 修复。审查确认无误的几处：小组件的显式网络路径已断、强制重下会清「可安装」标志、`lastProgress` 两侧复位、更新日志标签与 `b5064a39` 一致、搬走的测试语义未变、设置写入当前都幂等（没有重复生效的风险）。

| 级别 | 发现 | 处理 |
|---|---|---|
| P1 | 续期超时没兑现「一次会话失效只自动续期一次」：等锁的每个请求都会各自再登一次 | 协调器记住失败的那一代，同一代的后续请求直接拿到失败；成功登录（`RepositoryAhuSession.signIn`）清掉记忆；两条新用例钉住两侧 |
| P1 | 读失败回落到空设置，会把「个性化/预取已关」读成开、把「仅 Wi-Fi」读成关（本轮引入的隐私回归） | 三个开关改成「先映射、失败给保守值」：个性化关、预取关、仅 Wi-Fi 开；文件正常但没有该项时仍走原默认值 |
| P2 | `cancel()` 打断不了换源：ViewModel 已销毁，镜像下载还会自己开始 | 整个换源过程挂在被追踪的任务上 |
| P2 | 主站在 join 期间已完成时，镜像仍会重下一遍并覆盖已验证的包 | join 后先校验本地包，命中即报「已本地」并收手（迁移前的行为） |
| P2 | R28 挡不住学习通客户端与凭据写入 | R28 增加 `ChaoxingApi` / `Store` 的 import 禁令与凭据、登录调用的内容围栏；后台改用窄端口 `ChaoxingReminderStore`（只含作业、日程、提醒设置与记账）；`BootReceiver` 里多余的 `Store.init` 删除（Application 已经装过） |
| P2 | 第二次写失败会成为 `viewModelScope` 里的未捕获异常 | 设置页 15 处写入统一走 `writeSetting`：记日志并把主题与主题色拉回落盘值 |
| P3 | 校园卡续期失败时登录态仍停在「已认证」 | 失败即标记过期，与 `RepositoryAhuSession` 同一条规则 |
| P3 | 测试归属守卫有三个绕过口（多处 include、`src/test/kotlin`、契约测试命名） | 三条全部加固 |
| P3 | 依赖锁只覆盖 release，而 CI 先跑 debug/test | 按 CI 实际任务重新生成 21 份锁（含 debug / test / lint 配置），并用「不写锁」重跑验证自洽 |
| P3 | 文档仍在承诺不存在的接口，P5 记录的份数与包名不准 | `CONTEXT.md` 与 §3.2 标注「目标词汇表」；P5 记录订正；零引用的 `PayCapsuleConfirmButton.kt` 删除 |


**仍未完成 / 已知偏差**（写下来，而不是假装不存在）

1. 五条主流程的真机回归仍未做（本机无设备）：登录 / 课表 / 充值 / 学习通 / 更新，另加小组件冷启动与支付灰度。本轮改动里「小组件只读缓存」与「强制重下清标志」都属于必须在设备上看一眼的行为。
2. `AhuSession.state` 在生产环境**没有读者**：写状态的路径齐全（登录、过期、复位），读的人一个也没有——界面问的是「本机有没有登录用户」，由 `SessionIdentity` 回答。按 ADR 0002 的「后果」一节，`sessionExpired` 的全屏引导本来要在 P2 重建，实际没有重建。补一个读者是用户可见的登录流改动，必须真机验证，因此保持现状并在此登记。
3. 下载器没有事件级测试缝（`ApkDownloadApi` 是懒加载伴生对象，客户端不可注入），所以「连续两次下载的镜像提示」「强制重下后清标志」这类状态与时序回归只能靠读代码发现。补缝（注入传输层）是独立的一刀。
4. §7 第 8 条的现状（2026-09-14 实测）：凭据仍不是「只经 `CredentialVault`」——`SecureCredentialVault` 有 4 处直接调用（`LoginViewModel` 存、`EvaluationRepository` 与 `TokenManager` 读、`AHUCache.clearAll` 清），此外有大量 `SessionStore` 的身份读取（界面与缓存，属身份而非凭据）。接口本身在跑（新会话层经 `CredentialVault` 读写）；把这四处收口要给两个 Kotlin object（`AHUCache`、`TokenManager`）找注入点，是独立的一刀。
5. §7 第 5 条（「承载状态或 IO 的 object 数量为 0」）在本项目不可达：`AHUCache`、Rust SDK 桥、`CurrentWeekResolver`、`SessionRefreshCoordinator`、`AhuSessionState` 都是这类对象。它只有在读成「object 不得成为跨模块的可变状态入口」时才成立。


阶段收尾验证（§6 要求）：

| 检查 | 命令 | 结果 |
|---|---|---|
| 单测（全模块） | `testDebugUnitTest` | **78 套件 / 378 用例 / 0 失败** |
| 静态检查 | `:app:lintRelease` | BUILD SUCCESSFUL |
| 发布构建 | `:app:assembleRelease` | BUILD SUCCESSFUL，`app-release-unsigned.apk` 14.91 MB（R8 已运行） |


- **现状**：单 Gradle 模块 `:app`，292 个源文件 / 67,075 行；分层靠包名，不靠接口。
- **核心矛盾**：需要"各司其职"的五处（网络、会话与验证、存储、更新下载、后台组件）都没有单一定义者，谁都能直接调用谁。
- **已有成功范式**：`BaseDataSource` 接缝背后挂 3 个适配器（Crawler / Rust SDK / mock），支撑了 v3.0 的后端切换。**本计划不发明新架构，只把这个范式复制到其余领域。**
- **执行策略**：先接缝、后模块；每阶段零行为变更或可独立回滚；不做大爆炸重写。
- **代价**：6 个阶段，全职约 6–10 周；兼职推进约 3–4 个月。每阶段独立可发版。

---

## 1. 目标与非目标

### 1.1 目标（三条同时成立才算达成）

1. **各司其职**：每个模块只负责自己的服务，对外只暴露一个深接口；越权访问（UI 摸数据库、网络层调业务）由 CI 自动拒绝。
2. **高度可维护性**：改动局部化——改一处只影响一处；新成员或新 agent 能在 10 分钟内判断"这个需求该改哪个模块"。
3. **最小风险**：每一步都能独立验证、独立回滚；登录、课表、充值、学习通、更新五条主流程在每一步之后与改前行为一致。

### 1.2 非目标（本轮明确不做）

- 不重写业务逻辑，不改 UI 表现，不换技术栈（继续 Kotlin + Compose + Hilt + Retrofit + Rust SDK）。
- 不追求测试覆盖率数字，只在接缝写契约测试。
- 不为了"看起来模块化"而拆 Gradle 模块。

### 1.3 术语

| 术语 | 含义 | 反例（本项目中不要这么说） |
|---|---|---|
| 模块 | 有接口与实现的整体，粒度不限（函数、类、包、层） | 组件、单元、service |
| 接口 | 调用者必须知道的一切：签名、不变式、顺序约束、错误模式、配置要求 | API、方法列表 |
| 接缝 | 可以"在此处之外替换行为"的位置，即接口所在之处 | 边界（boundary） |
| 适配器 | 在接缝上满足接口的具体实现，描述角色而非内容 | 实现类 |

---

## 2. 现状体检（证据）

### 2.1 规模与形态

| 项 | 事实 |
|---|---|
| Gradle 模块 | 只有 `:app`（`settings.gradle.kts` 仅 `include(":app")`） |
| 源码规模 | `app/src/main/java` 292 文件 / 67,075 行 |
| 测试 | JVM 单测 53 个文件；instrumentation 6 个（Room 迁移与集成） |
| CI | `.github/workflows/ci.yaml`：Rust 单测 + clippy → `:app:testDebugUnitTest` + `assembleDebug` → `lintRelease` → `assembleRelease` |

### 2.2 依赖方向（按 import 统计）

| 边 | 次数 | 结论 |
|---|---|---|
| `ui → data` | 219 | UI 直接摸数据层 |
| `ui → personalization` | 86 | UI 直接摸端侧模型 |
| `data → ui` | 1 | **反向依赖，分层已破** |
| `personalization → ui` | 2 | 领域层内含 Compose |
| `appwidget → ui` | 2 | 后台组件反向依赖 UI |
| `ui.screen → data.dao` | 21 | 界面直接读写缓存 |
| `ui.screen → data.crawler` | 10 | 界面直接调协议 API |

### 2.3 全局状态

- `AHUApplication`：`public static Application app`（`getApp()` 被引用 20 处）、`public volatile static Boolean sessionExpired`（6 处读写，含 2 处 UI）、`public static Object reLoginMutex`（**声明后无人使用**）。
- 全项目 `object` 声明 74 处，其中 65 个文件里的 `object` 承载状态或 IO。
- UI 层有 32 个文件直接引用上述全局单例。

### 2.4 五处没有定义者的地方

| 领域 | 现状 |
|---|---|
| 网络 | 9 处独立构造 OkHttp/Retrofit；全局 `CookieManager.cookieJar`；`TokenAuthenticator` 反向 import `AHURepository`，在 `runBlocking` 中用存储的密码做完整登录 |
| 会话与验证 | 登录流程塞在 `AHURepository.loginWithCrawler()` 一个方法里（CAS 探测 + 验证码 multipart + DES 表单加密 + 密码登录 + cookie 回填 + loopback cookie 导入）；`DES.java`（1001 行）放在 `utils` |
| 存储 | `AHUCache`（985 行）全局 KV 兼凭据仓；`PreferencesManager`（290 行）混装设置项与个性化开关；MMKV / SecureStorage / DataStore / Room 四套并存 |
| 更新下载 | APK 分段下载器、镜像切换、SHA-256、安装意图住在 `MainViewModel`（2074 行）里 |
| 后台组件 | `appwidget` / `notification` / `reminder` 共 14 文件直接 import 数据层，2 个文件摸全局单例 |

### 2.5 已经做对、必须保留的

- `BaseDataSource` 接缝与三适配器（真实接缝，已有两个以上适配器）。
- `SecureStorage`：AES-GCM + Android Keystore，fail-closed，解密失败即删除而非降级。
- `EncryptedCookiePersistor`：cookie 以密文落盘。
- `SessionRefreshCoordinator`：generation 机制防止并发重复登录（思路正确，只是被全局变量绑住）。
- APK 安装前签名指纹校验 `MainActivity.hasMatchingSigningCertificate()`；更新包 SHA-256 校验；`network_security_config` 仅对 loopback 放行明文。
- `*ArchitectureTest.kt`：用 JVM 测试读源码锁架构约束的现成先例（`ApkDownloadArchitectureTest`、`NetworkSecurityConfigTest`、`RadiantThemeArchitectureTest`）。
- 签名密钥走 `keystore.properties`、Sentry DSN 走 Gradle property，均已 gitignore。

---

## 3. 职责边界（各司其职）

### 3.1 依赖规则（唯一硬约束）

```text
:app (组合根：只做绑定与导航)
 ├─> :feature:*  ─> :core:* / :data:*
 ├─> :background ─> :core:data-api（只读）
 └─> :integration:native-sdk

禁止：:core:* / :data:*  ──✗──> :feature:* / :ui
禁止：:feature:a          ──✗──> :feature:b
禁止：网络层              ──✗──> 业务层
禁止：后台组件            ──✗──> 登录、写操作
```

只有 `:app` 知道"哪个适配器给哪个接口"。这条规则是整套方案的地基，其余都是它的推论。

### 3.2 模块职责表

| 模块 | 负责 | 不负责 | 对外接口 |
|---|---|---|---|
| `:core:model` | 领域模型、值对象 | 任何 IO、Android 依赖 | 数据类本身 |
| `:core:common` | `AhuError`、Dispatcher、时钟、Result 映射 | 业务语义 | `AhuError`、`Clock`、`Dispatchers` |
| `:core:designsystem` | 主题、形状、通用组件、动效令牌 | 取数、导航目标 | Compose 组件 |
| `:core:network` | 传输、重试、超时、Cookie 存储、日志脱敏 | 认识任何业务类型 | `HttpTransport` |
| `:core:auth` | 登录态、凭据、验证码编排、会话刷新 | HTTP 细节、UI 呈现 | `AhuSession`、`CredentialVault`、`HumanChallenge` |
| `:core:storage` | 设置、按用户缓存的分级读写 | 凭据（归 auth） | `SettingsStore`、`ProfileCache` |
| `:data:ahu` | jwxt / adwmh / ycard 协议适配（含校方 DES） | 缓存、UI 文案、登录态 | `CampusGateway` |
| `:data:chaoxing` | 学习通协议与凭据 | 日历提醒调度 | `ChaoxingGateway` |
| `:data:repository-index` | 仓库索引与文档下载 | 页面排版 | `RepositoryIndex` |
| `:data:personalization` | 行为记录、端侧训练与推理、遥测 | Compose 宿主、UI 决策文案 | `BehaviorRecorder`、`PresetSuggestions`、`PersonalizationSettings` + 词汇表 |
| `:data:update` | 版本检查、下载、校验、安装意图 | 弹窗与进度 UI | `AppUpdater` |
| `:integration:native-sdk` | Rust 库加载、loopback 启停、端口与令牌 | 业务协议语义 | `NativeCampusService` |
| `:feature:*` | 一个界面的状态与交互 | 直接访问其它 feature、直接访问存储 | ViewModel + Screen |
| `:background` | 小组件、通知、提醒的渲染与调度 | 触发登录、发起写请求 | 只读模型 + 调度接口 |

> 表里的接口名是**目标词汇表**，不是今天的实现清单。其中 `HttpTransport`、`CampusGateway`、`ChaoxingGateway`、
> `AppUpdater`、`HumanChallenge`、`ProfileCache`、`:data:ahu`、`:integration:native-sdk` 至今没有落地：
> 实际在跑的是 `AhuHttp`/`AhuRetrofit`（:core:network）、`:data:schedule` / `:data:grade` / `:data:recharge` / `:data:chaoxing` 各自的端口、
> `ApkUpdateChecker` + `ApkDownloader`（:data:update），以及留在 :app 的 RustSDK 装配与验证码界面。
> 理由分散在各阶段的范围修正里；「明确不做」一节列了不该为此开工的部分。

### 3.3 关键接口草案

```kotlin
// ── core:network ────────────────────────────────────────────────
/** 只负责"把一次请求发出去并如实回报结果"，不认识任何业务类型。 */
interface HttpTransport {
    suspend fun execute(call: HttpCall): HttpOutcome
}

/** 会话过期时网络层唯一允许调用的能力；实现由 core:auth 提供。 */
interface SessionExpiryHook {
    suspend fun onExpired(stamp: SessionStamp): Boolean
}

// ── core:auth ───────────────────────────────────────────────────
/** 登录态的唯一真相，取代 AHUApplication.sessionExpired。 */
interface AhuSession {
    val state: StateFlow<SessionState>          // Unknown / Anonymous / Authenticated / Expired
    suspend fun signIn(input: SignInInput): SignInResult
    suspend fun ensureFresh(stamp: SessionStamp): Boolean
    suspend fun signOut()
}

/** 凭据只能经此读写；实现 = SecureStorage(AES-GCM + Keystore)。 */
interface CredentialVault {
    suspend fun put(profile: ProfileId, secret: Secret)
    suspend fun get(profile: ProfileId): Secret?
    suspend fun erase(profile: ProfileId)
}

/** 图形/短信验证码：由 UI 提供实现，登录流程只依赖抽象。 */
interface HumanChallenge {
    suspend fun solve(challenge: CaptchaChallenge): CaptchaAnswer
}

// ── core:storage ────────────────────────────────────────────────
interface SettingsStore {                        // 不能丢，失败回默认值
    fun observe(key: SettingKey): Flow<String?>
    suspend fun put(key: SettingKey, value: String)
}
interface ProfileCache {                         // 可以丢，miss 就回源
    suspend fun read(key: CacheKey): String?
    suspend fun write(key: CacheKey, value: String)
}

// ── data:update ─────────────────────────────────────────────────
interface AppUpdater {
    suspend fun check(policy: UpdatePolicy): UpdateDecision
    fun download(decision: UpdateDecision): Flow<DownloadProgress>
    suspend fun install(artifact: ApkArtifact): InstallRequest   // 内含签名指纹校验
}

// ── integration:native-sdk ──────────────────────────────────────
interface NativeCampusService { suspend fun start(): Boolean; suspend fun stop() }

// ── 统一错误模型（core:common），替代 AHUResponse/Result 双轨 ──────
sealed interface AhuError {
    data object Network : AhuError
    data object Timeout : AhuError
    data object Unauthorized : AhuError
    data class ProtocolChanged(val endpoint: String) : AhuError
    data class Server(val code: Int, val message: String?) : AhuError
}
```

---

## 4. 分阶段计划

每个阶段的格式：**目标 → 改动 → 风险 → 验证 → 回滚 → 完成判据**。
通用的最小风险纪律见附录 A。

### P0 冻结与守护（0.5–1 天，风险：极低）

- **目标**：把第 3 节写进仓库，并让违规提交无法进入 master。
- **改动**
  - 新增 `docs/architecture/CONTEXT.md`（= 本文档第 3 节，供人与 agent 检索）。
  - 新增 3 条 ADR：错误模型、会话模型、存储分级（`docs/architecture/adr/`）。
  - 新增 `app/src/test/java/com/ahu/ahutong/architecture/ModuleBoundaryTest.kt`：沿用现有 `*ArchitectureTest.kt` 的写法读源码断言 import 边。
  - 新增**基线允许清单** `docs/architecture/boundary-allowlist.txt`：把当前存量违规逐条登记，规则是**只减不增**。
  - 首条断言：`data/**` 不得 `import com.ahu.ahutong.ui`；`ui/screen/**` 不得 `import …data.crawler.api`。
- **验证**：故意加一行违规 import，CI 必须失败；撤销后必须通过。
- **回滚**：纯新增文件，删除即可。
- **完成判据**：边界测试进入 CI 且能拦住一次真实违规。

### P1 纯搬运（3–5 天，风险：低）

- **目标**：建立 `:core:model`、`:core:common`、`:core:designsystem` 三个零行为变更的模块。
- **改动**：只做移动与 import 调整——`data/model` + 领域模型 → `:core:model`；`ext/` + `data/debug/DebugClock` + `AHUResponse` → `:core:common`；`ui/theme`、`ui/shape`、`ui/components` → `:core:designsystem`。
- **风险**：R8/资源引用、Hilt 生成代码跨模块后失效。
- **验证**：53 个单测 + 6 个 instrumentation 测试全绿；`assembleRelease` 通过；手动回归登录 → 课表 → 充值 → 学习通 → 小组件。
- **回滚**：一个 PR 一个模块，单独 revert。
- **完成判据**：`git diff` 中只有文件移动与 import 变化，无逻辑差异。

### P2 立起三个接缝（2–3 周，风险：中）

按 2.1 → 2.2 → 2.3 → 2.4 顺序，每一步独立发版。

- **2.1 `:core:network`**：`HttpTransport` 工厂统一 9 个 OkHttp/Retrofit 构建点（保留 APK 独立 Dispatcher 的设计）；日志拦截器仅 debug 生效并对 `Cookie/Authorization/密码` 字段白名单剔除；上线 `AhuError` 并删除 `AHUResponse`/`Result` 双轨。
- **2.2 `:core:auth`**：落地 `AhuSession` / `CredentialVault` / `HumanChallenge`；把 `TokenAuthenticator` 对 `AHURepository` 的依赖换成 `SessionExpiryHook`；清除 `sessionExpired`（6 处）与 `reLoginMutex`；后台静默全量重登改为**有界刷新**，失败即抛 `Unauthorized` 由 UI 引导重登。
- **2.3 `:core:storage`**：`AHUCache` 按语义拆为凭据（归 auth）/ 缓存（`ProfileCache`）/ 设置（`SettingsStore`）；旧 MMKV 明文 box 保留一个版本周期的迁移读取，补"迁移完成即清除明文"测试。
- **2.4 契约测试**：同一套断言同时跑 `CrawlerCampusGateway` 与 `NativeCampusGateway`（固定 HTML/JSON fixture），证明两个适配器行为等价。
- **验证**：每个接缝都有 fake 适配器；UI 与 feature 的单测不再联网；登录态在断网/密码错/协议变更三种情况下给出不同 `AhuError`。
- **回滚**：接口与实现同 PR 落地，旧实现保留一版（`initializeDataSource` 已是现成的切换开关），出问题切回旧实现即可，无需回滚代码。
- **完成判据**：`rg "AHUApplication.sessionExpired"` 为 0；`TokenAuthenticator` 无业务 import；`AHUApplication.getApp()` 引用数从 20 降至 0。

### P3 逐个抽出 feature 模块（每个 3–5 天，风险：中低）

按风险从低到高：`:feature:repository-index` → `:feature:settings` → `:feature:schedule` → `:feature:grade` → `:feature:xuexiaotong` → `:feature:recharge`。

- 每个 feature：ViewModel 只依赖接口；Screen 只依赖 ViewModel；自带 fake 适配器的 JVM 测试。
- 支付路径（recharge / ycard）**单独灰度**，并保留旧调用点直到灰度全量。
- **回滚**：feature 粒度 PR，单独 revert 不影响其它 feature。

### P4 后台、更新、个性化的收口（1–2 周，风险：中）

- `:background`：小组件/通知/提醒改为只依赖只读模型（`ScheduleReadModel`、`ReminderPlan`），用 Hilt `@AndroidEntryPoint` / `HiltWorker` 注入；验收标准是**进程被杀后的冷启动只读缓存渲染，不触发登录、不发起写请求**。
- `:data:update`：把下载器从 `MainViewModel` 抽出，**签名指纹校验一并搬进模块内部**（现在在 Activity 里，任何新调用点都可能绕过）。
- `:data:personalization`：对外收窄为 `BehaviorRecorder` + `PresetSuggestions` + 词汇表（原计划的 `TelemetrySink` 未建，理由见本节的「范围修正」）；`SmartSuggestionHost` 这类 Compose 宿主搬出领域层；模型损坏、训练失败、遥测失败一律静默降级，不得影响主流程。

### P5 固化与清理（3–5 天，风险：低）

- 删除死代码：`AHUApplication.startLocalService()`（无任何调用点）、`reLoginMutex`。
- 合并 `ui/component`（1 文件）与 `ui/components`（15 文件）双包。
- `boundary-allowlist.txt` 清空，边界测试转为无豁免。
- CI 增补：模块边界测试、依赖锁（lockfile）、密钥扫描（Bugly appid 目前硬编码在 `AHUApplication.java`，应移入 `buildConfigField`）。

---

## 5. 风险登记表

| 风险 | 触发条件 | 缓解措施 | 回滚方式 |
|---|---|---|---|
| 登录态回归 | 会话重构后出现静默登出/重复登录 | `AhuSession` 契约测试 + `SessionRefreshPolicyTest` 扩展为并发契约测试 | 单 PR revert；旧路径保留一版 |
| 两个校园网关行为漂移 | Rust 与 HTTP 适配器对同一响应处理不一致 | 同一套 fixture 契约测试双跑 | 用现有 `initializeDataSource` 切回 Crawler |
| 校方协议变更 | 教务/CAS 页面结构变化 | 协议适配器独立成包 + fixture 版本化；`ProtocolChanged` 错误码直通 UI | 适配器局部修改，不影响其余模块 |
| 数据迁移丢失 | MMKV 明文 → SecureStorage 迁移中断 | 迁移测试 + 回滚兼容测试（已有 `BehaviorDatabaseRollbackCompatibilityTest` 先例） | 保留旧读取路径一个版本周期 |
| 构建时间上升 | 模块数量增加导致编译变慢 | 只在存在两个真实适配器处抽模块；**上限 6 已作废**——P3 的六个 feature 模块本身就撑破了它，正确的约束是"`core:` 与 `data:` 只放有真实适配器的接缝"；维持 CI 缓存与 `in-process` 策略 | 合并回 `:app` 的成本极低（仅 Gradle 配置） |
| 端侧模型损坏 | 训练中断、模型文件损坏 | 每个 predictor 的静默降级契约（现有 `JourneyFailureIsolationTest` 思路） | 关闭个性化开关即回到无预测路径 |
| 隐私与遥测越界 | 新增上报字段 | 默认关闭 + `TelemetryPayloadPrivacyTest` 白名单锁死 | 关闭个性化与遥测开关（`PersonalizationSettings`） |
| 单人重构阻塞多人开发 | 长期存在于大改的分支 | 每阶段独立 PR 且尽快合并；禁止"搬文件 + 改逻辑"同一 PR | 见附录 A 的"先搬不改"纪律 |

---

## 6. 门禁与验证

| 时机 | 检查项 |
|---|---|
| 每个 PR | `:app:testDebugUnitTest`；新增/修改的接缝必须有契约测试；`ModuleBoundaryTest` 通过 |
| 每阶段收尾 | `lintRelease` + `assembleRelease`；五条主流程手动回归（登录 / 课表 / 充值 / 学习通 / 更新） |
| 涉及数据层 | Room 迁移测试 + 回滚兼容测试 |
| 涉及后台 | 进程被杀后的冷启动验收 |
| 涉及支付 | 单独灰度 + 小额真机验证后再全量 |

---

## 7. 验收清单（可用命令核查）

1. `rg "^import com\.ahu\.ahutong\.ui" app/src/main/java/com/ahu/ahutong/data` 输出为空。
2. `rg "AHUApplication\.sessionExpired|reLoginMutex" app/src` 输出为空。
3. `rg "AHUApplication\.getApp\(\)" app/src` 输出为空。
4. `rg -c "OkHttpClient\.Builder|Retrofit\.Builder" app/src/main` 收敛为 1 个工厂 + 1 个 loopback 专用实例。
5. 承载状态或 IO 的 `object` 数量为 0（白名单：常量与纯函数）。
6. `docs/architecture/boundary-allowlist.txt` 为空。
7. 每个领域接口都有 fake 适配器与契约测试在 CI 中运行。
8. 凭据只经 `CredentialVault`；日志与遥测 PII 扫描通过。

---

## 8. 明确不做

1. **不开新仓库大爆炸重写**：项目含 3 套登录协议、Rust 桥、桌面小组件、分段下载器，重写等于把已知兼容性坑重踩一遍；`BaseDataSource` 已经证明"接缝先行"可行。
2. **不为模块化而模块化**：只有存在两个真实适配器时才抽 Gradle 模块，否则只做包级接口与边界测试。
3. **不拿 Rust SDK 迁移当解耦载体**：它是适配器，不是架构。
4. **不追覆盖率数字**：只在新接缝写契约测试，旧逻辑测试随模块迁移而调整，不为覆盖率补无意义断言。
5. **不在重构中顺手改 UI 行为**：任何用户可见变化单独成 PR、单独验证。

---

## 附录 A：执行纪律（最小风险的关键）

1. **先搬不改，再改不搬**：文件移动/改名单独一个 PR，逻辑修改单独一个 PR。评审者能仅凭 diff 判断"是否有行为风险"。
2. **一个 PR 一个目标**：不混入其它需求、重构或格式化。
3. **新接缝三件套**：接口 + 生产适配器 + fake 适配器（含契约测试），缺一不可。
4. **旧实现保留一版**：新接缝上线时旧路径不立即删除；出现问题时通过开关切回，而不是回滚发布。
5. **每个 PR 写明**：改动范围、验证方式（命令与结果）、回滚方式、影响面（公共接口 / 数据结构 / 权限 / 依赖版本 / Gradle 配置）。
6. 分支与提交信息遵循仓库 `AGENTS.md`：`p/{用户名}/(feat|fix)/{name}`，Conventional Commits。

## 附录 B：首批可直接照做的文件清单

```text
docs/architecture/CONTEXT.md                       # 第 3 节内容
docs/architecture/decoupling-plan.md               # 本文档
docs/architecture/boundary-allowlist.txt           # 存量违规清单，只减不增
docs/architecture/adr/0001-error-model.md
docs/architecture/adr/0002-session-model.md
docs/architecture/adr/0003-storage-tiers.md
app/src/test/java/com/ahu/ahutong/architecture/ModuleBoundaryTest.kt
```
