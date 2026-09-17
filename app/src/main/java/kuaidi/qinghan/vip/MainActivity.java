package kuaidi.qinghan.vip;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主页：按用途分组的取件入口列表。界面全部由代码构建。
 *
 * <p>结构：顶部标题栏（实色，滚动内容从它下方穿过）→ 分组卡片（取件码 / 常用），
 * 卡片内先是分组名，再是入口行。入口行 = 目标 App 的真实图标 + 功能名（身份码 /
 * 待取快递 / 扫一扫 / 收付款）+ 所属应用名 + 箭头；目标 App 未安装时图标压暗、
 * 副标题改「未安装」。
 *
 * <p>视觉是**纯实色**：页面底色 + 卡片表面 + 一道发丝线。没有壁纸照片，也没有毛玻璃 /
 * 折射着色器 —— 因此不依赖 GPU 能力（AGSL 只在 API 33+ 且驱动支持时可用），任何设备上
 * 观感一致，也不必为了"压在照片上还要看得清"去做 tint / 降级那一套。
 *
 * <p>标题栏右上角是设置入口：底部弹层里勾选要显示哪些入口，取消勾选的入口从主页消失
 * （偏好见 {@link EntryPrefs}）。弹层是叠在根布局上的一层普通 View，不是新 Activity ——
 * 本应用对外仍然只有 1 个 Activity，弹层因此可以直接复用这里的排版与取色方法。
 *
 * <p>颜色全部走 {@code res/values[-night]/colors.xml} 语义令牌，深浅色自动适配；
 * 动效只使用 transform / opacity，统一时长与缓动，入场 stagger，尊重系统「移除动画」设置。
 */
public final class MainActivity extends Activity {

    /** 快捷方式 / 小组件带参启动时使用的 action。 */
    public static final String ACTION_OPEN = "kuaidi.qinghan.vip.OPEN";

    /** 目标入口的 key，取值见 {@link Destination#key}。 */
    public static final String EXTRA_DESTINATION = "destination";

    // ---- 设计令牌：颜色（资源 id，值见 res/values[-night]/colors.xml）----
    private static final int COLOR_PAGE_BACKGROUND = R.color.page_background;
    private static final int COLOR_SURFACE = R.color.surface;
    private static final int COLOR_CARD_STROKE = R.color.card_stroke;
    private static final int COLOR_TEXT_PRIMARY = R.color.text_primary;
    private static final int COLOR_TEXT_SECONDARY = R.color.text_secondary;
    /** 行分隔线 / 行涟漪的透明度（0-255）：都取主文字色的低透明度，深浅色下自动反过来。 */
    private static final int DIVIDER_ALPHA = 0x1a;
    private static final int ROW_RIPPLE_ALPHA = 0x1f;
    /** 目标 App 未安装时图标的透明度。 */
    private static final float ICON_MISSING_ALPHA = 0.45f;

    // ---- 设计令牌：尺寸与间距 ----
    // 主页默认一屏放满：默认勾选 10 行（取件码 6 + 常用 4），菜鸟 / 小红书 / 得物 默认不勾。
    // 行高与组间距是按「这 10 行正好铺满 360dp×791dp」标定的（实测末行底边 2264，
    // 可用高度到 2289）。用户再勾上默认不勾的入口时行数变多，ScrollView 兜底往下滚动。
    private static final int PAGE_PADDING_DP = 16;
    private static final int PAGE_BOTTOM_DP = 12;
    /** 标题栏与首个分组之间的留白。 */
    private static final int HEADER_GAP_DP = 10;
    /** 两个分组卡片之间的留白。 */
    private static final int SECTION_GAP_DP = 14;
    /** 卡片圆角：13.14dp（用户指定）。 */
    private static final float CARD_RADIUS_DP = 13.14f;
    private static final int CARD_STROKE_DP = 1;
    private static final int HEADER_PADDING_TOP_DP = 10;
    private static final int HEADER_PADDING_BOTTOM_DP = 10;
    private static final int PANEL_HEADER_PADDING_TOP_DP = 7;
    private static final int PANEL_HEADER_PADDING_BOTTOM_DP = 6;
    /** 入口行最小行高：40dp 图标 + 上下各 8dp 留白 = 56dp，高于 Android 推荐的 48dp 触控目标。 */
    private static final int ROW_MIN_HEIGHT_DP = 56;
    private static final int ROW_PADDING_START_DP = 14;
    private static final int ROW_PADDING_END_DP = 14;
    private static final int ROW_PADDING_VERTICAL_DP = 8;
    private static final int ROW_ICON_DP = 40;
    private static final int ROW_ICON_GAP_DP = 14;
    private static final int ROW_LABEL_GAP_DP = 5;
    private static final int CHEVRON_DP = 18;
    /** 标题栏图标按钮的外框（图标 24dp 居中）：撑到 48dp 以下的推荐最小触控尺寸之上。 */
    private static final int HEADER_ACTION_DP = 44;
    /** 设置页行尾的勾选框：24dp 方框、7dp 圆角、2dp 描边，对勾 16dp 居中。 */
    private static final int CHECK_SIZE_DP = 24;
    private static final int CHECK_RADIUS_DP = 7;
    private static final int CHECK_STROKE_DP = 2;
    private static final int CHECK_TICK_DP = 16;
    /** 勾选框未选中时的描边透明度（0-255）：够看清边框，又不跟已选中的实心框抢注意力。 */
    private static final int CHECK_STROKE_ALPHA = 0x80;
    /** 设置页单行列表的最小行高：只有一行文字，比主页两行入口行矮一截。 */
    private static final int TOGGLE_ROW_MIN_HEIGHT_DP = 52;

