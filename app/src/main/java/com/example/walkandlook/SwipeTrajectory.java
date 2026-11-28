package com.example.walkandlook;

import android.graphics.Path;

import java.util.Random;

/**
 * 拟人化随机轨迹生成器。
 *
 * 每次滑动都会重新计算：
 * - 起点在设定位置附近随机偏移；
 * - 长度在「距离下限 ~ 距离上限」之间随机；
 * - 方向角与弯曲幅度带随机抖动；
 * - 轨迹本身叠加多段横向摆动，看起来像真人手指那样歪歪扭扭。
 *
 * 所有坐标均为屏幕像素。抖动强度取自 {@link SwipeConfig#jitterPercent}。
 */
final class SwipeTrajectory {

    /** 抖动为 100% 时，起点最多偏移屏幕短边的 6%。 */
    private static final float START_SHIFT_RATIO = 0.06f;
    /** 抖动为 100% 时，方向角最多偏移 22°。 */
    private static final float ANGLE_SHIFT_DEG = 22f;
    /** 抖动为 100% 时，弯曲幅度额外随机 ±8% 屏幕高度。 */
    private static final float CURVE_RANDOM_RATIO = 0.08f;
    /** 抖动为 100% 时，轨迹摆动幅度约为路径长度的 6%。 */
    private static final float WOBBLE_RATIO = 0.06f;

    final float startX, startY, endX, endY;

    private final float curvePx;
    private final float wobblePx;
    private final float minX, minY, maxX, maxY;

    private SwipeTrajectory(float startX, float startY, float endX, float endY,
                            float curvePx, float wobblePx,
                            float minX, float minY, float maxX, float maxY) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.curvePx = curvePx;
        this.wobblePx = wobblePx;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    /** 按配置随机生成一条本次滑动的起终点与轨迹参数。 */
    static SwipeTrajectory generate(SwipeConfig c, float width, float height,
                                    float density, Random random) {
        float strength = clamp(c.jitterPercent / 100f, 0f, 1f);
        float minSide = Math.min(width, height);

        // 起点在「下限 ~ 上限」区间内随机（屏宽 / 屏高百分比）；
        // 再叠加抖动带来的微偏移，让固定区间时也有轻微浮动。
        float sx = width * (SwipeConfig.resolveStartX(c) / 100f);
        float sy = height * (SwipeConfig.resolveStartY(c) / 100f);
        if (strength > 0f) {
            sx += signed(random) * strength * minSide * START_SHIFT_RATIO;
            sy += signed(random) * strength * minSide * START_SHIFT_RATIO;
        }
        sx = clamp(sx, 1f, width - 1f);
        sy = clamp(sy, 1f, height - 1f);

        // 长度按「屏高百分比」换算成像素，与屏幕大小成正比，不同手机体感一致。
        float distancePx = SwipeConfig.resolveDistancePercent(c) / 100f * height;

        float angle = c.angle;
        if (strength > 0f) {
            angle += signed(random) * strength * ANGLE_SHIFT_DEG;
        }
        double rad = Math.toRadians(angle);
        float ex = clamp(sx + (float) (Math.cos(rad) * distancePx), 1f, width - 1f);
        float ey = clamp(sy + (float) (Math.sin(rad) * distancePx), 1f, height - 1f);

        // 弯曲幅度按「屏高百分比」换算成像素，与屏幕大小成正比。
        float curve = c.curvePercent / 100f * height;
        if (strength > 0f) {
            curve += signed(random) * strength * CURVE_RANDOM_RATIO * height;
        }
        float length = (float) Math.hypot(ex - sx, ey - sy);
        float wobble = strength * length * WOBBLE_RATIO;

        return new SwipeTrajectory(sx, sy, ex, ey, curve, wobble,
                1f, 1f, width - 1f, height - 1f);
    }

    /** 生成去程（reverse=false）或回程（reverse=true）的路径。 */
    Path buildPath(Random random, boolean reverse) {
        if (reverse) {
            return humanPath(endX, endY, startX, startY, -curvePx, wobblePx, random,
                    minX, minY, maxX, maxY);
        }
        return humanPath(startX, startY, endX, endY, curvePx, wobblePx, random,
                minX, minY, maxX, maxY);
    }

    /**
     * 构建一条平滑、略带摆动的路径：
     * curve 是整体弧度（沿垂直方向的偏移），wobble 是叠加的随机摆动幅度。
     * 两端点保持精确，中间点用 Catmull-Rom 转三次贝塞尔平滑连接。
     */
    static Path humanPath(float x0, float y0, float x1, float y1,
                          float curve, float wobble, Random random,
                          float minX, float minY, float maxX, float maxY) {
        Path path = new Path();
        path.moveTo(x0, y0);

        float vx = x1 - x0;
        float vy = y1 - y0;
        float length = (float) Math.hypot(vx, vy);
        if (length < 1f) {
            path.lineTo(x1, y1);
            return path;
        }
        float nx = -vy / length;
        float ny = vx / length;

        int segments = 5 + random.nextInt(4); // 5~8 段，每次长度不同
        float phase1 = random.nextFloat() * 6.2831855f;
        float phase2 = random.nextFloat() * 6.2831855f;
        float[] px = new float[segments + 1];
        float[] py = new float[segments + 1];
        px[0] = x0;
        py[0] = y0;
        px[segments] = x1;
        py[segments] = y1;

        for (int i = 1; i < segments; i++) {
            float t = (float) i / segments;
            float envelope = (float) Math.sin(Math.PI * t);
            // 低频大摆 + 高频小抖，叠加整体弧度
            float wave = (float) (Math.sin(t * Math.PI * 2f + phase1) * 0.65f
                    + Math.sin(t * Math.PI * 4f + phase2) * 0.35f);
            float offset = envelope * curve + wave * wobble * envelope;
            float x = x0 + vx * t + nx * offset;
            float y = y0 + vy * t + ny * offset;
            px[i] = clamp(x, minX, maxX);
            py[i] = clamp(y, minY, maxY);
        }

        for (int i = 0; i < segments; i++) {
            float p0x = px[i == 0 ? 0 : i - 1];
            float p0y = py[i == 0 ? 0 : i - 1];
            float p3x = px[i + 2 > segments ? segments : i + 2];
            float p3y = py[i + 2 > segments ? segments : i + 2];
            float c1x = px[i] + (px[i + 1] - p0x) / 6f;
            float c1y = py[i] + (py[i + 1] - p0y) / 6f;
            float c2x = px[i + 1] - (p3x - px[i]) / 6f;
            float c2y = py[i + 1] - (p3y - py[i]) / 6f;
            path.cubicTo(c1x, c1y, c2x, c2y, px[i + 1], py[i + 1]);
        }
        return path;
    }

    /** 返回 [-1, 1] 之间的随机数。 */
    private static float signed(Random random) {
        return random.nextFloat() * 2f - 1f;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
