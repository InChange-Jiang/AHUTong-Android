# 校园卡账单（消费流水）抓取逻辑完整说明

> 交接文档：供主 Agent 开发"账单功能"使用。
> 依据：2026-09-22 对 `ycard.ahu.edu.cn` 的实测（真实登录态 + H5 前端 JS 反解 + Vue 组件原始数据提取）。
> 结论先行：**账单流水可以基于工程现有 ycard 爬虫登录态直接抓取，认证零新增开发。**

---

## 1. 系统背景：两套独立系统（均为工程既有）

| 系统 | 域名 | 厂商 | 本工程用途 |
|---|---|---|---|
| 安大智慧门户 | `adwmh.ahu.edu.cn` | — | 支付码（`/xzxcard/qrcode`）、余额（`/xzxcard/yue`）、失物招领 |
| 一卡通平台 | `ycard.ahu.edu.cn` | 新中新"慧新E校"（新开普 berserker 体系） | **工程既有，生产在用**：`YcardApi`（BASE_URL 就指向它）、`TokenManager` SSO 登录与 token 刷新、卡信息 `queryCard`、浴室缴费（`/charge/*`、`/blade-pay/pay`）。**本次开发的唯一增量是 `/berserker-search/*` 下的账单端点调用** |

**支付码来自 adwmh，账单流水在 ycard**——两者是不同系统、不同登录态。但 **`ycard.ahu.edu.cn` 对本工程不是新东西**：`YcardApi`/`TokenManager`/浴室缴费整条链路早已在生产运行，卡信息 `queryCard` 与账单接口同 host、同认证头、同 token。本次改动 = 往 `YcardApi` 里加几个 Retrofit 方法声明 + 数据模型 + Repository 转发。**登录态、token 获取、401 刷新一行都不用新写，全部复用现有代码。**

本工程现有支付码获取链路（三层 fallback，账单功能不涉及，仅供理解架构）：
1. 本地 Rust HTTP 服务 `LocalServiceClient.getQrcode()` → `$baseUrl/ycard/qrcode`
2. Rust JNI `RustSDK.getQrcodeSafe()`（GuiXu submodule）
3. Kotlin 直爬 `AdwmhApi.API.getQrcode()` → `https://adwmh.ahu.edu.cn/xzxcard/qrcode`

**注意：Rust SDK 没有流水接口**（JNI external 列表里只有 qrcode/balance/exam/grade 等），所以账单功能没有 Rust fallback，只有 Kotlin/ycard 一条路。这层要写清在 AHURepository 里——不要像 getQrcode 那样做多层 fallback，直接走 ycard。

---

## 2. 认证机制（核心：全部复用现有代码）

### 2.1 Token 获取（已实现，勿重写）

代码位置：`app/src/main/java/com/ahu/ahutong/data/crawler/manager/TokenManager.kt`

流程（生产已在用）：
1. `probeCampusCardLogin()`：从 `https://ycard.ahu.edu.cn/berserker-auth/cas/redirect/neusoftCas?targetUrl=https://ycard.ahu.edu.cn/plat/?name=loginTransit` 开始，用 `loginRedirectClient`（**followRedirects=false，手动跟随**）逐跳走 CAS 重定向，捕获 URL 中 `ticket=` 参数（排除 `ST-`/`PT-` 开头的中央 CAS 一次性票据，取新中新的加密 ticket）。
2. `getToken(username=ticket解码值, password=ticket解码值)`：`POST /berserker-auth/oauth/token`，form 参数 `grant_type=password&scope=all&loginFrom=h5&logintype=sso&device_token=h5&synAccessSource=h5`，固定头 `Authorization: Basic bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm06bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm1fc2VjcmV0`（mobile_service_platform:mobile_service_platform_secret）。返回 JSON 含 `access_token`（JWT，约 623 字符）。
3. 前置条件：中央 CAS 会话有效（CookieManager.cookieJar 维护的 one.ahu.edu.cn cookie）。CAS 过期时 `refreshStoredSession()` 会用 `SecureCredentialVault.wisdomPassword()` 里存的统一认证密码自动续期后重试。

