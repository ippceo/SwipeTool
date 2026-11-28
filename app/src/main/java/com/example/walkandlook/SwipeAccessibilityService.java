package com.example.walkandlook;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    /** 上一轮手势实际占用的时长（毫秒），用于计算循环间隔（录制序列时长不固定）。 */
    private long mLastGestureSpanMs = 0L;
    /** 已调度但尚未执行的录制批次，停止时需要一并取消。 */
    private final List<Runnable> mPendingBatches = new ArrayList<>();

    /** 系统单次手势最多包含的笔画数，超出需要分批发送。 */
    private static final int MAX_STROKES_PER_GESTURE = 10;
    /** 系统单次手势的最长时长（毫秒）。 */
    private static final long MAX_GESTURE_DURATION_MS = 60000L;

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
            // 一次完整循环耗时：往返时含去程+停顿+回程；录制序列则取该序列总时长。
            long fallbackDuration = SwipeConfig.resolveDuration(mConfig);
            long gestureSpan = mLastGestureSpanMs > 0L
                    ? mLastGestureSpanMs
                    : (mConfig.roundTrip
                    ? (fallbackDuration * 2L + mConfig.pauseMs)
                    : fallbackDuration);
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

    /** 本次运行的循环次数上限：0 表示无限。 */
    public static int getLoops() {
        if (sInstance == null || sInstance.mConfig == null) return 0;
        return sInstance.mConfig.loops;
    }

    /** 剩余次数；无限循环或未运行时返回 -1。 */
    public static int getRemainingCount() {
        if (sInstance == null || sInstance.mConfig == null) return -1;
        int loops = sInstance.mConfig.loops;
        if (loops <= 0) return -1;
        return Math.max(0, loops - sInstance.mDoneCount);
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
        clearPendingBatches();
    }

    /**
     * 发送一次滑动手势。启用录制序列时按人手的真实轨迹回放；
     * 否则起点、长度、方向与弯曲都在这里重新随机，轨迹本身带横向摆动。
     */
    private boolean swipeOnce(SwipeConfig c) {
        if (c.useRecorded) {
            RecordedSequence seq = RecordedSwipeStore.find(this, c.recordedId);
            if (seq != null && !seq.strokes.isEmpty()) {
                mLastGestureSpanMs = Math.max(1L, seq.totalDurationMs());
                return playSequence(seq);
            }
            // 录制数据丢失（被删除 / 未选择）时回退到参数滑动，避免直接停住
        }

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return false;
        wm.getDefaultDisplay().getMetrics(dm);

        Random random = new Random();
        SwipeTrajectory trajectory = SwipeTrajectory.generate(
                c, dm.widthPixels, dm.heightPixels, dm.density, random);
        // 本次滑动的时长在「下限 ~ 上限」之间随机（相等时即固定）
        long duration = SwipeConfig.resolveDuration(c);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        if (c.roundTrip) {
            // 去程 + 回程，回程在去程结束后再开始
            builder.addStroke(new GestureDescription.StrokeDescription(
                    trajectory.buildPath(random, false), 0, duration));
            long returnStart = duration + c.pauseMs;
            builder.addStroke(new GestureDescription.StrokeDescription(
                    trajectory.buildPath(random, true), returnStart, duration));
            mLastGestureSpanMs = duration * 2L + c.pauseMs;
        } else {
            builder.addStroke(new GestureDescription.StrokeDescription(
                    trajectory.buildPath(random, false), 0, duration));
            mLastGestureSpanMs = duration;
        }

        return dispatchGesture(builder.build(), null, mHandler);
    }

    /**
     * 回放一整段录制序列：每一笔按其录制时的启动时间与持续时间发送，
     * 路径由密集的归一化采样点映射到当前屏幕，保留人手自然的抖动。
     * 系统单次手势最多 10 笔，超过时按时间顺序分批发送。
     */
    private boolean playSequence(RecordedSequence seq) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return false;
        wm.getDefaultDisplay().getMetrics(dm);
        final int w = dm.widthPixels;
        final int h = dm.heightPixels;

        List<RecordedSequence.Stroke> strokes = new ArrayList<>();
        for (RecordedSequence.Stroke s : seq.strokes) {
            if (s.points.size() >= 2) strokes.add(s);
        }
        if (strokes.isEmpty()) return false;
        Collections.sort(strokes, (a, b) -> Long.compare(a.startMs, b.startMs));

        clearPendingBatches();

        for (int i = 0; i < strokes.size(); i += MAX_STROKES_PER_GESTURE) {
            int end = Math.min(strokes.size(), i + MAX_STROKES_PER_GESTURE);
            long batchStart = strokes.get(i).startMs;
            GestureDescription.Builder builder = new GestureDescription.Builder();
            boolean added = false;

            for (int j = i; j < end; j++) {
                RecordedSequence.Stroke s = strokes.get(j);
                Path path = new Path();
                boolean started = false;
                for (RecordedSequence.Point p : s.points) {
                    float x = clamp(p.x * (w - 1f) + 0.5f, 1f, w - 1f);
                    float y = clamp(p.y * (h - 1f) + 0.5f, 1f, h - 1f);
                    if (!started) {
                        path.moveTo(x, y);
                        started = true;
                    } else {
                        path.lineTo(x, y);
                    }
                }
                if (!started) continue;
                long start = Math.max(0L, s.startMs - batchStart);
                long dur = Math.max(1L, Math.min(s.durationMs,
                        MAX_GESTURE_DURATION_MS - start));
                if (start >= MAX_GESTURE_DURATION_MS) continue;
                builder.addStroke(new GestureDescription.StrokeDescription(path, start, dur));
                added = true;
            }
            if (!added) continue;

            final GestureDescription gesture = builder.build();
            long delay = Math.max(0L, batchStart);
            if (delay == 0L) {
                dispatchGesture(gesture, null, mHandler);
            } else {
                Runnable batch = () -> dispatchGesture(gesture, null, mHandler);
                mPendingBatches.add(batch);
                mHandler.postDelayed(batch, delay);
            }
        }
        return true;
    }

    private void clearPendingBatches() {
        for (Runnable r : mPendingBatches) {
            mHandler.removeCallbacks(r);
        }
        mPendingBatches.clear();
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
