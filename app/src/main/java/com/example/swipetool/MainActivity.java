package com.example.swipetool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 控制面板：设置滑动的方向 / 起点 / 长度 / 速度 / 次数 / 间隔，
 * 底部 Tab 按「参数 / 执行 / 录制 / 预设」分类，避免单页过长。
 * 通过无障碍服务执行手势，点「开始」会自动拉起悬浮球，方便切到目标应用后停止。
 */
public class MainActivity extends Activity {

    private static final int REQ_RECORD = 1002;

    private EditText etXMin, etXMax, etYMin, etYMax, etAngle, etDistMin, etDistMax,
            etDurationMin, etDurationMax, etInterval, etIntervalMin, etIntervalMax,
            etLoops, etPause, etCurve, etMaxDur, etJitter, etStartDelay;
    private CheckBox cbRoundTrip, cbIntervalRandom, cbUseRecorded;
    private Button btnStart, btnStop;
    private TextView tvStatus, tvRecorded;
    private SwipePreviewView mPreview;
    /** 复选框未勾选时折叠起来的分组。 */
    private LinearLayout llIntervalRandom, llRoundTrip;

    /** 底部 Tab：内容页与按钮。 */
    private View[] mTabContents;
    private Button[] mTabButtons;

    /** 当前选中的录制序列（界面状态，保存后持久化到 SwipeConfig）。 */
    private String mRecordedId = "";
    private String mRecordedName = "";
    /** 正在批量回填界面时，抑制开关监听里的提示。 */
    private boolean mApplyingConfig = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Handler mStatusHandler = new Handler(Looper.getMainLooper());
    private final Runnable mStatusTicker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            mStatusHandler.postDelayed(this, 500L);
        }
    };

    // 各参数「!」提示气泡的说明文案
    private static final String INFO_X_MIN = "起点横坐标的随机下限，按屏幕宽度百分比 0–100。每次滑动会在「X 下限 ~ X 上限」之间随机取一个起点；上下限相等即固定 X。0 = 屏幕最左边，100 = 最右边。";
    private static final String INFO_X_MAX = "起点横坐标的随机上限。与「X 下限」配合决定起点的水平随机范围，相等即固定。";
    private static final String INFO_Y_MIN = "起点纵坐标的随机下限，按屏幕高度百分比 0–100。每次滑动会在「Y 下限 ~ Y 上限」之间随机取一个起点；上下限相等即固定 Y。0 = 屏幕最上边，100 = 最下边。";
    private static final String INFO_Y_MAX = "起点纵坐标的随机上限。与「Y 下限」配合决定起点的垂直随机范围，相等即固定。";
    private static final String INFO_ANGLE = "滑动方向角。0 = 向右，90 = 向下，180 = 向左，270 = 向上；可填任意角度实现斜向滑动。会被「随机抖动」小幅扰动。";
    private static final String INFO_DIST_MIN = "单次滑动长度的随机下限，按屏幕高度百分比 0–100（例如 30 表示屏高的 30%）。与「距离上限」相等即固定长度。按屏幕比例计算，不同手机体感一致，不会出现 4000 这种离谱值。";
    private static final String INFO_DIST_MAX = "单次滑动长度的随机上限（屏高百分比）。每次滑动都会在「下限 ~ 上限」之间随机取一个长度，不会每次都一样。";
    private static final String INFO_DURATION_MIN = "单次滑动时长的随机下限，单位毫秒。数值越小滑动越快。与「时长上限」相等时即为固定时长。";
    private static final String INFO_DURATION_MAX = "单次滑动时长的随机上限，单位毫秒。每次滑动都会在「下限 ~ 上限」之间随机取一个时长，让快慢也带自然的起伏。";
    private static final String INFO_INTERVAL = "未开启「间隔随机」时，连续两次滑动之间的固定等待时间，单位毫秒。开启「间隔随机」后将在最小~最大之间随机取值。";
    private static final String INFO_INTERVAL_MIN = "开启「间隔随机」后，两次滑动之间间隔的下限，单位毫秒。";
    private static final String INFO_INTERVAL_MAX = "开启「间隔随机」后，两次滑动之间间隔的上限，单位毫秒。每次循环都会在此区间内随机取值，让节奏不固定。";
    private static final String INFO_PAUSE = "开启「往返滑动」后，到达终点后停顿多久再滑回起点，单位毫秒。";
    private static final String INFO_CURVE = "轨迹的基础弯曲幅度，0 为直线；正数向一侧弯曲，负数向另一侧弯曲，单位 dp。开启随机抖动后，每次还会在此基础上随机偏转，形成歪歪扭扭的走势。";
    private static final String INFO_LOOPS = "总共执行多少次滑动。0 表示无限循环，直到手动停止或达到「运行时长上限」。";
    private static final String INFO_MAX_DUR = "滑动运行多久后自动停止，单位秒。0 表示不限制，可防止忘记关闭。";
    private static final String INFO_JITTER = "拟人化随机强度（百分比）。0 = 关闭方向 / 弯曲 / 摆动的随机；起点与长度仍会在各自的「下限 ~ 上限」之间随机（上下限相等时才固定）。数值越大，连续操作看起来越像真人手指。";
    private static final String INFO_RECORDED = "开启后不再按上面的参数生成滑动，而是原样回放你录制的轨迹（可包含多笔）。录制会保留真实的采样点与时间，因此能重现人手滑动时的抖动和弯曲。";
    private static final String INFO_START_DELAY = "点「开始滑动 / 试放」后等待的秒数，方便你切到目标应用再开始。0 = 立即开始。仅主界面按钮生效，点悬浮球不延迟。";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 不自动弹输入法：窗口级别强制隐藏，切回 App 也不会弹出键盘
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(buildUi());
        // 打开界面时显示「上一次保存」的参数，未保存的修改不会被带进来
        applyConfigToUi(SwipeConfigStore.load(this));
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideKeyboard();
        mStatusHandler.removeCallbacks(mStatusTicker);
        mStatusHandler.post(mStatusTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStatusHandler.removeCallbacks(mStatusTicker);
    }

    // ---------- UI 构建：底部 Tab 分类 ----------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 内容区：4 个 Tab 页叠放，切换时显隐
        FrameLayout content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        ScrollView tabParams = buildParamsTab();
        ScrollView tabExecute = buildExecuteTab();
        ScrollView tabRecord = buildRecordTab();
        ScrollView tabPreset = buildPresetTab();
        mTabContents = new View[]{tabParams, tabExecute, tabRecord, tabPreset};
        for (View v : mTabContents) {
            v.setVisibility(View.GONE);
            content.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // 底部 Tab 栏
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(0xFFF5F5F5);
        tabBar.setPadding(0, dp(2), 0, dp(2));
        String[] labels = {"参数", "执行", "录制", "预设"};
        mTabButtons = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setOnClickListener(v -> {
                hideKeyboard();
                switchTab(idx);
            });
            mTabButtons[i] = b;
            tabBar.addView(b, weight());
        }

        root.addView(content);
        root.addView(tabBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        switchTab(0);
        return root;
    }

    private void switchTab(int idx) {
        for (int i = 0; i < mTabContents.length; i++) {
            mTabContents[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < mTabButtons.length; i++) {
            boolean sel = i == idx;
            mTabButtons[i].setSelected(sel);
            mTabButtons[i].setTextColor(sel ? 0xFF1976D2 : 0xFF777777);
        }
    }

    private ScrollView buildParamsTab() {
        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(16), dp(16), dp(16), dp(16));
        sv.addView(ll, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTitle(ll, "滑动参数");
        TextView hint = new TextView(this);
        hint.setText("起点 / 长度 / 时长 / 间隔都支持「下限 ~ 上限」随机，"
                + "相等即为固定值；距离与起点按屏幕比例计算。");
        hint.setTextSize(13f);
        hint.setTextColor(0xFF555555);
        hint.setPadding(0, 0, 0, dp(4));
        ll.addView(hint);

        addSection(ll, "方向快捷（角度：0=右 90=下 180=左 270=上）");
        LinearLayout dirRow = new LinearLayout(this);
        dirRow.setOrientation(LinearLayout.HORIZONTAL);
        dirRow.addView(dirButton("↑ 上", "270"), wrap());
        dirRow.addView(dirButton("↓ 下", "90"), wrap());
        dirRow.addView(dirButton("← 左", "180"), wrap());
        dirRow.addView(dirButton("→ 右", "0"), wrap());
        ll.addView(dirRow);

        // 实时预览：竖屏手机比例，让用户直观看到滑动会怎么走
        addSection(ll, "滑动预览（竖屏比例，随参数实时更新）");
        mPreview = new SwipePreviewView(this);
        ll.addView(mPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSection(ll, "参数（点右侧「!」查看含义）");
        etXMin = addField(ll, "起点 X 下限 · 屏宽百分比 0–100", "50", INFO_X_MIN);
        etXMax = addField(ll, "起点 X 上限 · 屏宽百分比 0–100", "50", INFO_X_MAX);
        etYMin = addField(ll, "起点 Y 下限 · 屏高百分比 0–100", "50", INFO_Y_MIN);
        etYMax = addField(ll, "起点 Y 上限 · 屏高百分比 0–100", "50", INFO_Y_MAX);
        etAngle = addField(ll, "角度 · 0–359（0右 90下 180左 270上）", "90", INFO_ANGLE);
        etDistMin = addField(ll, "距离下限 · 屏高百分比 0–100", "30", INFO_DIST_MIN);
        etDistMax = addField(ll, "距离上限 · 屏高百分比 0–100（与下限相同即固定）", "50", INFO_DIST_MAX);
        etDurationMin = addField(ll, "时长下限 · 50–60000 ms（越小越快）", "400", INFO_DURATION_MIN);
        etDurationMax = addField(ll, "时长上限 · 50–60000 ms（与下限相同即固定）", "700", INFO_DURATION_MAX);
        etInterval = addField(ll, "间隔（固定）· 0–600000 ms", "500", INFO_INTERVAL);

        cbIntervalRandom = new CheckBox(this);
        cbIntervalRandom.setText("间隔随机：每次在最小~最大之间随机取值");
        cbIntervalRandom.setOnCheckedChangeListener((b, v) -> refreshConditionalFields());
        ll.addView(cbIntervalRandom);
        llIntervalRandom = new LinearLayout(this);
        llIntervalRandom.setOrientation(LinearLayout.VERTICAL);
        ll.addView(llIntervalRandom, fullWidth(llIntervalRandom));
        etIntervalMin = addField(llIntervalRandom, "最小间隔 · 0–600000 ms", "200", INFO_INTERVAL_MIN);
        etIntervalMax = addField(llIntervalRandom, "最大间隔 · 0–600000 ms", "800", INFO_INTERVAL_MAX);

        cbRoundTrip = new CheckBox(this);
        cbRoundTrip.setText("往返滑动（到终点后滑回起点）");
        cbRoundTrip.setOnCheckedChangeListener((b, v) -> refreshConditionalFields());
        ll.addView(cbRoundTrip);
        llRoundTrip = new LinearLayout(this);
        llRoundTrip.setOrientation(LinearLayout.VERTICAL);
        ll.addView(llRoundTrip, fullWidth(llRoundTrip));
        etPause = addField(llRoundTrip, "往返停顿 · 0–60000 ms", "200", INFO_PAUSE);

        etCurve = addField(ll, "弯曲幅度 · -2000～2000 dp（0=直线）", "0", INFO_CURVE);
        etLoops = addField(ll, "循环次数 · 0–1000000（0=无限）", "0", INFO_LOOPS);
        etMaxDur = addField(ll, "运行时长上限 · 0–86400 秒（0=不限）", "0", INFO_MAX_DUR);
        etJitter = addField(ll, "随机抖动 · 0–100 %（0=关闭，越大越像真人）", "40", INFO_JITTER);

        // 预览随参数实时刷新
        watch(etXMin); watch(etXMax); watch(etYMin); watch(etYMax);
        watch(etAngle); watch(etDistMin); watch(etDistMax);
        watch(etCurve); watch(etJitter);
        refreshConditionalFields();
        return sv;
    }

    private ScrollView buildExecuteTab() {
        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(16), dp(16), dp(16), dp(16));
        sv.addView(ll, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTitle(ll, "执行控制");
        TextView hint = new TextView(this);
        hint.setText("基于无障碍服务发送标准触摸手势。点「开始滑动」会自动拉起悬浮球，"
                + "方便切到目标应用后随时停止。");
        hint.setTextSize(13f);
        hint.setTextColor(0xFF555555);
        hint.setPadding(0, 0, 0, dp(8));
        ll.addView(hint);

        addSection(ll, "权限 / 悬浮球");
        Button btnPermission = new Button(this);
        btnPermission.setText("开启无障碍权限");
        btnPermission.setOnClickListener(v -> openAccessibilitySettings());
        ll.addView(btnPermission, fullWidth(btnPermission));

        Button btnFloat = new Button(this);
        btnFloat.setText("开启悬浮球（跨应用快速启停）");
        btnFloat.setOnClickListener(v -> toggleFloating());
        ll.addView(btnFloat, fullWidth(btnFloat));

        tvStatus = new TextView(this);
        tvStatus.setTextSize(15f);
        tvStatus.setPadding(0, dp(8), 0, dp(8));
        ll.addView(tvStatus);

        addSection(ll, "启动");
        etStartDelay = addField(ll, "启动延迟 · 0–60 秒（切到目标应用再开始；悬浮球不延迟）",
                "0", INFO_START_DELAY);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        btnStart = new Button(this);
        btnStart.setText("▶ 开始滑动");
        btnStart.setOnClickListener(v -> startSwipe());
        btnStop = new Button(this);
        btnStop.setText("■ 停止");
        btnStop.setOnClickListener(v -> {
            SwipeAccessibilityService.stop();
            refreshStatus();
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
        });
        actions.addView(btnStart, weight());
        actions.addView(btnStop, weight());
        ll.addView(actions);

        TextView foot = new TextView(this);
        foot.setText("提示：开始后可切到任意 App，滑动会在当前屏幕上执行。"
                + "可用悬浮球、通知栏或回到此处停止。");
        foot.setTextSize(13f);
        foot.setTextColor(0xFF777777);
        foot.setPadding(0, dp(12), 0, 0);
        ll.addView(foot);
        return sv;
    }

    private ScrollView buildRecordTab() {
        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(16), dp(16), dp(16), dp(16));
        sv.addView(ll, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTitle(ll, "录制滑动序列");
        LinearLayout recordToggleRow = new LinearLayout(this);
        recordToggleRow.setOrientation(LinearLayout.HORIZONTAL);
        recordToggleRow.setGravity(Gravity.CENTER_VERTICAL);
        cbUseRecorded = new CheckBox(this);
        cbUseRecorded.setText("使用录制序列回放（替代按参数生成的滑动）");
        cbUseRecorded.setOnCheckedChangeListener((b, checked) -> {
            if (checked && mRecordedId.isEmpty() && !mApplyingConfig) {
                Toast.makeText(this, "请先录制或选择一个序列", Toast.LENGTH_SHORT).show();
            }
            updateRecordedLabel();
            refreshStatus();
        });
        recordToggleRow.addView(cbUseRecorded, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        recordToggleRow.addView(makeInfoIcon(INFO_RECORDED), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ll.addView(recordToggleRow);

        tvRecorded = new TextView(this);
        tvRecorded.setTextSize(14f);
        tvRecorded.setTextColor(0xFF555555);
        tvRecorded.setPadding(0, dp(2), 0, dp(4));
        ll.addView(tvRecorded);

        LinearLayout recordRow1 = new LinearLayout(this);
        recordRow1.setOrientation(LinearLayout.HORIZONTAL);
        Button btnRecord = new Button(this);
        btnRecord.setText("录制新序列…");
        btnRecord.setOnClickListener(v -> startActivityForResult(
                new Intent(this, RecordActivity.class), REQ_RECORD));
        Button btnRecordList = new Button(this);
        btnRecordList.setText("序列列表 / 删除…");
        btnRecordList.setOnClickListener(v -> showRecordListDialog());
        recordRow1.addView(btnRecord, weight());
        recordRow1.addView(btnRecordList, weight());
        ll.addView(recordRow1);

        Button btnRecordPreview = new Button(this);
        btnRecordPreview.setText("试放一次选中的序列");
        btnRecordPreview.setOnClickListener(v -> previewRecorded());
        ll.addView(btnRecordPreview, fullWidth(btnRecordPreview));

        TextView recordHint = new TextView(this);
        recordHint.setText("录制会在本机保存归一化坐标与时间戳；回放时按原轨迹发送触摸事件，"
                + "并沿用「参数」页的循环次数 / 间隔 / 运行时长上限。点「保存」后悬浮球也会回放该序列。");
        recordHint.setTextSize(12f);
        recordHint.setTextColor(0xFF777777);
        recordHint.setPadding(0, dp(8), 0, 0);
        ll.addView(recordHint);
        return sv;
    }

    private ScrollView buildPresetTab() {
        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(16), dp(16), dp(16), dp(16));
        sv.addView(ll, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTitle(ll, "参数预设");

        TextView explain = new TextView(this);
        explain.setText("「保存当前参数」与「另存到槽位」作用不同，因此不合并：\n\n"
                + "• 保存当前参数：把界面参数写成「生效配置」，悬浮球和下次启动都会用它（立即生效）。\n"
                + "• 另存到槽位：仅存成模板（共 3 个槽），不会自动生效；需「读取槽位」载入界面后再点「保存」才生效。\n\n"
                + "简单说：保存 = 立即生效；另存 = 存档备用。");
        explain.setTextSize(13f);
        explain.setTextColor(0xFF444444);
        explain.setPadding(0, 0, 0, dp(8));
        ll.addView(explain);

        addSection(ll, "生效配置");
        Button btnSave = new Button(this);
        btnSave.setText("保存当前参数（悬浮球 / 下次启动使用）");
        btnSave.setOnClickListener(v -> saveCurrentConfig());
        ll.addView(btnSave, fullWidth(btnSave));

        addSection(ll, "模板槽位（存档备用）");
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnSaveAs = new Button(this);
        btnSaveAs.setText("另存到槽位…");
        btnSaveAs.setOnClickListener(v -> showSaveAsDialog());
        Button btnLoad = new Button(this);
        btnLoad.setText("读取槽位…");
        btnLoad.setOnClickListener(v -> showLoadDialog());
        presetRow.addView(btnSaveAs, weight());
        presetRow.addView(btnLoad, weight());
        ll.addView(presetRow);

        TextView note = new TextView(this);
        note.setText("读取槽位只会把参数填到界面；记得再点「保存当前参数」才会让悬浮球生效。");
        note.setTextSize(12f);
        note.setTextColor(0xFF888888);
        note.setPadding(0, dp(6), 0, 0);
        ll.addView(note);
        return sv;
    }

    // ---------- 行为 ----------

    private Button dirButton(String label, String angle) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> {
            etAngle.setText(angle);
            hideKeyboard();
        });
        return b;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开设置，请手动进入 系统设置 > 无障碍", Toast.LENGTH_LONG).show();
        }
    }

    /** 手动开启悬浮球：需要 overlay 权限，缺失时引导去设置。 */
    private void toggleFloating() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许「显示在其他应用上层」", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        requestNotificationPermissionIfNeeded();
        startFloatingService();
        Toast.makeText(this, "悬浮球已开启：使用已保存的参数，点它开始/停止", Toast.LENGTH_LONG).show();
    }

    /** 点「开始」时静默拉起悬浮球：已运行则跳过；缺 overlay 权限只提示不拦截。 */
    private void ensureFloatingForStart() {
        if (FloatingService.isRunning()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "未授予悬浮窗权限：切到目标应用后可回 App 或用通知栏停止",
                    Toast.LENGTH_LONG).show();
            return;
        }
        requestNotificationPermissionIfNeeded();
        startFloatingService();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2002);
        }
    }

    private void startFloatingService() {
        Intent i = new Intent(this, FloatingService.class)
                .setAction(FloatingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private boolean isAccessibilityEnabled() {
        int enabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        if (enabled == 0) return false;
        String setting = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (setting == null) return false;
        String target = getPackageName() + "/" + SwipeAccessibilityService.class.getName();
        for (String part : setting.split(":")) {
            if (part.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private void refreshStatus() {
        String status;
        boolean canStart;
        boolean canStop;

        boolean perm = isAccessibilityEnabled();
        boolean connected = SwipeAccessibilityService.isConnected();
        boolean running = SwipeAccessibilityService.isRunning();
        if (!perm) {
            status = "状态：✗ 无障碍权限未开启";
            canStart = false;
            canStop = false;
        } else if (!connected) {
            status = "状态：权限已开启，服务未连接（可尝试重启 App）";
            canStart = false;
            canStop = false;
        } else if (running) {
            status = "状态：● 正在滑动，已完成 " + SwipeAccessibilityService.getDoneCount() + " 次";
            canStart = false;
            canStop = true;
        } else {
            status = "状态：✓ 就绪，可以开始";
            canStart = true;
            canStop = false;
        }

        if (canStart && cbUseRecorded != null && cbUseRecorded.isChecked()
                && (mRecordedId == null || mRecordedId.isEmpty())) {
            status = "状态：请先录制或选择要回放的序列";
            canStart = false;
        }

        tvStatus.setText(status);
        btnStart.setEnabled(canStart);
        btnStop.setEnabled(canStop);
    }

    /** 刷新「当前选中的录制序列」提示文字。 */
    private void updateRecordedLabel() {
        if (tvRecorded == null) return;
        if (mRecordedId == null || mRecordedId.isEmpty()) {
            tvRecorded.setText("当前序列：未选择（点「录制新序列…」或「序列列表」）");
        } else {
            tvRecorded.setText("当前序列：" + (mRecordedName.isEmpty() ? mRecordedId : mRecordedName));
        }
    }

    /** 弹出录制序列列表：点选即启用，可进入删除流程。 */
    private void showRecordListDialog() {
        final java.util.List<RecordedSequence> all = RecordedSwipeStore.list(this);
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有录制序列，请先点「录制新序列…」", Toast.LENGTH_LONG).show();
            return;
        }
        final String[] items = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            RecordedSequence s = all.get(i);
            items[i] = s.name + "（" + s.strokes.size() + " 笔 · "
                    + String.format(java.util.Locale.US, "%.1f", s.totalDurationMs() / 1000f)
                    + " 秒）";
        }
        new AlertDialog.Builder(this)
                .setTitle("选择要回放的序列")
                .setItems(items, (d, which) -> {
                    RecordedSequence s = all.get(which);
                    mRecordedId = s.id;
                    mRecordedName = s.name;
                    cbUseRecorded.setChecked(true);
                    updateRecordedLabel();
                    refreshStatus();
                    Toast.makeText(this, "已选择：" + s.name, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("删除…", (d, w) -> showDeleteRecordDialog(all))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteRecordDialog(final java.util.List<RecordedSequence> all) {
        final String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("勾选要删除的序列")
                .setMultiChoiceItems(names, null, null)
                .setPositiveButton("删除", (d, w) -> {
                    AlertDialog ad = (AlertDialog) d;
                    android.util.SparseBooleanArray checked = ad.getListView()
                            .getCheckedItemPositions();
                    int removed = 0;
                    for (int i = 0; i < all.size(); i++) {
                        if (!checked.get(i)) continue;
                        String id = all.get(i).id;
                        if (RecordedSwipeStore.delete(this, id)) removed++;
                        if (id.equals(mRecordedId)) {
                            mRecordedId = "";
                            mRecordedName = "";
                            cbUseRecorded.setChecked(false);
                        }
                    }
                    updateRecordedLabel();
                    refreshStatus();
                    Toast.makeText(this, "已删除 " + removed + " 个序列",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 立刻回放一次当前选中的序列（仅用于验证效果）。 */
    private void previewRecorded() {
        hideKeyboard();
        if (mRecordedId == null || mRecordedId.isEmpty()) {
            Toast.makeText(this, "请先录制或选择一个序列", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAccessibilityEnabled() || !SwipeAccessibilityService.isConnected()) {
            Toast.makeText(this, "试放需要无障碍权限，请先开启", Toast.LENGTH_LONG).show();
            return;
        }
        final RecordedSequence seq = RecordedSwipeStore.find(this, mRecordedId);
        if (seq == null) {
            Toast.makeText(this, "序列已不存在，请重新选择", Toast.LENGTH_SHORT).show();
            mRecordedId = "";
            mRecordedName = "";
            updateRecordedLabel();
            return;
        }
        SwipeConfig c = collectConfig();
        c.useRecorded = true;
        c.loops = 1;
        c.intervalMs = 0L;
        c.intervalRandom = false;
        c.maxDurationSec = 0L;
        ensureFloatingForStart();
        runWithDelay(c.startDelaySec, () -> {
            SwipeAccessibilityService.start(c);
            Toast.makeText(this, "正在试放 1 次：" + seq.name, Toast.LENGTH_SHORT).show();
        }, "试放");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RECORD && resultCode == RESULT_OK && data != null) {
            String id = data.getStringExtra(RecordActivity.EXTRA_ID);
            String name = data.getStringExtra(RecordActivity.EXTRA_NAME);
            if (id != null && !id.isEmpty()) {
                mRecordedId = id;
                mRecordedName = name == null ? "" : name;
                cbUseRecorded.setChecked(true);
                updateRecordedLabel();
                refreshStatus();
                Toast.makeText(this, "已选用录制序列，点「保存」后悬浮球生效",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private SwipeConfig collectConfig() {
        SwipeConfig c = new SwipeConfig();
        c.xMinPercent = clampF(parseFloat(etXMin, c.xMinPercent), 0f, 100f);
        c.xMaxPercent = clampF(parseFloat(etXMax, c.xMaxPercent), 0f, 100f);
        if (c.xMaxPercent < c.xMinPercent) {
            float t = c.xMinPercent; c.xMinPercent = c.xMaxPercent; c.xMaxPercent = t;
        }
        c.yMinPercent = clampF(parseFloat(etYMin, c.yMinPercent), 0f, 100f);
        c.yMaxPercent = clampF(parseFloat(etYMax, c.yMaxPercent), 0f, 100f);
        if (c.yMaxPercent < c.yMinPercent) {
            float t = c.yMinPercent; c.yMinPercent = c.yMaxPercent; c.yMaxPercent = t;
        }
        c.angle = normalizeAngle(parseFloat(etAngle, c.angle));
        c.distanceMinPercent = clampF(parseFloat(etDistMin, c.distanceMinPercent), 0f, 100f);
        c.distanceMaxPercent = clampF(parseFloat(etDistMax, c.distanceMaxPercent), 0f, 100f);
        if (c.distanceMaxPercent < c.distanceMinPercent) {
            float t = c.distanceMinPercent; c.distanceMinPercent = c.distanceMaxPercent; c.distanceMaxPercent = t;
        }
        c.durationMinMs = clampL((long) parseLong(etDurationMin, c.durationMinMs), 50L, 60000L);
        c.durationMaxMs = clampL((long) parseLong(etDurationMax, c.durationMaxMs), 50L, 60000L);
        if (c.durationMaxMs < c.durationMinMs) {
            long t = c.durationMinMs; c.durationMinMs = c.durationMaxMs; c.durationMaxMs = t;
        }
        c.intervalMs = clampL((long) parseLong(etInterval, c.intervalMs), 0L, 600000L);
        c.intervalRandom = cbIntervalRandom.isChecked();
        c.intervalMinMs = clampL(parseLong(etIntervalMin, c.intervalMinMs), 0L, 600000L);
        c.intervalMaxMs = clampL(parseLong(etIntervalMax, c.intervalMaxMs), 0L, 600000L);
        if (c.intervalMaxMs < c.intervalMinMs) {
            long t = c.intervalMinMs; c.intervalMinMs = c.intervalMaxMs; c.intervalMaxMs = t;
        }
        c.loops = (int) clampL(parseLong(etLoops, c.loops), 0L, 1000000L);
        c.roundTrip = cbRoundTrip.isChecked();
        c.pauseMs = clampL((long) parseLong(etPause, c.pauseMs), 0L, 60000L);
        c.curveDp = clampF(parseFloat(etCurve, c.curveDp), -2000f, 2000f);
        c.maxDurationSec = clampL(parseLong(etMaxDur, c.maxDurationSec), 0L, 86400L);
        c.jitterPercent = clampF(parseFloat(etJitter, c.jitterPercent), 0f, 100f);
        c.startDelaySec = (int) clampL(parseLong(etStartDelay, c.startDelaySec), 0L, 60L);
        c.useRecorded = cbUseRecorded != null && cbUseRecorded.isChecked();
        c.recordedId = mRecordedId;
        c.recordedName = mRecordedName;
        return c;
    }

    /** 保存当前界面参数为「生效配置」，供悬浮球与下次启动使用。 */
    private void saveCurrentConfig() {
        hideKeyboard();
        SwipeConfigStore.save(this, collectConfig());
        Toast.makeText(this, "已保存：悬浮球和下次启动将使用这些参数", Toast.LENGTH_SHORT).show();
    }

    private void showSaveAsDialog() {
        final String[] items = new String[]{slotLabel(1), slotLabel(2), slotLabel(3)};
        new AlertDialog.Builder(this)
                .setTitle("另存到槽位")
                .setItems(items, (d, which) -> {
                    SwipeConfigStore.saveSlot(this, which + 1, collectConfig());
                    Toast.makeText(this, "已另存到槽 " + (which + 1) + "（已存储）",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLoadDialog() {
        final String[] items = new String[]{slotLabel(1), slotLabel(2), slotLabel(3)};
        new AlertDialog.Builder(this)
                .setTitle("读取槽位")
                .setItems(items, (d, which) -> {
                    applyConfigToUi(SwipeConfigStore.loadSlot(this, which + 1));
                    Toast.makeText(this, "已读取槽 " + (which + 1)
                            + "，点「保存」后悬浮球才会使用", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 槽位在弹窗里的显示名：已保存过的标记「已存储」，方便区分。 */
    private String slotLabel(int slot) {
        return "槽 " + slot + (SwipeConfigStore.hasSlot(this, slot) ? "（已存储）" : "（空）");
    }

    private void applyConfigToUi(SwipeConfig c) {
        etXMin.setText(fmtNum(c.xMinPercent));
        etXMax.setText(fmtNum(c.xMaxPercent));
        etYMin.setText(fmtNum(c.yMinPercent));
        etYMax.setText(fmtNum(c.yMaxPercent));
        etAngle.setText(fmtNum(c.angle));
        etDistMin.setText(fmtNum(c.distanceMinPercent));
        etDistMax.setText(fmtNum(c.distanceMaxPercent));
        etDurationMin.setText(String.valueOf(c.durationMinMs));
        etDurationMax.setText(String.valueOf(c.durationMaxMs));
        etInterval.setText(String.valueOf(c.intervalMs));
        etLoops.setText(String.valueOf(c.loops));
        cbRoundTrip.setChecked(c.roundTrip);
        etPause.setText(String.valueOf(c.pauseMs));
        etCurve.setText(fmtNum(c.curveDp));
        etMaxDur.setText(String.valueOf(c.maxDurationSec));
        etJitter.setText(fmtNum(c.jitterPercent));
        cbIntervalRandom.setChecked(c.intervalRandom);
        etIntervalMin.setText(String.valueOf(c.intervalMinMs));
        etIntervalMax.setText(String.valueOf(c.intervalMaxMs));
        etStartDelay.setText(String.valueOf(c.startDelaySec));
        mRecordedId = c.recordedId == null ? "" : c.recordedId;
        mRecordedName = c.recordedName == null ? "" : c.recordedName;
        mApplyingConfig = true;
        cbUseRecorded.setChecked(c.useRecorded);
        mApplyingConfig = false;
        updateRecordedLabel();
        refreshConditionalFields();
        refreshPreview();
    }

    private static String fmtNum(float v) {
        if (v == Math.floor(v)) {
            return String.valueOf((int) v);
        }
        return String.valueOf(v);
    }

    private void startSwipe() {
        hideKeyboard();
        // 直接使用界面上的当前参数执行；是否持久化由「保存」按钮决定
        SwipeConfig c = collectConfig();

        if (c.useRecorded) {
            if (!isAccessibilityEnabled() || !SwipeAccessibilityService.isConnected()) {
                Toast.makeText(this, "录制序列回放需要无障碍权限，请先开启",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (c.recordedId.isEmpty() || RecordedSwipeStore.find(this, c.recordedId) == null) {
                Toast.makeText(this, "请先录制或选择要回放的序列", Toast.LENGTH_SHORT).show();
                return;
            }
            ensureFloatingForStart();
            runWithDelay(c.startDelaySec, () -> {
                SwipeAccessibilityService.start(c);
                Toast.makeText(this, "开始回放录制序列", Toast.LENGTH_SHORT).show();
                refreshStatus();
            }, "开始");
            refreshStatus();
            return;
        }

        if (!isAccessibilityEnabled() || !SwipeAccessibilityService.isConnected()) {
            Toast.makeText(this, "请先开启无障碍权限", Toast.LENGTH_SHORT).show();
            return;
        }
        // 自动拉起悬浮球，方便切到目标应用后停止
        ensureFloatingForStart();
        runWithDelay(c.startDelaySec, () -> {
            SwipeAccessibilityService.start(c);
            Toast.makeText(this, "开始滑动", Toast.LENGTH_SHORT).show();
            refreshStatus();
        }, "开始");
        refreshStatus();
    }

    /** 按启动延迟执行：延迟 > 0 时先提示，再 postDelayed；悬浮球不走这里（不延迟）。 */
    private void runWithDelay(int delaySec, Runnable action, String label) {
        if (delaySec <= 0) {
            action.run();
            return;
        }
        Toast.makeText(this, delaySec + " 秒后" + label + "，请切到目标应用",
                Toast.LENGTH_LONG).show();
        mHandler.postDelayed(action, delaySec * 1000L);
    }

    /** 收起输入法并清除焦点，避免切回 App 时键盘自动弹出。 */
    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    private float parseFloat(EditText et, float def) {
        try { return Float.parseFloat(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    private long parseLong(EditText et, long def) {
        try { return Long.parseLong(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    private static float clampF(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static long clampL(long v, long min, long max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** 把任意角度归一化到 [0, 360)。 */
    private static float normalizeAngle(float a) {
        float r = a % 360f;
        return r < 0 ? r + 360f : r;
    }

    // ---------- UI 辅助方法 ----------

    private void addTitle(LinearLayout root, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(22f);
        tv.setTextColor(0xFF222222);
        tv.setPadding(0, 0, 0, dp(4));
        root.addView(tv);
    }

    private void addSection(LinearLayout root, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTextColor(0xFF1976D2);
        tv.setPadding(0, dp(12), 0, dp(4));
        root.addView(tv);
    }

    private EditText addField(ViewGroup root, String label, String def, String info) {
        // 标签行：左侧名称（含取值范围），右侧可点击的「!」提示图标
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setPadding(0, dp(6), 0, dp(2));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14f);
        labelRow.addView(tv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(makeInfoIcon(info), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(labelRow);

        EditText et = new EditText(this);
        et.setText(def);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(et, fullWidth(et));
        return et;
    }

    /** 生成一个可点击的「!」图标，点击弹出该参数的详细说明。 */
    private View makeInfoIcon(String infoText) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_info_circle);
        int pad = dp(5);
        iv.setPadding(pad, pad, pad, pad);
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setContentDescription("参数说明");
        iv.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("参数说明")
                .setMessage(infoText)
                .setPositiveButton("知道了", null)
                .show());
        return iv;
    }

    /** 监听输入框变化，实时刷新预览图。 */
    private void watch(EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refreshPreview(); }
        });
    }

    /** 读取当前输入，更新预览图。 */
    private void refreshPreview() {
        if (mPreview == null || etXMin == null) return;
        float xMin = parseFloat(etXMin, 50f);
        float xMax = parseFloat(etXMax, 50f);
        float yMin = parseFloat(etYMin, 50f);
        float yMax = parseFloat(etYMax, 50f);
        float ang = parseFloat(etAngle, 90f);
        float dMin = parseFloat(etDistMin, 30f);
        float dMax = parseFloat(etDistMax, 50f);
        float curve = parseFloat(etCurve, 0f);
        float jitter = parseFloat(etJitter, SwipeConfig.DEFAULT_JITTER);
        mPreview.update(xMin, xMax, yMin, yMax, ang, dMin, dMax, curve,
                cbRoundTrip.isChecked(), jitter);
    }

    /** 根据复选框状态折叠/展开依赖的分组，并刷新相关输入框可用性。 */
    private void refreshConditionalFields() {
        if (cbIntervalRandom == null) return;
        boolean rndInterval = cbIntervalRandom.isChecked();
        if (llIntervalRandom != null) {
            llIntervalRandom.setVisibility(rndInterval ? View.VISIBLE : View.GONE);
        }
        etInterval.setEnabled(!rndInterval);
        etIntervalMin.setEnabled(rndInterval);
        etIntervalMax.setEnabled(rndInterval);
        if (llRoundTrip != null && cbRoundTrip != null) {
            llRoundTrip.setVisibility(cbRoundTrip.isChecked() ? View.VISIBLE : View.GONE);
        }
        refreshPreview();
    }

    private LinearLayout.LayoutParams fullWidth(View v) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