    // ---- 设计令牌：排版 ----
    private static final float HEADER_TITLE_SP = 24f;
    private static final float SECTION_LABEL_SP = 13f;
    private static final float ROW_TITLE_SP = 16f;
    private static final float ROW_SUBTITLE_SP = 12f;

    // ---- 设计令牌：动效 ----
    // 统一时长与缓动：进入整体 240ms、每项 36ms 错峰；按压 60ms 起效、160ms 回弹；
    // 全部只动 transform / opacity，不引起布局回流；系统「移除动画」为 0 时整体跳过。
    private static final long ENTER_DURATION = 240L;
    private static final long ENTER_STAGGER = 36L;
    private static final long PRESS_DOWN_MS = 60L;
    private static final long PRESS_UP_MS = 160L;
    /** 设置页进 / 出场的时长（透明度 + 12dp 位移）。 */
    private static final long SETTINGS_DURATION = 200L;
    /** 按下时图标的缩放（行本身不缩放：整行缩放会让卡片边缘露白）。 */
    private static final float ICON_PRESS_SCALE = 0.9f;
    private static final float PRESS_SLIDE_DP = 3f;
    // 缓动对齐 skill 规范：cubic-bezier(.2,.8,.2,1)
    private static final Interpolator EASE_OUT = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    /** 入场动画的参与者，按视觉顺序记录，统一错峰播放入场。 */
    private final List<View> entranceQueue = new ArrayList<>();

    /** 系统栏 inset（px）：API 35+ 才有值，更低版本窗口本身不沉浸，两个都是 0。 */
    private int systemBarTop;
    private int systemBarBottom;
    private FrameLayout rootView;
    private View headerView;
    private ScrollView contentScroll;
    private LinearLayout contentColumn;
    /**
     * 设置弹层（打开中或正在关闭，非 null 即占用）：返回键、遮罩点击都看它。
     * 关闭动画期间也不置空——重开时靠它把旧面板摘掉，防止两个面板叠着显示。
     */
    private View settingsView;
    /** 弹层里的底部面板：退场动画要单独把它往下滑，所以单独留一份引用。 */
    private View settingsSheet;
    /** 关闭动画进行中：这期间再按返回不重复触发关闭，也让重开先收掉旧面板。 */
    private boolean settingsClosing;
    /** Android 13+ 的返回回调，见 {@link #registerBackCallback()}。 */
    private OnBackInvokedCallback backCallback;
    /**
     * 目标 App 启动图标的进程内缓存：主页每次重建（设置页关闭时）都要给 15 行重新取图标，
     * 不缓存就得一遍遍戳 PackageManager；图标是只读共用，多个 ImageView 同时显示没问题。
     */
    private final Map<String, Drawable> appIconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (dispatchDestination(getIntent())) {
            return;
        }

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(isNight() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        // 35+ 窗口是 edge-to-edge，状态栏透明、由根布局的页面底色透上来；
        // 更低版本窗口本身不延伸到状态栏，把状态栏染成同色避免断层。
        getWindow()
                .setStatusBarColor(
                        Build.VERSION.SDK_INT >= 35
                                ? Color.TRANSPARENT
                                : color(COLOR_PAGE_BACKGROUND));
        setContentView(buildRoot());
        // post 在首帧绘制前执行：在这里才置初始状态，避免闪一下「最终态」。
        getWindow().getDecorView().post(this::animateEntrance);
    }

    /**
     * 返回键：设置弹层开着就先关弹层。
     *
     * <p>Android 13+ 上如果注册过 {@code OnBackInvokedCallback}，系统会走回调而不再调用这里；
     * 两条路都留着，才能同时覆盖「预测性返回开 / 关」两种状态。
     */
    @Override
    public void onBackPressed() {
        if (settingsView != null) {
            // 关闭动画进行中按返回：面板本来就在退场，不再重复触发
            if (!settingsClosing) {
                closeSettings();
            }
            return;
        }
        super.onBackPressed();
    }

    /**
     * 本应用是 {@code singleTask}：应用开着时再从桌面图标点开 / 快捷方式 / 小组件触发，
     * 不再新建实例，而是把 Intent 送到这里。带目标入口的照常跳转并关掉自己，
     * 普通启动（主页已在前台）什么都不做——这正是「返回不会重复出现界面」的关键：
     * 叠出来的第二个实例没了，返回键永远只需要一次就能退出。
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        dispatchDestination(intent);
    }

    /**
     * 带目标入口（{@link #ACTION_OPEN} 等）的 Intent：跳转目标并关掉自己，返回 {@code true}；
     * 普通启动（桌面图标）返回 {@code false}，由调用方继续构建主页。
     */
    private boolean dispatchDestination(Intent intent) {
        setIntent(intent);
        Destination destination = destinationFromIntent();
        if (destination == null) {
            return false;
        }
        DeepLinkLauncher.open(this, destination);
        finish();
        return true;
    }

    /** 桌面图标走 ACTION_MAIN 时返回 {@code null}，其余情况解析出目标入口。 */
    private Destination destinationFromIntent() {
        String action = getIntent().getAction();
        if (ACTION_OPEN.equals(action)) {
            return Destination.fromKey(getIntent().getStringExtra(EXTRA_DESTINATION));
        }
        if ("kuaidi.qinghan.vip.OPEN_CAINIAO".equals(action)) {
            return Destination.CAINIAO;
        }
        if ("kuaidi.qinghan.vip.OPEN_TAOBAO".equals(action)) {
            return Destination.TAOBAO;
        }
        if ("kuaidi.qinghan.vip.OPEN_PINDUODUO".equals(action)) {
            return Destination.PINDUODUO;
        }
        if ("kuaidi.qinghan.vip.OPEN_TAOBAO_PENDING".equals(action)) {
            return Destination.TAOBAO_PENDING;
        }
        if ("kuaidi.qinghan.vip.OPEN_PINDUODUO_PENDING".equals(action)) {
            return Destination.PINDUODUO_PENDING;
        }
        return null;
    }

