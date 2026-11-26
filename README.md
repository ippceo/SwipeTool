# SwipeTool · 屏幕滑动工具

一个轻量的 Android 手势工具：通过无障碍服务在屏幕上执行**可调方向、角度、位置、速度**的滑动操作。纯触摸事件模拟，不采集数据、不针对任何具体 App。

![Build APK](https://github.com/ippceo/SwipeTool/actions/workflows/build.yml/badge.svg)

## 功能

- **方向**：上下左右一键切换，也可填任意角度（0–359，支持斜向）
- **位置**：起点按屏幕宽高百分比定位
- **速度**：滑动时长（毫秒）可调，越小越快
- **轨迹**：直线，或带弧度的贝塞尔曲线（弯曲幅度可调）
- **循环**：单次或循环，次数可设（0 = 无限）
- **往返**：到达终点后自动滑回起点，可设停顿
- **节奏**：两次滑动之间可设间隔

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

## 工作原理

核心是 Android 标准的 `AccessibilityService` + `GestureDescription`：

1. `SwipeAccessibilityService` 注册为无障碍服务，获得发送手势的能力（`canPerformGestures`）。
2. 根据参数计算起点与终点坐标，构造 `Path`（直线用 `lineTo`，弧线用 `quadTo`）。
3. 用 `GestureDescription.Builder` 添加 `StrokeDescription`，通过 `dispatchGesture` 交给系统执行。
4. 用 `Handler` 按「手势时长 + 间隔」循环调度，实现连续/往返滑动。

全程只发送标准的触摸事件序列，没有任何平台识别或对抗逻辑。

## 安装

从 [Actions](https://github.com/ippceo/SwipeTool/actions) 页面下载最新一次构建的 `SwipeTool-debug-apk` 产物，解压得到 `app-debug.apk`，传输到手机安装（debug 签名，需允许「未知来源安装」）。

## 使用

1. 打开 App，点「1. 开启无障碍权限」。
2. 在系统设置里找到「屏幕滑动工具」，打开开关。
3. 返回 App，设置参数，点「开始滑动」。
4. 切到任意界面，滑动会在当前屏幕上执行。
5. 停止：回到 App 点「停止」；若已离开，可重新打开 App 或关闭无障碍服务。

## 云端构建（无需本地环境）

本项目通过 GitHub Actions 在云端编译，本地**不需要**安装 Android Studio / JDK / Gradle。

- 工作流：`.github/workflows/build.yml`
- 触发：向 `master` / `main` 分支 push，或在 Actions 页面手动 `Run workflow`
- 产物：`app/build/outputs/apk/debug/app-debug.apk`，上传为 artifact
- 环境：JDK 17 + 预装 Android SDK（`platforms;android-34`、`build-tools;34.0.0`）+ Gradle 8.7（构建时下载，不依赖 `gradle-wrapper.jar`）

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
├── .github/workflows/build.yml          # 云端编译 APK
├── app/
│   ├── build.gradle                     # 模块配置（compileSdk 34 / minSdk 24）
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/swipetool/
│       │   ├── MainActivity.java              # 控制面板
│       │   ├── SwipeAccessibilityService.java # 手势引擎
│       │   └── SwipeConfig.java               # 参数模型
│       └── res/                               # 图标、字符串、主题、无障碍配置
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 已知限制

- 滑动在当前前台屏幕上执行；切到其他 App 后无法从本 App 界面直接停止，需回到 App 或关闭无障碍服务。
- 部分厂商系统（MIUI / ColorOS 等）对无障碍服务有额外限制，若被自动关闭需在系统设置里加入白名单。
- 坐标系为物理屏幕像素，起点与终点会自动裁剪到屏幕范围内。

## 权限与免责声明

- 仅使用 Android 无障碍服务发送通用手势，不读取屏幕内容、不联网、不上传任何数据。
- 本项目仅用于自动化操作辅助（如长页面滚动、手势测试等中性场景），请勿用于绕过平台风控、刷量或任何违反第三方服务条款的用途。

## License

[MIT](LICENSE)
