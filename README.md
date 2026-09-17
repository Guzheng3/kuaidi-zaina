# 快递在哪儿 — kuaidi.qinghan.vip

一个极简的「取件码 / 待取快递」聚合入口：把常用的几个取件入口放在一屏里，点一下就直达目标 App 的对应页面。

**不联网、不申请任何权限、无第三方 SDK。** 应用本身不取码、不读通知、不碰账号，只是把目标 App 自己的页面拉起来。

| | 值 |
| --- | --- |
| 包名 | `kuaidi.qinghan.vip` |
| versionCode / versionName | 3 / 1.0.2 |
| minSdk / targetSdk / compileSdk | 23 / 35 / 35 |
| 权限 | **0 条**（清单里没有任何 `<uses-permission>`） |
| 签名 | `qinghan.keystore`（`CN=qinghan, O=qinghan, C=CN`；**不入库**，缺失时 `build.ps1` 会自动生成） |
| APK | `build/kuaidi-zaina-1.0.2.apk` |

---

## 目录结构

```
pickup-code-launcher/
├── README.md                 ← 本文件
├── build.ps1                 ← 构建脚本（纯 SDK 命令行，无需 Gradle / 联网）
├── qinghan.keystore          ← 固定签名（首次构建自动生成，此后复用；不入库）
│
├── app/src/main/             ← 唯一源码目录
│   ├── AndroidManifest.xml
│   ├── java/kuaidi/qinghan/vip/       6 个 Java 文件
│   └── res/                             资源 XML + 图标位图
│
├── build/                    ← 构建产物（不入库）
│   ├── kuaidi-zaina-*.apk
│   └── screenshots/          ← 各版本的真机/模拟器验证截图
│
└── backup/                   ← 历史存档（不参与构建）
    ├── icon-sources/                   桌面图标的原图与生成脚本
    ├── icon-parcel-vector/             更早一版「纸箱」矢量图标
    └── pre-icon-redesign/              换图标之前的版本
```

---

## 源码清单

### Java（`app/src/main/java/kuaidi/qinghan/vip/`）

| 文件 | 职责 |
| --- | --- |
| `MainActivity.java` | 主页 + 设置弹层；UI **全部由代码构建**，含分组卡片 / 入口行 / insets / 动效 |
| `Destination.java` | **核心数据表**：11 个取件入口的 key / 完整入口名 / App 显示名 / 包名 / deep link / 网页兜底 / 是否默认勾选 |
| `DeepLinkLauncher.java` | 四级降级跳转 + 异常兜底 + 微信快捷方式分发协议 / 支付宝深链 |
| `EntryPrefs.java` | 显示开关的偏好存取（双集合，见下文） |
| `QuickAction.java` | 「常用」组的 4 个动作（微信 / 支付宝的扫一扫与收付款） |
| `PickupWidgetProvider.java` | 桌面小组件：一行三个身份码入口 |

### 资源（`app/src/main/res/`）

* `drawable/`：矢量与 Shape（入口字形 `ic_func_*`、快捷方式图标 `ic_shortcut_*`、勾选框、齿轮、箭头、卡片与小组件底）
* `drawable-nodpi/`：微信 / 支付宝的 4 个品牌字形位图
* `mipmap-anydpi-v26/` + `mipmap-xxxhdpi/`：自适应图标与传统回退图（见「桌面图标」）
* `values/` + `values-night/`：浅色 / 深色语义色令牌
* `layout/pickup_widget.xml`、`xml/shortcuts.xml`、`xml/pickup_widget_info.xml`

---

## 构建

```powershell
# 需要 JDK 17+ 与 Android SDK（build-tools 35 / platform android-35）
powershell -ExecutionPolicy Bypass -File build.ps1

# 可选：指定版本号、SDK 路径、构建工具版本
powershell -ExecutionPolicy Bypass -File build.ps1 -VersionCode 4 -VersionName 1.0.3
```

流程：`aapt2 compile` → `aapt2 link`（生成 `R.java`）→ `aapt2 optimize`（压缩资源路径）
→ `javac` → `d8` → 注入 `classes.dex` → `zipalign` → `apksigner`。产出在 `build/`。

