# 胶片风格 App（FilmStyleApp）

一个从参考图提取胶片风格、并实时套用到照片的安卓 App 骨架。

## 核心功能

1. **从图片提取风格**：选一张参考图，自动提取「色彩 + 颗粒 + 暗角 + 对比度」完整风格
2. **自主命名**：给提取的风格起名字，保存到预设库
3. **实时套用**：GPU shader 实时渲染，毫秒级预览

## 项目结构

```
app/src/main/
├── java/com/filmstyle/app/
│   ├── StylePreset.kt       # 风格预设数据类（色彩+氛围）
│   ├── StyleExtractor.kt    # 风格提取（RGB→Lab + 颗粒/暗角/对比度）
│   ├── PresetStore.kt       # 预设库存储（SharedPreferences JSON）
│   ├── StyleRenderer.kt     # GLES 渲染器（实时套用）
│   └── MainActivity.kt      # UI + 交互
├── res/
│   ├── layout/              # 布局（主界面 + 列表项）
│   ├── raw/style_preset.glsl # 风格迁移 shader
│   └── values/              # 主题、字符串
```

## 如何运行

### 方式一：Android Studio 本地编译

1. 用 **Android Studio** 打开本目录（`FilmStyleApp`）
2. 等待 Gradle 同步完成
3. 连接设备或启动模拟器，点击运行

> 需要 Android SDK 34 + JDK 17。首次打开会自动下载 Gradle 8.7。

### 方式二：GitHub Actions 云端编译（无需本地装环境）

本项目已配置 `.github/workflows/build-apk.yml`，推送到 GitHub 后会自动编译出 APK：

1. 把项目推到 GitHub 仓库
   ```bash
   git init
   git add .
   git commit -m "init: 胶片风格 App"
   git branch -M main
   git remote add origin https://github.com/你的用户名/FilmStyleApp.git
   git push -u origin main
   ```
2. 推送后自动触发编译（云端自带 Java 17 + Android SDK，无需本地环境）
3. 编译完成后，进入仓库 **Actions** 页面 → 点击当次运行 → 底部 **Artifacts** 区域下载 `FilmStyleApp-debug.apk`
4. 把 APK 传到手机安装即可（Debug 版可直接安装）

#### 发布到 Release（生成可分享下载链接）

打一个 tag 即可自动编译并发布到 Release：

```bash
git tag v1.0
git push origin v1.0
```

发布后，仓库 **Releases** 页面会出现一个带 `.apk` 下载链接的版本，可直接分享给别人。

## 技术要点

- **风格 = 6 个数字**（Lab 均值 + 标准差）+ 3 个氛围标量（颗粒/暗角/对比度）
- **提取在 CPU**（下采样 128px，约 10ms）
- **套用在 GPU**（GLES 3.0 fragment shader，一条公式）
- 算法与 `spektrafilm/build/style_preset.py` 参考实现**完全一致**（已交叉验证）

## 与后端算法的对应关系

| Kotlin 文件 | Python 参考 |
|---|---|
| `StyleExtractor.extract()` | `style_preset.py::extract_preset()` |
| `StylePreset` | `style_preset.py::StylePreset` |
| `style_preset.glsl` | `style_preset.py::apply_preset()` 的 GPU 版 |
| `PresetStore` | `style_preset.py::save/load_presets` |
