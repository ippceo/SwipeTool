package com.example.walkandlook;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

/**
 * 把滑动参数持久化到 SharedPreferences。
 * - 默认槽（""）：悬浮球在任意界面拉起滑动、以及 App 下次启动时使用，只有点「保存」才会更新。
 * - 命名槽（s1_ / s2_ / s3_）：用户通过「另存」保存的多组预设，通过「读取」载入界面。
 */
public final class SwipeConfigStore {

    private static final String PREF = "swipe_config";
    /** 版本号按槽位存储，避免「另存到别的槽」把默认槽的迁移标记提前置位。 */
    private static final String KEY_VERSION = "slotConfigVersion";
    private static final int CONFIG_VERSION = 4;

    private SwipeConfigStore() {
    }

    // ---------- 默认（最近一次保存）配置 ----------

    public static void save(Context ctx, SwipeConfig c) {
        saveTo(ctx, c, "");
    }

    public static SwipeConfig load(Context ctx) {
        return loadFrom(ctx, "");
    }

    // ---------- 预设槽位（1..3） ----------

    public static void saveSlot(Context ctx, int slot, SwipeConfig c) {
        saveTo(ctx, c, prefix(slot));
    }

    public static SwipeConfig loadSlot(Context ctx, int slot) {
        return loadFrom(ctx, prefix(slot));
    }

    private static String prefix(int slot) {
        return "s" + slot + "_";
    }

