# Seen

个人的已看电影库，Android 原生应用。以电影为主体，记录每次观看的日期、地点、可选评分与感受；本地保存，无 Seen 账号、广告或遥测；可选连接 TMDB 获取资料。

当前版本 **v0.4.0**。已完成本地构建、API 36 与界面专项验证，用户已授权提交、推送和 annotated tag；实际远端交付以 [Actions](https://github.com/Staaaaaaaaar/seen/actions/workflows/android.yml) 与 [tags](https://github.com/Staaaaaaaaar/seen/tags) 为准，不创建正式 GitHub Release。变更与验收见 [v0.4 验证日志](docs/v0.4.0-validation.md)，历史基线见 [v0.3 验证日志](docs/v0.3.0-validation.md) 与 [CHANGELOG](CHANGELOG.md)。

v0.2.1 维护补丁：优先连接 `api.tmdb.org`，网络失败时回退原入口，详见 [补丁验证日志](docs/v0.2.1-validation.md)。已有 v0.2.0 tag 保持不变。

## 主要实现

Kotlin + Jetpack Compose + Material 3，单 Activity、单 app 模块，手动依赖注入。Compose UI → ViewModel（StateFlow / 协程）→ Repository → Room（SQLite / Flow）。电影和每次观看分别存储；底部提供电影库、添加记录、设置，电影库以海报去重展示，详情保留每次观看历史。手动建片先填写资料，再添加观看，最终事务保存；外观跟随系统浅深色。系统图片选择器导入海报后复制到私有存储；编辑草稿用 AtomicFile 暂存。

v0.1 提供海报/时间视图、本地搜索、电影详情、手动建片、本地海报、观看增删改查、重复观看、0.5–5 星半星评分和可选感受。v0.2 增加 TMDB 中文/原名搜索、分页、导演/片长/国家资料、私有海报与 TMDB ID 复用；v0.3 提供全部电影/观看/海报 JSON 导出、预检与确认后覆盖恢复；v0.4 调整为底部三入口与纯海报电影库，完善记录流程并支持系统浅深色。

在「设置 → TMDB 凭证」输入 TMDB **API Read Access Token**（非 API Key），可从 [TMDB 账户 API 设置](https://www.themoviedb.org/settings/api) 获取。模拟器若因 DNS 异常搜索超时，见 [可选联网修复](docs/development.md#模拟器-tmdb-解析异常的可选修复)。凭据仅写入本机应用私有存储，不内置到 APK、不提交；移除凭据不影响已存资料。添加电影时可搜索 TMDB 或手动添加，搜索会发送关键词，失败可重试；保存后的资料与海报离线可读。海报失败不阻止建片，可选本地海报。

在「设置 → 导出数据」导出全部已保存数据，或在「设置 → 导入备份」选择文件预检。备份为明文，含感受和海报，请保存到可信位置。恢复会替换本机全部电影与观看，不能合并；可先导出原库。未完成的编辑须先保存或放弃。凭据与偏好保留，损坏/不支持的备份在写库前拒绝。协议和限制见 [备份格式](docs/backup-format.md)。

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

Debug 包名 `com.seen.film.debug`，APK：`app/build/outputs/apk/debug/app-debug.apk`。测试报告：`app/build/reports/`。

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
- [产品与设计](docs/design-review.md) · [UI 示意](docs/ui-review.svg) · [开发规划](docs/roadmap.md)。
- [v0.4 底栏与海报电影库方案](docs/v0.4.0-navigation-design.md) · [最新四屏布局稿](docs/design/v0.4.0-layout-v3.png)（设计依据；实际验收见 [验证日志](docs/v0.4.0-validation.md)）。
- [v0.2.1 验证日志](docs/v0.2.1-validation.md) · [v0.2 验证日志](docs/v0.2.0-validation.md) · [v0.1 验证日志](docs/v0.1.0-validation.md) · [版本与发布](docs/versioning.md)。
