package com.example.swipetool;

/**
 * 滑动参数配置。所有滑动都基于屏幕绝对坐标的触摸事件模拟，
 * 不涉及任何平台检测绕过逻辑。
 */
public class SwipeConfig {
    /** 起点横坐标，屏幕宽度百分比 0-100 */
    public float xPercent = 50f;
    /** 起点纵坐标，屏幕高度百分比 0-100 */
    public float yPercent = 50f;
    /** 滑动角度：0=右, 90=下, 180=左, 270=上（屏幕坐标系，y 向下） */
    public float angle = 90f;
    /** 滑动距离，单位 dp */
    public float distanceDp = 400f;
    /** 单次滑动持续时间，单位毫秒（越小越快） */
    public long durationMs = 500L;
    /** 两次滑动之间的间隔，单位毫秒 */
    public long intervalMs = 500L;
    /** 循环次数，0 表示无限循环 */
    public int loops = 0;
    /** 是否往返滑动（到达终点后再滑回起点） */
    public boolean roundTrip = false;
    /** 往返时到达终点后的停顿，单位毫秒 */
    public long pauseMs = 200L;
    /** 轨迹弯曲幅度，单位 dp，0 表示直线 */
    public float curveDp = 0f;
}