    /** 某个槽位是否已经保存过配置（用于「另存 / 读取」里标记「已存储」）。 */
    public static boolean hasSlot(Context ctx, int slot) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .contains(prefix(slot) + KEY_VERSION);
    }

    // ---------- 内部实现 ----------

    private static void saveTo(Context ctx, SwipeConfig c, String p) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putFloat(p + "xMinPercent", c.xMinPercent)
                .putFloat(p + "xMaxPercent", c.xMaxPercent)
                .putFloat(p + "yMinPercent", c.yMinPercent)
                .putFloat(p + "yMaxPercent", c.yMaxPercent)
                .putFloat(p + "angle", c.angle)
                .putFloat(p + "distanceMinPercent", c.distanceMinPercent)
                .putFloat(p + "distanceMaxPercent", c.distanceMaxPercent)
                .putLong(p + "durationMinMs", c.durationMinMs)
                .putLong(p + "durationMaxMs", c.durationMaxMs)
                .putLong(p + "intervalMs", c.intervalMs)
                .putBoolean(p + "intervalRandom", c.intervalRandom)
                .putLong(p + "intervalMinMs", c.intervalMinMs)
                .putLong(p + "intervalMaxMs", c.intervalMaxMs)
                .putInt(p + "loops", c.loops)
                .putBoolean(p + "roundTrip", c.roundTrip)
                .putLong(p + "pauseMs", c.pauseMs)
                .putFloat(p + "curvePercent", c.curvePercent)
                .putLong(p + "maxDurationSec", c.maxDurationSec)
                .putFloat(p + "jitterPercent", c.jitterPercent)
                .putBoolean(p + "useRecorded", c.useRecorded)
                .putString(p + "recordedId", c.recordedId)
                .putString(p + "recordedName", c.recordedName)
                .putInt(p + "startDelaySec", c.startDelaySec)
                .putInt(p + KEY_VERSION, CONFIG_VERSION)
                .apply();
    }

    private static SwipeConfig loadFrom(Context ctx, String p) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        SwipeConfig c = new SwipeConfig();
        int version = sp.getInt(p + KEY_VERSION, 0);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float screenHeightDp = dm.density > 0f ? dm.heightPixels / dm.density : 800f;

        // 起点：1.8.0 之前是单值 xPercent / yPercent，迁移成「下限 = 上限 = 旧值」，
        // 既保留原来的位置，又能在新界面上直接看到「区间」输入。
        if (sp.contains(p + "xMinPercent")) {
            c.xMinPercent = sp.getFloat(p + "xMinPercent", c.xMinPercent);
            c.xMaxPercent = sp.getFloat(p + "xMaxPercent", c.xMaxPercent);
        } else {
            float legacyX = sp.getFloat(p + "xPercent", c.xMinPercent);
            c.xMinPercent = legacyX;
            c.xMaxPercent = legacyX;
        }
        if (sp.contains(p + "yMinPercent")) {
            c.yMinPercent = sp.getFloat(p + "yMinPercent", c.yMinPercent);
            c.yMaxPercent = sp.getFloat(p + "yMaxPercent", c.yMaxPercent);
        } else {
            float legacyY = sp.getFloat(p + "yPercent", c.yMinPercent);
            c.yMinPercent = legacyY;
            c.yMaxPercent = legacyY;
        }

        c.angle = sp.getFloat(p + "angle", c.angle);

        // 距离：1.8.0 之前单位是 dp（distanceMinDp / distanceMaxDp，更早是 distanceDp），
        // 迁移成「屏高百分比」：percent = oldDp / 屏幕高度(dp) * 100，钳到合理范围，
        // 保证升级后滑动长度体感与原来接近。
        if (sp.contains(p + "distanceMinPercent")) {
            c.distanceMinPercent = sp.getFloat(p + "distanceMinPercent", c.distanceMinPercent);
            c.distanceMaxPercent = sp.getFloat(p + "distanceMaxPercent", c.distanceMaxPercent);
        } else {
            float legacyDistance = sp.getFloat(p + "distanceDp", -1f);
            float loDp, hiDp;
            if (legacyDistance > 0f) {
                loDp = Math.max(1f, legacyDistance * 0.8f);
                hiDp = Math.max(loDp, legacyDistance * 1.2f);
            } else {
                loDp = sp.getFloat(p + "distanceMinDp", c.distanceMinPercent);
                hiDp = sp.getFloat(p + "distanceMaxDp", c.distanceMaxPercent);
            }
            c.distanceMinPercent = clampPercent(loDp / screenHeightDp * 100f);
            c.distanceMaxPercent = clampPercent(hiDp / screenHeightDp * 100f);
            if (c.distanceMaxPercent < c.distanceMinPercent) {
                float t = c.distanceMinPercent;
                c.distanceMinPercent = c.distanceMaxPercent;
                c.distanceMaxPercent = t;
            }
        }

        // 时长：旧版本只有一个固定时长 durationMs，迁移为固定区间（下限 = 上限）。
        long legacyDuration = sp.getLong(p + "durationMs", -1L);
        if (legacyDuration > 0L && !sp.contains(p + "durationMinMs")) {
            c.durationMinMs = legacyDuration;
            c.durationMaxMs = legacyDuration;
        } else {
            c.durationMinMs = sp.getLong(p + "durationMinMs", c.durationMinMs);
            c.durationMaxMs = sp.getLong(p + "durationMaxMs", c.durationMaxMs);
        }
        c.intervalMs = sp.getLong(p + "intervalMs", c.intervalMs);
        c.intervalRandom = sp.getBoolean(p + "intervalRandom", c.intervalRandom);
        c.intervalMinMs = sp.getLong(p + "intervalMinMs", c.intervalMinMs);
        c.intervalMaxMs = sp.getLong(p + "intervalMaxMs", c.intervalMaxMs);
        c.loops = sp.getInt(p + "loops", c.loops);
        c.roundTrip = sp.getBoolean(p + "roundTrip", c.roundTrip);
        c.pauseMs = sp.getLong(p + "pauseMs", c.pauseMs);
        // 弯曲：1.8.1 之前单位是 dp（curveDp），迁移成「屏高百分比」。
        if (sp.contains(p + "curvePercent")) {
            c.curvePercent = sp.getFloat(p + "curvePercent", c.curvePercent);
        } else {
            float legacyCurveDp = sp.getFloat(p + "curveDp", 0f);
            c.curvePercent = clampCurve(legacyCurveDp / screenHeightDp * 100f);
        }
        c.maxDurationSec = sp.getLong(p + "maxDurationSec", c.maxDurationSec);
        c.jitterPercent = sp.getFloat(p + "jitterPercent", c.jitterPercent);
        c.useRecorded = sp.getBoolean(p + "useRecorded", c.useRecorded);
        c.recordedId = sp.getString(p + "recordedId", c.recordedId);
        c.recordedName = sp.getString(p + "recordedName", c.recordedName);
        c.startDelaySec = sp.getInt(p + "startDelaySec", c.startDelaySec);

        // 旧版本默认不抖动，升级后给一个明显可见的默认随机强度
        boolean savedByNewFormat = sp.contains(p + "distanceMinPercent");
        if (version < 2 && !savedByNewFormat && c.jitterPercent <= 0f) {
            c.jitterPercent = SwipeConfig.DEFAULT_JITTER;
        }
        return c;
    }

    /** 把屏高百分比钳到 [1, 95]，避免迁移后出现 0 或超出屏幕的离谱值。 */
    private static float clampPercent(float v) {
        if (v < 1f) return 1f;
        if (v > 95f) return 95f;
        return v;
    }

    /** 弯曲百分比钳到 [-100, 100]。 */
    private static float clampCurve(float v) {
        if (v < -100f) return -100f;
        if (v > 100f) return 100f;
        return v;
    }
}
