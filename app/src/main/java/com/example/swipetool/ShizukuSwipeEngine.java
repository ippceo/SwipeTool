package com.example.swipetool;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Shizuku 执行引擎：作为无障碍服务的替代方案。
 *
 * 通过 Shizuku 绑定一个运行在 shell(ADB) 权限进程中的 {@link SwipeUserService}，
 * 由该服务执行系统的 input swipe 命令注入滑动。同样只做通用输入模拟。
 *
 * 使用前提：设备已安装并启动 Shizuku（或 Sui），且已向本应用授权。
 */
public final class ShizukuSwipeEngine {

    private static ISwipeService sService;
    private static boolean sBound = false;
    private static boolean sRunning = false;
    private static int sDone = 0;
    private static SwipeConfig sConfig;

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    private static final ServiceConnection sConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sService = ISwipeService.Stub.asInterface(binder);
            sBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sService = null;
            sBound = false;
        }
    };

    private ShizukuSwipeEngine() {
    }

    /** Shizuku 是否已安装并运行（binder 可用）。 */
    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 本应用是否已获得 Shizuku 授权。 */
    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 请求 Shizuku 授权（结果通过 Shizuku 的全局监听回调）。 */
    public static void requestPermission(int requestCode) {
        try {
            Shizuku.requestPermission(requestCode);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isRunning() {
        return sRunning;
    }

    public static int getDoneCount() {
        return sDone;
    }

    /** 绑定 UserService；重复调用安全。 */
    public static void bind(Context context) {
        if (sBound && sService != null) return;
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(context.getPackageName(), SwipeUserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("swipe")
                    .debuggable(false)
                    .version(1);
            Shizuku.bindUserService(args, sConnection);
        } catch (Throwable ignored) {
            // Shizuku 未就绪或绑定失败，调用方会通过 isAvailable/hasPermission 给出提示
        }
    }

    public static void start(SwipeConfig config) {
        stop();
        sConfig = config;
        sDone = 0;
        sRunning = true;
        sHandler.post(sTask);
    }

    public static void stop() {
        sRunning = false;
        sHandler.removeCallbacks(sTask);
    }

    private static final Runnable sTask = new Runnable() {
        @Override
        public void run() {
            if (!sRunning || sConfig == null) return;
            if (sService == null) {
                // UserService 尚未绑定完成，稍后重试
                sHandler.postDelayed(this, 500L);
                return;
            }
            final SwipeConfig c = sConfig;
            final int[] p = computeCoords(c);
            sExecutor.execute(() -> {
                try {
                    sService.swipe(p[0], p[1], p[2], p[3], (int) c.durationMs);
                } catch (Throwable ignored) {
                }
                sHandler.post(() -> {
                    if (!sRunning) return;
                    sDone++;
                    if (c.loops > 0 && sDone >= c.loops) {
                        stop();
                        return;
                    }
                    long span = c.roundTrip ? (c.durationMs * 2L + c.pauseMs) : c.durationMs;
                    sHandler.postDelayed(sTask, span + c.intervalMs + 50L);
                });
            });
        }
    };

    private static int[] computeCoords(SwipeConfig c) {
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        float maxX = dm.widthPixels - 1f;
        float maxY = dm.heightPixels - 1f;
        float sx = clamp(dm.widthPixels * (c.xPercent / 100f), 1f, maxX);
        float sy = clamp(dm.heightPixels * (c.yPercent / 100f), 1f, maxY);
        double rad = Math.toRadians(c.angle);
        float ex = clamp(sx + (float) (c.distanceDp * dm.density * Math.cos(rad)), 1f, maxX);
        float ey = clamp(sy + (float) (c.distanceDp * dm.density * Math.sin(rad)), 1f, maxY);
        return new int[]{(int) sx, (int) sy, (int) ex, (int) ey};
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