### 2.2 请求认证头

```
Synjones-Auth: bearer <access_token>
```

- H5 前端用的是小写 `synjones-auth`，App 的 `YcardApi.authInterceptor` 用 `Synjones-Auth`——HTTP 头不区分大小写，等价。
- 该拦截器已存在于 `YcardApi.kt` companion（对非 token 请求自动附加），**新接口只要挂到 `YcardApi.API` 的 client 上就自动带认证**。
- 每个业务请求必须带查询参数 `synAccessSource=h5`（H5 拦截器统一追加；App 侧现有 `queryCard` 也是显式传的）。

### 2.3 Token 生命周期与 401 处理

- 无 token / token 失效：HTTP 401，body `{"code":401,"message":"缺失令牌,鉴权失败"}`（实测）。
- access_token 过期后需重新走 2.1 换取；refresh_token 有效期 7 天（`expires_in=6047999`）。
- **必须用现有 `YcardApi.authorizedCall` 包装**（`YcardApi.kt` companion）——它实现了 401 检测 + 单飞刷新 + 并发合并，是现成模式：

```kotlin
val result = YcardApi.authorizedCall { 新接口方法() }
```

参考现有用法：`CrawlerDataSource.getCardInfo()` 里的 `YcardApi.authorizedCall { loadCardRecharge() }`。

---

## 3. 账单接口契约（本次实测确认）

Base URL：`https://ycard.ahu.edu.cn/`（即 `YcardApi.BASE_URL`）

### 3.1 流水分页查询（主接口）

```
GET /berserker-search/search/personal/turnover
```

| 参数 | 类型 | 说明 |
|---|---|---|
| `size` | int | 页大小（H5 用 8，可自定） |
| `current` | int | 页码，从 1 开始 |
| `timeFrom` | str | 可选。`YYYY-MM-DD`，起始日 |
| `timeTo` | str | 可选。`YYYY-MM-DD`，截止日 |
| `type` | int | 可选。2=消费，1=充值；不传=全部 |
| `synAccessSource` | str | `h5`，必带 |

**实测行为：不传时间参数时默认返回当月数据。** H5 首页"本月"标签就是不带时间参数直接请求的（抓包确认：`?size=8&current=1&synAccessSource=h5` 无时间参数，返回当月流水）。

响应（标准分页包装）：
```json
{
  "code": 200,
  "data": {
    "records": [ /* TurnoverRecord[] */ ],
    "total": 123,
    "pages": 16
  }
}
```
`total` 为 null 时 H5 停止加载（当月无数据的边界情况，注意判空）。

### 3.2 单笔详情

```
GET /berserker-search/search/personal/turnover?orderId=<orderId>
```
返回 `data.records[0]` 即该笔记录（H5 账单详情页就是这么取的）。

### 3.3 收支汇总

```
GET /berserker-search/statistics/turnover/count?timeFrom=2026-09-01&timeTo=2026-09-30&synAccessSource=h5
```
响应：
```json
{ "code": 200, "data": { "expenses": 33362, "income": 33150 } }
```
**单位是分**（expenses/100 = 333.62 元，与页面显示一致，实测核对过）。

### 3.4 图表聚合（可选，做统计页再用）

```
GET /berserker-search/statistics/turnover/sum/user
  ?dateStr=2026-09        // 月:YYYY-MM；年:YYYY
  &dateType=month         // month | year
  &statisticsDateStr=day  // month 查询按 day 聚合；year 查询按 month
  &type=2                 // 2=支出，1=收入
```
返回按日/按月的金额 map。

### 3.5 交易类型字典

```
GET /berserker-search/search/turnoverType?synAccessSource=h5
```

### 3.6 名下卡列表（可选，多卡筛选用）

