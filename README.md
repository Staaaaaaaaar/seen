# Seen

个人的电影档案，Android 原生应用。收藏电影，独立评分，记录每一次观看与随时产生的想法；本地保存，无 Seen 账号、广告或遥测，可选连接 TMDB 获取电影资料。

本轮统一影库改版已进入本地验收，尚未调整版本号或发布。设计与实现范围见 [设计说明](docs/superpowers/specs/2026-11-07-library-redesign-design.md)，本轮证据与待验收项见 [验证记录](docs/library-redesign-validation.md)。历史发行信息见 [CHANGELOG](CHANGELOG.md) 与 [发布流程](docs/release.md)。

## 主要实现

Kotlin + Jetpack Compose + Material 3，单 Activity、单 app 模块，手动依赖注入。Compose UI → ViewModel → Repository → Room。根页面为影库、统计，设置位于右上角。外观跟随系统浅深色，标题与个人文字使用离线中文衬线字体。

- 影库采用紧凑海报列表，支持全部、已看、未看筛选，当前筛选内的本地搜索，以及添加时间、片名、年份、个人评分、最近观看排序。电影可没有观看、留言或评分。
- 个人评分独立于观看，支持 0.5–5 星、半星和清除。只有观看记录计入已看和观看次数；普通留言不计数。两种记录都支持日期、可选具体时间和地点。详情展示主要资料、简介和演职人员。
- 统计仅包含已看电影部数、观看次数、月频率与类型分布，支持今年、全部。类型按电影去重，多类型可重叠；无类型资料单独提示。
- TMDB 支持简体中文、English 资料偏好，影响搜索片名、详细资料与默认海报。设置中显式更新现有电影，准备全部完成后统一切换；失败或取消保留原版本。准备期间可继续留言、观看和评分，暂缓增删电影与恢复备份；未完成的 TMDB 建片草稿须先保存或放弃。手动电影不自动联网改写。

在「设置 → TMDB 凭证」输入 **API Read Access Token**（非 API Key），可从 [TMDB 账户设置](https://www.themoviedb.org/settings/api) 获取。凭据仅存本机，不写入 APK、日志或备份。移除凭据不影响已存资料。TMDB 添加按 ID 去重；海报下载失败仍可保存电影，之后可在资料语言页更新。手动电影保留可选本地海报，TMDB 使用自动默认海报。模拟器 DNS 排障见 [开发指南](docs/development.md#模拟器-tmdb-解析异常的可选修复)。

「设置 → 导出数据 / 导入备份」使用明文 JSON，包含电影、独立评分、观看、留言、资料及所引用海报。恢复前预检并确认覆盖，不合并；凭据与显示偏好保留。新格式 v2 继续兼容读取旧格式 v1。未保存草稿须先保存或放弃，失败保留原库。协议限制见 [备份格式](docs/backup-format.md)。

## 从拉取到运行（推荐 Android Studio）

1. `git clone git@github.com:Staaaaaaaaar/seen.git`，打开仓库根目录（含 `settings.gradle.kts`），不要只打开 `app/`。
2. 安装兼容 **AGP 8.13.2** 的 Android Studio 和完整 **JDK 17**。IDE 使用自带 JBR；在 Settings → Build Tools → Gradle 中为项目选择 JDK 17，并使用 Gradle Wrapper。
3. 用 SDK Manager 安装 **Android SDK Platform 36、Build Tools 35.0.0、Platform-Tools**，阅读并接受相关许可。SDK 路径由 Studio 写入 `local.properties`，不提交此文件。
4. Gradle Sync；选择 `app` 配置和 `debug` 变体。Device Manager 创建 **API 36** 模拟器，按宿主机选择镜像架构（Linux/Windows Intel 为 x86_64，Apple Silicon 为 arm64-v8a），启动后点击 Run ▶。
5. 运行测试和 lint，见下方。首次同步需要访问 Google Maven、Maven Central、Gradle 下载源；代理分别在 IDE 和 Gradle 配置，遇到错误按 [开发指南](docs/development.md) 定位。

以上过程可直接用官方工具完成，不依赖项目安装脚本。Linux x86_64 辅助脚本已在本机验证；macOS / Windows 使用 Studio 和下列 Gradle 任务，尚未做实机验证。

## 测试与日常开发

终端的 `JAVA_HOME` 应与 Studio 的 Gradle JDK 一致，指向 JDK 17；SDK 使用 `local.properties` 或 `ANDROID_HOME`。无需安装全局 Gradle。Windows PowerShell 把 `./gradlew` 替换为 `.\gradlew.bat`。

```bash
# 编译 App / 测试 APK、执行 JVM 单测和 lint
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
# 先在 Device Manager 启动一个隔离 API 36 模拟器，再运行设备集成测试
./gradlew :app:connectedDebugAndroidTest
```

Linux 可选辅助入口（每步独立，不自动串联安装与运行）：

```bash
bash scripts/doctor.sh               # 只读环境预检
bash scripts/setup.sh                # 对齐本机 SDK/JDK 配置，不下载、不构建、不打开 IDE
bash scripts/check.sh                # 同上方构建任务，另生成验证记录
bash scripts/emulator.sh --show      # 启动项目专用模拟器，不下载组件
ANDROID_SERIAL=emulator-5556 bash scripts/run.sh    # 构建、覆盖安装、启动
ANDROID_SERIAL=emulator-5556 bash scripts/check.sh --device
```

SDK 的显式 CLI 安装、路径优先级、模拟器准备、代理与排障见 [开发指南](docs/development.md)。多设备时必须选择测试设备；请使用隔离模拟器，保留承载真实记录的实例，签名不一致时不能靠卸载解决。

Debug 包名 `com.seen.film.debug`，APK：`app/build/outputs/apk/debug/app-debug.apk`；正式包名 `com.seen.film`，签名构建见 [发布流程](docs/release.md)。测试报告：`app/build/reports/`。

## 项目导航

| 路径 | 内容 |
| --- | --- |
| `app/src/main/` | ui / domain / data、Android 资源 |
| `app/src/debug/` | 四类 Compose Preview |
| `app/src/test/` / `app/src/androidTest/` | JVM 规则测试 / 数据与 UI 集成测试 |
| `app/schemas/` | Room 历史 schema |
| `gradle/` | Wrapper、版本目录、依赖校验 |
| `scripts/` / `.github/workflows/` | 可选开发辅助 / CI |

- [开发指南](docs/development.md)：准备、开发、运行、测试、排障。
- [贡献指南](CONTRIBUTING.md) · [代理开发约定](AGENTS.md)：变更与验收规范。
- [产品与设计](docs/design-review.md) · [设计资料与归档](docs/design/README.md) · [开发规划](docs/roadmap.md)。
- [v0.4 底栏与海报电影库方案](docs/v0.4.0-navigation-design.md) · [最新四屏布局稿](docs/design/v0.4.0-layout-v3.png)（设计依据；实际验收见 [验证日志](docs/v0.4.0-validation.md)）。
- [发布流程](docs/release.md) · [脚本索引](scripts/README.md)。
- [v0.2.1 验证日志](docs/v0.2.1-validation.md) · [v0.2 验证日志](docs/v0.2.0-validation.md) · [v0.1 验证日志](docs/v0.1.0-validation.md) · [版本与发布](docs/versioning.md)。
