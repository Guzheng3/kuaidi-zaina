package kuaidi.qinghan.vip;

import java.util.Locale;

/**
 * 每一个取件入口（菜鸟 / 淘宝 / 拼多多 / 京东 / 小红书 / 抖音 / 得物，身份码与待取列表）。
 *
 * <p>字段含义：
 * <ul>
 *   <li>{@code key} —— 稳定的字符串标识，用于 Intent extra、快捷方式 id 与快捷方式查找</li>
 *   <li>{@code title} —— 完整的入口名（「菜鸟身份码」），用于快捷方式标签与失败提示</li>
 *   <li>{@code appName} —— 目标 App 的显示名（「菜鸟」），主页列表行里作为副标题</li>
 *   <li>{@code packageName} —— 目标 App 包名</li>
 *   <li>{@code appUris} —— 按优先级依次尝试的 App 内 deep link</li>
 *   <li>{@code webUri} —— App 与 deep link 都不可用时的网页兜底地址</li>
 *   <li>{@code openAppWhenDeepLinkUnavailable} —— 是否在 deep link 失效时直接启动目标 App</li>
 * </ul>
 */
enum Destination {
    CAINIAO(
            "cainiao",
            "菜鸟身份码",
            "菜鸟",
            "com.cainiao.wireless",
            // 菜鸟对外的通用路由入口（OpenGuoGuoUrlActivity，已导出）：
            // 它会把 cainiao://startapp/<path> 转成 guoguo://go/<path> 交由内部路由打开。
            // 不能直接用 guoguo://go/station_code —— 该 Activity 未导出，第三方无法发起。
            new String[] {"cainiao://startapp/station_code"},
            "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO",
            true,
            // 默认不勾选：主页默认一屏只放 10 行（6 个待取/待收货 + 4 个常用），
            // 菜鸟 / 小红书 / 得物 这三个归到「要用时再去设置里勾」
            false),
    TAOBAO(
            "taobao",
            "淘宝身份码",
            "淘宝",
            "com.taobao.taobao",
            new String[] {
                "taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fmarket.m.taobao.com%2Fapp%2Fcn-yz%2Fmulti-activity%2FauthCode.html",
                "tbopen://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fmarket.m.taobao.com%2Fapp%2Fcn-yz%2Fmulti-activity%2FauthCode.html"
            },
            "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html",
            false),
    TAOBAO_PENDING(
            "taobao_pending",
            "淘宝待取快递",
            "淘宝",
            "com.taobao.taobao",
            new String[] {
                "taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1100333%2Flast-mile-fe%2Fm-end-school-tab%2Fhome",
                "tbopen://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fpages-fast.m.taobao.com%2Fwow%2Fz%2Funiapp%2F1100333%2Flast-mile-fe%2Fm-end-school-tab%2Fhome"
            },
            "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home",
            false),
    TAOBAO_RECEIVE(
            "taobao_receive",
            "淘宝待收货",
            "淘宝",
            "com.taobao.taobao",
            // 淘宝原生订单列表 TBOrderListActivity 自己注册的对外入口（清单实证）：
            // scheme=taobao + host=go + path=/my_orders，tabCode 选到「待收货」。
            // 别用 tbopen 包 H5：淘宝网页版在 App 内是未登录态，订单页会被重定向回首页。
            new String[] {"taobao://go/my_orders?tabCode=waitConfirm"},
            "https://main.m.taobao.com/order/index.html?hybrid=true",
            false),
    PINDUODUO(
            "pinduoduo",
            "拼多多身份码",
            "拼多多",
            "com.xunmeng.pinduoduo",
            new String[] {
                "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11&refer_page_name=login&refer_page_sn=10169",
                "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11",
                "pinduoduo://com.xunmeng.pinduoduo/mdkd/package"
            },
            "https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0",
            false),
    PINDUODUO_PENDING(
            "pinduoduo_pending",
            "拼多多待取快递",
            "拼多多",
            "com.xunmeng.pinduoduo",
            new String[] {"pinduoduo://com.xunmeng.pinduoduo/mdkd/package"},
            "https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0",
            false),
    PINDUODUO_RECEIVE(
            "pinduoduo_receive",
            "拼多多待收货",
            "拼多多",
            "com.xunmeng.pinduoduo",
            // 拼多多的订单页是 orders.html，type 选标签（实测 type=3 = 待收货）；
            // 后面三个参数是抖音式页面自身的埋点/tab 开关，照抄 dex 里的原文。
            new String[] {
                "pinduoduo://com.xunmeng.pinduoduo/orders.html?type=3&comment_tab=1&combine_orders=1&main_orders=1"
            },
            // 拼多多没有公开的订单页 H5（dex 里只有 mobile.yangkeduo.com 这个域名可确认），
            // 兜底就给到站点首页——和 XHS 那条的处理方式一致
            "https://mobile.yangkeduo.com/",
            false),
    JD(
            "jd",
            "京东待取快递",
            "京东",
            "com.jingdong.app.mall",
            // openjd 是京东对外的唤起协议，params 为 URL 编码后的 JSON：
            // {"category":"jump","des":"orderlist"} —— 直接落到「我的订单」列表。
            new String[] {
                "openjd://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22orderlist%22%7D"
            },
            "https://order.jd.com/center/list.action",
            false),
    XHS(
            "xhs",
            "小红书待取快递",
            "小红书",
            "com.xingin.xhs",
            // xhsdiscover 是小红书的唤起协议，rn/lancer-order/order/list 为订单列表页路由。
            new String[] {"xhsdiscover://rn/lancer-order/order/list"},
            "https://www.xiaohongshu.com/",
            false,
            // 默认不勾选：主页一屏放不下这么多入口，小红书归到「要用时再去设置里勾」
            false),
    DOUYIN(
            "douyin",
            "抖音待取快递",
            "抖音",
            "com.ss.android.ugc.aweme",
            // 抖音没有独立的订单页路由：订单是「商城」里的一个 tab（内部路由 //mall/xtab）。
            // 这条是抖音自己给「订单」入口内嵌的 schema —— 用 delivery_value 指定订单页的
            // Lynx 包，tab_id 选到「待收货/使用」，order_guide_highlight 让商城高亮该入口。
            // 参数是双重 URL 编码的（%253D = 编码后的 %3D），照抄抖音的原文，别手写解码。
            new String[] {
                "snssdk1128://mall/xtab?enter_from=shortcut_fyp&force_refresh=1"
                        + "&delivery_type=webcast_lynxview"
                        + "&delivery_value=https%253A%252F%252Flf-webcast-sourcecdn-tos.bytegecko.com"
                        + "%252Fobj%252Fbyte-gurd-source%252Fwebcast%252Ffalcon%252Fdouyin"
                        + "%252Fecommerce_orders_douyin%252Fapp_new%252Ftemplate.js"
                        + "%253Fenter_from%253Dshortcut_order%2526tab_id%253D3"
                        + "&order_guide_highlight=1&launch_mode=os_1screen"
            },
            "https://www.douyin.com/",
            false),
    DEWU(
            "dewu",
            "得物待取快递",
            "得物",
            "com.shizhuang.duapp",
            // 得物对外有 duapplauncher / dewuapp 两个无 host 限制的通用入口（都指向 DeepLinkActivity），
            // 但 https://m.dewu.com 只注册了 /router/product/ProductDetail 一条路径，订单页走不通。
            // 得物自己的「我的」页配置里，订单各标签都是 https://m.dewu.com/router/order/buyer/orderList?tabId=N
            // （tabId 0=全部 1=待付款 2=待发货 3=待收货 6=待评价），换成 dewuapp:// 就能直达。
            new String[] {"dewuapp://m.dewu.com/router/order/buyer/orderList?tabId=3"},
            "https://m.dewu.com/",
            false,
            // 默认不勾选：同小红书，主页一屏放不下
            false);

