# 项目规则 / 用户偏好

## 发布流程（默认偏好）

用户希望：**每次功能/改动完成后，默认直接发布 Release**，不需要再逐次确认。

标准流程：

1. 改完代码后本地编译校验：`gradle assembleDebug assembleRelease`（需 JDK 17 + Android SDK 34 + Gradle 8.7）。
2. 递增 `app/build.gradle` 中的 `versionCode` 与 `versionName`。
3. 提交并推送 `master`：`git push origin HEAD:master`。
4. 打标签并推送触发 GitHub Release：`git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z`。
   - 仓库的 `.github/workflows/build.yml` 仅在推送 `v*` 标签时才执行 `Create GitHub Release`，
     会附带编译好的 `app-release.apk`。只推 `master` 只会构建、不会发布 Release。
5. 用 GitHub API 确认：Actions run 为 `completed / success`，且 `releases/tags/vX.Y.Z` 已存在并带 `app-release.apk`。

## 说明

- 标签命名沿用历史 `vX.Y.Z`（如 `v1.4.0`、`v1.5.0`），与 `versionName` 对应。
- 发布属于公开动作，但用户已授权：此项目此后默认执行，无需重复询问。
- 纯 CI / 文档类、不改变 App 行为的改动，可不发新版 Release（避免同一 APK 重复发版）。

## 签名（固定密钥，务必遵守）

- 起因：早期 release 用的是 AGP 自动生成的 debug keystore，GitHub Actions 每次构建都是新机器、会重新生成随机密钥，导致版本间签名不一致、覆盖安装被拒（需先卸载）。
- 现状：已改为**固定签名密钥**，所有版本共用一个证书，可直接覆盖升级。
- 证书：`CN=SwipeTool, O=ippceo, C=CN`，SHA-256 = `64f2dd5e30e7ceff207d7c5f0affd0eadf64f5d09827e63f101d372fcadcba2c`。
- 注入方式：CI 从 **GitHub Secret `KEYSTORE_BLOB`** 读取，格式为单行 `password|alias|base64`。
  工作流优先用它；缺失时才回退到分离的 `KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`。
- `app/build.gradle`：当存在 `KEYSTORE_FILE` 环境变量时用固定密钥签名，否则回退 debug（仅本地无密钥构建用）。
- 密钥备份（**不进仓库**，已被 `*.jks` gitignore）：`signing-backup/swipetool-release.jks`，口令见 `KEYSTORE_BLOB` 前缀。
- 严禁重新生成密钥或更换签名方式——一旦更换，所有用户又得卸载重装。此密钥丢了就无法再用同一签名更新，务必自行长期备份。
- 历史遗留：v1.4.0、v1.5.0 是旧的随机 debug 签名；从 v1.5.1 起为固定签名，旧版需卸载一次后再装。

