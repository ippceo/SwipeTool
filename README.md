# SwipeTool · 屏幕滑动工具

一个轻量的 Android 手势工具：通过**无障碍服务**或 **Shizuku** 在屏幕上执行**方向、角度、位置、速度、轨迹、节奏**均可调的滑动操作。纯触摸事件模拟，不读取屏幕内容、不联网、不上传任何数据，也不针对任何具体 App。

![Build APK](https://github.com/ippceo/SwipeTool/actions/workflows/build.yml/badge.svg)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84)
![Version](https://img.shields.io/badge/version-1.7.0-blue)

> 适用场景：长页面自动滚动、阅读翻页、演示 / 手势测试等中性自动化辅助。
> 请勿用于绕过平台风控、刷量或任何违反第三方服务条款的用途（见文末免责声明）。

---

## 快速开始

1. **安装**：从 [Releases](https://github.com/ippceo/SwipeTool/releases) 下载最新的 `SwipeTool-<版本号>.apk` 安装（允许「未知来源安装」）。
2. **授权执行方式**（二选一）：
   - **无障碍服务**（推荐，功能最全）：打开 App → 点「1a. 开启无障碍权限」→ 在系统设置中打开「屏幕滑动工具」开关。
   - **Shizuku**：先安装并启动 [Shizuku](https://shizuku.rikka.app/download/)，回到 App 点「1b. 检查 / 授权 Shizuku」并允许授权。
3. **开始**：设置参数（可看下方的实时预览图）→ 点「开始滑动」→ 切到任意界面，滑动会在当前屏幕执行。停止时回到 App 点「停止」，或用悬浮球 / 通知栏。

---

## 功能特性

| 类别 | 说明 |
|---|---|
| 方向 / 角度 | 上下左右一键切换，也可填任意角度（0–359，支持斜向） |
| 位置 | 起点按屏幕宽高百分比定位 |
| 速度 | 滑动时长（毫秒）可调，越小越快 |
| 轨迹 | 拟人化随机轨迹：起点、终点、长度、方向与弯曲都可随机，轨迹带横向摆动（仅无障碍方式） |
| 循环 / 往返 | 可设循环次数（0 = 无限）；往返可设回程停顿 |
| 节奏 | 两次滑动之间可设固定间隔；或开启「间隔随机」在最小~最大之间随机 |
| 随机抖动 | 0–100% 强度，越大越像真人：起点偏移、方向抖动、长度区间、弯曲摆动；连续操作不重复 |
| 录制序列 | 面板上手动画出多笔滑动并命名保存；可打开列表选择 / 删除 / 试放，回放保留真实抖动与弯曲（仅无障碍方式） |
| 实时预览 | 主界面示意图，抖动开启时会画出多条随机轨迹，直观显示每次会怎么歪 |
| 悬浮球 | 浮在所有应用之上，点一下开始 / 停止，可拖动；始终使用「已保存」的参数 |
| 通知栏 | 可直接「开始 / 停止 / 退出」，点「退出」会直接关闭整个应用 |
| 参数提示 | 每个参数带取值范围，点右侧「!」查看详细含义 |
| 保存 / 另存 / 读取 | 「保存」让参数生效；「另存」写入 3 个槽位；「读取」把槽位载入界面（再点保存才生效） |
| 时长上限 | 运行指定秒数后自动停止，避免忘记关 |

---

## 界面说明

- **执行方式**：在「无障碍服务」与「Shizuku」之间切换。
- **滑动预览**：随参数实时更新，红色圆点是起点、蓝色箭头是方向、虚线表示往返回程。
- **滑动参数**：每个参数名后标注取值范围，右侧「!」图标点开是详细说明。
- **录制滑动序列**：点「录制新序列…」进入录制面板，用手指画出多笔轨迹后保存命名；「序列列表 / 删除…」可选择或删除，勾选「使用录制序列回放」后开始 / 悬浮球都会回放该序列。
- **悬浮球**：圆形按钮，**灰蓝 ▶ = 已停止**，**绿色 ■ = 运行中**；按住可拖动位置。
- **通知栏**：前台通知常驻，提供「开始 / 停止」与「退出」动作。

---

## 参数说明

| 参数 | 含义 | 范围 / 默认 |
|---|---|---|
| 起点 X / Y | 滑动起点，按屏幕宽 / 高百分比 | 0–100，默认 50 / 50 |
| 角度 | 0=右，90=下，180=左，270=上 | 0–359，默认 90 |
| 距离下限 / 上限 | 每次滑动的长度在此区间随机（dp），两者相等即固定长度 | 1–4000，默认 300 / 500 |
| 滑动时长 | 单次手势持续（ms），越小越快 | 50–60000，默认 500 |
| 间隔 | 两次滑动之间等待（ms，未开启间隔随机时生效） | 0–600000，默认 500 |
| 最小间隔 | 开启「间隔随机」后，间隔下限（ms） | 0–600000，默认 200 |
| 最大间隔 | 开启「间隔随机」后，间隔上限（ms），每次循环在此区间随机 | 0–600000，默认 800 |
| 循环次数 | 0 表示无限 | 0–1000000，默认 0 |
| 往返停顿 | 回程前停顿（ms） | 0–60000，默认 200 |
| 弯曲幅度 | 轨迹的基础弧度（dp），0 为直线 | -2000–2000，默认 0 |
| 运行时长上限 | 到达后自动停止（秒） | 0 = 不限，最大 86400 |
| 随机抖动 | 拟人化随机强度（%），影响起点 / 方向 / 弯曲 / 摆动与间隔 | 0–100，默认 40 |

---

## 拟人化随机轨迹与随机间隔

### 拟人化随机轨迹

强度 `jitter = jitterPercent / 100`，每次滑动都会重新随机：

| 维度 | 随机方式 |
|---|---|
| 起点 X / Y | 在设定位置附近 ±`j × 6%` 屏幕短边内随机偏移 |
| 长度 | 在「距离下限 ~ 距离上限」之间均匀随机 |
| 方向角 | ±`j × 22°` |
| 弯曲 | 在基础弯曲上再叠加 ±`j × 70 dp` 的随机偏转 |
| 轨迹摆动 | 路径分成 5~8 段，沿垂直方向叠加随机摆动（约路径长度的 `j × 6%`） |

例如抖动设为 `40`，起点会在屏幕短边 ±2.4% 内浮动、方向 ±8.8°、弯曲 ±28 dp，轨迹本身也会歪歪扭扭，而不是一条标准直线。设为 `0` 时关闭起点 / 方向 / 弯曲 / 摆动随机，但长度仍会在「距离下限 ~ 上限」内随机；把上下限填成同一个值即可完全固定。Shizuku 方式受 `input swipe` 限制只能直线，但起点、终点和长度仍会随机。

### 随机间隔

开启「间隔随机」后，每次循环的间隔会在「最小间隔 ~ 最大间隔」之间随机取值，可再叠加「随机抖动」的间隔扰动。两种执行方式均支持。

---

## 安装

- **推荐**：从 [Releases](https://github.com/ippceo/SwipeTool/releases) 下载最新的 `SwipeTool-<版本号>.apk` 直接安装。
- 也可从 [Actions](https://github.com/ippceo/SwipeTool/actions) 下载构建产物（debug / release APK）。
- 均为统一的固定密钥签名（release 使用项目专用签名密钥），**同一签名**，版本之间可直接覆盖升级，不需要卸载。
- 系统要求：Android 7.0（API 24）及以上。

---

## 使用

### 方式一：在 App 内操作

1. 打开 App，选择「执行方式」并完成授权（见「快速开始」）。
2. 返回 App，设置参数，点「开始滑动」（直接使用界面上的当前参数）。
3. 切到任意界面，滑动会在当前屏幕上执行。
4. 停止：回到 App 点「停止」。
5. 想让悬浮球或下次启动也使用这套参数：点「保存当前参数」；只想存成模板则点「另存到槽位」，以后用「读取槽位」载入。
6. 想回放真人轨迹：在「录制滑动序列」里录制并保存，勾选「使用录制序列回放」后再点「保存」。之后点「开始滑动」或悬浮球都会回放这段序列；「试放一次」可立即验证。

### 方式二：悬浮球（跨应用快速启停）

1. 先在 App 内设置参数并点「保存当前参数」。
2. 点「1c. 开启悬浮球」，按提示允许「显示在其他应用上层」（Android 13+ 还会请求通知权限）。
3. 屏幕边缘出现圆形悬浮球（灰蓝 ▶ = 停止，绿色 ■ = 运行中）。
4. 切到任意应用，点悬浮球即可开始 / 停止；按住拖动可调整位置。悬浮球始终使用「已保存」的参数，界面上未保存的修改不会生效。
5. 退出：下拉通知栏，点通知里的「退出」，会停止滑动并**直接关闭整个应用**；通知内也可直接「开始 / 停止」。

---

## 工作原理

核心是 Android 标准的 `AccessibilityService` + `GestureDescription`：

1. `SwipeAccessibilityService` 注册为无障碍服务，获得发送手势的能力（`canPerformGestures`）。
2. 根据参数计算起点与终点坐标（长度在区间内随机、起点 / 方向 / 弯曲带抖动），构造 `Path`（多段三次贝塞尔，并叠加横向摆动）。
3. 用 `GestureDescription.Builder` 添加 `StrokeDescription`，通过 `dispatchGesture` 交给系统执行。
4. 用 `Handler` 按「手势时长 + 间隔」循环调度，实现连续 / 往返滑动。
5. 启用录制序列时，`RecordedSequence` 中每一笔的归一化采样点（0~1）映射到当前屏幕，按录制时的启动时间与持续时间重建 `StrokeDescription`；超过系统单次 10 笔的限制时自动按时间分批发送。录制数据仅保存在本机 `filesDir/recordings.json`。

全程只发送标准的触摸事件序列，没有任何平台识别或对抗逻辑。

### Shizuku 方式（可选）

1. `ShizukuSwipeEngine` 通过 `Shizuku.bindUserService` 绑定一个运行在 shell(ADB) 权限进程中的 `SwipeUserService`。
2. `SwipeUserService` 执行系统的 `input swipe x1 y1 x2 y2 duration` 命令注入滑动。
3. 前提：设备已安装并启动 [Shizuku](https://shizuku.rikka.app/download/)（或 Sui），并已向本应用授权。

Shizuku 方式为直线滑动；往返与曲线轨迹仅无障碍方式支持，随机抖动 / 随机间隔两种方式都支持。

---

## 常见问题（FAQ）

**Q：无障碍服务开了但状态显示「服务未连接」？**
A：先确认系统设置里「屏幕滑动工具」开关已打开；部分系统开启后需要稍等或重启 App。若仍不行，可开关一次或重启设备。

**Q：悬浮球不显示？**
A：需要授予「显示在其他应用上层」权限；MIUI / ColorOS 等系统还需在「后台弹出界面」「自启动」等设置中放行本应用。

**Q：切到其他 App 后无法从本 App 停止？**
A：这是预期行为——滑动在当前前台屏幕执行。请使用悬浮球、通知栏「停止」，或回到 App 停止。

**Q：Shizuku 方式为什么不能往返 / 曲线？**
A：受系统 `input swipe` 能力限制，Shizuku 只能直线滑动；需要往返 / 曲线请切换到无障碍方式。

**Q：连续滑动轨迹太固定 / 想更随机？**
A：把「随机抖动」调到 30–60，再把「距离下限 / 上限」拉开一点；抖动为 0 且上下限相同时才会完全固定。

---

## 云端构建（无需本地环境）

本项目通过 GitHub Actions 在云端编译，本地**不需要**安装 Android Studio / JDK / Gradle。

- 工作流：`.github/workflows/build.yml`
- 触发：向 `master` / `main` 分支 push，推送 `v*` tag，或在 Actions 页面手动 `Run workflow`
- 产物：Actions artifact 为 `SwipeTool-<版本号>-debug.apk` 与 `SwipeTool-<版本号>.apk`
- 发布：**推送 `v*` tag** 时自动创建 GitHub Release，附件名为 `SwipeTool-<版本号>.apk`
- 签名：release 使用固定签名密钥。CI 通过 GitHub Secrets（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）注入；未配置时回退 debug 签名（版本间签名会不一致，仅供本地调试）
- 环境：JDK 17 + 预装 Android SDK（`platforms;android-34`、`build-tools;34.0.0`）+ Gradle 8.7（构建时下载，不依赖 `gradle-wrapper.jar`）；依赖走阿里云 Maven 镜像，避免 Maven Central 限流

## 本地构建（可选）

需自备 Android Studio，或 JDK 17 + Android SDK 34 + Gradle 8.7：

```bash
git clone https://github.com/ippceo/SwipeTool.git
cd SwipeTool
# 用 Android Studio 打开，或：
gradle assembleDebug
```

---

## 项目结构

```
SwipeTool/
├── .github/workflows/build.yml          # 云端编译 debug + release APK，打 tag 时发布 Release
├── .trae/rules/project_rules.md         # 维护者协作规则（发布流程等）
├── app/
│   ├── build.gradle                     # 模块配置（compileSdk 34 / minSdk 24，含 Shizuku 依赖与 AIDL）
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── aidl/com/example/swipetool/
│       │   └── ISwipeService.aidl             # Shizuku UserService 接口
│       ├── java/com/example/swipetool/
│       │   ├── MainActivity.java              # 控制面板（参数、预览、授权、启停）
│       │   ├── SwipePreviewView.java          # 滑动预览自定义 View
│       │   ├── SwipeAccessibilityService.java # 无障碍手势引擎
│       │   ├── ShizukuSwipeEngine.java        # Shizuku 执行引擎（绑定并调度 UserService）
│       │   ├── SwipeUserService.java          # 运行在 shell 权限进程，执行 input swipe
│       │   ├── SwipeConfig.java               # 参数模型（含间隔解析 / 随机）
│       │   ├── SwipeConfigStore.java          # 参数持久化（供悬浮球复用）
│       │   ├── RecordActivity.java            # 录制界面（画布、撤销、清空、保存命名）
│       │   ├── RecordCanvasView.java          # 录制画布（逐点记录归一化坐标与时间）
│       │   ├── RecordedSequence.java          # 录制序列模型（多笔 + JSON 序列化）
│       │   ├── RecordedSwipeStore.java        # 录制序列本地存储（filesDir/recordings.json）
│       │   └── FloatingService.java           # 悬浮球服务（前台服务 + 悬浮按钮 + 通知）
│       └── res/                               # 图标、字符串、主题、无障碍配置
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## 已知限制

- 滑动在当前前台屏幕上执行；切到其他 App 后无法从本 App 界面直接停止，需回到 App，或用悬浮球 / 通知。
- 部分厂商系统（MIUI / ColorOS 等）对无障碍服务有额外限制，若被自动关闭需在系统设置里加入白名单。
- 坐标系为物理屏幕像素，起点与终点会自动裁剪到屏幕范围内。
- Shizuku 方式需自行安装并激活 Shizuku（或 Sui）；部分设备重启后需重新启动 Shizuku 服务。
- Shizuku 方式为直线滑动，不支持往返与曲线轨迹（受 `input swipe` 能力限制）。
- 录制序列回放只支持无障碍方式；Shizuku 无法发送曲线 / 多笔轨迹。录制单段最多 20 笔、60 秒，单笔最多 3000 个采样点。
- 悬浮球需授予「显示在其他应用上层」权限；部分系统（MIUI / ColorOS 等）还需在「后台弹出界面」等设置中放行，否则可能不显示。

---

## 权限与免责声明

- **无障碍方式**仅通过无障碍服务发送通用手势；**Shizuku 方式**通过 UserService 以 shell(ADB) 权限执行 `input swipe`。两者都**不读取屏幕内容、不联网、不上传任何数据**。
- 悬浮球需要「显示在其他应用上层」（`SYSTEM_ALERT_WINDOW`）与前台服务 / 通知权限（Android 13+），仅用于显示控制按钮并承载前台服务，不读取屏幕内容。
- 本项目仅用于自动化操作辅助（如长页面滚动、手势测试等中性场景），**请勿用于绕过平台风控、刷量或任何违反第三方服务条款的用途**。使用本工具产生的一切后果由使用者自行承担。

---

## License

[MIT](LICENSE)
