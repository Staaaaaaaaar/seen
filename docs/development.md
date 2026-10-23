# 开发指南

本指南按现有脚本整理，面向 Ubuntu 22.04 / Linux x86_64。工具版本以仓库配置为准；本机安装记录与验收状态见 [验证日志](v0.1.0-validation.md)。

## 首次配置

1. 安装 JDK 17（Ubuntu 包名 `openjdk-17-jdk`）和 Android Studio。脚本不负责安装它们。
2. 准备 `bash`、`python3`、`curl`、`unzip`、`sha256sum`、`flock` 和 `rg`（Ubuntu 包名 `ripgrep`）。
3. 确保终端能下载 Google Android SDK、Google Maven、Maven Central 和 Gradle 发行包，并有足够磁盘空间。模拟器还需可用的 `/dev/kvm`。
4. 在项目根目录运行以下命令，按提示阅读并接受所需 SDK 许可。

```bash
# 使用其他安装位置时，在配置前覆盖路径（均为绝对路径）：
export SEEN_JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_STUDIO_HOME=/absolute/path/to/android-studio
# 可选：export SEEN_ANDROID_SDK=/absolute/path/to/android-sdk
bash scripts/setup.sh
```

Studio 默认位置为 `.tools/android-studio`；不在此处安装时必须设置 `ANDROID_STUDIO_HOME`。SDK 默认位置为 `.tools/android-sdk`。仅准备与构建、不打开 IDE 时用 `bash scripts/setup.sh --no-open`。

setup 会创建缺失的 IDE 配置、安装缺失 SDK、生成 `local.properties` 和 `.tools/android-env.sh`，然后调用 `check.sh`。安装内容为 API 36、Build Tools 35.0.0、Platform-Tools 和命令行工具；Gradle 由标准 8.13 Wrapper 提供，无需全局安装。

IDE 使用随附 JBR；项目 Gradle 使用 JDK 17。SDK 命令行工具优先使用 Studio 的 JBR，也可用 `SEEN_SDK_JAVA_HOME` 单独指定。`setup.sh --check-only` 只输出 Java 版本并检查 SDK 文件存在，不代表构建或设备验收通过。

## 本机配置与代理

| 位置 | 用途 |
| --- | --- |
| `local.properties` | 本机 SDK 路径 |
| `.tools/android-env.sh` | CLI 使用的 Java、SDK、AVD 与 Gradle 路径 |
| `.gradle/config.properties`、`.idea/gradle.xml` | Studio 的 Gradle JDK |
| `.tools/studio-profile/`、`.tools/studio.properties` | IDE 设置、缓存、插件与日志 |
| `.tools/gradle-home/` | Gradle 缓存与本机 properties |
| `.tools/android-user/`、`.tools/android-avd/` | Android 用户设置与专用模拟器 |

上述设置均不入库。`configure_studio.py` 只创建缺失配置，保留已有设置；变更 JDK、SDK 或代理后应核对已有配置，不要假定重新运行会全部覆盖。SDK 安装器会更新 `local.properties` 的 SDK 路径。

首次配置可从 `HTTPS_PROXY` 继承无认证 HTTP 代理到 IDE 与 Gradle。SDK 安装器可读取 HTTP/HTTPS 代理环境变量或 IDE 代理设置；不同工具的代理不保证自动同步。变更时同时检查 Studio HTTP Proxy 与 `.tools/gradle-home/gradle.properties`。

依赖下载需要镜像时，现有配置支持在本机 Gradle properties 中设置 `seen.googleMavenMirror=<HTTPS URL>`，仅影响 AndroidX/Android 工具组。默认 CI 使用官方源，不将个人代理或镜像写入公共配置；不关闭 TLS 校验。

## 构建与图形调试

```bash
bash scripts/check.sh        # App / 测试 APK 编译、JVM 单测、lint、验证记录
bash scripts/studio.sh       # 打开项目，执行 Gradle Sync 后运行或调试
```