### 脚本里的三个环境适配

1. **中文/非 ASCII 路径**：先检测工程路径，非 ASCII 就把源码复制到 `%TEMP%` 下再构建
   （`aapt2` / `d8` 在中文路径下会失败），构建完再拷回来。
2. **javac 输出**：原生工具会往 stderr 写提示，脚本统一用 `$ErrorActionPreference = "Continue"`
   并在每次调用后显式检查 `$LASTEXITCODE`，否则提示会被当成致命错误中断构建。
3. **`JAVA_TOOL_OPTIONS`**：构建期间清掉，避免 JVM 提示混进输出。

### 想用 Android Studio / Gradle？

本工程刻意不含 Gradle 配置（可完全离线复现）。迁到 AGP 8+：

1. 删除 `AndroidManifest.xml` 里的 `package="kuaidi.qinghan.vip"`；
2. 在 `build.gradle.kts` 写 `android { namespace = "kuaidi.qinghan.vip" }`。

---

## 应用做了什么

1. **主页**：两个分组卡片 —— 「取件码」（淘宝 / 拼多多 / 京东 / 抖音，6 行）+
   「常用」（微信、支付宝的扫一扫与收付款，4 行），默认 10 行**正好铺满一屏**。
   菜鸟 / 小红书 / 得物**默认不勾选**，在设置弹层里勾出来即可（多出来的行往下滚动）。
   每行显示目标 App 的**真实图标** + 功能名 + 所属应用；目标 App 未安装时图标压暗、
   副标题改「未安装」（点击仍会走网页兜底）。
   入口行点击后按「App deep link → 直接拉起 App → 网页兜底 → Toast」四级降级。
2. **桌面小组件**：3×1 一行三个身份码入口（菜鸟 / 淘宝 / 拼多多）点击直达。与主页同语言：
   中性卡片 + 目标 App 真实图标 + 中性色标签，品牌色只出现在图标里；未安装则回退品牌色矢量图。
3. **显示开关**：主页右上角齿轮弹出设置面板，勾选要显示在主页的入口。整个分组被隐藏时连卡片带
   组名一起消失；一个都不剩时给一句居中空状态。

### 组件

| 组件 | 类 | 说明 |
| --- | --- | --- |
| Activity | `MainActivity` | 主页；`singleTask`（应用开着时再点图标不叠新实例）；带 `ACTION_OPEN` 时跳转并 `finish()`，`onNewIntent` 接续同样处理 |
| AppWidgetProvider | `PickupWidgetProvider` | 桌面小组件（`BIND_APPWIDGET` 保护） |

另有 5 个**静态快捷方式**（长按图标弹出，`xml/shortcuts.xml`）。

---

## `Destination` 全量数据

按 key 排列。带 ✱ 的是**默认不勾选**的入口。

