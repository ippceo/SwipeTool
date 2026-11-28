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
