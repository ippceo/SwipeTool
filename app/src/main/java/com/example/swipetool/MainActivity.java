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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/**
 * 控制面板：设置滑动的方向 / 角度 / 位置 / 速度 / 次数 / 间隔，
 * 选择执行方式（无障碍服务 / Shizuku），然后启动或停止滑动。
 */
public class MainActivity extends Activity {

    private static final int REQ_SHIZUKU = 1001;

    private EditText etX, etY, etAngle, etDistMin, etDistMax, etDuration, etInterval, etLoops, etPause,
            etCurve, etMaxDur, etJitter, etIntervalMin, etIntervalMax;
    private CheckBox cbRoundTrip, cbIntervalRandom;
    private Button btnStart, btnStop;
    private TextView tvStatus;
    private RadioGroup rgMode;
    private RadioButton rbA11y, rbShizuku;
    private SwipePreviewView mPreview;

    // 各参数「!」提示气泡的说明文案
    private static final String INFO_X = "滑动起点的横坐标，按屏幕宽度百分比定位。0 = 屏幕最左边，100 = 最右边，50 = 水平居中。会与「随机抖动」叠加做小幅扰动。";
    private static final String INFO_Y = "滑动起点的纵坐标，按屏幕高度百分比定位。0 = 屏幕最上边，100 = 最下边，50 = 垂直居中。会与「随机抖动」叠加做小幅扰动。";
    private static final String INFO_ANGLE = "滑动方向角。0 = 向右，90 = 向下，180 = 向左，270 = 向上；可填任意角度实现斜向滑动。会被「随机抖动」小幅扰动。";
    private static final String INFO_DIST_MIN = "单次滑动长度的随机下限，单位 dp（会按屏幕密度换算成像素）。与「距离上限」相等时即为固定长度。";
    private static final String INFO_DIST_MAX = "单次滑动长度的随机上限，单位 dp。每次滑动都会在「下限 ~ 上限」之间随机取一个长度，不会每次都一样。";
    private static final String INFO_DURATION = "单次手势从起点滑到终点的持续时间，单位毫秒。数值越小滑动越快，越大越慢。无障碍与 Shizuku 两种方式均生效。";
    private static final String INFO_INTERVAL = "未开启「间隔随机」时，连续两次滑动之间的固定等待时间，单位毫秒。开启「间隔随机」后将在最小~最大之间随机取值。";
    private static final String INFO_INTERVAL_MIN = "开启「间隔随机」后，两次滑动之间间隔的下限，单位毫秒。";
    private static final String INFO_INTERVAL_MAX = "开启「间隔随机」后，两次滑动之间间隔的上限，单位毫秒。每次循环都会在此区间内随机取值，让节奏不固定。";
    private static final String INFO_PAUSE = "开启「往返滑动」后，到达终点后停顿多久再滑回起点，单位毫秒。仅无障碍方式支持往返。";
    private static final String INFO_CURVE = "轨迹的基础弯曲幅度，0 为直线；正数向一侧弯曲，负数向另一侧弯曲，单位 dp。开启随机抖动后，每次还会在此基础上随机偏转，形成歪歪扭扭的走势。仅无障碍方式支持曲线，Shizuku 方式恒为直线。";
    private static final String INFO_LOOPS = "总共执行多少次滑动。0 表示无限循环，直到手动停止或达到「运行时长上限」。";
    private static final String INFO_MAX_DUR = "滑动运行多久后自动停止，单位秒。0 表示不限制，可防止忘记关闭。";
    private static final String INFO_JITTER = "拟人化随机强度（百分比）。0 = 关闭起点 / 方向 / 弯曲 / 摆动的随机；滑动长度仍会在「距离下限 ~ 上限」之间随机（两者相等时才是固定长度）。数值越大，连续操作看起来越像真人手指。";