| key | 标题 | 目标包名 | App deep link（按序尝试） | 网页兜底 |
| --- | --- | --- | --- | --- |
| `cainiao` ✱ | 菜鸟身份码 | `com.cainiao.wireless` | `cainiao://startapp/station_code` | `market.m.taobao.com/…?bizEntry=ALIPAY_GUOGUO` |
| `taobao` | 淘宝身份码 | `com.taobao.taobao` | `taobao://…` → `tbopen://…`（`h5Url=…/authCode.html`） | `market.m.taobao.com/…/authCode.html` |
| `taobao_pending` | 淘宝待取快递/身份码 | `com.taobao.taobao` | `taobao://…` → `tbopen://…`（`…/m-end-school-tab/home`） | `pages-fast.m.taobao.com/…/m-end-school-tab/home` |
| `taobao_receive` | 淘宝待收货 | `com.taobao.taobao` | `taobao://go/my_orders?tabCode=waitConfirm` | `main.m.taobao.com/order/index.html?hybrid=true` |
| `pinduoduo` | 拼多多身份码 | `com.xunmeng.pinduoduo` | `pinduoduo://…?tab=ID_CODE&…` 三级降级 | `m.pinduoduo.net/mdkd/package?…` |
| `pinduoduo_pending` | 拼多多待取快递/身份码 | `com.xunmeng.pinduoduo` | `pinduoduo://com.xunmeng.pinduoduo/mdkd/package` | 同上 |
| `pinduoduo_receive` | 拼多多待收货 | `com.xunmeng.pinduoduo` | `pinduoduo://com.xunmeng.pinduoduo/orders.html?type=3&…` | `mobile.yangkeduo.com`（无公开订单页） |
| `jd` | 京东待取快递 | `com.jingdong.app.mall` | `openjd://virtual?params={"category":"jump","des":"orderlist"}` | `order.jd.com/center/list.action` |
| `xhs` ✱ | 小红书待取快递 | `com.xingin.xhs` | `xhsdiscover://rn/lancer-order/order/list` | `www.xiaohongshu.com` |
| `douyin` | 抖音待取快递 | `com.ss.android.ugc.aweme` | `snssdk1128://mall/xtab?…delivery_value=<订单页 Lynx 包>&…&tab_id=3`（双重 URL 编码） | `www.douyin.com`（无公开订单页） |
| `dewu` ✱ | 得物待取快递 | `com.shizhuang.duapp` | `dewuapp://m.dewu.com/router/order/buyer/orderList?tabId=3` | `https://m.dewu.com/` |

> 表里的 `taobao` / `pinduoduo`（身份码）与 `cainiao` 仍在数据表里，因为**桌面小组件**用的就是
> 这三个身份码入口；它们只是不出现在主页默认列表里。

### 这些链接是怎么来的

**没有一个是从文档或别处抄的**：全部是在装了对应 App 的真机上，拆开它自己的安装包扫 dex / 清单，
找到它自己注册的路由，再真机点一遍确认落点。逐个记一下关键发现（也方便日后失效时复现）：

* **`cainiao`**：身份码页 `IdentityCodeActivity` 声明了 `guoguo://go/station_code` 但
  **`exported=false`** —— 那是它内部用的，第三方（包括 shell）都调不起，直接用会退化成
  「打开菜鸟首页」。菜鸟对外真正开放的入口是 `OpenGuoGuoUrlActivity`（已导出，注册
  `cainiao://startapp` / `cainiao://router`）：它把 `cainiao://startapp/<path>` 的
  scheme+host 替换为 `guoguo://go` 后交给内部路由打开。实测直达
  `com.cainiao.wireless/.identity_code.IdentityCodeActivity`。
* **`taobao_receive`**：先在 dex 里定位订单列表组件 `TBOrderListActivity`，再回清单看它**自己导出**
  的 intent-filter —— `scheme=taobao` + `host=go` + `path=/my_orders` 就是对外入口，拼上订单页的
  标签码 `tabCode=waitConfirm`（dex 里的标签码：`all` / `waitPay` / `waitSend` / `waitConfirm` /
  `reFund` / `waitRate`）即可直达。
  ⚠️ **别用 `tbopen` 包订单页 H5**：淘宝网页版在 App 内是**未登录态**，订单页会被重定向回首页
  （实测踩过）。
* **`pinduoduo_receive`**：dex 里找到订单页 `orders.html` 与 6 个 `type=N` 变体，实测 `type=3`
  落在「待收货」（`0`=全部 `1`=待付款 `2`=拼团中…）。后面三个参数
  `comment_tab` / `combine_orders` / `main_orders` 是页面自己的 tab 与埋点开关，别删。
* **`jd`**：京东「我的」页的服务端配置里就写着自己的跳转 —— 在它的安装包里能读到
  `"jumpUrl":"openApp.jdMobile://virtual?params={\"category\":\"jump\",\"des\":\"orderlist\"}"`。
  `openjd` 与 `openApp.jdMobile` 都是它注册的 scheme，本项目用前者（实测落到
  `com.jd.lib.ordercenter.taro.OrderListActivityTaro`）。
