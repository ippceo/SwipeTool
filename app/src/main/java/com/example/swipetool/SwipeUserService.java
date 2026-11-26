package com.example.swipetool;

/**
 * 运行在 Shizuku 提供的 shell(ADB) 权限进程中的用户服务。
 *
 * 该进程由 Shizuku 以 shell uid（2000）启动，具备 INJECT_EVENTS 能力，
 * 因此可以直接执行系统的 input 命令注入触摸事件，无需无障碍服务。
 * 这里只执行通用的 input swipe，不涉及任何平台识别或对抗逻辑。
 */
public class SwipeUserService extends ISwipeService.Stub {

    @Override
    public void swipe(int x1, int y1, int x2, int y2, int durationMs) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "input", "swipe",
                    String.valueOf(x1), String.valueOf(y1),
                    String.valueOf(x2), String.valueOf(y2),
                    String.valueOf(Math.max(1, durationMs))
            });
            p.waitFor();
        } catch (Throwable ignored) {
            // 单次失败忽略，调度器会继续下一次
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
