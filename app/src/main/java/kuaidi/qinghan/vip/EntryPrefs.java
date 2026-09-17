package kuaidi.qinghan.vip;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 主页入口的显示开关：设置弹层里勾选 / 取消的记录。
 *
 * <p>存**两个集合**——「显式隐藏」与「显式显示」。只有用户真正动过的入口才进集合，
 * 没动过的走 {@code defaultVisible}（每个入口自带，见 {@link Destination#defaultVisible}）。
 *
 * <p>为什么要分两个集合：只存隐藏项的话，「默认不勾选」的入口一旦被用户勾上、又想让
 * 以后新增的入口默认不勾，就没有地方区分「用户勾过」与「用户没动过」。分成两个集合后，
 * 语义是：显式隐藏 &gt; 显式显示 &gt; 默认值。
 *
 * <p>用 SharedPreferences，不引入任何权限；写入走 {@code apply()}，不阻塞界面。
 */
final class EntryPrefs {

    private static final String FILE = "entry_visibility";
    /** 用户显式取消勾选的入口。 */
    private static final String KEY_HIDDEN = "hidden";
    /** 用户显式勾选的入口（用于把默认不勾选的入口勾回来）。 */
    private static final String KEY_SHOWN = "shown";

    private EntryPrefs() {}

    /** 该入口是否应出现在主页：显式隐藏 &gt; 显式显示 &gt; 入口自带的默认值。 */
    static boolean isVisible(Context context, String key, boolean defaultVisible) {
        if (hidden(context).contains(key)) {
            return false;
        }
        if (shown(context).contains(key)) {
            return true;
        }
        return defaultVisible;
    }

    /** 勾选 / 取消勾选，立即落盘。 */
    static void setVisible(Context context, String key, boolean visible) {
        // getStringSet 返回的是内部实例，必须复制后再改，否则改的是 SharedPreferences 的内存镜像
        Set<String> hidden = new HashSet<>(hidden(context));
        Set<String> shown = new HashSet<>(shown(context));
        if (visible) {
            hidden.remove(key);
            shown.add(key);
        } else {
            shown.remove(key);
            hidden.add(key);
        }
        prefs(context)
                .edit()
                .putStringSet(KEY_HIDDEN, hidden)
                .putStringSet(KEY_SHOWN, shown)
                .apply();
    }

    private static Set<String> hidden(Context context) {
        return prefs(context).getStringSet(KEY_HIDDEN, Collections.<String>emptySet());
    }

    private static Set<String> shown(Context context) {
        return prefs(context).getStringSet(KEY_SHOWN, Collections.<String>emptySet());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