* **`xhs`**：`xhsdiscover://rn/lancer-order/order/list` 直接能在小红书自己的安装包里查到
  （`classes9.dex`，旁边还有 `lancer-order/my/coupon`、`lancer-order/order/package_detail`）。
* **`douyin`**：抖音的小程序外组件全部 `not exported`，官方 scheme 的 appid 形式也已被封，
  第三方只能用 `snssdk1128://` 走它的内部路由。而抖音**没有独立的订单页路由** —— 订单是
  「商城」里的一个 tab（内部路由 `//mall/xtab`）。于是拆 `base.apk`（362MB / 55 个 dex）扫字符串，
  在 `classes38.dex` 里找到抖音**自己内嵌**的那条订单页 schema（带 `enter_from=shortcut_order` /
  `order_guide_highlight` 参数），其 `delivery_value` 指向订单页的 Lynx 包
  `ecommerce_orders_douyin/app_new/template.js`。`tab_id=3` 是订单页的「待收货 / 使用」标签
  （`0` = 全部）。参数是**双重 URL 编码**的（`%253D` 是编码后的 `%3D`），要改的话别手写解码。
* **`dewu`**：得物对外有 `duapplauncher` / `dewuapp` 两个**无 host 限制**的通用入口（都指向
  `DeepLinkActivity`），但 `https://m.dewu.com` 的 App Link 只注册了
  `/router/product/ProductDetail` 一条路径 —— 直接发订单页的 https 链接系统解析不到
  （实测 `unable to resolve`）。拆 `base.apk`（121MB / 22 个 dex）后在 `classes11.dex` 里找到
  得物**自己「我的」页的配置**：订单各标签的原生跳转就是
  `https://m.dewu.com/router/order/buyer/orderList?tabId=N`（`0`=全部 `1`=待付款 `2`=待发货
  `3`=待收货 `6`=待评价），把 scheme 换成 `dewuapp://` 即从第三方直达，实测落到
  `com.shizhuang.duapp.modules.orderlist.activity.MyBuyActivityV2`。

> **共同点**：这些都是各 App 的**内部路由**，不是公开开放 API，随版本更新可能失效。
> 改动前后都应在装了最新版对应 App 的真机上逐个复测。

---

## 主页 UI 关键取值（代码构建，统一设计令牌）

```java
PAGE_BACKGROUND = #f2f3f5（浅） / #0f1012（深）   页面底色，整页就这一个实色
SURFACE         = #ffffff（浅） / #1b1f24（深）   卡片表面，比页面亮一档
CARD_STROKE     = #1f000000（浅） / #2effffff（深） 卡片与标题栏的发丝线
TEXT_PRIMARY    = #1b1d1b（浅） / #e7eae5（深）    TEXT_SECONDARY = #333c37（浅） / #a6aca3（深）
ACCENT_*        = 菜鸟 #147848 / 淘宝 #c24b12 / 拼多多 #be233c / 京东 #e1251b
                  / 小红书 #d81e3f / 抖音 #161823 / 得物 #0b7a72 / 微信 #07c160 / 支付宝 #1677ff
卡片圆角 = 13.14dp   卡片描边 = 1dp   行高 ≥ 56dp（40dp 图标 + 上下各 8dp 留白）
```

结构：顶部标题栏（与页面同色但不透明 + 底边 1dp 发丝线；标题 24sp，无副标题）
→ 两个分组卡片。**分组名在卡片内部**，其下是入口行；
行 = 40dp 目标 App 真实图标（`PackageManager#getApplicationIcon`，取不到时回退品牌色圆角方块
+ 反白字形并压暗到 45%）+ 功能名 16sp + 所属应用 12sp + 18dp 箭头，
行间分隔线从文字起始位置起、取低透明度主文字色。

**间距按「默认 10 行铺满一屏」标定**：在 360dp×791dp 上，末行底边离可用高度只剩约 8dp
（详见「验证记录」）。小屏或大字放不下时，ScrollView 仍是兜底，不会截断。

**纯实色，没有壁纸也没有毛玻璃**：整页只有三个层次（页面底色 / 卡片表面 / 发丝线），
不依赖任何 GPU 能力，任何设备上观感一致，也不必为了「压在照片上还要看得清」去调 tint 与对比度。