仅需打开 IDE 的 SDK 设置时用 `bash scripts/studio.sh --configure-sdk`。Preview 入口为 `app/src/debug/java/com/seen/film/ui/LibraryPreviews.kt`，含海报、时间、空状态与 200% 字号；渲染结果仍待实际确认。Preview 不替代保存、返回栈、系统选图与数据持久化测试。

直接使用 Gradle / adb 前，在同一终端运行 `source .tools/android-env.sh`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，包名 `com.seen.film.debug`。

## 模拟器与设备测试

```bash
bash scripts/emulator.sh --show    # 创建并打开专用 API 36 模拟器
```

专用 AVD 为 `Seen_API36`，Pixel 7 / Google APIs x86_64，端口 5556。首次运行下载缺失组件，不清空已有 AVD。需要 KVM；如明确接受较慢的软件模拟，可设置 `SEEN_EMULATOR_NO_ACCEL=1`。

在另一终端、项目根目录执行：

```bash
source .tools/android-env.sh
adb devices -l
ANDROID_SERIAL=emulator-5556 bash scripts/check.sh --device
```

真机需开启开发者选项和 USB 调试、接受 RSA 授权，并有设备访问权限。当前设备测试门槛为 API 36；多设备连接时以 `ANDROID_SERIAL` 明确选择隔离测试设备。Redmi K70 Ultra 的选图、键盘、手势区、TalkBack、大字号与覆盖安装仍需人工回归。

完整 v0.1 自动验收使用：

```bash
bash scripts/verify-v0.1.sh
```

该脚本先执行构建检查；缺少锁文件或校验元数据时启用 Gradle 生成，再准备专用模拟器、运行设备测试、核对源码/APK 哈希并做 Git 预检，成功后收集到 `.tools/v0.1.0/`。生成的 `app/gradle.lockfile` 与 `gradle/verification-metadata.xml` 应审查依赖来源后提交。脚本不执行 tag 或发布；Git 预检失败也不能视作完整交付通过。手工先打开的专用模拟器会保留，脚本自行启动的模拟器会在结束时关闭。

设备测试使用隔离测试 Application 和临时数据库。不得卸载或清除承载真实记录的应用；不同机器/CI 的 Debug 签名可能不同，签名冲突不能靠卸载真实实例解决。

## 日志与故障定位

| 内容 | 位置 |
| --- | --- |
| 环境准备 | `.tools/setup.log` |
| 最近构建 / 设备测试 | `.tools/build-latest.log` / `.tools/device-tests-latest.log` |
| 完整验收 / 模拟器 | `.tools/v0.1-verification.log` / `.tools/emulator.log` |
| 构建报告 | `app/build/reports/` |
| 构建 / 设备验证记录 | `.tools/check-result.json` / `.tools/device-result.json` |
| 依赖诊断 | `.tools/dependency-diagnosis.log` |

依赖解析失败时运行 `bash scripts/diagnose-dependencies.sh`，查看原始异常和编译器 classpath；诊断通过不代表构建通过。按实际报错区分下载、编译和 lint 问题，不默认清缓存或升级版本。

受限会话的 socket、网络或 X11 失败不能直接归因为宿主机故障；图形调试与完整验收应在具备相应权限的普通终端运行。

## 工程维护

- 单 app 模块、单 Activity，手动依赖注入；UI → ViewModel → Repository，Room Flow 提供已保存数据。
- 版本统一在 `gradle/libs.versions.toml` 管理；工具链升级与业务改动分别验证。
- 保留 `app/schemas/` 的历史版本；数据库结构变化须显式迁移，不允许 destructive migration。
- CI 使用同一 `check.sh`，先构建、单测与 lint，再执行 API 36 集成测试；成功构建才上传 APK。远端实际状态见验证日志。
- 密钥、本机设置、构建产物、备份和真实记录不入库。签名和交付见 [版本约定](versioning.md)。