    /** 根布局：页面底色 → 滚动内容 → 顶层标题栏（设置页在需要时再叠上来）。 */
    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(COLOR_PAGE_BACKGROUND));
        root.addView(buildContent(), new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        headerView = buildHeaderBar();
        FrameLayout.LayoutParams headerParams =
                new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        headerParams.gravity = Gravity.TOP;
        root.addView(headerView, headerParams);
        rootView = root;
        return root;
    }

    /**
     * 顶部标题栏：与页面同色但不透明，滚动内容从它下方穿过时被挡住，底边一道发丝线分界。
     *
     * <p>标题栏高度取决于字号与状态栏 inset，无法在编译期写死，因此内容区的上内边距
     * 由 {@link #applyContentPadding()} 在标题栏布局完成后回填。
     */
    private View buildHeaderBar() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.header_background);
        // 右内边距只给 12dp：设置按钮外框 44dp、24dp 图标居中其中，
        // 图标的右缘因此落在 22dp 上，与左边 20dp 的标题基本对齐
        header.setPadding(
                dp(20), dp(HEADER_PADDING_TOP_DP), dp(12), dp(HEADER_PADDING_BOTTOM_DP));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(
                text("快递在哪儿", HEADER_TITLE_SP, color(COLOR_TEXT_PRIMARY), Typeface.BOLD));
        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        header.addView(
                headerAction(
                        R.drawable.ic_settings,
                        "设置",
                        COLOR_TEXT_SECONDARY,
                        view -> openSettings()));

        // 标题栏高度变化（首帧布局、状态栏 inset、字号缩放、旋转）都要重算内容区上内边距。
        header.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (bottom - top != oldBottom - oldTop) {
                        applyContentPadding();
                    }
                });

        applyHeaderInsets(header);

        // 入场顺序即视觉顺序：标题栏在最上，先动
        entranceQueue.add(0, header);
        return header;
    }

    /**
     * 标题栏的状态栏 inset：35+ 是 edge-to-edge，标题栏自己把 bars.top 加到上内边距里；
     * 更低版本窗口不延伸到状态栏，不需要加（也不注册监听）。
     *
     * <p>先用手上已知的 {@link #systemBarTop} 垫一次：设置页是运行时才插进视图树的，
     * 不能假设它挂上之后一定还会收到一次 inset 回调。
     */
    private void applyHeaderInsets(View header) {
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        header.setPadding(
                header.getPaddingLeft(),
                systemBarTop + dp(HEADER_PADDING_TOP_DP),
                header.getPaddingRight(),
                header.getPaddingBottom());
        header.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    systemBarTop = bars.top;
                    view.setPadding(
                            view.getPaddingLeft(),
                            bars.top + dp(HEADER_PADDING_TOP_DP),
                            view.getPaddingRight(),
                            view.getPaddingBottom());
                    return insets;
                });
    }

    /**
     * 标题栏上的图标按钮：44dp 圆形触控区 + 24dp 图标，按下是圆形涟漪。
     * 图标与行尾箭头一样运行时染色，深浅色自动跟随。
     */
    private View headerAction(
            int iconRes, String description, int colorRes, View.OnClickListener click) {
        FrameLayout button = new FrameLayout(this);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        button.setBackground(
                new RippleDrawable(
                        rippleState(tint(color(COLOR_TEXT_PRIMARY), ROW_RIPPLE_ALPHA)), null, mask));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable drawable = getDrawable(iconRes).mutate();
        drawable.setTint(color(colorRes));
        icon.setImageDrawable(drawable);
        button.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));

        button.setLayoutParams(
                new LinearLayout.LayoutParams(dp(HEADER_ACTION_DP), dp(HEADER_ACTION_DP)));
        button.setContentDescription(description);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(click);
        return button;
    }

    /** 滚动内容：两个分组（取件码 / 常用），每组 = 一张卡片，卡片内先是分组名再是入口行。 */
    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        contentScroll = scroll;

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        // 组间距与底部留白都放在内容里，不放 ScrollView 的 padding：
        // ScrollView 默认 clipToPadding=true，内容画不进 padding 区，底部 padding 会把最后一行切掉。
        column.setPadding(
                dp(PAGE_PADDING_DP),
                dp(HEADER_GAP_DP),
                dp(PAGE_PADDING_DP),
                dp(PAGE_BOTTOM_DP));
        contentColumn = column;
        scroll.addView(column, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        renderContent(true);

        // Android 15+（target 35）强制 edge-to-edge：内容会延伸到状态栏/手势条之下，
        // 这里记住上下系统栏 inset，由 applyContentPadding() 统一折算成内边距。
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                        systemBarTop = bars.top;
                        systemBarBottom = bars.bottom;
                        applyContentPadding();
                        return insets;
                    });
        }
        return scroll;
    }

    /**
     * 按当前显示开关重建主页内容：只放勾选过的入口，整个分组都被隐藏时连卡片带组名一起不出现，
     * 一个入口都不剩时给一句空状态提示。
     *
     * @param firstBuild 首帧构建，把新卡片登记进入场动画队列；设置页返回时重建则不动队列
     *                   （旧卡片已经不在视图树里，队列不该继续持有它们）
     */
    private void renderContent(boolean firstBuild) {
        if (contentColumn == null) {
            return;
        }
        contentColumn.removeAllViews();
        if (!firstBuild) {
            entranceQueue.clear();
        }

        List<View> cards = new ArrayList<>();
        List<Entry> pickup = visibleEntries(pickupEntries());
        List<Entry> quick = visibleEntries(quickEntries());
        if (!pickup.isEmpty()) {
            cards.add(buildSection("取件码", pickup));
        }
        if (!quick.isEmpty()) {
            cards.add(buildSection("常用", quick));
        }

        if (cards.isEmpty()) {
            contentColumn.addView(buildEmptyState(), wrapParams());
        } else {
            for (int i = 0; i < cards.size(); i++) {
                // 组间距由这里按「第几张卡片」给：某个分组被隐藏时，剩下的卡片自动补上它的位置
                LinearLayout.LayoutParams params = wrapParams();
                params.topMargin = i == 0 ? 0 : dp(SECTION_GAP_DP);
                contentColumn.addView(cards.get(i), params);
            }
        }

        if (firstBuild) {
            entranceQueue.addAll(cards);
        }
        applyContentPadding();
    }

    /** 按偏好过滤入口：设置页里没勾的都不出现在主页。 */
    private List<Entry> visibleEntries(List<Entry> entries) {
        List<Entry> shown = new ArrayList<>();
        for (Entry entry : entries) {
            if (EntryPrefs.isVisible(this, entry.key, entry.defaultVisible)) {
                shown.add(entry);
            }
        }
        return shown;
    }

    /** 一个入口都不显示时的空状态：不是报错，只是告诉用户去哪儿加回来。 */
    private View buildEmptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(12), dp(64), dp(12), dp(12));

        // 居中写在每个 TextView 自己身上（撑满宽度 + 文字居中）：
        // 容器只负责纵向排列，这样两行文字各自居中，不受行宽差异影响
        TextView title = text("入口都被隐藏了", 16f, color(COLOR_TEXT_PRIMARY), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, wrapParams());

        TextView hint =
                text(
                        "点右上角的设置，勾选想显示的入口",
                        13f,
                        color(COLOR_TEXT_SECONDARY),
                        Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = topMarginParams(8);
        box.addView(hint, hintParams);
        return box;
    }

    /**
     * 内容区上内边距 = 标题栏实际高度（滚动内容正好在标题栏下缘被裁掉，底边那道发丝线即裁切线）。
     *
     * <p>标题栏高度里已经含状态栏 inset（它自己的 padding 就加了 {@code bars.top}），
     * 这里不能再加一次，否则内容会被整体下推一个状态栏的高度。组间距（{@link #HEADER_GAP_DP}）
     * 与底部系统栏 inset 都算在内容自己的 padding 里，见 {@link #buildContent()}。
     */
    private void applyContentPadding() {
        if (contentScroll == null || headerView == null || contentColumn == null) {
            return;
        }
        contentScroll.setPadding(0, headerView.getHeight(), 0, 0);
        contentColumn.setPadding(
                dp(PAGE_PADDING_DP),
                dp(HEADER_GAP_DP),
                dp(PAGE_PADDING_DP),
                systemBarBottom + dp(PAGE_BOTTOM_DP));
    }

    /** 分组名（「取件码」「常用」）：小号粗体 + 字距，主页卡片与设置弹层共用。 */
    private TextView sectionHeading(String label) {
        TextView heading = text(label, SECTION_LABEL_SP, color(COLOR_TEXT_SECONDARY), Typeface.BOLD);
        heading.setLetterSpacing(0.05f);
        heading.setPadding(
                dp(ROW_PADDING_START_DP),
                dp(PANEL_HEADER_PADDING_TOP_DP),
                dp(ROW_PADDING_END_DP),
                dp(PANEL_HEADER_PADDING_BOTTOM_DP));
        return heading;
    }

    /** 分组卡片的公共骨架：实色表面 + 组名，以及组名下的那条分隔线。 */
    private LinearLayout newCard(String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardSurface());
        card.addView(sectionHeading(label), wrapParams());
        card.addView(buildDivider(ROW_PADDING_START_DP));
        return card;
    }

    /** 行间分隔线：从行内文字起始位置起（与组名下那条对齐），不到卡片右缘。 */
    private void addRowDivider(LinearLayout card) {
        card.addView(buildDivider(ROW_PADDING_START_DP + ROW_ICON_DP + ROW_ICON_GAP_DP));
    }

    /** 主页的分组卡片：组名 + 入口行（点击即跳转）。 */
    private View buildSection(String label, List<Entry> entries) {
        LinearLayout card = newCard(label);
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                addRowDivider(card);
            }
            card.addView(
                    buildRow(entries.get(i), i == 0, i == entries.size() - 1), wrapParams());
        }
        return card;
    }

    /** 设置弹层里的一个分组：组名 + 勾选行，行间线与组名那条对齐（都从 14dp 起）。 */
    private void addToggleGroup(LinearLayout column, String label, List<Entry> entries, int topMarginDp) {
        TextView heading = sectionHeading(label);
        LinearLayout.LayoutParams headingParams = wrapParams();
        headingParams.topMargin = dp(topMarginDp);
        column.addView(heading, headingParams);
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                column.addView(buildDivider(ROW_PADDING_START_DP));
            }
            column.addView(
                    buildToggleRow(entries.get(i), i == 0, i == entries.size() - 1), wrapParams());
        }
    }

    // ---- 设置弹层 -------------------------------------------------------------

    /**
     * 打开设置弹层：遮罩 + 底部面板，不新开 Activity。
     *
     * <p>本应用对外只有 1 个 Activity（见 README 的组件表），弹层直接复用这里的
     * 排版 / 取色 / 圆角方法，也不必再往清单里加组件。关闭方式有三条：
     * 点遮罩、返回键、Android 13+ 的预测性返回。
     */
    private void openSettings() {
        if (rootView == null) {
            return;
        }
        if (settingsView != null) {
            // 弹层还开着、或关闭动画还没播完：先把旧面板摘掉再开新的，
            // 否则新旧两个面板会叠在一起显示（退场中的那个还在半透明下滑）
            settingsView.animate().cancel();
            rootView.removeView(settingsView);
            settingsView = null;
            settingsClosing = false;
            settingsSheet = null;
            unregisterBackCallback();
        }

        FrameLayout popup = new FrameLayout(this);

        // 遮罩：盖住整屏（主页只露上半屏可见），点击空白处 = 关闭
        View scrim = new View(this);
        scrim.setBackgroundColor(0x59000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(view -> closeSettings());
        popup.addView(scrim, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        LinearLayout sheet = buildSettingsSheet();
        FrameLayout.LayoutParams sheetParams =
                new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        sheetParams.gravity = Gravity.BOTTOM;
        popup.addView(sheet, sheetParams);

        rootView.addView(popup, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        settingsView = popup;
        settingsSheet = sheet;
        registerBackCallback();

        if (animationsAllowed()) {
            scrim.setAlpha(0f);
            sheet.setAlpha(0f);
            sheet.setTranslationY(dp(64));
            scrim.animate()
                    .alpha(1f)
                    .setDuration(SETTINGS_DURATION)
                    .setInterpolator(EASE_OUT)
                    .start();
            sheet.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(SETTINGS_DURATION)
                    .setInterpolator(EASE_OUT)
                    .start();
        }
    }

    /**
     * 底部面板：抓柄 + 标题 + 两组勾选列表，上两角与主页卡片同半径。
     *
     * <p>列表高度先按内容预量一次：内容短就撑到自然高度，超过上限（屏幕 66%）就压成
     * 滚动区——矮屏上弹层也不至于盖满整屏，遮罩区一点不剩。
     */
    private LinearLayout buildSettingsSheet() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color(COLOR_SURFACE));
        background.setCornerRadii(
                new float[] {
                    dp(CARD_RADIUS_DP), dp(CARD_RADIUS_DP), 0, 0, 0, 0, 0, 0
                });
        sheet.setBackground(background);
        // 底部垫系统栏 inset：35+ 手势条区域不让内容贴边
        sheet.setPadding(0, dp(10), 0, systemBarBottom + dp(PAGE_BOTTOM_DP));

        View grabber = new View(this);
        grabber.setBackground(rounded(tint(color(COLOR_TEXT_SECONDARY), 0x4d), 2));
        // 注意：LinearLayout.LayoutParams 的三参构造是 (宽, 高, weight)，没有 gravity 参数——
        // 把 Gravity 常量传进去只会变成 weight=1 且默认左对齐（抓柄跑到左上角的 bug 就是这么来的），
        // 居中要靠 gravity 字段
        LinearLayout.LayoutParams grabberParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        grabberParams.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(grabber, grabberParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(20), dp(14), dp(20), dp(6));
        titles.addView(text("设置", 18f, color(COLOR_TEXT_PRIMARY), Typeface.BOLD));
        TextView subtitle =
                text(
                        "勾选要显示在主页的入口",
                        12f,
                        color(COLOR_TEXT_SECONDARY),
                        Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = wrapParams();
        subtitleParams.topMargin = dp(4);
        titles.addView(subtitle, subtitleParams);
        sheet.addView(titles, wrapParams());

        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        addToggleGroup(column, "取件码", pickupEntries(), 0);
        addToggleGroup(column, "常用", quickEntries(), SECTION_GAP_DP);
        scroll.addView(column, wrapParams());

        // 预量列表高度：column 还没挂进视图树，先按屏幕宽度测一遍
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        column.measure(
                MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int contentHeight = column.getMeasuredHeight();
        int maxScroll = (int) (getResources().getDisplayMetrics().heightPixels * 0.66f);
        scroll.setLayoutParams(
                new LinearLayout.LayoutParams(MATCH_PARENT, Math.min(contentHeight, maxScroll)));
        sheet.addView(scroll);
        return sheet;
    }

    /**
     * 设置页的一行：一行完整入口名（「菜鸟身份码」）+ 勾选框，点**整行**切换
     * （只让 24dp 的方框可点太小了）。勾选状态立即落盘，不必再点「保存」。
     */
    private View buildToggleRow(Entry entry, boolean first, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(TOGGLE_ROW_MIN_HEIGHT_DP));
        row.setPadding(
                dp(ROW_PADDING_START_DP),
                dp(ROW_PADDING_VERTICAL_DP),
                dp(ROW_PADDING_END_DP),
                dp(ROW_PADDING_VERTICAL_DP));
        row.setBackground(rowRipple(first, last, tint(color(entry.accentRes), ROW_RIPPLE_ALPHA)));
        row.setClickable(true);
        row.setFocusable(true);

        TextView label =
                text(entry.fullName(), ROW_TITLE_SP, color(COLOR_TEXT_PRIMARY), Typeface.NORMAL);
        // 文字撑满剩余宽度、右端留白给勾选框：读屏与点击都落在整行上
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        labelParams.rightMargin = dp(ROW_ICON_GAP_DP);
        row.addView(label, labelParams);

        CheckView check = new CheckView();
        check.setChecked(EntryPrefs.isVisible(this, entry.key, entry.defaultVisible), false);
        row.addView(check, new LinearLayout.LayoutParams(dp(CHECK_SIZE_DP), dp(CHECK_SIZE_DP)));

        updateToggleDescription(row, entry, check.isChecked());
        row.setOnClickListener(
                view -> {
                    boolean next = !check.isChecked();
                    check.setChecked(next, true);
                    EntryPrefs.setVisible(this, entry.key, next);
                    updateToggleDescription(view, entry, next);
                    // 主页实时重建：弹层只盖住下半屏，勾完立刻能看到上面列表的变化
                    renderContent(false);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                });
        return row;
    }

    /** 勾选框是自绘的，读屏拿不到选中态，把它写进整行的描述里。 */
    private void updateToggleDescription(View row, Entry entry, boolean visible) {
        row.setContentDescription(entry.fullName() + (visible ? "，已显示" : "，已隐藏"));
    }

    /**
     * 关闭设置弹层：遮罩淡出、面板下滑，动画结束只摘掉弹层。
     *
     * <p>主页在每次勾选时已经实时更新（弹层只盖住下半屏，主页上半部分一直可见），
     * 关闭时不需要再重建，因此退场是纯动画、没有任何主线程重活。
     */
    private void closeSettings() {
        if (settingsView == null || settingsClosing) {
            return;
        }
        final View popup = settingsView;
        final View sheet = settingsSheet;
        settingsClosing = true;
        settingsSheet = null;
        unregisterBackCallback();

        if (!animationsAllowed()) {
            rootView.removeView(popup);
            settingsView = null;
            settingsClosing = false;
            return;
        }
        popup.animate()
                .alpha(0f)
                .setDuration(SETTINGS_DURATION)
                .setInterpolator(EASE_OUT)
                .withEndAction(
                        () -> {
                            rootView.removeView(popup);
                            // 只清自己的引用：如果这期间用户已经重开了新弹层（settingsView 已换），
                            // 不能把新弹层一起清掉
                            if (settingsView == popup) {
                                settingsView = null;
                            }
                            settingsClosing = false;
                        })
                .start();
        if (sheet != null) {
            sheet.animate()
                    .translationY(dp(64))
                    .setDuration(SETTINGS_DURATION)
                    .setInterpolator(EASE_OUT)
                    .start();
        }
    }

    /** Android 13+ 的返回派发，配合 {@link #onBackPressed()} 一起用，见那里的说明。 */
    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        backCallback = this::closeSettings;
        getOnBackInvokedDispatcher()
                .registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }

    private void unregisterBackCallback() {
        if (Build.VERSION.SDK_INT < 33 || backCallback == null) {
            return;
        }
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        backCallback = null;
    }

    /**
     * 设置页行尾的勾选框：未选中是描边空框，选中是实心框 + 反色对勾。
     *
     * <p>自绘而不用框架 {@code CheckBox}：框架控件自带 Material 尺寸与着色，
     * 与这里的行高、圆角、文字色对不上。
     */
    private final class CheckView extends FrameLayout {

        private final ImageView tick;
        private boolean checked;

        CheckView() {
            super(MainActivity.this);
            tick = new ImageView(MainActivity.this);
            tick.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Drawable mark = getDrawable(R.drawable.ic_check).mutate();
            mark.setTint(color(COLOR_PAGE_BACKGROUND));
            tick.setImageDrawable(mark);
            addView(
                    tick,
                    new FrameLayout.LayoutParams(dp(CHECK_TICK_DP), dp(CHECK_TICK_DP), Gravity.CENTER));
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value, boolean animate) {
            checked = value;
            GradientDrawable box = new GradientDrawable();
            box.setCornerRadius(dp(CHECK_RADIUS_DP));
            int stroke = dp(CHECK_STROKE_DP);
            if (value) {
                box.setColor(color(COLOR_TEXT_PRIMARY));
                box.setStroke(stroke, color(COLOR_TEXT_PRIMARY));
            } else {
                box.setColor(Color.TRANSPARENT);
                box.setStroke(stroke, tint(color(COLOR_TEXT_SECONDARY), CHECK_STROKE_ALPHA));
            }
            setBackground(box);

            float target = value ? 1f : 0f;
            float scale = value ? 1f : 0.6f;
            if (!animate || !animationsAllowed()) {
                tick.setAlpha(target);
                tick.setScaleX(scale);
                tick.setScaleY(scale);
                return;
            }
            tick.animate()
                    .alpha(target)
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(PRESS_UP_MS)
                    .setInterpolator(EASE_OUT)
                    .start();
        }
    }

    /** 行分隔线：从指定左内边距开始、不到卡片右缘，避免把卡片切成一条条。 */
    private View buildDivider(int leftMarginDp) {
        View divider = new View(this);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(MATCH_PARENT, Math.max(1, dp(1)));
        params.leftMargin = dp(leftMarginDp);
        params.rightMargin = dp(ROW_PADDING_END_DP);
        divider.setBackgroundColor(tint(color(COLOR_TEXT_PRIMARY), DIVIDER_ALPHA));
        divider.setLayoutParams(params);
        return divider;
    }

    /**
     * 入口行：目标 App 真实图标（未安装则品牌色字形，且压暗）+ 功能名 + 所属应用 + 箭头。
     *
     * @param first 卡片首行（涟漪圆角取上两角）
     * @param last  卡片末行（涟漪圆角取下两角）
     */
    private View buildRow(Entry entry, boolean first, boolean last) {
        Drawable appIcon = loadAppIcon(entry.packageName);
        boolean installed = appIcon != null;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(ROW_MIN_HEIGHT_DP));
        row.setPadding(
                dp(ROW_PADDING_START_DP),
                dp(ROW_PADDING_VERTICAL_DP),
                dp(ROW_PADDING_END_DP),
                dp(ROW_PADDING_VERTICAL_DP));
        row.setBackground(rowRipple(first, last, tint(color(entry.accentRes), ROW_RIPPLE_ALPHA)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription("打开" + entry.subtitle + entry.title);
        row.setOnClickListener(view -> entry.action.run());

        ImageView icon = entryIcon(entry, appIcon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(ROW_ICON_DP), dp(ROW_ICON_DP)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams =
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(ROW_ICON_GAP_DP);
        row.addView(labels, labelsParams);

        labels.addView(
                text(entry.title, ROW_TITLE_SP, color(COLOR_TEXT_PRIMARY), Typeface.NORMAL));

        String subtitleValue = installed ? entry.subtitle : "未安装";
        TextView subtitle =
                text(subtitleValue, ROW_SUBTITLE_SP, color(COLOR_TEXT_SECONDARY), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        subtitleParams.topMargin = dp(ROW_LABEL_GAP_DP);
        labels.addView(subtitle, subtitleParams);
        if (!installed) {
            subtitle.setAlpha(0.8f);
        }

        ImageView chevron = new ImageView(this);
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable chevronDrawable = getDrawable(R.drawable.ic_chevron_right).mutate();
        chevronDrawable.setTint(color(COLOR_TEXT_SECONDARY));
        chevron.setImageDrawable(chevronDrawable);
        chevron.setAlpha(0.7f);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(CHEVRON_DP), dp(CHEVRON_DP)));

        row.setOnTouchListener(pressFeedback(icon, chevron));
        return row;
    }

    /** 取件码入口：按「应用 → 身份码 / 待取快递 / 待收货」排列，与用户找码的顺序一致。 */
    private List<Entry> pickupEntries() {
        List<Entry> entries = new ArrayList<>();
        entries.add(pickupEntry(Destination.CAINIAO, "身份码", R.drawable.ic_func_qrcode));
        entries.add(
                pickupEntry(
                        Destination.TAOBAO_PENDING, "待取快递/身份码", R.drawable.ic_func_parcel));
        entries.add(pickupEntry(Destination.TAOBAO_RECEIVE, "待收货", R.drawable.ic_func_parcel));
        entries.add(
                pickupEntry(
                        Destination.PINDUODUO_PENDING,
                        "待取快递/身份码",
                        R.drawable.ic_func_parcel));
        entries.add(
                pickupEntry(Destination.PINDUODUO_RECEIVE, "待收货", R.drawable.ic_func_parcel));
        entries.add(pickupEntry(Destination.JD, "待取快递", R.drawable.ic_func_parcel));
        entries.add(pickupEntry(Destination.XHS, "待取快递", R.drawable.ic_func_parcel));
        entries.add(pickupEntry(Destination.DOUYIN, "待取快递", R.drawable.ic_func_parcel));
        entries.add(pickupEntry(Destination.DEWU, "待取快递", R.drawable.ic_func_parcel));
        return entries;
    }

    private Entry pickupEntry(Destination destination, String title, int glyphRes) {
        return new Entry(
                destination.key,
                title,
                destination.appName,
                destination.packageName,
                glyphRes,
                accentResOf(destination),
                destination.defaultVisible,
                () -> DeepLinkLauncher.open(this, destination));
    }

    /** 常用入口：微信 / 支付宝的扫一扫与收付款。 */
    private List<Entry> quickEntries() {
        List<Entry> entries = new ArrayList<>();
        entries.add(quickEntry(QuickAction.WECHAT_SCAN, R.drawable.glyph_wechat_scan));
        entries.add(quickEntry(QuickAction.WECHAT_PAY, R.drawable.glyph_wechat_pay));
        entries.add(quickEntry(QuickAction.ALIPAY_SCAN, R.drawable.glyph_alipay_scan));
        entries.add(quickEntry(QuickAction.ALIPAY_PAY, R.drawable.glyph_alipay_pay));
        return entries;
    }

    private Entry quickEntry(QuickAction action, int glyphRes) {
        return new Entry(
                action.key,
                action.title,
                action.appName,
                action.packageName,
                glyphRes,
                action.accentRes,
                true,
                () -> DeepLinkLauncher.openAction(this, action));
    }

    /** 行内图标：装了目标 App 就用它的真实启动图标，没装回退品牌色字形并压暗。 */
    private ImageView entryIcon(Entry entry, Drawable appIcon) {
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (appIcon != null) {
            icon.setImageDrawable(appIcon);
        } else {
            icon.setImageDrawable(brandGlyphIcon(entry.glyphRes, entry.accentRes));
            icon.setAlpha(ICON_MISSING_ALPHA);
        }
        return icon;
    }

    /**
     * 目标 App 的真实启动图标：已安装才拿得到，未安装（或读取失败）返回 {@code null}，
     * 由调用方回退成品牌色字形。清单里已声明这 9 个包名的可见性，Android 11+ 才能查到。
     */
    private Drawable loadAppIcon(String packageName) {
        Drawable cached = appIconCache.get(packageName);
        if (cached != null) {
            return cached;
        }
        try {
            Drawable icon = getPackageManager().getApplicationIcon(packageName);
            appIconCache.put(packageName, icon);
            return icon;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 行图标按入口所属软件取品牌强调色（涟漪色 / 图标回退底色）。 */
    private int accentResOf(Destination destination) {
        switch (destination) {
            case CAINIAO:
                return R.color.accent_cainiao;
            case TAOBAO:
            case TAOBAO_PENDING:
            case TAOBAO_RECEIVE:
                return R.color.accent_taobao;
            case PINDUODUO_RECEIVE:
                return R.color.accent_pinduoduo;
            case JD:
                return R.color.accent_jd;
            case XHS:
                return R.color.accent_xhs;
            case DOUYIN:
                return R.color.accent_douyin;
            case DEWU:
                return R.color.accent_dewu;
            default:
                return R.color.accent_pinduoduo;
        }
    }

    /**
     * 行涟漪：内容透明，只画涟漪，并用与卡片对齐的圆角裁切——首行圆上两角、末行圆下两角，
     * 中间行是直角，这样一行按下时不会溢出卡片的圆角。
     */
    private Drawable rowRipple(boolean first, boolean last, int rippleColor) {
        float radius = dp(CARD_RADIUS_DP);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadii(
                new float[] {
                    first ? radius : 0f, first ? radius : 0f,
                    first ? radius : 0f, first ? radius : 0f,
                    last ? radius : 0f, last ? radius : 0f,
                    last ? radius : 0f, last ? radius : 0f
                });
        return new RippleDrawable(rippleState(rippleColor), null, mask);
    }

    /** 卡片底：实色 surface + 一道发丝描边，四角同半径。 */
    private Drawable cardSurface() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(COLOR_SURFACE));
        drawable.setCornerRadius(dp(CARD_RADIUS_DP));
        drawable.setStroke(dp(CARD_STROKE_DP), color(COLOR_CARD_STROKE));
        return drawable;
    }

    /** 品牌色圆角方块 + 反白字形（应用未安装时的图标回退），保持应用图标的观感。 */
    private Drawable brandGlyphIcon(int glyphRes, int accentColorRes) {
        Drawable glyph = getDrawable(glyphRes).mutate();
        glyph.setTint(Color.WHITE);
        int inset = dp(9);
        LayerDrawable layers =
                new LayerDrawable(new Drawable[] {rounded(color(accentColorRes), 11), glyph});
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }

    /**
     * 按压反馈：按下 60ms 内图标缩至 {@value ICON_PRESS_SCALE} + 箭头右移 + 轻触震动，
     * 抬手 160ms 回弹。返回 false 不消费事件，点击/滚动照常派发；滚动被 ScrollView
     * 拦截时 ACTION_CANCEL 复原。
     */
    private View.OnTouchListener pressFeedback(ImageView icon, ImageView chevron) {
        return (view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    icon.animate()
                            .scaleX(ICON_PRESS_SCALE)
                            .scaleY(ICON_PRESS_SCALE)
                            .setDuration(PRESS_DOWN_MS)
                            .start();
                    chevron.animate()
                            .translationX(dp((int) PRESS_SLIDE_DP))
                            .setDuration(PRESS_DOWN_MS)
                            .start();
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    icon.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(PRESS_UP_MS)
                            .setInterpolator(EASE_OUT)
                            .start();
                    chevron.animate()
                            .translationX(0f)
                            .setDuration(PRESS_UP_MS)
                            .setInterpolator(EASE_OUT)
                            .start();
                    break;
                default:
                    break;
            }
            return false;
        };
    }

    /** 入场：每个分组卡片自上而下淡入 + 16dp 上移，逐卡片错峰 {@value ENTER_STAGGER}ms。 */
    private void animateEntrance() {
        if (!animationsAllowed()) {
            for (View view : entranceQueue) {
                view.setAlpha(1f);
                view.setTranslationY(0f);
            }
            return;
        }
        long delay = 0L;
        for (View view : entranceQueue) {
            view.setAlpha(0f);
            view.setTranslationY(dp(16));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(ENTER_DURATION)
                    .setStartDelay(delay)
                    .setInterpolator(EASE_OUT)
                    .start();
            delay += ENTER_STAGGER;
        }
    }

    /** 跟随系统「移除动画」设置：为 0 时跳过入场。 */
    private boolean animationsAllowed() {
        return Settings.Global.getFloat(
                        getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                > 0f;
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    /** 涟漪状态：按下/聚焦可见，其余透明。 */
    private static ColorStateList rippleState(int rippleColor) {
        return new ColorStateList(
                new int[][] {
                    new int[] {android.R.attr.state_pressed},
                    new int[] {android.R.attr.state_focused},
                    new int[] {}
                },
                new int[] {rippleColor, rippleColor, Color.TRANSPARENT});
    }

    /** 把颜色换成指定 alpha（0-255）的自身色调：{@code tint(0xc24b12, 0x1a)}。 */
    private static int tint(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** 解析语义色令牌（随系统深浅色自动取值）。 */
    private int color(int resId) {
        return getColor(resId);
    }

    private int dp(int value) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.round(value * metrics.density);
    }

    /** dp → px 的小数版：卡片圆角是 13.14dp 这种非整数值。 */
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 撑满宽度、高度自适应：卡片与行的通用布局参数。 */
    private static LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    }

    /** 同上，外加与上一块的留白（dp）。 */
    private LinearLayout.LayoutParams topMarginParams(int topMarginDp) {
        LinearLayout.LayoutParams params = wrapParams();
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** 列表行的数据：稳定 key + 功能名 + 所属应用 + 目标包名 + 图标回退素材 + 跳转动作。 */
    private static final class Entry {
        /** 与 {@link Destination#key} / {@link QuickAction#key} 同源，用作显示开关的偏好键。 */
        final String key;
        final String title;
        final String subtitle;
        final String packageName;
        final int glyphRes;
        final int accentRes;
        /** 设置弹层里是否默认勾选；用户勾过 / 取消过则以用户的为准（见 {@link EntryPrefs}）。 */
        final boolean defaultVisible;
        final Runnable action;

        Entry(
                String key,
                String title,
                String subtitle,
                String packageName,
                int glyphRes,
                int accentRes,
                boolean defaultVisible,
                Runnable action) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.packageName = packageName;
            this.glyphRes = glyphRes;
            this.accentRes = accentRes;
            this.defaultVisible = defaultVisible;
            this.action = action;
        }

        /** 完整入口名：所属应用 + 功能名（「菜鸟身份码」「微信扫一扫」），设置页列表行用它。 */
        String fullName() {
            return subtitle + title;
        }
    }
}
