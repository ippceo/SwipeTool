// Shizuku UserService 接口：在 shell 权限进程中执行滑动。
package com.example.swipetool;

interface ISwipeService {
    // 执行一次直线滑动，durationMs 为手势持续时间
    void swipe(int x1, int y1, int x2, int y2, int durationMs);

    // Shizuku 规定的销毁 transaction code
    void destroy() = 16777114;
}
