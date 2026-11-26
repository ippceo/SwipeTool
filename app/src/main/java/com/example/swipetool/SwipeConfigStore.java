package com.example.swipetool;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 把滑动参数持久化到 SharedPreferences。
 * - 默认槽（""）：供悬浮球在任意界面拉起滑动时复用最近一次配置。
 * - 命名槽（s1_ / s2_ / s3_）：用户保存的多组预设。
 */
public final class SwipeConfigStore {

    private static final String PREF = "swipe_config";

    private SwipeConfigStore() {
    }

    // ---------- 默认（最近一次）配置 ----------

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
                .putFloat(p + "distanceDp", c.distanceDp)
                .putLong(p + "durationMs", c.durationMs)
                .putLong(p + "intervalMs", c.intervalMs)
                .putInt(p + "loops", c.loops)
                .putBoolean(p + "roundTrip", c.roundTrip)
                .putLong(p + "pauseMs", c.pauseMs)
                .putFloat(p + "curveDp", c.curveDp)
                .putBoolean(p + "useShizuku", c.useShizuku)
                .putLong(p + "maxDurationSec", c.maxDurationSec)
                .apply();
    }

    private static SwipeConfig loadFrom(Context ctx, String p) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        SwipeConfig c = new SwipeConfig();
        c.xPercent = sp.getFloat(p + "xPercent", c.xPercent);
        c.yPercent = sp.getFloat(p + "yPercent", c.yPercent);
        c.angle = sp.getFloat(p + "angle", c.angle);
        c.distanceDp = sp.getFloat(p + "distanceDp", c.distanceDp);
        c.durationMs = sp.getLong(p + "durationMs", c.durationMs);
        c.intervalMs = sp.getLong(p + "intervalMs", c.intervalMs);
        c.loops = sp.getInt(p + "loops", c.loops);
        c.roundTrip = sp.getBoolean(p + "roundTrip", c.roundTrip);
        c.pauseMs = sp.getLong(p + "pauseMs", c.pauseMs);
        c.curveDp = sp.getFloat(p + "curveDp", c.curveDp);
        c.useShizuku = sp.getBoolean(p + "useShizuku", c.useShizuku);
        c.maxDurationSec = sp.getLong(p + "maxDurationSec", c.maxDurationSec);
        return c;
    }
}
