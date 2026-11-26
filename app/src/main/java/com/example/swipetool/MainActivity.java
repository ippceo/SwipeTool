package com.example.swipetool;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 控制面板：设置滑动的方向 / 角度 / 位置 / 速度 / 次数 / 间隔，
 * 然后启动或停止滑动服务。
 */
public class MainActivity extends Activity {

    private EditText etX, etY, etAngle, etDistance, etDuration, etInterval, etLoops, etPause, etCurve;
    private CheckBox cbRoundTrip;
    private Button btnStart, btnStop;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
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
                + "首次使用需开启无障碍权限，然后回到本页点「开始滑动」。");
        hint.setTextSize(14f);
        hint.setTextColor(0xFF555555);
        hint.setPadding(0, 0, 0, dp(8));
        root.addView(hint);

        // 无障碍权限入口
        Button btnPermission = new Button(this);
        btnPermission.setText("1. 开启无障碍权限");
        btnPermission.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(btnPermission, fullWidth(btnPermission));

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
        root.addView(actions);

        TextView foot = new TextView(this);
        foot.setText("提示：开始后可切到任意 App，滑动会在当前屏幕上执行。\n"
                + "停止请回到本页点「停止」，或下拉通知栏结束服务。");
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
        boolean perm = isAccessibilityEnabled();
        boolean connected = SwipeAccessibilityService.isConnected();
        boolean running = SwipeAccessibilityService.isRunning();
        String status;
        if (!perm) {
            status = "状态：✗ 无障碍权限未开启";
        } else if (!connected) {
            status = "状态：权限已开启，服务未连接（可尝试重启 App）";
        } else if (running) {
            status = "状态：● 正在滑动，已完成 "
                    + SwipeAccessibilityService.getDoneCount() + " 次";
        } else {
            status = "状态：✓ 就绪，可以开始";
        }
        tvStatus.setText(status);
        btnStart.setEnabled(perm && connected && !running);
        btnStop.setEnabled(running);
    }

    private void startSwipe() {
        if (!isAccessibilityEnabled() || !SwipeAccessibilityService.isConnected()) {
            Toast.makeText(this, "请先开启无障碍权限", Toast.LENGTH_SHORT).show();
            return;
        }
        SwipeConfig c = new SwipeConfig();
        c.xPercent = parseFloat(etX, c.xPercent);
        c.yPercent = parseFloat(etY, c.yPercent);
        c.angle = parseFloat(etAngle, c.angle);
        c.distanceDp = parseFloat(etDistance, c.distanceDp);
        c.durationMs = (long) parseLong(etDuration, c.durationMs);
        c.intervalMs = (long) parseLong(etInterval, c.intervalMs);
        c.loops = (int) parseLong(etLoops, c.loops);
        c.roundTrip = cbRoundTrip.isChecked();
        c.pauseMs = (long) parseLong(etPause, c.pauseMs);
        c.curveDp = parseFloat(etCurve, c.curveDp);

        if (c.durationMs < 50) c.durationMs = 50;
        if (c.intervalMs < 0) c.intervalMs = 0;
        if (c.loops < 0) c.loops = 0;

        SwipeAccessibilityService.start(c);
        Toast.makeText(this, "开始滑动", Toast.LENGTH_SHORT).show();
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
