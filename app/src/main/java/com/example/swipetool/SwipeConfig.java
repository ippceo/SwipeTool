package com.example.swipetool;

/**
 * 滑动参数配置。所有滑动都基于屏幕绝对坐标的触摸事件模拟，
 * 不涉及任何平台检测绕过逻辑。
 *
 * 距离与起点都以「屏幕比例」表达：起点按屏宽 / 屏高百分比定位，
 * 滑动长度按屏高百分比取值，避免在不同分辨率 / 密度的手机上出现
 * 「同一个 dp 值长度体感差很多」的问题。
 */
public class SwipeConfig {
    /** 默认随机抖动强度（%）。升级自旧版本时会用它替代原来默认的 0。 */
    public static final float DEFAULT_JITTER = 40f;

    /** 起点横坐标随机下限，屏幕宽度百分比 0-100；与上限相等即固定 X */
    public float xMinPercent = 50f;
    /** 起点横坐标随机上限，屏幕宽度百分比 0-100 */
    public float xMaxPercent = 50f;
    /** 起点纵坐标随机下限，屏幕高度百分比 0-100；与上限相等即固定 Y */
    public float yMinPercent = 50f;
    /** 起点纵坐标随机上限，屏幕高度百分比 0-100 */
    public float yMaxPercent = 50f;
    /** 滑动角度：0=右, 90=下, 180=左, 270=上（屏幕坐标系，y 向下） */
    public float angle = 90f;
    /** 距离随机下限，单位为「屏幕高度百分比」0-100；与上限相等即固定长度 */
    public float distanceMinPercent = 30f;
    /** 距离随机上限，单位为「屏幕高度百分比」0-100 */
    public float distanceMaxPercent = 50f;
    /** 单次滑动时长的随机下限，单位毫秒（越小越快） */
    public long durationMinMs = 400L;
    /** 单次滑动时长的随机上限，单位毫秒；与下限相等即固定时长 */
    public long durationMaxMs = 700L;
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
    /** 轨迹弯曲幅度，单位为「屏幕高度百分比」-100～100，0 表示直线 */
    public float curvePercent = 0f;
    /** 运行时长上限，单位秒，0 表示不限制（到时自动停止） */
    public long maxDurationSec = 0L;
    /**
     * 随机抖动强度 0-100（%）：
     * 影响起点偏移、方向角、弯曲幅度与轨迹摆动，同时叠加到间隔上。
     * 0 表示关闭这些随机，仅保留「距离下限~上限」的长度随机
     * 与「起点下限~上限」的位置随机。
     */
    public float jitterPercent = DEFAULT_JITTER;

    /** 是否使用「录制序列」回放，替代按参数生成的随机滑动。 */
    public boolean useRecorded = false;
    /** 当前选用的录制序列 id；为空表示未选择。 */
    public String recordedId = "";
    /** 当前选用的录制序列名称，仅用于界面展示。 */
    public String recordedName = "";

    /**
     * 启动延迟（秒）：点主界面「开始滑动 / 试放」后等待 N 秒再真正执行，
     * 方便用户切到目标应用。仅主界面按钮生效；悬浮球点击不延迟。
     */
    public int startDelaySec = 0;

    /** 返回 [min, max] 之间的随机浮点数。 */
    public static float rnd(float min, float max) {
        if (max <= min) return min;
        return min + (float) Math.random() * (max - min);
    }

    /** 解析本次滑动起点横坐标（屏宽百分比），在 [下限, 上限] 之间随机。 */
    public static float resolveStartX(SwipeConfig c) {
        float lo = Math.min(c.xMinPercent, c.xMaxPercent);
        float hi = Math.max(c.xMinPercent, c.xMaxPercent);
        return rnd(lo, hi);
    }

    /** 解析本次滑动起点纵坐标（屏高百分比），在 [下限, 上限] 之间随机。 */
    public static float resolveStartY(SwipeConfig c) {
        float lo = Math.min(c.yMinPercent, c.yMaxPercent);
        float hi = Math.max(c.yMinPercent, c.yMaxPercent);
        return rnd(lo, hi);
    }

    /** 解析本次滑动长度（屏高百分比），在 [下限, 上限] 之间随机。 */
    public static float resolveDistancePercent(SwipeConfig c) {
        float lo = Math.max(0f, Math.min(c.distanceMinPercent, c.distanceMaxPercent));
        float hi = Math.max(lo, Math.max(c.distanceMinPercent, c.distanceMaxPercent));
        return rnd(lo, hi);
    }

    /**
     * 解析本次滑动实际使用的时长（毫秒）：
     * 在 [durationMinMs, durationMaxMs] 之间随机取值；两者相等即固定时长。
     */
    public static long resolveDuration(SwipeConfig c) {
        long lo = Math.min(c.durationMinMs, c.durationMaxMs);
        long hi = Math.max(c.durationMinMs, c.durationMaxMs);
        lo = Math.max(1L, lo);
        hi = Math.max(lo, hi);
        if (hi == lo) return lo;
        return lo + (long) (Math.random() * (double) (hi - lo + 1L));
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
