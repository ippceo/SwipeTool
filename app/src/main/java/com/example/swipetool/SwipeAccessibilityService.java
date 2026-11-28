package com.example.swipetool;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.WindowManager;

/**
 * 基于无障碍服务的屏幕滑动引擎。
 *
 * 通过 GestureDescription 向系统发送标准的触摸手势（stroke），
 * 支持直线与带弧度的轨迹、可调时长与循环。
 * 这里只做通用的输入事件模拟，不针对任何具体 App 做识别或对抗。
 */
public class SwipeAccessibilityService extends AccessibilityService {

    /** 静态实例，供 Activity 直接调用（同进程内）。 */
    private static SwipeAccessibilityService sInstance;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SwipeConfig mConfig;
    private boolean mRunning = false;
    private int mDoneCount = 0;
    private long mStartTime = 0L;

    private final Runnable mSwipeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mRunning || mConfig == null) return;

            // 运行时长上限（0 = 不限）
            if (mConfig.maxDurationSec > 0
                    && SystemClock.elapsedRealtime() - mStartTime >= mConfig.maxDurationSec * 1000L) {
                stop();
                return;
            }

            boolean dispatched = swipeOnce(mConfig);
            if (!dispatched) {
                // 手势发送失败（例如屏幕不可交互），稍后重试
                mHandler.postDelayed(this, 1000L);
                return;
            }

            mDoneCount++;
            int loops = mConfig.loops;
            if (loops > 0 && mDoneCount >= loops) {
                stop();
                return;
            }
            // 一次完整循环耗时：往返时含去程+停顿+回程，再加本次间隔。
            long gestureSpan = mConfig.roundTrip
                    ? (mConfig.durationMs * 2L + mConfig.pauseMs)
                    : mConfig.durationMs;
            // 间隔：固定值，或在「最小~最大」之间随机（再叠加抖动扰动）
            long interval = SwipeConfig.resolveInterval(mConfig);
            long cycle = gestureSpan + interval + 50L; // 50ms 余量，避免手势重叠
            mHandler.postDelayed(this, cycle);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 本服务不需要监听事件，仅用于执行手势
    }

    @Override
    public void onInterrupt() {
        stop();
    }

    @Override
    public void onDestroy() {
        stop();
        sInstance = null;
        super.onDestroy();
    }

    /** 服务是否已连接到系统。 */
    public static boolean isConnected() {
        return sInstance != null;
    }

    /** 当前是否正在滑动。 */
    public static boolean isRunning() {
        return sInstance != null && sInstance.mRunning;
    }

    /** 已完成的滑动次数。 */
    public static int getDoneCount() {
        return sInstance == null ? 0 : sInstance.mDoneCount;
    }

    /** 启动滑动。 */
    public static void start(SwipeConfig config) {
        if (sInstance == null) return;
        sInstance.startInternal(config);
    }

    /** 停止滑动。 */
    public static void stop() {
        if (sInstance != null) sInstance.stopInternal();
    }

    private void startInternal(SwipeConfig config) {
        stopInternal();
        mConfig = config;
        mDoneCount = 0;
        mRunning = true;
        mStartTime = SystemClock.elapsedRealtime();
        mHandler.post(mSwipeRunnable);
    }

    private void stopInternal() {
        mRunning = false;
        mHandler.removeCallbacks(mSwipeRunnable);
    }

    /**
     * 发送一次滑动手势。
     */
    private boolean swipeOnce(SwipeConfig c) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return false;
        wm.getDefaultDisplay().getMetrics(dm);

        float density = dm.density;
        float maxX = dm.widthPixels - 1f;
        float maxY = dm.heightPixels - 1f;

        // 随机抖动：对起点、距离、角度做小幅扰动，让轨迹不固定
        float j = Math.max(0f, Math.min(1f, c.jitterPercent / 100f));
        float sx = clamp(dm.widthPixels * (c.xPercent / 100f), 1f, maxX);
        float sy = clamp(dm.heightPixels * (c.yPercent / 100f), 1f, maxY);
        if (j > 0f) {
            sx = clamp(sx + rnd(-j * dm.widthPixels * 0.05f, j * dm.widthPixels * 0.05f), 1f, maxX);
            sy = clamp(sy + rnd(-j * dm.heightPixels * 0.05f, j * dm.heightPixels * 0.05f), 1f, maxY);
        }

        float angle = c.angle + (j > 0f ? rnd(-j * 20f, j * 20f) : 0f);
        float distanceDp = c.distanceDp * (1f + (j > 0f ? rnd(-j, j) : 0f));
        double rad = Math.toRadians(angle);
        float dx = (float) (distanceDp * density * Math.cos(rad));
        float dy = (float) (distanceDp * density * Math.sin(rad));
        // 终点限制在屏幕内，避免越界坐标导致 dispatchGesture 失败
        float ex = clamp(sx + dx, 1f, maxX);
        float ey = clamp(sy + dy, 1f, maxY);

        GestureDescription.Builder builder = new GestureDescription.Builder();

        if (c.roundTrip) {
            // 去程 + 回程，回程在去程结束后再开始
            Path outPath = buildPath(sx, sy, ex, ey, c.curveDp * density);
            builder.addStroke(new GestureDescription.StrokeDescription(
                    outPath, 0, c.durationMs));

            long returnStart = c.durationMs + c.pauseMs;
            Path backPath = buildPath(ex, ey, sx, sy, -c.curveDp * density);
            builder.addStroke(new GestureDescription.StrokeDescription(
                    backPath, returnStart, c.durationMs));
        } else {
            Path path = buildPath(sx, sy, ex, ey, c.curveDp * density);
            builder.addStroke(new GestureDescription.StrokeDescription(
                    path, 0, c.durationMs));
        }

        return dispatchGesture(builder.build(), null, mHandler);
    }

    /** 将数值限制在 [min, max] 区间内。 */
    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** 返回 [min, max] 之间的随机浮点数。 */
    private static float rnd(float min, float max) {
        if (max <= min) return min;
        return min + (float) Math.random() * (max - min);
    }

    /**
     * 构造滑动轨迹。curve 为 0 时是直线，非 0 时用二次贝塞尔曲线制造弧度。
     */
    private static Path buildPath(float sx, float sy, float ex, float ey, float curve) {
        Path path = new Path();
        path.moveTo(sx, sy);
        if (Math.abs(curve) < 0.5f) {
            path.lineTo(ex, ey);
        } else {
            // 取线段中点，沿垂直方向偏移作为控制点
            float mx = (sx + ex) / 2f;
            float my = (sy + ey) / 2f;
            float vx = ex - sx;
            float vy = ey - sy;
            float len = (float) Math.sqrt(vx * vx + vy * vy);
            if (len < 0.01f) {
                path.lineTo(ex, ey);
            } else {
                float nx = -vy / len;
                float ny = vx / len;
                path.quadTo(mx + nx * curve, my + ny * curve, ex, ey);
            }
        }
        return path;
    }
}
