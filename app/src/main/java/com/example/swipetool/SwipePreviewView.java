package com.example.swipetool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 滑动参数预览：把「起点 / 方向 / 距离 / 弯曲 / 往返 / 随机抖动」画成示意图。
 * 抖动开启时会额外画出两条淡淡的随机轨迹，直观体现每次滑动都会歪歪扭扭、不重复。
 * 仅作可视化，不执行任何操作。
 */
public class SwipePreviewView extends View {

    private float xPercent = 50f;
    private float yPercent = 50f;
    private float angle = 90f;
    private float distanceDp = 400f;
    private float curveDp = 0f;
    private boolean roundTrip = false;
    private float jitterPercent = SwipeConfig.DEFAULT_JITTER;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mDensity;

    public SwipePreviewView(Context context) {
        super(context);
        init();
    }

    public SwipePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mDensity = getResources().getDisplayMetrics().density;
        mTextPaint.setColor(0xFF555555);
        mTextPaint.setTextSize(11f * mDensity);
        mArrowPaint.setStyle(Paint.Style.FILL);
    }

    /** 更新预览参数并触发重绘。 */
    public void update(float x, float y, float ang, float dist, float curve,
                       boolean rt, float jitter) {
        xPercent = clamp(x, 0f, 100f);
        yPercent = clamp(y, 0f, 100f);
        angle = ang % 360f;
        if (angle < 0) angle += 360f;
        distanceDp = Math.max(0f, dist);
        curveDp = curve;
        roundTrip = rt;
        jitterPercent = clamp(jitter, 0f, 100f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = 8f * mDensity;
        float left = pad, top = pad;
        float right = w - pad, bottom = h - pad;
        float pw = right - left;
        float ph = bottom - top;

        // 背景卡片
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFFFAFAFA);
        canvas.drawRoundRect(left, top, right, bottom, 10f * mDensity, 10f * mDensity, mPaint);

        // 屏幕边框
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2f * mDensity);
        mPaint.setColor(0xFFBDBDBD);
        canvas.drawRoundRect(left, top, right, bottom, 10f * mDensity, 10f * mDensity, mPaint);

        // 起点坐标（按百分比映射到预览框内）
        float baseSx = left + pw * (xPercent / 100f);
        float baseSy = top + ph * (yPercent / 100f);

        // 把距离（1..4000 dp）映射到预览框对角线的比例，保证长距离也能看出方向
        float diag = (float) Math.sqrt(pw * pw + ph * ph);
        float baseLen = Math.min(diag * 0.45f,
                Math.max(14f * mDensity, distanceDp * mDensity * 0.10f));
        float curvePreview = curveDp * mDensity * 0.10f;
        float strength = clamp(jitterPercent / 100f, 0f, 1f);
        float minSide = Math.min(pw, ph);
        float margin = 4f * mDensity;
        float boundsLeft = left + margin, boundsTop = top + margin;
        float boundsRight = right - margin, boundsBottom = bottom - margin;

        Random random = new Random();
        // 先画两条淡淡的随机轨迹，再画主轨迹，形成「每次都不一样」的直观效果
        for (int i = 2; i >= 0; i--) {
            float sx = baseSx, sy = baseSy;
            float sampleAngle = angle;
            float len = baseLen;
            float curve = curvePreview;
            if (strength > 0f) {
                sx += (random.nextFloat() * 2f - 1f) * strength * minSide * 0.05f;
                sy += (random.nextFloat() * 2f - 1f) * strength * minSide * 0.05f;
                sampleAngle += (random.nextFloat() * 2f - 1f) * strength * 22f;
                len *= 1f + (random.nextFloat() * 2f - 1f) * strength * 0.30f;
                curve += (random.nextFloat() * 2f - 1f) * strength * minSide * 0.06f;
            }
            sx = clamp(sx, boundsLeft, boundsRight);
            sy = clamp(sy, boundsTop, boundsBottom);

            double rad = Math.toRadians(sampleAngle);
            float ex = clamp(sx + (float) (len * Math.cos(rad)), boundsLeft, boundsRight);
            float ey = clamp(sy + (float) (len * Math.sin(rad)), boundsTop, boundsBottom);
            float wobble = strength * len * 0.06f;

            Path path = SwipeTrajectory.humanPath(sx, sy, ex, ey, curve, wobble, random,
                    boundsLeft, boundsTop, boundsRight, boundsBottom);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeWidth((i == 0 ? 3.5f : 2f) * mDensity);
            mPaint.setColor(i == 0 ? 0xFF1976D2 : (i == 1 ? 0x551976D2 : 0x2E1976D2));
            canvas.drawPath(path, mPaint);
            mPaint.setStrokeCap(Paint.Cap.BUTT);

            if (i == 0) {
                drawArrowHead(canvas, sx, sy, ex, ey, 0xFF1976D2);

                if (roundTrip) {
                    Path backPath = SwipeTrajectory.humanPath(ex, ey, sx, sy,
                            -curve, wobble, random,
                            boundsLeft, boundsTop, boundsRight, boundsBottom);
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(2.5f * mDensity);
                    mPaint.setColor(0xFF43A047);
                    mPaint.setPathEffect(new DashPathEffect(
                            new float[]{9f * mDensity, 7f * mDensity}, 0));
                    canvas.drawPath(backPath, mPaint);
                    mPaint.setPathEffect(null);
                }
            }
        }

        // 起点圆点（固定位置，代表参数里的起点）
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFFE53935);
        canvas.drawCircle(baseSx, baseSy, 5.5f * mDensity, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.5f * mDensity);
        mPaint.setColor(Color.WHITE);
        canvas.drawCircle(baseSx, baseSy, 5.5f * mDensity, mPaint);

        // 标注文字
        mTextPaint.setColor(0xFFE53935);
        canvas.drawText("起点", baseSx + 8f * mDensity, baseSy - 8f * mDensity, mTextPaint);
    }

    private void drawArrowHead(Canvas canvas, float sx, float sy, float ex, float ey, int color) {
        double ang = Math.atan2(ey - sy, ex - sx);
        float size = 11f * mDensity;
        float x1 = ex - (float) (size * Math.cos(ang - Math.PI / 6));
        float y1 = ey - (float) (size * Math.sin(ang - Math.PI / 6));
        float x2 = ex - (float) (size * Math.cos(ang + Math.PI / 6));
        float y2 = ey - (float) (size * Math.sin(ang + Math.PI / 6));
        Path tri = new Path();
        tri.moveTo(ex, ey);
        tri.lineTo(x1, y1);
        tri.lineTo(x2, y2);
        tri.close();
        mArrowPaint.setColor(color);
        canvas.drawPath(tri, mArrowPaint);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
