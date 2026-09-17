package kuaidi.qinghan.vip;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

/**
 * 桌面小组件：一行三个入口，分别直达菜鸟 / 淘宝 / 拼多多的身份码。
 *
 * <p>图标优先取目标 App 的真实启动图标（与主页入口行同源，用户一眼能认出），
 * 未安装时回退成品牌色矢量图（复用静态快捷方式那套 {@code ic_shortcut_*}）。
 * RemoteViews 只能传位图，所以真实图标要在这里离屏画成 Bitmap 再交给桌面。
 */
public final class PickupWidgetProvider extends AppWidgetProvider {

    /** 布局里的图标边长（与 pickup_widget.xml 保持一致）。 */
    private static final int ICON_DP = 24;
    /** 位图超采样倍率：桌面可能把小组件拉大，留一点余量，同时控制 Binder 传输体积。 */
    private static final float ICON_SUPERSAMPLE = 1.5f;

    /** 一列入口：容器 / 图标 / 回退图标的资源 id + 点击动作 + 取到的真实图标位图。 */
    private static final class Column {
        final Destination destination;
        final int containerId;
        final int iconId;
        final int fallbackIconRes;
        final int requestCode;
        Bitmap icon;

        Column(
                Destination destination,
                int containerId,
                int iconId,
                int fallbackIconRes,
                int requestCode) {
            this.destination = destination;
            this.containerId = containerId;
            this.iconId = iconId;
            this.fallbackIconRes = fallbackIconRes;
            this.requestCode = requestCode;
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Column[] columns = {
            new Column(
                    Destination.CAINIAO,
                    R.id.widget_cainiao,
                    R.id.widget_icon_cainiao,
                    R.drawable.ic_shortcut_cainiao,
                    101),
            new Column(
                    Destination.TAOBAO,
                    R.id.widget_taobao,
                    R.id.widget_icon_taobao,
                    R.drawable.ic_shortcut_taobao,
                    102),
            new Column(
                    Destination.PINDUODUO,
                    R.id.widget_pinduoduo,
                    R.id.widget_icon_pinduoduo,
                    R.drawable.ic_shortcut_pinduoduo,
                    103)
        };

        // 图标只取一次：多个小组件实例复用同一份位图。
        for (Column column : columns) {
            column.icon = appIconBitmap(context, column.destination.packageName);
        }

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views =
                    new RemoteViews(context.getPackageName(), R.layout.pickup_widget);
            for (Column column : columns) {
                views.setOnClickPendingIntent(
                        column.containerId,
                        pendingIntent(context, column.destination, column.requestCode));
                if (column.icon != null) {
                    views.setImageViewBitmap(column.iconId, column.icon);
                } else {
                    views.setImageViewResource(column.iconId, column.fallbackIconRes);
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private PendingIntent pendingIntent(Context context, Destination destination, int requestCode) {
        Intent intent =
                new Intent(context, MainActivity.class)
                        .setAction(MainActivity.ACTION_OPEN)
                        .putExtra(MainActivity.EXTRA_DESTINATION, destination.key);
        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * 目标 App 的真实图标，离屏画成正方形位图（RemoteViews 只能传位图）。
     * 未安装或取图标失败返回 {@code null}，由调用方回退到品牌色矢量图。
     */
    private Bitmap appIconBitmap(Context context, String packageName) {
        try {
            Drawable icon = context.getPackageManager().getApplicationIcon(packageName);
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int size = Math.max(1, Math.round(ICON_DP * metrics.density * ICON_SUPERSAMPLE));
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            icon.setBounds(0, 0, size, size);
            icon.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }
}
