package com.example.swipetool;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 把滑动参数持久化到 SharedPreferences，
 * 供悬浮球在任意界面拉起滑动时复用最近一次配置。
 */
public final class SwipeConfigStore {

    private static final String PREF = "swipe_config";

    private SwipeConfigStore() {
    }

    public static void save(Context ctx, SwipeConfig c) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putFloat("xPercent", c.xPercent)
                .putFloat("yPercent", c.yPercent)
                .putFloat("angle", c.angle)
                .putFloat("distanceDp", c.distanceDp)
                .putLong("durationMs", c.durationMs)
                .putLong("intervalMs", c.intervalMs)
                .putInt("loops", c.loops)
                .putBoolean("roundTrip", c.roundTrip)
                .putLong("pauseMs", c.pauseMs)
                .putFloat("curveDp", c.curveDp)
                .putBoolean("useShizuku", c.useShizuku)
                .apply();
    }

    public static SwipeConfig load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        SwipeConfig c = new SwipeConfig();
        c.xPercent = p.getFloat("xPercent", c.xPercent);
        c.yPercent = p.getFloat("yPercent", c.yPercent);
        c.angle = p.getFloat("angle", c.angle);
        c.distanceDp = p.getFloat("distanceDp", c.distanceDp);
        c.durationMs = p.getLong("durationMs", c.durationMs);
        c.intervalMs = p.getLong("intervalMs", c.intervalMs);
        c.loops = p.getInt("loops", c.loops);
        c.roundTrip = p.getBoolean("roundTrip", c.roundTrip);
        c.pauseMs = p.getLong("pauseMs", c.pauseMs);
        c.curveDp = p.getFloat("curveDp", c.curveDp);
        c.useShizuku = p.getBoolean("useShizuku", c.useShizuku);
        return c;
    }
}
