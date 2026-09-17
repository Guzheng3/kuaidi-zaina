package kuaidi.qinghan.vip;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

/** 按「App deep link → 直接启动 App → 网页 → 提示失败」的顺序打开取件入口。 */
final class DeepLinkLauncher {

    private static final String TAG = "PickupDeepLink";

    /** App 内 deep link 的通用跳转标志。 */
    private static final int APP_FLAGS =
            Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP;

    // ---- 微信快捷方式分发协议（manifest 实证：ShortCutDispatchActivity exported 且无权限）----
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String WECHAT_LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI";
    private static final String WECHAT_DISPATCH_ACTION = "com.tencent.mm.ui.ShortCutDispatchAction";
    private static final String WECHAT_LAUNCH_TYPE_KEY = "LauncherUI.Shortcut.LaunchType";

    /** 无过渡动画启动：省掉系统默认的跳转动画，点击到目标页面的感知延迟更低。 */
    private static void startFast(Context context, Intent intent) {
        if (context instanceof Activity) {
            ActivityOptions options = ActivityOptions.makeCustomAnimation(context, 0, 0);
            context.startActivity(intent, options.toBundle());
            return;
        }
        context.startActivity(intent);
    }

    /** 打开常用功能（扫一扫 / 收付款）：微信走分发协议、支付宝走深链，失败退回直启 App。 */
    static void openAction(Context context, QuickAction action) {
        Intent intent = buildActionIntent(action);
        if (intent != null) {
            intent.addFlags(APP_FLAGS | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startFast(context, intent);
                Log.i(TAG, "Opened " + action.appName + " " + action.title);
                return;
            } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
                Log.w(TAG, "Could not open " + action.appName + " " + action.title, e);
            }
        }
        tryOpenInstalledApp(context, action.packageName);
    }

    /** 构造目标应用的直达 Intent；构造失败返回 null（由调用方退回直启 App）。 */
    private static Intent buildActionIntent(QuickAction action) {
        try {
            if (action.dispatchLaunchType != null) {
                // 微信分发协议：LauncherUI 已导出，extras 携带目标功能的 LaunchType
                Intent intent = new Intent(WECHAT_DISPATCH_ACTION);
                intent.setComponent(new ComponentName(WECHAT_PACKAGE, WECHAT_LAUNCHER_UI));
                intent.putExtra(WECHAT_LAUNCH_TYPE_KEY, action.dispatchLaunchType);
                return intent;
            }
            if (action.deepLinkUri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.deepLinkUri));
                intent.setPackage(action.packageName);
                return intent;
            }
        } catch (Throwable t) {
            Log.w(TAG, "buildActionIntent failed", t);
        }
        return null;
    }

    private DeepLinkLauncher() {}

    static void open(Context context, Destination destination) {
        for (String uri : destination.appUris) {
            if (tryOpen(context, uri, destination.packageName)) {
                return;
            }
        }

        if (destination.openAppWhenDeepLinkUnavailable
                && tryOpenInstalledApp(context, destination.packageName)) {
            Toast.makeText(
                            context,
                            "菜鸟身份码入口不可用，已打开菜鸟 App，请在首页进入身份码",
                            Toast.LENGTH_LONG)
                    .show();
            return;
        }

        // 先带着包名跳（让目标 App 自己接住），不行再交给系统选择器。
        if (tryOpen(context, destination.webUri, destination.packageName)) {
            return;
        }
        if (!tryOpen(context, destination.webUri, null)) {
            Toast.makeText(context, "暂时无法打开" + destination.title, Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean tryOpen(Context context, String uri, String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (packageName != null) {
            intent.setPackage(packageName);
        }
        intent.addFlags(APP_FLAGS);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        ComponentName resolved = intent.resolveActivity(context.getPackageManager());
        if (resolved == null) {
            Log.i(TAG, "No activity resolved for " + uri + " with package " + packageName);
            return false;
        }

        try {
            startFast(context, intent);
            Log.i(TAG, "Opened " + uri + " with " + resolved.flattenToShortString());
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Could not open " + uri + " with package " + packageName, e);
            return false;
        }
    }

    private static boolean tryOpenInstalledApp(Context context, String packageName) {
        Intent intent =
                context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Log.i(TAG, "No launch activity found for package " + packageName);
            return false;
        }

        intent.addFlags(APP_FLAGS);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            startFast(context, intent);
            Log.i(TAG, "Opened installed app " + packageName);
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Could not open installed app " + packageName, e);
            return false;
        }
    }
}