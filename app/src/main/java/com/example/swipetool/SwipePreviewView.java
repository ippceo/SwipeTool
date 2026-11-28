package com.example.swipetool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * 滑动参数预览：把「起点 / 方向 / 距离 / 弯曲 / 往返」画成一张直观示意图，
 * 让用户在设置参数时能立刻看出滑动会怎么走。仅作可视化，不执行任何操作。
 */
public class SwipePreviewView extends View {

    private float xPercent = 50f;
    private float yPercent = 50f;
    private float angle = 90f;
    private float distanceDp = 400f;
    private float curveDp = 0f;
    private boolean roundTrip = false;

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
    public void update(float x, float y, float ang, float dist, float curve, boolean rt) {
        xPercent = clamp(x, 0f, 100f);
        yPercent = clamp(y, 0f, 100f);
        angle = ang % 360f;
        if (angle < 0) angle += 360f;
        distanceDp = Math.max(0f, dist);
        curveDp = curve;
        roundTrip = rt;
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
        float sx = left + pw * (xPercent / 100f);
        float sy = top + ph * (yPercent / 100f);

        // 把距离（1..4000 dp）映射到预览框对角线的比例，保证长距离也能看出方向
        double rad = Math.toRadians(angle);
        float diag = (float) Math.sqrt(pw * pw + ph * ph);
        float len = Math.min(diag * 0.45f, Math.max(14f * mDensity, distanceDp * mDensity * 0.10f));
        float ex = sx + (float) (len * Math.cos(rad));
        float ey = sy + (float) (len * Math.sin(rad));
        // 终点约束在预览框内
        ex = clamp(ex, left + 4f * mDensity, right - 4f * mDensity);
        ey = clamp(ey, top + 4f * mDensity, bottom - 4f * mDensity);

        // 弯曲幅度映射到预览尺寸
        float curvePreview = curveDp * mDensity * 0.10f;

        // 去程轨迹
        Path outPath = buildPath(sx, sy, ex, ey, curvePreview);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3.5f * mDensity);
        mPaint.setColor(0xFF1976D2);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(outPath, mPaint);
        mPaint.setStrokeCap(Paint.Cap.BUTT);

        // 终点箭头
        drawArrowHead(canvas, sx, sy, ex, ey, 0xFF1976D2);

        // 往返：回程用绿色虚线
        if (roundTrip) {
            Path backPath = buildPath(ex, ey, sx, sy, -curvePreview);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(2.5f * mDensity);
            mPaint.setColor(0xFF43A047);
            mPaint.setPathEffect(new DashPathEffect(new float[]{9f * mDensity, 7f * mDensity}, 0));
            canvas.drawPath(backPath, mPaint);
            mPaint.setPathEffect(null);
        }

        // 起点圆点
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFFE53935);
        canvas.drawCircle(sx, sy, 5.5f * mDensity, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.5f * mDensity);
        mPaint.setColor(Color.WHITE);
        canvas.drawCircle(sx, sy, 5.5f * mDensity, mPaint);

        // 标注文字
        mTextPaint.setColor(0xFFE53935);
        canvas.drawText("起点", sx + 8f * mDensity, sy - 8f * mDensity, mTextPaint);
        if (roundTrip) {
            mTextPaint.setColor(0xFF43A047);
            canvas.drawText("回程", ex - 30f * mDensity, ey + 16f * mDensity, mTextPaint);
        }
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

    private static Path buildPath(float sx, float sy, float ex, float ey, float curve) {
        Path path = new Path();
        path.moveTo(sx, sy);
        if (Math.abs(curve) < 0.5f) {
            path.lineTo(ex, ey);
        } else {
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

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