```
GET /berserker-app/ykt/tsm/getCampusCards
```
响应 `data.card[]`，元素含 `account`、`cardname`。流水查询加 `fromAccount=<account>` 可按卡筛选（H5 筛选栏这么做的）。

---

## 4. 流水记录字段（真实数据提取，已核对）

从登录态页面 Vue 组件提取的原始记录（去掉前端计算字段 `my*` 后）：

| 字段 | 示例 | 说明 |
|---|---|---|
| `orderId` | `1790074672000254546100004213923387` | 唯一 ID，详情查询用 |
| `tranamt` | `560` | **交易金额，分**。÷100 显示 |
| `cardBalance` | `1` | 交易后卡余额，**分** |
| `feeAmt` | `0` | 手续费，分 |
| `ebagamt` | `0` | 电子钱包金额，分 |
| `typeFrom` | `"2"` | **2=支出（消费），1=收入（充值）** |
| `turnoverType` | `"二维码支付"` / `"充值"` | 大类 |
| `consumeTypeName` | `"联机扫码支付(被扫)"` | 细类，可能为 null |
| `resume` | `"北二区食堂一楼-扫码支付"` | 摘要 |
| `toMerchant` | `"北二区食堂一楼"` | 商户/地点 |
| `remark` | `"二维码=[41475389...] [reserve=...]"` | 备注；**支付码消费时记录当次二维码内容** |
| `effectdateStr` | `"2026-09-22 18:57:52"` | 交易时间（北京时间，字符串已本地化） |
| `jndatetimeStr` | `"2026-09-22 18:57:56"` | 记账时间 |
| `effectdate` | `"2026-09-22T10:57:52.000+0000"` | ISO 原始（UTC） |
| `fromAccount` | `"254546"` | 卡账号 |
| `sno` | `"P125301134"` | 学号 |
| `userName` | `"王锦程"` | 姓名（右带空格，trim） |
| `locationName` | `"77-139"` | 终端位置码 |
| `isRefund` | `null` | 退款标记 |
| `labelId`/`labelName`/`labelRemark` | `""` | 用户自定义标签（H5 功能，可忽略） |
| `consumeType` | `"502"` | 消费类型码 |
| `tranCode` | `"99"` / `"16"` | 交易码（99=扫码消费，16=充值，非穷举） |
| `typeId` | `"4"` / `"2"` | 类型 ID |

展示层规则（H5 的做法，建议沿用）：
- 金额：`tranamt / 100`，保留 2 位；支出前加 `-`，收入前加 `+`
- 时间：直接用 `effectdateStr`（已是北京时间字符串），不要自己解析 `+0000` 的 ISO 字段
- 退款判断：`isRefund`

---

## 5. 浏览器 vs 手机（App）环境差异 —— 重点章节

本次验证是在内置浏览器（桌面 UA：`...TRAESOLOCN/1.107.1 Chrome/...`）完成的。开发时注意以下差异：

### 5.1 认证链路差异（最重要）

| | 浏览器 H5 | Android App |
|---|---|---|
| 登录方式 | 用户在登录页输账号密码 / 跳转统一认证 CAS 页（one.ahu.edu.cn），**依赖浏览器 Cookie 会话**自动走重定向 | 无浏览器 UI。`TokenManager.probeCampusCardLogin()` 用 `loginRedirectClient`（禁自动重定向）**手动逐跳跟随**，捕获 ticket |
| Token 存储 | `sessionStorage.access_token` | `TokenManager` 内存单例 |
| CAS 过期 | 用户重新登录 | `refreshStoredSession()` 用 SecureCredentialVault 里的统一认证密码自动续期（已实现） |

**结论：App 侧不存在"会话"概念，一切靠 TokenManager 换 token。这条链路与现有 queryCard 完全一致且生产验证过，账单接口同 host 同认证，直接复用即可。**

### 5.2 UA（User-Agent）

