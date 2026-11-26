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
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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

    private EditText etX, etY, etAngle, etDistance, etDuration, etInterval, etLoops, etPause, etCurve, etMaxDur, etJitter;
    private CheckBox cbRoundTrip;
    private Button btnStart, btnStop;
    private TextView tvStatus;
    private RadioGroup rgMode;
    private RadioButton rbA11y, rbShizuku;

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

        addSection(root, "滑动参数");
        etX = addField(root, "起点 X（屏宽百分比 0-100）", "50");
        etY = addField(root, "起点 Y（屏高百分比 0-100）", "50");
        etAngle = addField(root, "角度（0-359，可任意斜向）", "90");
        etDistance = addField(root, "距离（dp）", "400");
        etDuration = addField(root, "滑动时长 ms（越小越快）", "500");
        etInterval = addField(root, "间隔 ms", "500");

        cbRoundTrip = new CheckBox(this);
        cbRoundTrip.setText("往返滑动（到终点后滑回起点）");
        root.addView(cbRoundTrip);
        etPause = addField(root, "往返停顿 ms", "200");
        etCurve = addField(root, "轨迹弯曲幅度 dp（0=直线）", "0");
        etLoops = addField(root, "循环次数（0=无限）", "0");
        etMaxDur = addField(root, "运行时长上限（秒，0=不限，到时自动停）", "0");
        etJitter = addField(root, "随机抖动强度 %（0=关闭；扰动起点/角度/距离/间隔）", "0");

        Button btnPreset = new Button(this);
        btnPreset.setText("参数预设（保存 / 加载）");
        btnPreset.setOnClickListener(v -> showPresetDialog());
        root.addView(btnPreset, fullWidth(btnPreset));

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
        // 保存当前界面配置，供悬浮球在任意界面复用
        SwipeConfigStore.save(this, collectConfig());

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
        Toast.makeText(this, "悬浮球已开启：切到任意界面，点它开始/停止", Toast.LENGTH_LONG).show();
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
        c.distanceDp = clampF(parseFloat(etDistance, c.distanceDp), 1f, 4000f);
        c.durationMs = clampL((long) parseLong(etDuration, c.durationMs), 50L, 60000L);
        c.intervalMs = clampL((long) parseLong(etInterval, c.intervalMs), 0L, 600000L);
        c.loops = (int) clampL(parseLong(etLoops, c.loops), 0L, 1000000L);
        c.roundTrip = cbRoundTrip.isChecked();
        c.pauseMs = clampL((long) parseLong(etPause, c.pauseMs), 0L, 60000L);
        c.curveDp = clampF(parseFloat(etCurve, c.curveDp), -2000f, 2000f);
        c.maxDurationSec = clampL(parseLong(etMaxDur, c.maxDurationSec), 0L, 86400L);
        c.jitterPercent = clampF(parseFloat(etJitter, c.jitterPercent), 0f, 100f);
        c.useShizuku = useShizukuMode();
        return c;
    }

    private void showPresetDialog() {
        final String[] items = new String[]{
                "槽 1 · 保存当前", "槽 1 · 加载",
                "槽 2 · 保存当前", "槽 2 · 加载",
                "槽 3 · 保存当前", "槽 3 · 加载"
        };
        new AlertDialog.Builder(this)
                .setTitle("参数预设")
                .setItems(items, (d, which) -> {
                    int slot = which / 2 + 1;
                    if (which % 2 == 0) {
                        SwipeConfigStore.saveSlot(this, slot, collectConfig());
                        Toast.makeText(this, "已保存到槽 " + slot, Toast.LENGTH_SHORT).show();
                    } else {
                        applyConfigToUi(SwipeConfigStore.loadSlot(this, slot));
                        Toast.makeText(this, "已加载槽 " + slot, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyConfigToUi(SwipeConfig c) {
        etX.setText(fmtNum(c.xPercent));
        etY.setText(fmtNum(c.yPercent));
        etAngle.setText(fmtNum(c.angle));
        etDistance.setText(fmtNum(c.distanceDp));
        etDuration.setText(String.valueOf(c.durationMs));
        etInterval.setText(String.valueOf(c.intervalMs));
        etLoops.setText(String.valueOf(c.loops));
        cbRoundTrip.setChecked(c.roundTrip);
        etPause.setText(String.valueOf(c.pauseMs));
        etCurve.setText(fmtNum(c.curveDp));
        etMaxDur.setText(String.valueOf(c.maxDurationSec));
        etJitter.setText(fmtNum(c.jitterPercent));
        if (c.useShizuku) {
            rbShizuku.setChecked(true);
        } else {
            rbA11y.setChecked(true);
        }
    }

    private static String fmtNum(float v) {
        if (v == Math.floor(v)) {
            return String.valueOf((int) v);
        }
        return String.valueOf(v);
    }

    private void startSwipe() {
        SwipeConfig c = collectConfig();
        SwipeConfigStore.save(this, c);

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

    private EditText addField(LinearLayout root, String label, String def) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14f);
        tv.setPadding(0, dp(6), 0, dp(2));
        root.addView(tv);

        EditText et = new EditText(this);
        et.setText(def);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(et, fullWidth(et));
        return et;
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
