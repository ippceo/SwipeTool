// Shizuku UserService 接口：在 shell 权限进程中执行滑动。
// 注意：AIDL 要求所有方法要么全部指定 id，要么全部不指定，
// 因此 swipe 指定 =1，destroy 使用 Shizuku 规定的 16777114。
package com.example.swipetool;

interface ISwipeService {
    // 执行一次直线滑动，durationMs 为手势持续时间
    void swipe(int x1, int y1, int x2, int y2, int durationMs) = 1;

    // Shizuku 规定的销毁 transaction code
    void destroy() = 16777114;
}
