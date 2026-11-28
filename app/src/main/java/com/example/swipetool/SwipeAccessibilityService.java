package com.example.swipetool;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.WindowManager;

import java.util.Random;

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
     * 发送一次滑动手势。起点、长度、方向与弯曲都在这里重新随机，
     * 轨迹本身带横向摆动，不会每次都是一模一样的直线。
     */
    private boolean swipeOnce(SwipeConfig c) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return false;
        wm.getDefaultDisplay().getMetrics(dm);

        Random random = new Random();
        SwipeTrajectory trajectory = SwipeTrajectory.generate(
                c, dm.widthPixels, dm.heightPixels, dm.density, random);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        if (c.roundTrip) {
            // 去程 + 回程，回程在去程结束后再开始
            builder.addStroke(new GestureDescription.StrokeDescription(
                    trajectory.buildPath(random, false), 0, c.durationMs));
            long returnStart = c.durationMs + c.pauseMs;
            builder.addStroke(new GestureDescription.StrokeDescription(
                    trajectory.buildPath(random, true), returnStart, c.durationMs));
        } else {
            builder.addStroke(new GestureDescription.StrokeDescription(
                    trajectory.buildPath(random, false), 0, c.durationMs));
        }

        return dispatchGesture(builder.build(), null, mHandler);
    }
}