    private final Handler mStatusHandler = new Handler(Looper.getMainLooper());
    private final Runnable mStatusTicker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            mStatusHandler.postDelayed(this, 500L);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener mShizukuListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQ_SHIZUKU) return;
                boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                Toast.makeText(MainActivity.this,
                        granted ? "Shizuku 已授权" : "Shizuku 授权被拒绝",
                        Toast.LENGTH_SHORT).show();
                if (granted) ShizukuSwipeEngine.bind(this);
                refreshStatus();
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        // 打开界面时显示「上一次保存」的参数，未保存的修改不会被带进来
        applyConfigToUi(SwipeConfigStore.load(this));
        try {
            Shizuku.addRequestPermissionResultListener(mShizukuListener);
        } catch (Throwable ignored) {
        }
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mStatusHandler.removeCallbacks(mStatusTicker);
        mStatusHandler.post(mStatusTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStatusHandler.removeCallbacks(mStatusTicker);
    }

    @Override
    protected void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(mShizukuListener);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(16);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTitle(root, "屏幕滑动工具");
        TextView hint = new TextView(this);
        hint.setText("纯触摸事件模拟 · 方向 / 角度 / 位置 / 速度可调\n"
                + "执行方式可选「无障碍服务」或「Shizuku」。");
        hint.setTextSize(14f);
        hint.setTextColor(0xFF555555);
        hint.setPadding(0, 0, 0, dp(8));
        root.addView(hint);

        // 执行方式
        addSection(root, "执行方式");
        rgMode = new RadioGroup(this);
        rgMode.setOrientation(RadioGroup.HORIZONTAL);
        rbA11y = new RadioButton(this);
        rbA11y.setId(View.generateViewId());
        rbA11y.setText("无障碍服务");
        rbShizuku = new RadioButton(this);
        rbShizuku.setId(View.generateViewId());
        rbShizuku.setText("Shizuku");
        rgMode.addView(rbA11y);
        rgMode.addView(rbShizuku);
        rgMode.check(rbA11y.getId());
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == rbShizuku.getId()
                    && ShizukuSwipeEngine.isAvailable()
                    && ShizukuSwipeEngine.hasPermission()) {
                ShizukuSwipeEngine.bind(this);
            }
            refreshStatus();
        });
        root.addView(rgMode);

        // 权限入口
        Button btnPermission = new Button(this);
        btnPermission.setText("1a. 开启无障碍权限");
        btnPermission.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(btnPermission, fullWidth(btnPermission));

        Button btnShizukuPerm = new Button(this);
        btnShizukuPerm.setText("1b. 检查 / 授权 Shizuku");
        btnShizukuPerm.setOnClickListener(v -> ensureShizuku());
        root.addView(btnShizukuPerm, fullWidth(btnShizukuPerm));

        Button btnFloat = new Button(this);
        btnFloat.setText("1c. 开启悬浮球（跨应用快速启停）");
        btnFloat.setOnClickListener(v -> toggleFloating());
        root.addView(btnFloat, fullWidth(btnFloat));

        tvStatus = new TextView(this);
        tvStatus.setTextSize(15f);
        tvStatus.setPadding(0, dp(8), 0, dp(8));
        root.addView(tvStatus);

        addSection(root, "方向快捷设置（角度：0=右 90=下 180=左 270=上）");
        LinearLayout dirRow = new LinearLayout(this);
        dirRow.setOrientation(LinearLayout.HORIZONTAL);
        dirRow.addView(dirButton("↑ 上", "270"), wrap());
        dirRow.addView(dirButton("↓ 下", "90"), wrap());
        dirRow.addView(dirButton("← 左", "180"), wrap());
        dirRow.addView(dirButton("→ 右", "0"), wrap());
        root.addView(dirRow);

        // 实时预览：让用户直观看到滑动会怎么走
        addSection(root, "滑动预览（随参数实时更新）");
        mPreview = new SwipePreviewView(this);
        root.addView(mPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));

        addSection(root, "滑动参数（点右侧「!」查看含义）");
        etX = addField(root, "起点 X · 屏宽百分比 0–100", "50", INFO_X);
        etY = addField(root, "起点 Y · 屏高百分比 0–100", "50", INFO_Y);
        etAngle = addField(root, "角度 · 0–359（0右 90下 180左 270上）", "90", INFO_ANGLE);
        etDistMin = addField(root, "距离下限 · 1–4000 dp", "300", INFO_DIST_MIN);
        etDistMax = addField(root, "距离上限 · 1–4000 dp", "500", INFO_DIST_MAX);
        etDuration = addField(root, "滑动时长 · 50–60000 ms（越小越快）", "500", INFO_DURATION);
        etInterval = addField(root, "间隔（固定）· 0–600000 ms", "500", INFO_INTERVAL);

        cbIntervalRandom = new CheckBox(this);
        cbIntervalRandom.setText("间隔随机：每次在最小~最大之间随机取值");
        cbIntervalRandom.setOnCheckedChangeListener((b, v) -> refreshIntervalFieldsEnabled());
        root.addView(cbIntervalRandom);
        etIntervalMin = addField(root, "最小间隔 · 0–600000 ms", "200", INFO_INTERVAL_MIN);
        etIntervalMax = addField(root, "最大间隔 · 0–600000 ms", "800", INFO_INTERVAL_MAX);

        cbRoundTrip = new CheckBox(this);
        cbRoundTrip.setText("往返滑动（到终点后滑回起点）");
        cbRoundTrip.setOnCheckedChangeListener((b, v) -> refreshPreview());
        root.addView(cbRoundTrip);
        etPause = addField(root, "往返停顿 · 0–60000 ms", "200", INFO_PAUSE);
        etCurve = addField(root, "弯曲幅度 · -2000～2000 dp（0=直线）", "0", INFO_CURVE);
        etLoops = addField(root, "循环次数 · 0–1000000（0=无限）", "0", INFO_LOOPS);
        etMaxDur = addField(root, "运行时长上限 · 0–86400 秒（0=不限）", "0", INFO_MAX_DUR);
        etJitter = addField(root, "随机抖动 · 0–100 %（0=关闭，越大越像真人）", "40", INFO_JITTER);

        // 预览随参数实时刷新
        watch(etX); watch(etY); watch(etAngle); watch(etDistMin); watch(etDistMax);
        watch(etCurve); watch(etJitter);
        refreshIntervalFieldsEnabled();
        refreshPreview();

        Button btnSave = new Button(this);
        btnSave.setText("保存当前参数（悬浮球 / 下次启动使用）");
        btnSave.setOnClickListener(v -> saveCurrentConfig());
        root.addView(btnSave, fullWidth(btnSave));

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
        root.addView(presetRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        btnStart = new Button(this);
        btnStart.setText("▶ 开始滑动");
        btnStart.setOnClickListener(v -> startSwipe());
        btnStop = new Button(this);
        btnStop.setText("■ 停止");
        btnStop.setOnClickListener(v -> {
            SwipeAccessibilityService.stop();
            ShizukuSwipeEngine.stop();
            refreshStatus();
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
        });
        actions.addView(btnStart, weight());
        actions.addView(btnStop, weight());
        root.addView(actions);

        TextView foot = new TextView(this);
        foot.setText("提示：开始后可切到任意 App，滑动会在当前屏幕上执行。\n"
                + "Shizuku 方式需要先安装并启动 Shizuku App 并授权；"
                + "往返/曲线轨迹仅无障碍方式支持，Shizuku 方式为直线。");
        foot.setTextSize(13f);
        foot.setTextColor(0xFF777777);
        foot.setPadding(0, dp(12), 0, 0);
        root.addView(foot);

        return scroll;
    }

    private Button dirButton(String label, String angle) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> etAngle.setText(angle));
        return b;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开设置，请手动进入 系统设置 > 无障碍", Toast.LENGTH_LONG).show();
        }
    }

    private void ensureShizuku() {
        if (!ShizukuSwipeEngine.isAvailable()) {
            Toast.makeText(this, "Shizuku 未运行\n请先安装 Shizuku App 并用 ADB / root 启动服务",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ShizukuSwipeEngine.hasPermission()) {
            ShizukuSwipeEngine.bind(this);
            Toast.makeText(this, "Shizuku 已授权，已连接服务", Toast.LENGTH_SHORT).show();
        } else {
            ShizukuSwipeEngine.requestPermission(REQ_SHIZUKU);
            Toast.makeText(this, "已发起授权请求，请在弹窗中允许", Toast.LENGTH_LONG).show();
        }
        refreshStatus();
    }

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
        // 悬浮球始终使用「已保存」的参数；这里不再自动保存当前界面
        // Android 13+ 通知权限（前台服务通知）
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2002);
        }

        Intent i = new Intent(this, FloatingService.class)
                .setAction(FloatingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        Toast.makeText(this, "悬浮球已开启：使用已保存的参数，点它开始/停止", Toast.LENGTH_LONG).show();
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

    private boolean useShizukuMode() {
        return rbShizuku != null && rbShizuku.isChecked();
    }

    private void refreshStatus() {
        String status;
        boolean canStart;
        boolean canStop;

        if (useShizukuMode()) {
            if (!ShizukuSwipeEngine.isAvailable()) {
                status = "状态：✗ Shizuku 未运行（请先启动 Shizuku）";
                canStart = false;
                canStop = false;
            } else if (!ShizukuSwipeEngine.hasPermission()) {
                status = "状态：Shizuku 运行中，未授权（点「1b」授权）";
                canStart = false;
                canStop = false;
            } else if (ShizukuSwipeEngine.isRunning()) {
                status = "状态：● 正在滑动（Shizuku），已完成 "
                        + ShizukuSwipeEngine.getDoneCount() + " 次";
                canStart = false;
                canStop = true;
            } else {
                status = "状态：✓ Shizuku 就绪，可以开始";
                canStart = true;
                canStop = false;
            }
        } else {
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
                status = "状态：● 正在滑动（无障碍），已完成 "
                        + SwipeAccessibilityService.getDoneCount() + " 次";
                canStart = false;
                canStop = true;
            } else {
                status = "状态：✓ 就绪，可以开始";
                canStart = true;
                canStop = false;
            }
        }

        tvStatus.setText(status);
        btnStart.setEnabled(canStart);
        btnStop.setEnabled(canStop);
    }

    private SwipeConfig collectConfig() {
        SwipeConfig c = new SwipeConfig();
        c.xPercent = clampF(parseFloat(etX, c.xPercent), 0f, 100f);
        c.yPercent = clampF(parseFloat(etY, c.yPercent), 0f, 100f);
        c.angle = normalizeAngle(parseFloat(etAngle, c.angle));
        c.distanceMinDp = clampF(parseFloat(etDistMin, c.distanceMinDp), 1f, 4000f);
        c.distanceMaxDp = clampF(parseFloat(etDistMax, c.distanceMaxDp), 1f, 4000f);
        if (c.distanceMaxDp < c.distanceMinDp) {
            float t = c.distanceMinDp; c.distanceMinDp = c.distanceMaxDp; c.distanceMaxDp = t;
        }
        c.durationMs = clampL((long) parseLong(etDuration, c.durationMs), 50L, 60000L);
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
        c.useShizuku = useShizukuMode();
        return c;
    }

    /** 保存当前界面参数为「生效配置」，供悬浮球与下次启动使用。 */
    private void saveCurrentConfig() {
        SwipeConfigStore.save(this, collectConfig());
        Toast.makeText(this, "已保存：悬浮球和下次启动将使用这些参数", Toast.LENGTH_SHORT).show();
    }

    private void showSaveAsDialog() {
        final String[] items = new String[]{"槽 1", "槽 2", "槽 3"};
        new AlertDialog.Builder(this)
                .setTitle("另存到槽位")
                .setItems(items, (d, which) -> {
                    SwipeConfigStore.saveSlot(this, which + 1, collectConfig());
                    Toast.makeText(this, "已另存到槽 " + (which + 1), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLoadDialog() {
        final String[] items = new String[]{"槽 1", "槽 2", "槽 3"};
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

    private void applyConfigToUi(SwipeConfig c) {
        etX.setText(fmtNum(c.xPercent));
        etY.setText(fmtNum(c.yPercent));
        etAngle.setText(fmtNum(c.angle));
        etDistMin.setText(fmtNum(c.distanceMinDp));
        etDistMax.setText(fmtNum(c.distanceMaxDp));
        etDuration.setText(String.valueOf(c.durationMs));
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
        if (c.useShizuku) {
            rbShizuku.setChecked(true);
        } else {
            rbA11y.setChecked(true);
        }
        refreshIntervalFieldsEnabled();
        refreshPreview();
    }

    private static String fmtNum(float v) {
        if (v == Math.floor(v)) {
            return String.valueOf((int) v);
        }
        return String.valueOf(v);
    }

    private void startSwipe() {
        // 直接使用界面上的当前参数执行；是否持久化由「保存」按钮决定
        SwipeConfig c = collectConfig();

        if (c.useShizuku) {
            if (!ShizukuSwipeEngine.isAvailable()) {
                Toast.makeText(this, "Shizuku 未运行：请先安装并启动 Shizuku App",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!ShizukuSwipeEngine.hasPermission()) {
                Toast.makeText(this, "请先点「1b. 检查 / 授权 Shizuku」并允许授权",
                        Toast.LENGTH_LONG).show();
                return;
            }
            ShizukuSwipeEngine.bind(this);
            ShizukuSwipeEngine.start(c);
            Toast.makeText(this, "开始滑动（Shizuku）", Toast.LENGTH_SHORT).show();
        } else {
            if (!isAccessibilityEnabled() || !SwipeAccessibilityService.isConnected()) {
                Toast.makeText(this, "请先开启无障碍权限", Toast.LENGTH_SHORT).show();
                return;
            }
            SwipeAccessibilityService.start(c);
            Toast.makeText(this, "开始滑动", Toast.LENGTH_SHORT).show();
        }
        refreshStatus();
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

    private EditText addField(LinearLayout root, String label, String def, String info) {
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
        if (mPreview == null || etX == null) return;
        float x = parseFloat(etX, 50f);
        float y = parseFloat(etY, 50f);
        float ang = parseFloat(etAngle, 90f);
        float dMin = parseFloat(etDistMin, 300f);
        float dMax = parseFloat(etDistMax, 500f);
        float dist = (dMin + dMax) / 2f;
        float curve = parseFloat(etCurve, 0f);
        float jitter = parseFloat(etJitter, SwipeConfig.DEFAULT_JITTER);
        mPreview.update(x, y, ang, dist, curve, cbRoundTrip.isChecked(), jitter);
    }

    /** 根据「间隔随机」开关启用/禁用相关输入框。 */
    private void refreshIntervalFieldsEnabled() {
        if (cbIntervalRandom == null) return;
        boolean rnd = cbIntervalRandom.isChecked();
        etInterval.setEnabled(!rnd);
        etIntervalMin.setEnabled(rnd);
        etIntervalMax.setEnabled(rnd);
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
