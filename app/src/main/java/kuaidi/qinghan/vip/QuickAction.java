package kuaidi.qinghan.vip;

/**
 * 日常常用功能直达：微信 / 支付宝的扫一扫与收付款。
 *
 * <p>两类跳转：
 * <ul>
 *   <li><b>微信</b>：走微信自带的快捷方式分发协议 —— 显式启动已导出的 {@code LauncherUI}，
 *       action {@code com.tencent.mm.ui.ShortCutDispatchAction} +
 *       extra {@code LauncherUI.Shortcut.LaunchType}。协议与取值取自微信 manifest 声明
 *       （{@code ShortCutDispatchActivity} exported 且无权限要求）和系统 dumpsys shortcut
 *       中微信导出的快捷方式定义，真机实测可直达扫一扫 / 收付款原生页。</li>
 *   <li><b>支付宝</b>：{@code alipayqr://platformapi/startapp?saId=...} 深链
 *       （10000007 扫一扫 / 20000056 收付款，真机实测直达原生页）。</li>
 * </ul>
 * 跳转失败时由 {@link DeepLinkLauncher#openAction} 兜底为直启 App。
 */
enum QuickAction {
    WECHAT_SCAN(
            "wechat_scan",
            "微信",
            "扫一扫",
            "com.tencent.mm",
            "launch_type_scan_qrcode",
            null,
            R.color.accent_wechat),
    WECHAT_PAY(
            "wechat_pay",
            "微信",
            "收付款",
            "com.tencent.mm",
            "launch_type_offline_wallet",
            null,
            R.color.accent_wechat),
    ALIPAY_SCAN(
            "alipay_scan",
            "支付宝",
            "扫一扫",
            "com.eg.android.AlipayGphone",
            null,
            "alipayqr://platformapi/startapp?saId=10000007",
            R.color.accent_alipay),
    ALIPAY_PAY(
            "alipay_pay",
            "支付宝",
            "收付款",
            "com.eg.android.AlipayGphone",
            null,
            "alipayqr://platformapi/startapp?saId=20000056",
            R.color.accent_alipay);

    /** 稳定标识：与 {@link Destination#key} 同一命名空间，用于主页显示开关的偏好键。 */
    final String key;
    final String appName;
    final String title;
    final String packageName;
    /** 非空：微信快捷方式分发协议的 LaunchType。 */
    final String dispatchLaunchType;
    /** 非空：支付宝深链。 */
    final String deepLinkUri;
    final int accentRes;

    QuickAction(
            String key,
            String appName,
            String title,
            String packageName,
            String dispatchLaunchType,
            String deepLinkUri,
            int accentRes) {
        this.key = key;
        this.appName = appName;
        this.title = title;
        this.packageName = packageName;
        this.dispatchLaunchType = dispatchLaunchType;
        this.deepLinkUri = deepLinkUri;
        this.accentRes = accentRes;
    }
}