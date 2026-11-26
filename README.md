# SwipeTool · 屏幕滑动工具

一个轻量的 Android 手势工具：通过无障碍服务或 Shizuku 在屏幕上执行**可调方向、角度、位置、速度**的滑动操作。纯触摸事件模拟，不采集数据、不针对任何具体 App。

![Build APK](https://github.com/ippceo/SwipeTool/actions/workflows/build.yml/badge.svg)

## 功能

- **方向**：上下左右一键切换，也可填任意角度（0–359，支持斜向）
- **位置**：起点按屏幕宽高百分比定位
- **速度**：滑动时长（毫秒）可调，越小越快
- **轨迹**：直线，或带弧度的贝塞尔曲线（弯曲幅度可调）
- **循环**：单次或循环，次数可设（0 = 无限）
- **往返**：到达终点后自动滑回起点，可设停顿
- **节奏**：两次滑动之间可设间隔
- **执行方式**：无障碍服务（支持往返 / 曲线轨迹）或 Shizuku（shell 权限直线滑动，无需无障碍）
- **悬浮球**：浮在所有应用之上，点一下开始 / 停止，无需切回本 App
- **预设**：3 个槽位保存 / 加载常用参数组合
- **时长上限**：运行指定秒数后自动停止，避免忘记关
- **随机抖动**：0–100% 强度，对起点 / 角度 / 距离 / 间隔做随机扰动，让连续操作不总是同一轨迹（两种执行方式均支持）

## 参数说明

| 参数 | 含义 | 范围 / 默认 |
|---|---|---|
| 起点 X / Y | 滑动起点，按屏幕百分比 | 0–100，默认 50 / 50 |
| 角度 | 0=右，90=下，180=左，270=上 | 0–359，默认 90 |
| 距离 | 滑动长度（dp） | 1–4000，默认 400 |
| 滑动时长 | 单次手势持续（ms），越小越快 | 50–60000，默认 500 |
| 间隔 | 两次滑动之间等待（ms） | 0–600000，默认 500 |
| 循环次数 | 0 表示无限 | 0–1000000，默认 0 |
| 往返停顿 | 回程前停顿（ms） | 0–60000，默认 200 |
| 弯曲幅度 | 轨迹弧度（dp），0 为直线 | -2000–2000，默认 0 |
| 运行时长上限 | 到达后自动停止（秒） | 0 = 不限，最大 86400 |
| 随机抖动 | 起点 / 角度 / 距离 / 间隔的随机扰动量级 | 0–100，默认 0（关闭） |

## 随机抖动

强度 `jitter = jitterPercent / 100`，每次滑动都会重新随机：

| 维度 | 扰动范围 |
|---|---|
| 起点 X / Y | ±`j × 5%` 屏幕宽 / 高 |
| 角度 | ±`j × 20°` |
| 距离 | ±`j × 100%` |
| 间隔 | ±`j × 100%` |

例如抖动设为 `20`，则角度在 ±4° 内浮动、距离在 ±20% 内浮动、起点在屏幕 ±1% 内浮动。
设为 `0` 即完全按固定参数执行。抖动只是让连续操作不完全重复，不改变滑动的性质。

## 工作原理

核心是 Android 标准的 `AccessibilityService` + `GestureDescription`：

1. `SwipeAccessibilityService` 注册为无障碍服务，获得发送手势的能力（`canPerformGestures`）。
2. 根据参数计算起点与终点坐标，构造 `Path`（直线用 `lineTo`，弧线用 `quadTo`）。
3. 用 `GestureDescription.Builder` 添加 `StrokeDescription`，通过 `dispatchGesture` 交给系统执行。
4. 用 `Handler` 按「手势时长 + 间隔」循环调度，实现连续/往返滑动。

全程只发送标准的触摸事件序列，没有任何平台识别或对抗逻辑。

### Shizuku 方式（可选）

选择 Shizuku 执行时走另一条通道：

1. `ShizukuSwipeEngine` 通过 `Shizuku.bindUserService` 绑定一个运行在 shell(ADB) 权限进程中的 `SwipeUserService`。
2. `SwipeUserService` 执行系统的 `input swipe x1 y1 x2 y2 duration` 命令注入滑动。
3. 前提：设备已安装并启动 [Shizuku](https://shizuku.rikka.app/download/)（或 Sui），并已向本应用授权。

Shizuku 方式为直线滑动；往返与曲线轨迹仅无障碍方式支持，随机抖动两种方式都支持。

## 安装

推荐从 [Releases](https://github.com/ippceo/SwipeTool/releases) 下载最新的 `app-release.apk` 直接安装；也可从 [Actions](https://github.com/ippceo/SwipeTool/actions) 下载构建产物。均为 debug 签名，需允许「未知来源安装」。

## 使用

### 方式一：在 App 内操作

1. 打开 App，选择「执行方式」：
   - **无障碍服务**：点「1a. 开启无障碍权限」，在系统设置里找到「屏幕滑动工具」并打开开关。
   - **Shizuku**：先安装并启动 Shizuku（或 Sui），点「1b. 检查 / 授权 Shizuku」，在弹窗中允许授权。
2. 返回 App，设置参数，点「开始滑动」。
3. 切到任意界面，滑动会在当前屏幕上执行。
4. 停止：回到 App 点「停止」。

### 方式二：悬浮球（跨应用快速启停）

1. 设置好参数，点「1c. 开启悬浮球」，按提示允许「显示在其他应用上层」（Android 13+ 还会请求通知权限）。
2. 屏幕边缘出现圆形悬浮球：灰色 ▶ 表示已停止，绿色 ■ 表示运行中。
3. 切到任意应用，点悬浮球即可开始 / 停止；按住拖动可调整位置。
4. 关闭悬浮球：下拉通知栏，点通知里的「关闭悬浮球」。

## 云端构建（无需本地环境）

本项目通过 GitHub Actions 在云端编译，本地**不需要**安装 Android Studio / JDK / Gradle。

- 工作流：`.github/workflows/build.yml`
- 触发：向 `master` / `main` 分支 push，推送 `v*` tag，或在 Actions 页面手动 `Run workflow`
- 产物：`app/build/outputs/apk/debug/app-debug.apk` 与 `app/build/outputs/apk/release/app-release.apk`，分别上传为 artifact
- 发布：推送 `v*` tag 时自动创建 GitHub Release 并附上 release APK（release 使用 debug keystore 签名，个人安装即可用）
- 环境：JDK 17 + 预装 Android SDK（`platforms;android-34`、`build-tools;34.0.0`）+ Gradle 8.7（构建时下载，不依赖 `gradle-wrapper.jar`）；依赖走阿里云 Maven 镜像，避免 Maven Central 限流

## 本地构建（可选）

如需本地编译，需自备 Android Studio 或 JDK 17 + Android SDK 34：

```bash
git clone https://github.com/ippceo/SwipeTool.git
cd SwipeTool
# 用 Android Studio 打开，或：
gradle assembleDebug
```

## 项目结构

```
SwipeTool/
├── .github/workflows/build.yml          # 云端编译 debug + release APK，打 tag 时发布 Release
├── app/
│   ├── build.gradle                     # 模块配置（compileSdk 34 / minSdk 24，含 Shizuku 依赖与 AIDL）
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── aidl/com/example/swipetool/
│       │   └── ISwipeService.aidl             # Shizuku UserService 接口
│       ├── java/com/example/swipetool/
│       │   ├── MainActivity.java              # 控制面板
│       │   ├── SwipeAccessibilityService.java # 无障碍手势引擎
│       │   ├── SwipeConfig.java               # 参数模型
│       │   ├── SwipeConfigStore.java          # 参数持久化（供悬浮球复用）
│       │   ├── ShizukuSwipeEngine.java        # Shizuku 执行引擎（绑定并调度 UserService）
│       │   ├── SwipeUserService.java          # 运行在 shell 权限进程，执行 input swipe
│       │   └── FloatingService.java           # 悬浮球服务（前台服务 + 悬浮按钮 + 通知）
│       └── res/                               # 图标、字符串、主题、无障碍配置
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 已知限制

- 滑动在当前前台屏幕上执行；切到其他 App 后无法从本 App 界面直接停止，需回到 App 或关闭服务。
- 部分厂商系统（MIUI / ColorOS 等）对无障碍服务有额外限制，若被自动关闭需在系统设置里加入白名单。
- 坐标系为物理屏幕像素，起点与终点会自动裁剪到屏幕范围内。
- Shizuku 方式需自行安装并激活 Shizuku（或 Sui）；部分设备重启后需重新启动 Shizuku 服务。
- Shizuku 方式为直线滑动，不支持往返与曲线轨迹（受 `input swipe` 能力限制）。
- 悬浮球需授予「显示在其他应用上层」权限；部分系统（MIUI / ColorOS 等）还需在「后台弹出界面」等设置中放行，否则悬浮球可能不显示。

## 权限与免责声明

- 无障碍方式仅通过无障碍服务发送通用手势；Shizuku 方式通过 UserService 以 shell(ADB) 权限执行 `input swipe`。两者都不读取屏幕内容、不联网、不上传任何数据。
- 悬浮球需要「显示在其他应用上层」（`SYSTEM_ALERT_WINDOW`）与前台服务 / 通知权限（Android 13+），仅用于显示控制按钮并承载前台服务，不读取屏幕内容。
- 本项目仅用于自动化操作辅助（如长页面滚动、手势测试等中性场景），请勿用于绕过平台风控、刷量或任何违反第三方服务条款的用途。

## License

[MIT](LICENSE)
