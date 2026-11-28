package com.example.walkandlook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 录制画布：用手指在区域内绘制滑动轨迹（可多笔），
 * 逐点记录归一化坐标与真实时间戳，从而完整保留人手的抖动。
 */
public class RecordCanvasView extends View {

    /** 画布内容变化回调（用于刷新笔数 / 时长提示）。 */
    public interface OnChangeListener {
        void onChange();
    }

    private final List<RecordedSequence.Stroke> mStrokes = new ArrayList<>();
    private RecordedSequence.Stroke mCurrent;
    private int mPointerId = -1;
    private long mRecordStart = 0L;
    private long mDownTime = 0L;
    private boolean mLimitWarned = false;

    private OnChangeListener mListener;

    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCurrentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mDensity;

    public RecordCanvasView(Context context) {
        super(context);
        init();
    }

    public RecordCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mDensity = getResources().getDisplayMetrics().density;

        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1f);
        mGridPaint.setColor(0xFFE3EAF2);

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(3f * mDensity);
        mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        mStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        mStrokePaint.setColor(0xFF1976D2);

        mCurrentPaint.setStyle(Paint.Style.STROKE);
        mCurrentPaint.setStrokeWidth(3.5f * mDensity);
        mCurrentPaint.setStrokeCap(Paint.Cap.ROUND);
        mCurrentPaint.setStrokeJoin(Paint.Join.ROUND);
        mCurrentPaint.setColor(0xFFE53935);

        mDotPaint.setStyle(Paint.Style.FILL);
        mDotPaint.setColor(0xFF43A047);

        mHintPaint.setColor(0xFF9E9E9E);
        mHintPaint.setTextSize(14f * mDensity);
        mHintPaint.setTextAlign(Paint.Align.CENTER);

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(2f * mDensity);
        mBorderPaint.setColor(0xFFB0BEC5);
    }

    public void setOnChangeListener(OnChangeListener l) {
        mListener = l;
    }

    public boolean hasContent() {
        return !mStrokes.isEmpty() || mCurrent != null;
    }

    public List<RecordedSequence.Stroke> getStrokes() {
        return mStrokes;
    }

    /** 总时长（毫秒），用于展示与回放间隔计算。 */
    public long getTotalDurationMs() {
        long end = 0L;
        for (RecordedSequence.Stroke s : mStrokes) {
            end = Math.max(end, s.startMs + s.durationMs);
        }
        return end;
    }

    public void undoLast() {
        if (!mStrokes.isEmpty()) {
            mStrokes.remove(mStrokes.size() - 1);
            notifyChanged();
        }
    }

    public void clearAll() {
        mStrokes.clear();
        mCurrent = null;
        mPointerId = -1;
        mRecordStart = 0L;
        mLimitWarned = false;
        invalidate();
        notifyChanged();
    }

    private void notifyChanged() {
        if (mListener != null) mListener.onChange();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (mStrokes.size() >= RecordedSequence.MAX_STROKES) {
                    if (!mLimitWarned) {
                        mLimitWarned = true;
                        Toast.makeText(getContext(),
                                "最多录制 " + RecordedSequence.MAX_STROKES + " 笔，请先撤销或清空",
                                Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }
                mLimitWarned = false;
                mPointerId = event.getPointerId(0);
                mDownTime = event.getEventTime();
                if (mRecordStart == 0L) {
                    mRecordStart = mDownTime;
                }
                mCurrent = new RecordedSequence.Stroke();
                mCurrent.startMs = Math.max(0L, mDownTime - mRecordStart);
                addPoint(event.getX(0), event.getY(0), 0L);
                invalidate();
                notifyChanged();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mCurrent == null) return false;
                int idx = event.findPointerIndex(mPointerId);
                if (idx < 0) return false;
                for (int h = 0; h < event.getHistorySize(); h++) {
                    long t = event.getHistoricalEventTime(h) - mDownTime;
                    addPoint(event.getHistoricalX(idx, h), event.getHistoricalY(idx, h), t);
                }
                addPoint(event.getX(idx), event.getY(idx), event.getEventTime() - mDownTime);
                // 达到时长或点数上限时自动结束本笔
                if (event.getEventTime() - mDownTime > RecordedSequence.MAX_DURATION_MS
                        || mCurrent.points.size() >= RecordedSequence.MAX_POINTS_PER_STROKE) {
                    finishStroke(event.getEventTime());
                }
                invalidate();
                notifyChanged();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mCurrent == null) return false;
                int idx = event.findPointerIndex(mPointerId);
                if (idx >= 0) {
                    addPoint(event.getX(idx), event.getY(idx), event.getEventTime() - mDownTime);
                }
                finishStroke(event.getEventTime());
                mPointerId = -1;
                invalidate();
                notifyChanged();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // 主手指提前抬起时结束本笔，避免后续误合并
                int upIdx = event.getActionIndex();
                if (mCurrent != null && event.getPointerId(upIdx) == mPointerId) {
                    addPoint(event.getX(upIdx), event.getY(upIdx),
                            event.getEventTime() - mDownTime);
                    finishStroke(event.getEventTime());
                    mPointerId = -1;
                    invalidate();
                    notifyChanged();
                }
                return true;
            }
            default:
                return false;
        }
    }

    private void addPoint(float px, float py, long t) {
        if (mCurrent == null) return;
        float x = clamp01(px / getWidth());
        float y = clamp01(py / getHeight());
        if (!mCurrent.points.isEmpty()) {
            RecordedSequence.Point last = mCurrent.points.get(mCurrent.points.size() - 1);
            float dx = x - last.x;
            float dy = y - last.y;
            // 过滤原地不动的重复采样，保留细微抖动
            if (dx * dx + dy * dy < 0.0000009f && t - last.t < 8L) {
                return;
            }
        }
        mCurrent.points.add(new RecordedSequence.Point(x, y, Math.max(0L, t)));
    }

    private void finishStroke(long eventTime) {
        if (mCurrent == null) return;
        long dur = Math.max(1L, eventTime - mDownTime);
        mCurrent.durationMs = Math.min(dur, RecordedSequence.MAX_DURATION_MS);
        if (mCurrent.points.size() >= 2) {
            mStrokes.add(mCurrent);
        }
        mCurrent = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawColor(0xFFF7F9FC);

        // 网格：横向 10 格、纵向 20 格，方便对照屏幕位置
        for (int i = 1; i < 10; i++) {
            float x = w * i / 10f;
            canvas.drawLine(x, 0f, x, h, mGridPaint);
        }
        for (int i = 1; i < 20; i++) {
            float y = h * i / 20f;
            canvas.drawLine(0f, y, w, y, mGridPaint);
        }
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, mBorderPaint);

        if (mStrokes.isEmpty() && mCurrent == null) {
            canvas.drawText("在这里用手指画出滑动轨迹（可多笔）", w / 2f, h / 2f, mHintPaint);
        }

        for (RecordedSequence.Stroke s : mStrokes) {
            drawStroke(canvas, s, mStrokePaint);
        }
        if (mCurrent != null) {
            drawStroke(canvas, mCurrent, mCurrentPaint);
        }
    }

    private void drawStroke(Canvas canvas, RecordedSequence.Stroke s, Paint paint) {
        if (s.points.isEmpty()) return;
        Path path = buildPath(s, getWidth(), getHeight());
        if (path == null) return;
        canvas.drawPath(path, paint);
        RecordedSequence.Point first = s.points.get(0);
        canvas.drawCircle(first.x * getWidth(), first.y * getHeight(), 4f * mDensity, mDotPaint);
    }

    /** 把归一化轨迹转换成像素路径。 */
    static Path buildPath(RecordedSequence.Stroke s, int width, int height) {
        if (s.points.size() < 2) return null;
        Path path = new Path();
        boolean started = false;
        for (RecordedSequence.Point p : s.points) {
            float x = p.x * width;
            float y = p.y * height;
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        return path;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