- `berserker-search/*` 查询类接口对 UA **不敏感**：实测桌面 UA 的浏览器和手机 H5 都能正常返回数据。
- **对比教训**：同 host 的 `/charge/*`、`/blade-pay/*` 缴费接口有 UA 校验——`YcardApi.bathroomMiniProgramInterceptor` 专门伪造微信小程序 UA（`BATHROOM_MINI_PROGRAM_USER_AGENT`）才能过。**账单接口不需要这些**，别画蛇添足加 UA 拦截器；但如果未来把账单和缴费打通（比如"去充值"按钮），要记住那条路有 UA 门槛。
- App OkHttp 默认 UA 调 `berserker-search` 预计没问题（同 host 的 `queryCard`、`getCampusCards` 类接口 App 一直在用默认 UA）。如遇意外 403，再考虑补 UA。

### 5.3 每请求必带 `synAccessSource=h5`

H5 前端拦截器对**每个**请求（GET 的 query / POST 的 body）统一追加 `synAccessSource`。App 侧现有 `queryCard` 显式传了此参数。新接口定义时照抄。

### 5.4 CORS / Referer

- CORS 是浏览器概念，OkHttp 不受限制，无需处理。
- H5 部分接口带 `Referer: https://ycard.ahu.edu.cn/charge-app/`（那是缴费页的要求）。`berserker-search` 系列实测**不带 Referer 也能过**（浏览器直连导航无 Referer 时仍返回 401 鉴权错误而非 403，说明只卡 token 不卡 Referer）。不需要加。

### 5.5 Cookie

- token 获取过程需要 CAS cookie（`CookieManager.cookieJar` 统一管理，已接好）。
- **拿到 token 后，流水请求只认 `Synjones-Auth` 头，不依赖 cookie**。

### 5.6 验证方式差异（给测试的提示）

- 在浏览器里直接 `fetch` 带 token 调接口时：跨域脚本里 fetch 是简单请求可能被 CORS 拦（浏览器环境特有）；用页面自身的 XHR 才是可信验证。
- 本次探索中"navigate + 自定义头直连返回 401"是**浏览器工具不给文档导航注入自定义头**导致的（已用 httpbin 回显证实头没带上），**不是接口拒绝**。真实有效性以 H5 页面自身请求成功 + Vue 组件数据提取为准。App 侧不存在此问题。

### 5.7 时区与金额

- API 的 ISO 时间是 UTC（`+0000`），但 `*Str` 字段已转北京时间。**展示一律用 `effectdateStr`**，避免设备时区影响。
- 金额一律分。双精度浮点转换时注意用整数运算后除 100，或用字符串格式化，避免浮点误差（H5 是 `money(tranamt/100, 2)` 工具函数）。

---

## 6. 集成架构与建议（不含代码，实现方式以工程既有模式为准）

> 本章只说明改哪些地方、参考哪个既有实现。**具体写法、命名、结构由主 Agent 按工程现有风格决定，本文档不提供代码模板。**

### 6.1 API 层：`YcardApi.kt`（`app/.../data/crawler/api/ycard/`）

- 工程既有文件，只需往 interface 里追加账单相关方法声明，端点与参数见第 3 节
- 全部走现有 `API` client，自动带 `authInterceptor`（`Synjones-Auth: bearer`），无需任何新拦截器
- 参考既有同类写法：同文件里 `loadCardRecharge`（带默认参数的 GET）
- 已确认：`authInterceptor` 只对 `/oauth/token`、`/neusoftCas`、`/charge/feeitem/toAppitem` 跳过 token，`/berserker-search/*` 与 `/berserker-app/ykt/tsm/getCampusCards` 都会正常认证，**不动拦截器**

### 6.2 数据模型：`app/.../data/crawler/model/ycard/`

- 新增流水相关 model，字段以第 4 节实测表格为准（Gson 直接映射，字段名与 JSON 一致）
- 金额字段用整型存分；`consumeTypeName`、`total` 等实测可为 null，须可空
- 分页包装含 records/total/pages 三字段

### 6.3 DataSource 层：`CrawlerDataSource.kt`

