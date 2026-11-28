package com.example.walkandlook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import java.util.Random;

/**
 * 滑动参数预览：把「起点区间 / 方向 / 距离区间 / 弯曲 / 往返 / 随机抖动」
 * 画成一个竖屏手机比例的示意图。抖动开启时会额外画出多条淡淡的随机轨迹，
 * 直观体现每次滑动的起点、长度、方向都会变化。仅作可视化，不执行任何操作。
 *
 * 预览框的宽高比严格等于当前手机屏幕的 屏高 / 屏宽：无论控件被分配到多高，
 * 都会在内部按真实比例居中绘制手机轮廓，绝不被拉伸变形。
 */
public class SwipePreviewView extends View {

    private float xMinPercent = 50f, xMaxPercent = 50f;
    private float yMinPercent = 50f, yMaxPercent = 50f;
    private float angle = 90f;
    private float distMinPercent = 30f, distMaxPercent = 50f;
    private float curvePercent = 0f;
    private boolean roundTrip = false;
    private float jitterPercent = SwipeConfig.DEFAULT_JITTER;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mDensity;
    /** 屏幕「屏高 / 屏宽」比例，取真实分辨率。 */
    private float mScreenRatio = 16f / 9f;

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

    /** 取真实屏幕分辨率，换算「屏高 / 屏宽」比例。 */
    @SuppressWarnings("deprecation")
    private void updateScreenRatio() {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(dm);
        } else {
            dm = getResources().getDisplayMetrics();
        }
        if (dm.widthPixels > 0 && dm.heightPixels > 0) {
            mScreenRatio = (float) dm.heightPixels / dm.widthPixels;
        }
    }

    /**
     * 只决定「给预览留多高的空间」：按宽度算出的等比高度若超过屏幕高度的 55%，
     * 就把高度压到 55%，宽度保持整行；真正的手机轮廓在 onDraw 里按比例居中绘制，
     * 因此永远不会变形。
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateScreenRatio();
        int w = MeasureSpec.getSize(widthMeasureSpec);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int h = (int) (w * mScreenRatio);
        int maxH = (int) (dm.heightPixels * 0.55f);
        if (h > maxH) h = maxH;
        setMeasuredDimension(w, h);
    }

    /** 更新预览参数（距离 / 弯曲均为屏高百分比）并触发重绘。 */
    public void update(float xMin, float xMax, float yMin, float yMax, float ang,
                       float distMin, float distMax, float curve,
                       boolean rt, float jitter) {
        xMinPercent = clamp(xMin, 0f, 100f);
        xMaxPercent = clamp(xMax, 0f, 100f);
        yMinPercent = clamp(yMin, 0f, 100f);
        yMaxPercent = clamp(yMax, 0f, 100f);
        if (xMaxPercent < xMinPercent) { float t = xMinPercent; xMinPercent = xMaxPercent; xMaxPercent = t; }
        if (yMaxPercent < yMinPercent) { float t = yMinPercent; yMinPercent = yMaxPercent; yMaxPercent = t; }
        angle = ang % 360f;
        if (angle < 0) angle += 360f;
        distMinPercent = Math.max(0f, distMin);
        distMaxPercent = Math.max(distMinPercent, distMax);
        curvePercent = clamp(curve, -100f, 100f);
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
        // 在可用区域内按真实比例找出最大的手机轮廓，并居中；绝不拉伸变形。
        float availW = w - pad * 2f;
        float availH = h - pad * 2f;
        if (availW <= 0 || availH <= 0) return;
        float phoneW = Math.min(availW, availH / mScreenRatio);
        float phoneH = phoneW * mScreenRatio;
        float left = (w - phoneW) / 2f;
        float top = (h - phoneH) / 2f;
        float right = left + phoneW;
        float bottom = top + phoneH;

        // 手机外框：深色机身 + 圆角
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFF263238);
        canvas.drawRoundRect(left, top, right, bottom, 16f * mDensity, 16f * mDensity, mPaint);

        // 屏幕区域（内缩一点，留出边框）
        float screenPad = 5f * mDensity;
        float sLeft = left + screenPad, sTop = top + screenPad;
        float sRight = right - screenPad, sBottom = bottom - screenPad;
        float sw = sRight - sLeft;
        float sh = sBottom - sTop;
        if (sw <= 0 || sh <= 0) return;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFFFAFAFA);
        canvas.drawRoundRect(sLeft, sTop, sRight, sBottom, 10f * mDensity, 10f * mDensity, mPaint);

        // 顶部听筒装饰
        mPaint.setColor(0xFFB0BEC5);
        float earW = sw * 0.28f;
        canvas.drawRoundRect(sLeft + sw / 2f - earW / 2f, sTop + 5f * mDensity,
                sLeft + sw / 2f + earW / 2f, sTop + 8f * mDensity,
                3f * mDensity, 3f * mDensity, mPaint);

        // 起点区间：用半透明红色矩形标出「起点会落在这个范围」
        float zLeft = sLeft + sw * (xMinPercent / 100f);
        float zRight = sLeft + sw * (xMaxPercent / 100f);
        float zTop = sTop + sh * (yMinPercent / 100f);
        float zBottom = sTop + sh * (yMaxPercent / 100f);
        if (zRight - zLeft < 3f * mDensity) zRight = zLeft + 3f * mDensity;
        if (zBottom - zTop < 3f * mDensity) zBottom = zTop + 3f * mDensity;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0x22E53935);
        canvas.drawRect(zLeft, zTop, zRight, zBottom, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.2f * mDensity);
        mPaint.setColor(0x88E53935);
        mPaint.setPathEffect(new DashPathEffect(new float[]{5f * mDensity, 4f * mDensity}, 0));
        canvas.drawRect(zLeft, zTop, zRight, zBottom, mPaint);
        mPaint.setPathEffect(null);

        // 基准起点 = 区间中心
        float baseSx = (zLeft + zRight) / 2f;
        float baseSy = (zTop + zBottom) / 2f;
        // 弯曲：屏高百分比 → 预览像素（预览的高度就代表整屏高度）
        float curvePreview = curvePercent / 100f * sh;
        float strength = clamp(jitterPercent / 100f, 0f, 1f);
        float minSide = Math.min(sw, sh);
        float margin = 4f * mDensity;
        float bLeft = sLeft + margin, bTop = sTop + margin;
        float bRight = sRight - margin, bBottom = sBottom - margin;

        Random random = new Random();
        // 先画两条淡淡的随机轨迹，再画主轨迹，形成「每次都不一样」的直观效果
        for (int i = 2; i >= 0; i--) {
            // 起点在区间内随机
            float sx = sLeft + sw * (SwipeConfig.rnd(xMinPercent, xMaxPercent) / 100f);
            float sy = sTop + sh * (SwipeConfig.rnd(yMinPercent, yMaxPercent) / 100f);
            float sampleAngle = angle;
            // 长度在区间内随机
            float len = (SwipeConfig.rnd(distMinPercent, distMaxPercent) / 100f) * sh;
            float curve = curvePreview;
            if (strength > 0f) {
                sx += (random.nextFloat() * 2f - 1f) * strength * minSide * 0.05f;
                sy += (random.nextFloat() * 2f - 1f) * strength * minSide * 0.05f;
                sampleAngle += (random.nextFloat() * 2f - 1f) * strength * 22f;
                len *= 1f + (random.nextFloat() * 2f - 1f) * strength * 0.30f;
                curve += (random.nextFloat() * 2f - 1f) * strength * sh * 0.08f;
            }
            sx = clamp(sx, bLeft, bRight);
            sy = clamp(sy, bTop, bBottom);

            double rad = Math.toRadians(sampleAngle);
            float ex = clamp(sx + (float) (len * Math.cos(rad)), bLeft, bRight);
            float ey = clamp(sy + (float) (len * Math.sin(rad)), bTop, bBottom);
            float wobble = strength * len * 0.06f;

            Path path = SwipeTrajectory.humanPath(sx, sy, ex, ey, curve, wobble, random,
                    bLeft, bTop, bRight, bBottom);
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
                            bLeft, bTop, bRight, bBottom);
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

        // 基准起点圆点（区间中心）
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFFE53935);
        canvas.drawCircle(baseSx, baseSy, 5.5f * mDensity, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.5f * mDensity);
        mPaint.setColor(Color.WHITE);
        canvas.drawCircle(baseSx, baseSy, 5.5f * mDensity, mPaint);

        mTextPaint.setColor(0xFFE53935);
        canvas.drawText("起点区间", baseSx + 8f * mDensity, baseSy - 8f * mDensity, mTextPaint);
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