    final String key;
    final String title;
    final String appName;
    final String packageName;
    final String[] appUris;
    final String webUri;
    final boolean openAppWhenDeepLinkUnavailable;
    /** 该入口在设置弹层里是否**默认**勾选（用户勾过/取消过就以用户的为准）。 */
    final boolean defaultVisible;

    Destination(
            String key,
            String title,
            String appName,
            String packageName,
            String[] appUris,
            String webUri) {
        this(key, title, appName, packageName, appUris, webUri, false, true);
    }

    Destination(
            String key,
            String title,
            String appName,
            String packageName,
            String[] appUris,
            String webUri,
            boolean openAppWhenDeepLinkUnavailable) {
        this(key, title, appName, packageName, appUris, webUri, openAppWhenDeepLinkUnavailable, true);
    }

    Destination(
            String key,
            String title,
            String appName,
            String packageName,
            String[] appUris,
            String webUri,
            boolean openAppWhenDeepLinkUnavailable,
            boolean defaultVisible) {
        this.key = key;
        this.title = title;
        this.appName = appName;
        this.packageName = packageName;
        this.appUris = appUris;
        this.webUri = webUri;
        this.openAppWhenDeepLinkUnavailable = openAppWhenDeepLinkUnavailable;
        this.defaultVisible = defaultVisible;
    }

    /** 按 key 查找，大小写不敏感；找不到返回 {@code null}。 */
    static Destination fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (Destination destination : values()) {
            if (destination.key.equals(normalized)) {
                return destination;
            }
        }
        return null;
    }
}