**为什么分组名在卡片里**：它要是浮在页面上，就只是页面底色上的一行小字，
和卡片内的入口行没有承托关系；放进卡片才和行一起构成一个整体。

**深色卡片为什么要靠 surface 提亮**：深色下页面是近黑，卡片若与页面同色就完全看不出边界；
`surface` 亮一档 + 一道 18% 白描边，卡片才立得起来。

**交互动效**（只动 transform/opacity，不引起布局回流；时长/缓动统一为令牌）：

| 场景 | 动效 | 时长 |
| --- | --- | --- |
| 入场 | 每张卡片自上而下淡入 + 16dp 上移，逐卡片错峰 36ms | 240ms |
| 按下 | 行内图标缩至 0.9 + 品牌色涟漪 + 轻触震动；行尾箭头右移 3dp | 60ms |
| 抬手 | 回弹至原状（`cubic-bezier(.2,.8,.2,1)`） | 160ms |

行涟漪用与卡片同半径的圆角裁切（首行圆上两角、末行圆下两角），按下时不会溢出卡片圆角；
行本身不缩放 —— 整行缩放会让卡片边缘露白。

遵循系统「移除动画」设置（`ANIMATOR_DURATION_SCALE=0` 时入场整体跳过）；
滚动被拦截时 `ACTION_CANCEL` 及时复原，动画可随时被新动作中断，不阻塞点击。

**Android 15+ 适配**：targetSdk 35 强制 edge-to-edge，`MainActivity` 用
`OnApplyWindowInsetsListener` 把系统栏 inset 折算成内边距。两个坑：

1. 内容区上内边距取**标题栏布局完成后的实测高度**（`getHeight()`，其自身 padding 已含状态栏
   inset），不再额外加一次 `bars.top` —— 加了会把整页下推一个状态栏的高度。
2. 组间距与底部 inset 放在**内容自己的 padding** 里，不放 `ScrollView` 的 padding：
   `ScrollView` 默认 `clipToPadding=true`，内容画不进 padding 区，底部 padding 会把最后一行
   切掉（实测底部 44dp 内容不可见，要滚动才出来）。

35 以下保持原非沉浸窗口行为。`values[-v27]/styles.xml` 里 `windowBackground` /
`navigationBarColor` 也统一到页面底色，启动首帧不会闪一下主题默认色。

**深色模式**：颜色全部走 `values/colors.xml` + `values-night/colors.xml` 语义令牌
（页面底色 / 卡片表面 / 发丝线 / 文字 / 强调色 / 小组件按压色），主题在
`values[-v27]/styles.xml` 与 `values-night[-v27]/styles.xml` 间按系统切换。
小组件的卡片底、描边、文字也引用同一套令牌，不需要 `drawable-night/` 变体。
品牌强调色（图标块）深浅色保持一致，白字对比度均 ≥4.5:1 —— 小红书用的是把官方红
`#ff2442`（白字仅 3.76:1）压暗后的 `#d81e3f`（5.03:1），抖音用的是它的品牌深色
（官方红 `#fe2c55` 白字仅 3.68:1，不达标故不用）。

---

## 设置弹层与显示开关

主页右上角一枚齿轮弹出一个**底部面板**：抓柄 + 「设置 / 勾选要显示在主页的入口」+
两组勾选列表。列表是**纯文字**：一行一个完整入口名（「菜鸟身份码」「微信扫一扫」）+
勾选框，不带目标 App 的图标 —— 这是选择列表，不是入口列表，图标在这里只是噪音。
面板上两角与主页卡片同半径（13.14dp），列表超高（屏幕 66%）时收缩成滚动区。

**弹层不是新的 Activity**，而是叠在根布局上的一层普通 View：本应用对外只有
1 个 Activity + 1 个 AppWidgetProvider（见上方组件表），弹层因此能直接复用主页那套
排版 / 取色 / 圆角方法，清单里也不必新增组件。关闭方式有三条：点遮罩、返回键、
Android 13+ 的预测性返回 —— `onBackPressed()` 与 `OnBackInvokedCallback` **两个都注册**，
因为预测性返回开 / 关时系统走的是不同的那一条路。

