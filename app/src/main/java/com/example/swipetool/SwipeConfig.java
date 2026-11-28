package com.example.swipetool;

/**
 * 滑动参数配置。所有滑动都基于屏幕绝对坐标的触摸事件模拟，
 * 不涉及任何平台检测绕过逻辑。
 */
public class SwipeConfig {
    /** 默认随机抖动强度（%）。升级自旧版本时会用它替代原来默认的 0。 */
    public static final float DEFAULT_JITTER = 40f;

    /** 起点横坐标，屏幕宽度百分比 0-100 */
    public float xPercent = 50f;
    /** 起点纵坐标，屏幕高度百分比 0-100 */
    public float yPercent = 50f;
    /** 滑动角度：0=右, 90=下, 180=左, 270=上（屏幕坐标系，y 向下） */
    public float angle = 90f;
    /** 距离随机下限，单位 dp */
    public float distanceMinDp = 300f;
    /** 距离随机上限，单位 dp；与下限相等即固定长度 */
    public float distanceMaxDp = 500f;
    /** 单次滑动持续时间，单位毫秒（越小越快） */
    public long durationMs = 500L;
    /** 两次滑动之间的固定间隔，单位毫秒（未开启随机间隔时使用） */
    public long intervalMs = 500L;
    /** 是否在最小 / 最大间隔之间随机取值，让每次间隔都不同 */
    public boolean intervalRandom = false;
    /** 随机间隔下限，单位毫秒 */
    public long intervalMinMs = 200L;
    /** 随机间隔上限，单位毫秒 */
    public long intervalMaxMs = 800L;
    /** 循环次数，0 表示无限循环 */
    public int loops = 0;
    /** 是否往返滑动（到达终点后再滑回起点） */
    public boolean roundTrip = false;
    /** 往返时到达终点后的停顿，单位毫秒 */
    public long pauseMs = 200L;
    /** 轨迹弯曲幅度，单位 dp，0 表示直线 */
    public float curveDp = 0f;
    /** 是否使用 Shizuku 执行（false = 无障碍服务） */
    public boolean useShizuku = false;
    /** 运行时长上限，单位秒，0 表示不限制（到时自动停止） */
    public long maxDurationSec = 0L;
    /**
     * 随机抖动强度 0-100（%）：
     * 影响起点偏移、方向角、弯曲幅度与轨迹摆动，同时叠加到间隔上。
     * 0 表示关闭这些随机，仅保留「距离下限~上限」的长度随机。
     */
    public float jitterPercent = DEFAULT_JITTER;

    /** 返回 [min, max] 之间的随机浮点数。 */
    public static float rnd(float min, float max) {
        if (max <= min) return min;
        return min + (float) Math.random() * (max - min);
    }

    /**
     * 解析本次循环实际使用的间隔（毫秒）：
     * 开启「间隔随机」时在 [intervalMinMs, intervalMaxMs] 内随机取值；
     * 否则使用固定 intervalMs。两种情况都会再叠加「随机抖动」对间隔的扰动。
     */
    public static long resolveInterval(SwipeConfig c) {
        long base;
        if (c.intervalRandom && c.intervalMaxMs > c.intervalMinMs) {
            base = c.intervalMinMs
                    + (long) (Math.random() * (double) (c.intervalMaxMs - c.intervalMinMs));
        } else {
            base = c.intervalMs;
        }
        float jj = Math.max(0f, Math.min(1f, c.jitterPercent / 100f));
        if (jj > 0f) {
            base = Math.max(0L, Math.round(base * (1f + rnd(-jj, jj))));
        }
        return Math.max(0L, base);
    }
}
