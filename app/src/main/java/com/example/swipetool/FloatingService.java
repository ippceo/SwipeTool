package com.example.swipetool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * 悬浮球服务：在任何应用之上提供一个可拖动的圆形按钮，
 * 单击切换「开始 / 停止」滑动，同时在前台通知里提供相同操作。
 *
 * 仅作为控制入口，本身不执行任何平台识别或对抗逻辑。
 */
public class FloatingService extends Service {

    public static final String ACTION_START = "com.example.swipetool.FLOAT_START";
    public static final String ACTION_STOP = "com.example.swipetool.FLOAT_STOP";
    public static final String ACTION_TOGGLE = "com.example.swipetool.FLOAT_TOGGLE";

    private static final String CHANNEL_ID = "floating_swipe";
    private static final int NOTIF_ID = 2001;

    private WindowManager mWindowManager;
    private FrameLayout mBall;        // 圆形外框：渐变背景 + 阴影
    private ImageView mBallIcon;      // 居中的播放 / 停止图标
    private WindowManager.LayoutParams mParams;
    private boolean mRunning = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopSwipe();
            removeBall();
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground();
        if (mBall == null) {
            showBall();
        }
        if (ACTION_TOGGLE.equals(action)) {
            toggle();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopSwipe();
        removeBall();
        super.onDestroy();
    }

    // ---------- 悬浮球 ----------

    private void showBall() {
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mWindowManager == null) {
            stopSelf();
            return;
        }

        int size = dp(56);
        mBall = new FrameLayout(this);
        mBall.setClickable(true);
        mBall.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBall.setElevation(dp(8));
        }
        mBallIcon = new ImageView(this);
        mBallIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 图标放进 FrameLayout 并指定 Gravity.CENTER，保证严格居中
        mBall.addView(mBallIcon, new FrameLayout.LayoutParams(
                dp(26), dp(26), Gravity.CENTER));
        updateBallLook();

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        mParams = new WindowManager.LayoutParams(
                size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = dp(12);
        mParams.y = dp(220);

        setupTouch();

        try {
            mWindowManager.addView(mBall, mParams);
        } catch (Throwable t) {
            Toast.makeText(this, "悬浮球添加失败，请检查「显示在其他应用上层」权限",
                    Toast.LENGTH_LONG).show();
            mBall = null;
            stopSelf();
        }
    }

    private void updateBallLook() {
        if (mBall == null) return;
        // 渐变填充 + 白色描边，比单色更有质感；运行中绿色，停止时灰蓝
        int[] colors = mRunning
                ? new int[]{0xFF66BB6A, 0xFF2E7D32}
                : new int[]{0xFF78909C, 0xFF37474F};
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(2), Color.WHITE);
        mBall.setBackground(bg);
        if (mBallIcon != null) {
            mBallIcon.setImageResource(mRunning ? R.drawable.ic_stop : R.drawable.ic_play);
        }
    }

    private void setupTouch() {
        mBall.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = mParams.x;
                        startY = mParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (e.getRawX() - downRawX);
                        int dy = (int) (e.getRawY() - downRawY);
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                            moved = true;
                        }
                        mParams.x = startX + dx;
                        mParams.y = startY + dy;
                        try {
                            mWindowManager.updateViewLayout(mBall, mParams);
                        } catch (Throwable ignored) {
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            toggle();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void removeBall() {
        if (mWindowManager != null && mBall != null) {
            try {
                mWindowManager.removeView(mBall);
            } catch (Throwable ignored) {
            }
        }
        mBall = null;
    }

    // ---------- 启停 ----------

    private void toggle() {
        if (mRunning) {
            stopSwipe();
        } else {
            startSwipe();
        }
    }

    private void startSwipe() {
        SwipeConfig c = SwipeConfigStore.load(this);

        if (c.useShizuku) {
            if (!ShizukuSwipeEngine.isAvailable()) {
                Toast.makeText(this, "Shizuku 未运行，请先启动 Shizuku", Toast.LENGTH_LONG).show();
                return;
            }
            if (!ShizukuSwipeEngine.hasPermission()) {
                Toast.makeText(this, "Shizuku 未授权，请先在 App 内授权", Toast.LENGTH_LONG).show();
                return;
            }
            ShizukuSwipeEngine.bind(this);
            ShizukuSwipeEngine.start(c);
            mRunning = true;
        } else {
            if (!SwipeAccessibilityService.isConnected()) {
                Toast.makeText(this, "无障碍服务未连接，请先开启无障碍权限", Toast.LENGTH_LONG).show();
                return;
            }
            SwipeAccessibilityService.start(c);
            mRunning = true;
        }

        updateBallLook();
        updateNotification();
    }

    private void stopSwipe() {
        SwipeAccessibilityService.stop();
        ShizukuSwipeEngine.stop();
        mRunning = false;
        updateBallLook();
        updateNotification();
    }

    // ---------- 前台通知 ----------

    private void ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "悬浮滑动控制", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
        }
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        PendingIntent togglePi = PendingIntent.getService(this, 1,
                new Intent(this, FloatingService.class).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // 「退出」：停止滑动 + 移除悬浮球 + 结束前台服务
        PendingIntent exitPi = PendingIntent.getService(this, 2,
                new Intent(this, FloatingService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // 点击通知主体回到 App 主界面
        PendingIntent openPi = PendingIntent.getActivity(this, 3,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContentTitle("屏幕滑动 · 悬浮控制")
                .setContentText(mRunning ? "运行中 · 点「停止」暂停，点「退出」关闭"
                        : "已就绪 · 点悬浮球开始，点「退出」关闭")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_play,
                        mRunning ? "停止" : "开始", togglePi).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "退出", exitPi).build());

        return b.build();
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