**只影响主页列表**：长按图标弹出的静态快捷方式与桌面小组件都不跟随这个开关，
弹层上的说明文案也只写「显示在主页的入口」。

**主页在勾选时实时更新**：弹层只盖住下半屏，主页上半部分一直可见，勾完立刻能看到
上面的列表变化（行消失 / 恢复、卡片重排、空状态出现），关闭时不需要再重建 ——
退场是纯动画（遮罩淡出 + 面板下滑），没有任何主线程重活。主页重建所需的目标 App 图标
存在 `appIconCache` 里（`loadAppIcon` 的进程内缓存），反复重建不再戳 `PackageManager`。

**偏好分两个集合**（`EntryPrefs`）：「显式隐藏」与「显式显示」，只有用户真正动过的入口才进集合，
没动过的走入口自带的 `defaultVisible`（见 `Destination#defaultVisible`）。
优先级是 **显式隐藏 &gt; 显式显示 &gt; 默认值**。

只存隐藏项是不够的：菜鸟 / 小红书 / 得物是「默认不勾选」，如果只存隐藏项，
用户勾上之后就没有地方区分「用户勾过」与「用户没动过」，将来想改默认值也改不动。
分两个集合后，「默认不勾选的入口被用户勾回来」也能正确记住。

```java
CHECK_SIZE_DP = 24   圆角 7dp   描边 2dp   对勾 16dp
未选中 = 透明底 + 50% 次要文字色描边     选中 = 实心（主文字色）+ 用页面底色描的对勾
```

勾选框是自绘的，没用框架 `CheckBox`：框架控件自带 Material 尺寸与着色，与这里的行高、
圆角、文字色对不上。点**整行**切换而不是只点方框（24dp 的点击目标太小），切换时给一次
轻触震动，与主页入口行一致；勾选立即落盘，没有「保存」按钮 —— 少一步就不容易忘。

**空状态**：所有入口都被取消勾选时，主页给一句「入口都被隐藏了 / 点右上角的设置，
勾选想显示的入口」，两行居中。这不是错误态，只是指路。

---

## 桌面图标

白色贴纸风位图（`backup/icon-sources/shortcuts_icon_156793.png`，128×128，四角透明）+ 纯白底。
资源按 API 分两套，靠限定符自动分流，清单里只写 `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`：

| 目录 | 内容 | 生效版本 |
| --- | --- | --- |
| `mipmap-anydpi-v26/` | `<adaptive-icon>`：白底（矢量）+ 前景位图 | Android 8.0+（API 26+） |
| `mipmap-xxxhdpi/` | 传统位图（白圆角方 + 白正圆两张，圆角画死） | API 23–25 |

三张位图都是 432×432（xxxhdpi，108dp 画布的 4 倍），由 `backup/icon-sources/icon-gen.ps1`
从原图生成：**原图按 72dp 缩进画布中心**（288px），四周留 18dp 出血区。

**为什么是白底 + 72dp 缩放**：位图是白色贴纸风格、四角透明。白底正好和它融为一体，
贴纸边缘那圈浅灰过渡（#EBEBEB 级）在白底上几乎不可见；如果整幅铺满 108dp 当背景，
圆形 / 方圆遮罩会裁掉贴纸四角、主体也会被裁。按 72dp 缩放后，位图主体（粉色 / 紫色那部分）
最外接点距画布中心 ≤30dp，落在 66dp 安全圈（半径 33dp）内，任何遮罩都裁不到主体。

**为什么只有 xxxhdpi 一份**：单密度桶就够所有设备用 —— 系统按屏幕密度缩放位图，
高分屏吃满 432px、低分屏等比缩小，不必为 5 档密度各存一份。

**monochrome 单色层已移除**：位图没有可挖的剪影，Android 13+ 主题图标直接回退用这张正常图标。

