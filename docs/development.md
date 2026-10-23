# 开发指南

主流程使用 Android Studio / SDK Manager / Device Manager 和仓库 Gradle Wrapper。辅助脚本面向 Linux x86_64；它们用于减少重复输入，不能代替宿主机的 JDK、网络、许可和虚拟化配置。首次打开步骤见 [README](../README.md)。

## 工具链与支持范围

| 项目 | 仓库要求 | 配置位置 |
| --- | --- | --- |
| Gradle 运行与 Kotlin 工具链 | 完整 JDK 17 | Studio Gradle JDK / `JAVA_HOME` |
| Gradle / AGP / Kotlin | 8.13 / 8.13.2 / 2.3.21 | Wrapper / `gradle/libs.versions.toml` |
| SDK | compile/target 36，min 26 | `app/build.gradle.kts` |
| SDK 组件 | Platform 36、Build Tools 35.0.0、Platform-Tools | SDK Manager |
| 自动设备验收 | API 36，Google APIs | Device Manager |

本机实际验证 Ubuntu 22.04 x86_64。macOS / Windows 使用相同 Gradle 工程与官方 GUI，尚未实测；Linux 专用脚本不在这些平台承诺兼容。Windows 使用 `gradlew.bat`。Android Studio 的 JBR 与项目 Gradle JDK 分开选择；CLI 和 IDE 应使用同一个 JDK 17，详见 [官方 JDK 说明](https://developer.android.com/build/jdks)。不自动升降版本来绕过下载问题。

## 首次准备：按阶段检查

先安装 Studio 与 JDK 17，用 SDK Manager 安装表中组件，打开工程同步。Ubuntu 可安装 `openjdk-17-jdk`；脚本另需 bash、python3，CLI 安装器才需要 curl、unzip、sha256sum、flock。IDE、KVM 和系统软件包由开发者管理。

已有 SDK 时，在终端选择真实路径（示例中的占位路径必须替换）：

```bash
export JAVA_HOME=/absolute/path/to/jdk-17
export ANDROID_HOME=/absolute/path/to/Android/Sdk
bash scripts/doctor.sh
bash scripts/setup.sh
bash scripts/check.sh
```

`doctor.sh` 只读，输出解析后的 JDK/SDK/cache 路径，检查 JDK 17 与 SDK 文件，提示 KVM 状态；不联网，不证明编译通过。`setup.sh` 仅写本机 `sdk.dir`、Gradle `java.home`，创建缺失的 IDE 配置与兼容环境入口；保留其他配置，不下载、不构建、不启动 IDE。`--check-only` 等价 doctor；旧 `--no-open` 保留为普通配置模式。

未安装 SDK 且希望使用 CLI 的 Linux x86_64 开发者，显式执行：

```bash
export JAVA_HOME=/absolute/path/to/jdk-17
export SEEN_ANDROID_SDK=/absolute/path/to/android-sdk
# 若当前 SDK 命令行工具要求比 17 更新的 Java，指定其运行时（可用 Studio 自带 JBR）
export SEEN_SDK_JAVA_HOME=/absolute/path/to/android-studio/jbr
bash scripts/setup.sh --install-sdk
```

安装器使用固定官方压缩包与 SHA-256，按需下载；许可在终端由使用者阅读并接受，不静默同意。构建仍使用 JDK 17。也可以完全通过 Studio 安装，省去此步骤。

## 路径、配置与代理

脚本统一通过 `scripts/env.sh` 解析路径，不依赖旧机器生成的绝对路径文件：

- JDK：`SEEN_JAVA_HOME` → `JAVA_HOME` → PATH 中 java 所属 JDK；要求完整 JDK 17。
- SDK：`SEEN_ANDROID_SDK` → `ANDROID_HOME` → `ANDROID_SDK_ROOT` → `local.properties` → 项目 `.tools/android-sdk`。`ANDROID_HOME` 与旧变量 `ANDROID_SDK_ROOT` 不一致时明确停止。
- 使用环境变量换 SDK 后执行 `setup.sh` 对齐 `local.properties`，否则标准 Gradle / Studio 仍可能使用旧路径。
- Gradle 默认使用标准 `~/.gradle`；保留已有 `.tools/gradle-home` 的老工作区缓存。`GRADLE_USER_HOME` 可明确覆盖。CI 使用 Actions 的标准缓存。
- 辅助脚本的 AVD 默认在 `.tools/android-avd`；Studio 从桌面启动时使用自己的 Device Manager 设备，两者可能不同。`ANDROID_AVD_HOME` / `ANDROID_USER_HOME` 可覆盖。
- 大型 SDK/IDE 建议使用仓库外的共享安装；保留项目内 `.tools` 时可在 Studio Project 视图对该目录 Mark Directory as → Excluded，避免索引下载缓存。
- `.tools/android-env.sh` 仅为兼容入口；新终端推荐 `source scripts/env.sh`。不要复制旧克隆的 local.properties、.tools、.idea 或 .gradle 到新机器。

`setup.sh` 保留已有 `.idea/gradle.xml`；若曾手选其他 Gradle JDK，请在 Studio 中切换到 JDK 17 或 `GRADLE_LOCAL_JAVA_HOME`。`studio.sh` 是本机隔离配置的可选启动器，需要 `ANDROID_STUDIO_HOME`（默认 `.tools/android-studio`）；日常可直接用正常的 Studio 启动入口。

代理由使用者显式配置，不从环境偷偷写入全局设置。Studio 设置 HTTP Proxy；Gradle 在当前 `GRADLE_USER_HOME` 的 `gradle.properties` 中设置 `systemProp.https.proxyHost` / `proxyPort`（及需要的 http 代理）。Wrapper 下载也需要可达代理。sdkmanager 安装器可读无认证 HTTP/SOCKS 环境代理；各工具的代理不是同一套设置。不要提交含代理凭据的文件，不关闭 TLS。

默认源为 Google Maven、Maven Central 和插件门户。本机确需镜像可在用户 Gradle properties 设置 `seen.googleMavenMirror=https://...`，仅限制到 Android/AndroidX 组。CI 使用官方源。依赖 SHA-256 能检测内容变化，不替代首次来源审查。

## 日常开发与 Preview

在 Studio 完成 Gradle Sync，选择 `app` / `debug`；Run 用于正常启动，Debug 可断点。Preview 在 `app/src/debug/java/com/seen/film/ui/LibraryPreviews.kt`，包含海报、时间、空状态与 200% 字号。若文件在首次成功同步前已经打开，关闭该标签再重新打开，让 Studio 重新识别 Compose Split / Design 编辑器。可在 Settings → Editor → UI Tools 检查 Kotlin 的视图设置。Preview 是布局辅助；真实系统选图、保存、返回栈和持久化仍需设备验证。

标准命令（可绕过所有辅助脚本）：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

`bash scripts/check.sh` 运行相同任务、配置检查与环境预检，并在成功后输出源码/APK 哈希绑定的 `.tools/check-result.json`。不会安装 SDK、更新依赖保护或打开窗口。APK：`app/build/outputs/apk/debug/app-debug.apk`；报告：`app/build/reports/tests/testDebugUnitTest/` 与 `app/build/reports/lint-results-debug.html`。

## 模拟器、安装与测试

推荐从 Device Manager 创建 API 36 Google APIs 设备，按宿主机选架构（[官方设备说明](https://developer.android.com/studio/run/managing-avds)），启动后选中设备 Run。Windows 在系统启用支持的虚拟化后端；Linux 检查虚拟化和 `/dev/kvm` 权限，组变更后可能需要重新登录。构建不要求 KVM。

Linux 专用 AVD 的可选流程：

```bash
# 仅首次安装组件，仍需阅读许可；不在启动脚本中自动下载
bash scripts/install_android_sdk.sh --emulator
bash scripts/emulator.sh --prepare   # 仅创建 Seen_API36，不清已有数据
bash scripts/emulator.sh --show      # 前台运行，关闭窗口即可退出
# 无窗口运行可用 --headless；另一个终端继续下列步骤
source scripts/env.sh
adb devices -l
ANDROID_SERIAL=emulator-5556 bash scripts/run.sh
```

专用 AVD 是 Pixel 7 / API 36 / Google APIs x86_64，端口 5556；如被占用先检查已有设备，不杀未知进程。`--headless` 使用软件图形但 CPU 仍默认 KVM。无 KVM 可显式选择 `SEEN_EMULATOR_NO_ACCEL=1`，速度可能很慢，不作为默认方案。

`run.sh` 要求明确 `ANDROID_SERIAL`，执行 assemble → `adb install -r` → 启动 Activity，不会自动选真机、卸载或清库。也可用原生命令：

```bash
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 shell am start -W -n com.seen.film.debug/com.seen.film.MainActivity
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
# 或生成额外验证记录
ANDROID_SERIAL=emulator-5556 bash scripts/check.sh --device
```

设备测试含 Room/事务/重启、私有海报、草稿与完整观看界面流程，测试 Application 与数据隔离。使用专用模拟器：Gradle 为安装测试 APK 可能处理包状态，不要对真实个人数据实例运行集成测试。不同机器的 Debug 签名可能不同；签名冲突请选择隔离设备或匹配密钥，不能卸载真实实例。Redmi 真机的选图、手势区、TalkBack、大字号和覆盖升级需单独人工回归。

完整本地门槛 `bash scripts/verify-v0.1.sh` 串联构建与专用 API 36 测试，已有模拟器保留，自行启动的退出时关闭。要求组件和依赖保护已准备；不会隐式安装或修改锁。成功收集到 `.tools/v0.1.0/`。本地门槛不查询 GitHub、不要求 gh 登录；发布前可单独运行 `python3 scripts/git_preflight.py` 做只读连通性检查。CI/tag 属发布检查，不由本地测试成功代替。

## 依赖与 CI

依赖版本集中管理，普通构建只读取锁与校验文件。新增/升级依赖时显式生成，再审查差异与来源：

```bash
source scripts/env.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --write-locks --write-verification-metadata sha256
```

生成后还需用**空依赖缓存**复验：已有缓存可能不再读取父 POM/BOM，导致生成元数据时漏项，而新机器会失败。为本次审查选一个尚未使用的 Gradle home（代理在其中单独配置；不复制依赖缓存），从官方源重新生成并审查差异：

```bash
GRADLE_USER_HOME="$PWD/.tools/dependency-review-home" ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --write-verification-metadata sha256
# 随后在相同目录用普通严格模式检查，不带 --write-*：
GRADLE_USER_HOME="$PWD/.tools/dependency-review-home" ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

Gradle 的自动生成仍可能漏掉父 POM/BOM；全新 CI 报缺失时，应按报告中的准确坐标从官方源显式解析元数据制品，由 Gradle 生成并审查哈希，不能直接加可信通配符或关闭校验。最终以普通严格模式和全新 CI 的实际结果为准。

这属于依赖维护与发布复验，日常开发继续使用正常缓存。

Studio 还会解析 Gradle 分发源码、库的 sources/Javadoc JAR 和 Kotlin 插件的 Gradle 专用源码变体，以及 AndroidX 源码变体中的 samples JAR。升级依赖后继续显式生成这些 IDE 制品校验，再实际执行一次 Studio Sync / Preview，避免“命令行通过但 IDE 被校验拦住”：

```bash
./gradlew seenCaptureIdeDependencies --init-script scripts/ide-dependencies.init.gradle --write-verification-metadata sha256
```

此任务按当前依赖图解析源码，只在依赖维护时运行，不嵌入日常 setup/check。依赖没有发布源码时不影响业务编译。

提交 `app/gradle.lockfile`、`settings-gradle.lockfile`、其他根锁文件（若生成）、`gradle/verification-metadata.xml`；不手写假哈希，不为了构建通过添加任意可信通配符。校验失败先核对来源，遵循 [Gradle 校验说明](https://docs.gradle.org/current/userguide/dependency_verification.html)。

Actions 的 build job 执行环境脚本回归、构建/单测/lint；API 36 job 依赖 build，执行集成测试，分别上传报告。使用 JDK 17、标准 Wrapper 与默认官方依赖源；SDK action 显式指定所需组件，不依赖其默认的旧 `tools` 包。远端是否通过以实际 workflow 结果为准。

## 排障与日志

| 失败阶段 | 检查方式 |
| --- | --- |
| 环境预检 | `bash scripts/doctor.sh`，查看实际解析路径；JDK 需有 javac |
| Sync / 下载 | 先查原始网络/TLS 错误、代理与源；`bash scripts/diagnose-dependencies.sh` 输出 classpath 诊断 |
| 编译 | 看失败 task 和源码行；不要把编译错误归因网络 |
| lint | `app/build/reports/lint-results-debug.html` |
| 模拟器 | `adb devices -l`、SDK Emulator 加速检查、KVM/宿主权限；完整验收日志 `.tools/emulator.log` |
| 设备测试 | `app/build/reports/androidTests/connected/` 与 `.tools/device-tests-latest.log` |
| 安装运行 | 明确 serial；检查签名、磁盘及 `adb -s SERIAL logcat` |

构建日志 `.tools/build-latest.log`，验证记录 `.tools/check-result.json` / `device-result.json`，完整验收 `.tools/v0.1-verification.log`。报告来自实际执行；配置预检和依赖诊断不代表 App 成功运行。受限会话的 socket、X11 或 KVM 可见性与宿主机不同，应在正常终端复核，不默认清缓存、删 AVD 或重装系统。