- 参考同文件既有 `getCardInfo()`：`YcardApi.authorizedCall { ... }` 包装 + 响应判空 + `toClosedFailure` 统一错误语义
- **不做 Rust fallback**（Rust SDK 无流水接口，JNI external 列表已核实），失败直接 Failure

### 6.4 Repository 层：`AHURepository.kt` + DataSource 接口

- 仿既有 `getCardInfo` / `getQrcode` 的转发模式（`withContext(Dispatchers.IO)`）
- 按工程分层惯例考虑是否需要落 RepositoryAhu / SdkDataSource 对应空实现（主 Agent 决定，保持与 queryCard 的处理方式一致即可）

### 6.5 缓存与数据策略（建议，非强制）

- 流水变化频率低：建议本地缓存（现有存储设施）+ `orderId` 去重，支持离线查看历史账单
- 汇总接口轻量，进页面实时拉
- 拉全月：分页循环至 `current > pages` 或 records 为空
- 日期参数：月初/月末，注意大小月与闰年（用标准日期库）

---

## 7. 边界与风险

1. **当月无交易**：`data.total` 为 null（H5 判断 `null===e.data.total` 停止），App 侧要判空防 NPE。
2. **多卡用户**：默认返回主卡流水；多卡要 `fromAccount` 筛选（先 `getCampusCards`）。
3. **匿名查询流（备用，不推荐）**：`POST /berserker-search/cardTransaction/query`（姓名+身份证+图形验证码 → uuid/account）→ `GET /berserker-search/cardTransaction/queryTurnover?uuid=&account=...`。需要验证码识别，价值不大，仅作记录。
4. **频率**：未见限流，但建议账单拉取做去抖（进页面拉一次 + 下拉刷新），别轮询。
5. **token 失效场景**：统一认证密码改了 / 7 天 refresh 过期 → `refreshStoredSession` 失败 → 按现有会话过期语义走重新登录，无需新逻辑。
6. **接口变更**：这是校方/厂商 H5 的内部接口（版本号 `1.07.1.16`，2025-04 构建），无公开 SLA。解析层要做好 `ProtocolChanged` 错误分类（工程已有 `AhuError.ProtocolChanged`），改版时给用户可读的提示。

---

## 8. 实测验证记录（证据链）

1. 浏览器登录 `ycard.ahu.edu.cn`（统一认证 one.ahu.edu.cn CAS 跳转）→ 首页正常，sessionStorage 有 623 字符 JWT。
2. 打开官方账单页 `https://ycard.ahu.edu.cn/campus-card/billing/list` → 渲染真实流水：本月支出 ¥333.62 / 收入 ¥331.50。
3. Performance API 抓到页面实际请求：`/berserker-search/search/personal/turnover?size=8&current=1&synAccessSource=h5`、`/berserker-search/statistics/turnover/count?timeFrom=2026-09-01&timeTo=2026-09-30&synAccessSource=h5` 等。
4. 从页面 Vue 组件（`list[16]`）提取原始记录 3 条，字段结构与金额（560 分 = ¥5.60，与页面显示一致）核对无误。
5. 无 token 直连 → `{"code":401,"message":"缺失令牌,鉴权失败"}`，确认鉴权依赖。
6. 前端 JS（`/campus-card/js/app.729f2ec6.js` + chunk）反解出全部接口调用代码，确认参数名与响应处理逻辑。

## 9. 附录：相关前端资源（如需人工复核）

- 一卡通 H5 门户：`https://ycard.ahu.edu.cn/plat/`（慧新E校）
- 账单 H5：`https://ycard.ahu.edu.cn/campus-card/?name=billList`（路由 `/campus-card/billing/list`）
- 账单页实现 chunk：`chunk-329ecfc2.c9855843.js`（流水列表）、`chunk-2d0c5588`（统计图表）
- 已下载的前端 JS 与 appScheme 配置存于本工作目录 `ycard-js/`（含 `appScheme.json`、`cc/` 75 个 chunk），可离线 grep 复核接口细节。
