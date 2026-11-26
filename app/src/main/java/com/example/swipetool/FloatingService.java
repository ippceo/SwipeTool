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
import android.widget.TextView;
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
    private TextView mBall;
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

        int size = dp(54);
        mBall = new TextView(this);
        mBall.setWidth(size);
        mBall.setHeight(size);
        mBall.setGravity(Gravity.CENTER);
        mBall.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mBall.setTextColor(Color.WHITE);
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
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(mRunning ? 0xFF43A047 : 0xFF757575);
        bg.setStroke(dp(2), Color.WHITE);
        mBall.setBackground(bg);
        mBall.setText(mRunning ? "■" : "▶");
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
        PendingIntent closePi = PendingIntent.getService(this, 2,
                new Intent(this, FloatingService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContentTitle("屏幕滑动 · 悬浮控制")
                .setContentText(mRunning ? "运行中 · 点悬浮球或此处停止" : "已就绪 · 点悬浮球开始")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_play,
                        mRunning ? "停止" : "开始", togglePi).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "关闭悬浮球", closePi).build());

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
