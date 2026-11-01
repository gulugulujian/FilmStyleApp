# 快速启动指南

## 方式 A：GitHub Actions 云端编译（推荐，无需本地装环境）

> 适合想「直接拿到 APK」的场景，不需要装 Android Studio。

1. 把项目推到 GitHub（假设仓库地址是 `你的用户名/FilmStyleApp`）：
   ```bash
   cd FilmStyleApp
   git init
   git add .
   git commit -m "init: 胶片风格 App"
   git branch -M main
   git remote add origin https://github.com/你的用户名/FilmStyleApp.git
   git push -u origin main
   ```
2. 推送后，GitHub 云端会自动编译（约 5~8 分钟）
3. 打开仓库 → **Actions** 标签 → 点击最近一次运行 → 底部 **Artifacts** 区下载 `FilmStyleApp-debug.apk`
4. 把这个 APK 传到手机安装即可

**发布成可分享链接**（打 tag 自动发布到 Release）：
```bash
git tag v1.0
git push origin v1.0
```
之后仓库 **Releases** 页会出现带 `.apk` 下载链接的版本。

## 方式 B：Android Studio 本地编译

## 前提

- **Android Studio**（推荐最新版，自带 JDK 17 和 Android SDK 管理）
- Android SDK Platform 34
- 一台 Android 设备（minSdk 24 = Android 7.0+）或模拟器

## 打开并运行

1. 打开 Android Studio → **Open** → 选择本目录 `FilmStyleApp`
2. 等待右下角 Gradle 同步完成（首次会下载 Gradle 8.7 和依赖，需联网，约几分钟）
3. 若提示缺少 `gradle-wrapper.jar`：在 Android Studio 里选择 `File → Sync Project with Gradle Files`，AS 会自动补齐；或从任意 Gradle 8.7 项目拷贝一个 `gradle/wrapper/gradle-wrapper.jar`
4. 连接设备（开启 USB 调试）或启动模拟器
5. 点击绿色 ▶ 运行按钮

## 首次使用流程

1. 点击「**从图片提取风格**」→ 从相册选一张你喜欢的参考图
2. 输入框里给风格起个名字（不填会自动命名「我的风格 N」）
3. App 自动提取「色彩 + 颗粒 + 暗角 + 对比度」，保存到预设库
4. 顶部预览区实时展示套用效果
5. 下方列表点击任意已保存的风格，可随时切换

## 常见问题

| 问题 | 解决 |
|---|---|
| Gradle 同步慢/失败 | 换网络，或配置镜像（`gradle.properties` 加阿里云镜像） |
| 编译报 SDK 找不到 | `File → Settings → SDK` 下载 Android 14 (API 34) |
| 模拟器太慢 | 用真机，或开启硬件加速 |
| shader 编译失败 | 确认设备支持 OpenGL ES 3.0（Android 4.3+ 都支持） |

## 权限说明

Android 13+ 需要 `READ_MEDIA_IMAGES` 权限读取相册；Android 12 及以下用 `READ_EXTERNAL_STORAGE`。两者都已在 `AndroidManifest.xml` 声明，运行时 Android 13+ 会自动弹窗请求。
