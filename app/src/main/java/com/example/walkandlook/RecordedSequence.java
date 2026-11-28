package com.example.walkandlook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段用户手动录制的滑动序列。
 *
 * 坐标使用 0~1 的归一化值（相对录制区域的宽高），回放时再映射到真实屏幕，
 * 因此不受录制时屏幕尺寸 / 分辨率影响。每一笔都保留真实的采样点与时间戳，
 * 能完整重现人手滑动的抖动与弯曲。
 */
public class RecordedSequence {

    /** 单段序列最多的笔画数（系统单次手势最多 10 笔，回放时会自动分批）。 */
    public static final int MAX_STROKES = 20;
    /** 单段序列最长时长（毫秒）。 */
    public static final long MAX_DURATION_MS = 60000L;
    /** 单笔最多的采样点数，避免数据过大。 */
    public static final int MAX_POINTS_PER_STROKE = 3000;

    public String id = "";
    public String name = "";
    public long createdAt = 0L;
    public final List<Stroke> strokes = new ArrayList<>();

    /** 一笔滑动（从手指按下到抬起）。 */
    public static class Stroke {
        /** 相对整段序列开始的启动时间，单位毫秒。 */
        public long startMs = 0L;
        /** 这一笔的持续时间，单位毫秒。 */
        public long durationMs = 0L;
        public final List<Point> points = new ArrayList<>();
    }

    /** 一个采样点。x / y 为 0~1 归一化坐标，t 为相对本笔按下的毫秒数。 */
    public static class Point {
        public float x;
        public float y;
        public long t;

        public Point() {
        }

        public Point(float x, float y, long t) {
            this.x = x;
            this.y = y;
            this.t = t;
        }
    }

    /** 整段序列的总时长（含最后一笔结束）。 */
    public long totalDurationMs() {
        long end = 0L;
        for (Stroke s : strokes) {
            end = Math.max(end, s.startMs + s.durationMs);
        }
        return end;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("createdAt", createdAt);
        JSONArray arr = new JSONArray();
        for (Stroke s : strokes) {
            JSONObject so = new JSONObject();
            so.put("start", s.startMs);
            so.put("dur", s.durationMs);
            JSONArray pts = new JSONArray();
            for (Point p : s.points) {
                JSONArray one = new JSONArray();
                one.put(round4(p.x));
                one.put(round4(p.y));
                one.put(p.t);
                pts.put(one);
            }
            so.put("pts", pts);
            arr.put(so);
        }
        o.put("strokes", arr);
        return o;
    }

    public static RecordedSequence fromJson(JSONObject o) {
        RecordedSequence seq = new RecordedSequence();
        if (o == null) return seq;
        seq.id = o.optString("id", "");
        seq.name = o.optString("name", "");
        seq.createdAt = o.optLong("createdAt", 0L);
        JSONArray arr = o.optJSONArray("strokes");
        if (arr == null) return seq;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject so = arr.optJSONObject(i);
            if (so == null) continue;
            Stroke s = new Stroke();
            s.startMs = Math.max(0L, so.optLong("start", 0L));
            s.durationMs = Math.max(0L, so.optLong("dur", 0L));
            JSONArray pts = so.optJSONArray("pts");
            if (pts != null) {
                for (int j = 0; j < pts.length(); j++) {
                    JSONArray one = pts.optJSONArray(j);
                    if (one == null || one.length() < 2) continue;
                    s.points.add(new Point(
                            (float) one.optDouble(0, 0d),
                            (float) one.optDouble(1, 0d),
                            one.optLong(2, 0L)));
                }
            }
            if (!s.points.isEmpty()) {
                seq.strokes.add(s);
            }
        }
        return seq;
    }

    private static double round4(float v) {
        return Math.round(v * 10000d) / 10000d;
    }
}
