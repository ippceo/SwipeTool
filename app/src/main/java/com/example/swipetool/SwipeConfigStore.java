package com.example.swipetool;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 把滑动参数持久化到 SharedPreferences。
 * - 默认槽（""）：悬浮球在任意界面拉起滑动、以及 App 下次启动时使用，只有点「保存」才会更新。
 * - 命名槽（s1_ / s2_ / s3_）：用户通过「另存」保存的多组预设，通过「读取」载入界面。
 */
public final class SwipeConfigStore {

    private static final String PREF = "swipe_config";
    /** 版本号按槽位存储，避免「另存到别的槽」把默认槽的迁移标记提前置位。
     *  使用新键名，避开 1.6.0 曾写过的全局 configVersion。 */
    private static final String KEY_VERSION = "slotConfigVersion";
    private static final int CONFIG_VERSION = 2;

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

    // ---------- 内部实现 ----------

    private static void saveTo(Context ctx, SwipeConfig c, String p) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putFloat(p + "xPercent", c.xPercent)
                .putFloat(p + "yPercent", c.yPercent)
                .putFloat(p + "angle", c.angle)
                .putFloat(p + "distanceMinDp", c.distanceMinDp)
                .putFloat(p + "distanceMaxDp", c.distanceMaxDp)
                .putLong(p + "durationMs", c.durationMs)
                .putLong(p + "intervalMs", c.intervalMs)
                .putBoolean(p + "intervalRandom", c.intervalRandom)
                .putLong(p + "intervalMinMs", c.intervalMinMs)
                .putLong(p + "intervalMaxMs", c.intervalMaxMs)
                .putInt(p + "loops", c.loops)
                .putBoolean(p + "roundTrip", c.roundTrip)
                .putLong(p + "pauseMs", c.pauseMs)
                .putFloat(p + "curveDp", c.curveDp)
                .putBoolean(p + "useShizuku", c.useShizuku)
                .putLong(p + "maxDurationSec", c.maxDurationSec)
                .putFloat(p + "jitterPercent", c.jitterPercent)
                .putBoolean(p + "useRecorded", c.useRecorded)
                .putString(p + "recordedId", c.recordedId)
                .putString(p + "recordedName", c.recordedName)
                .putInt(p + KEY_VERSION, CONFIG_VERSION)
                .apply();
    }

    private static SwipeConfig loadFrom(Context ctx, String p) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        SwipeConfig c = new SwipeConfig();
        int version = sp.getInt(p + KEY_VERSION, 0);
        // 1.6.0 已经保存过的槽位会带有 distanceMinDp，说明用户明确保存过，
        // 不再强制改写其抖动值（尊重用户主动设为 0 的选择）。
        boolean savedByNewFormat = sp.contains(p + "distanceMinDp");

        c.xPercent = sp.getFloat(p + "xPercent", c.xPercent);
        c.yPercent = sp.getFloat(p + "yPercent", c.yPercent);
        c.angle = sp.getFloat(p + "angle", c.angle);
        // 旧版本只有一个固定距离：迁移成该值 ±20% 的随机区间，
        // 既保留用户原来的长度量级，又能直接看到长度随机效果。
        float legacyDistance = sp.getFloat(p + "distanceDp", -1f);
        if (legacyDistance > 0f) {
            float lo = Math.max(1f, legacyDistance * 0.8f);
            float hi = Math.max(lo, legacyDistance * 1.2f);
            c.distanceMinDp = sp.getFloat(p + "distanceMinDp", lo);
            c.distanceMaxDp = sp.getFloat(p + "distanceMaxDp", hi);
        } else {
            c.distanceMinDp = sp.getFloat(p + "distanceMinDp", c.distanceMinDp);
            c.distanceMaxDp = sp.getFloat(p + "distanceMaxDp", c.distanceMaxDp);
        }
        c.durationMs = sp.getLong(p + "durationMs", c.durationMs);
        c.intervalMs = sp.getLong(p + "intervalMs", c.intervalMs);
        c.intervalRandom = sp.getBoolean(p + "intervalRandom", c.intervalRandom);
        c.intervalMinMs = sp.getLong(p + "intervalMinMs", c.intervalMinMs);
        c.intervalMaxMs = sp.getLong(p + "intervalMaxMs", c.intervalMaxMs);
        c.loops = sp.getInt(p + "loops", c.loops);
        c.roundTrip = sp.getBoolean(p + "roundTrip", c.roundTrip);
        c.pauseMs = sp.getLong(p + "pauseMs", c.pauseMs);
        c.curveDp = sp.getFloat(p + "curveDp", c.curveDp);
        c.useShizuku = sp.getBoolean(p + "useShizuku", c.useShizuku);
        c.maxDurationSec = sp.getLong(p + "maxDurationSec", c.maxDurationSec);
        c.jitterPercent = sp.getFloat(p + "jitterPercent", c.jitterPercent);
        c.useRecorded = sp.getBoolean(p + "useRecorded", c.useRecorded);
        c.recordedId = sp.getString(p + "recordedId", c.recordedId);
        c.recordedName = sp.getString(p + "recordedName", c.recordedName);

        // 旧版本默认不抖动，升级后给一个明显可见的默认随机强度
        if (version < CONFIG_VERSION && !savedByNewFormat && c.jitterPercent <= 0f) {
            c.jitterPercent = SwipeConfig.DEFAULT_JITTER;
        }
        return c;
    }
}