**传统回退图**（API 23–25）：白底圆角方（24dp 圆角）+ 同一张 72dp 位图；
`ic_launcher_round` 是白底正圆 + 同一张位图（7.1 的圆形桌面不再裁形，圆图必须画成真圆）。

---

## 验证记录

**真机：OPPO PLA110（Android 16 / SDK 36 / arm64-v8a，360dp × 791dp）**

* `adb install -r` 覆盖升级成功（同名包同签名），启动无崩溃
* 桌面图标实测：`aapt2 dump resources` 确认 `mipmap/ic_launcher` 有 `(xxxhdpi)` 传统位图 +
  `(anydpi-v26)` 自适应图两层，版本分流正确；截图采样核对图标区域以白色为主、
  主体色与设计稿一致（见 `build/screenshots/`）
* 浅色主题实测：两张卡片完整显示（含圆角与底边），默认 10 行全部落到真实 App 图标，
  副标题为应用名
* **一屏铺满实测（默认 10 行）**：末行底边 y=2264。可用高度 = 屏高 2373 − 页底留白 36 −
  系统导航栏 inset 48（`dumpsys window` 读到 `InsetsSource navigationBars
  frame=[0,2325][1080,2373]`）= 2289，**只剩 25px（约 8dp）余量** —— 10 行正好铺满，
  不滚动、也不留大片空白。行高 56dp、组间距 10 / 14dp 都是按这个目标标定的。
  勾上默认不勾的入口（11~13 行）后内容超高，ScrollView 自然往下滚，不会截断
* **每个入口的降级链**逐个触发过：日志显示 deep link → 直启 App → 网页兜底逐级降级，无崩溃
* **各入口的真机落点**（点主页对应行后 `dumpsys window` 读前台）：
  * 淘宝待收货 → `com.taobao.android.order.bundle.TBOrderListActivity`，截图核对停在**待收货**标签
  * 拼多多待收货 → `com.xunmeng.pinduoduo.activity.NewPageActivity`，同样停在**待收货**
  * 京东待取快递 → `com.jd.lib.ordercenter.taro.OrderListActivityTaro`（我的订单列表）
  * 小红书待取快递 → `com.xingin.reactnative.ui.XhsReactActivity`，核对为「搜索我的订单」页
  * 抖音待取快递 → 抖音「我的订单」且落在**待收货 / 使用**标签（`tab_id=3`）；
    同 schema 的 `tab_id=0` 落在「全部」，两条都截图核对过
  * 得物待取快递 → `com.shizhuang.duapp.modules.orderlist.activity.MyBuyActivityV2`（「我买到的」），
    停在**待收货**标签
* 返回行为：`singleTask` 启动模式 + `onNewIntent` —— 应用开着时回桌面再点开图标不会叠出
  第二个实例，返回键一次退出；快捷方式 / 小组件带参启动仍走 `onNewIntent` 正常跳转并自关
* 抓柄居中实测：弹层顶部抓柄像素采样 = `x 486..593`（宽 108px = 36dp）、`center=540` 正是
  1080 屏宽的正中、高 12px = 4dp。修掉了「抓柄跑到左上角」—— `LinearLayout.LayoutParams`
  的三参构造是 `(宽, 高, weight)`，之前把 `Gravity.CENTER_HORIZONTAL` 当 gravity 传了进去，
  实际成了 `weight=1` 且保持默认左对齐
* 底部裁切问题的修复就是在这一台上量出来的（`uiautomator dump` 读实际 bounds）
* `dumpsys` 确认：组件 = 1 Activity + 1 AppWidgetProvider，`<uses-permission>` = 0 条

**系统行为（不是应用缺陷，遇到时按提示选一次即可）**

* ⚠️ **ColorOS 的「应用启动拦截」**：首次从本应用跳抖音 / 得物会弹系统确认框
  「"快递在哪儿"想要打开"XX"」，选「30 天内允许」后不再打扰
* ⚠️ **应用分身**：装了分身的 App（如拼多多）会先弹 ColorOS 的「选择打开的应用」
  （拼多多 / 拼多多 1），默认实例可在「设置 - 应用 - 应用分身」里指定

**模拟器：API 35（Pixel 6 规格，x86_64 / SwiftShader）**

* 深色主题截图确认：卡片 surface 比页面亮一档、轮廓清楚，两张卡片完整不被裁
* `font_scale` 调到 1.3 仍无重叠或截断（标题栏高度与内边距都是布局后实测值）
* 5 个目标包名各装一个同名桩 APK 后复验：行内换成 `PackageManager` 返回的真实图标、
  副标题由「未安装」变回应用名（桩件仅用于验证取值路径，非真实图标）
* 设置弹层专项：按 `uiautomator dump` 给出的真实行边界逐行点选，状态与主页同步无误；
  全部取消勾选 → 主页落到空状态；`am force-stop` 再启动，勾选状态仍在（SharedPreferences
  落盘正常）；再逐行勾回来即恢复
* `uiautomator dump` 校验每行的 `content-desc` 随勾选在「已显示 / 已隐藏」之间翻转
  （勾选框是自绘的，读屏信息由整行的描述承担）
* 关闭流程：点遮罩 / 返回键关闭，退场是纯动画（遮罩淡出 + 面板下滑），无白屏空档、
  无主线程重建；弹层列表超高时收缩成滚动区，不会盖满整屏
* 深色模式：弹层面板、勾选框对比度正常；空状态两行文字居中，浅色 / 深色都验过

**桌面小组件**（模拟器上长按桌面 → Widgets → 拖到桌面）

* 桌面上实际渲染确认：卡片圆角/描边/标签色随深浅主题切换，且**同一份 drawable 生效**
  （不再有 `drawable-night` 变体）
* 只装菜鸟、淘宝两个同名桩件、不装拼多多：小组件里菜鸟/淘宝显示 `getApplicationIcon`
  返回的真实图标，拼多多显示 `ic_shortcut_pinduoduo` 回退图 —— 两条图标路径同屏验证
* 点击菜鸟列 → 日志出现该入口的深链降级链，证明 `setOnClickPendingIntent` 绑定正确
* 尺寸：`targetCellHeight` 被 Pixel Launcher 忽略，仍按 `minHeight` 推算 ——
  64dp 时落成 3×2、内容居中后上方一大片留白（看着像坏了），改 48dp 后落成 3×1 细条

**已知限制**：小组件图标在添加 / 重启 / 缩放时刷新（`updatePeriodMillis=0`），
先加小组件、后装目标 App 的话，图标要等下次刷新才变成真实图标。主页没有这个问题。

---

## 安全性

* 清单 `<uses-permission>` 数量 = **0**，无 `INTERNET` → 物理上无法外发数据
* 组件 = **1 个 Activity + 1 个 AppWidgetProvider**；0 个 Service、0 个 ContentProvider，
  无第三方 SDK（设置弹层是 Activity 内叠的一层 View，不是新增组件）
* 唯一的本地数据是**显示开关**：`SharedPreferences` 里两个 key 集合
  （`cainiao`、`wechat_scan` 这类固定标识），不含设备标识、账号或任何用户内容
* 全部对外 URL 共 **27 条**（16 条 App deep link + 11 条网页兜底），域名只有
  `taobao.com` / `pinduoduo.net` / `yangkeduo.com` / `jd.com` / `xiaohongshu.com` /
  `douyin.com` / `dewu.com` 这些平台官方域名，无 IP 字面量、无上报端点
* 应用**不取码、不读通知、不读短信、不碰相册与账号**：它只做一件事 —— 把目标 App 自己的页面拉起来

---

## 第三方说明

* 本应用是**跳转入口**，不包含任何快递/电商平台的数据。各行显示的应用名称、图标与品牌色
  权利归各自开发者与平台所有，此处仅用于标识「这一行会打开哪个 App」。
* 某个目标 App 未安装时，那一行会退化成网页兜底地址（同样只指向该平台的官方域名）。
* 各 App 的 deep link 都是**内部路由**，不是它们对第三方开放的 API；随版本更新可能失效，
  升级后请复测（复现方法见上文逐条记录）。
