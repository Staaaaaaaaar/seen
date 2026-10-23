# Seen

个人的已看电影库，Android 原生应用。以电影为主体，记录每次观看的日期、地点、评分与可选感受；离线使用，无账号、广告或遥测。

当前为 **v0.1.0 待验收候选，尚未发布**。已有编译与 6 项 JVM 单测通过的历史记录；最新候选的完整 Gradle/lint、API 36 设备测试及远端 CI 仍待验证，详见 [验证日志](docs/v0.1.0-validation.md)。

## 开发

面向 Ubuntu 22.04 / Linux x86_64。先安装 **JDK 17、Android Studio**；脚本可安装 SDK，但不会安装 JDK 或 IDE。无需全局 Gradle。

在项目根目录执行：

```bash
bash scripts/setup.sh          # 准备 SDK、构建、单测、lint，并打开 Studio
bash scripts/check.sh          # 日常构建、单测与 lint
bash scripts/studio.sh         # 打开 IDE
```

首次配置、路径覆盖、代理、模拟器与故障定位见 [开发指南](docs/development.md)。完整 v0.1 验收入口是 `bash scripts/verify-v0.1.sh`，会安装缺失模拟器组件，并可能生成依赖锁与校验元数据。

## 项目结构

```text
app/                 Android 应用、测试与 Room schema
  src/main/          ui / domain / data，单 Activity、单 app 模块
  src/debug/         Compose Preview
  src/test/          JVM 单测
  src/androidTest/   数据与界面集成测试
gradle/              Wrapper 与依赖版本目录
scripts/             环境、构建、诊断与验收入口
.github/workflows/   Android CI
docs/                开发指南、设计、规划与验证日志
```

Kotlin + Compose + Material 3，ViewModel → Repository → Room；Java 17，min SDK 26，compile/target SDK 36。依赖版本以 [版本目录](gradle/libs.versions.toml) 为准。本地 SDK、IDE 配置和缓存放在被忽略的 `.tools/`。

Debug 包名为 `com.seen.film.debug`，APK 为 `app/build/outputs/apk/debug/app-debug.apk`。请保留承载真实记录的实例；签名不一致时不要通过卸载或清数据处理。

## 文档

- [开发指南](docs/development.md)：环境配置、构建、调试、测试与日志位置。
- [产品与设计](docs/design-review.md) · [UI 示意](docs/ui-review.svg)：现行交互与视觉约定。
- [开发规划](docs/roadmap.md)：版本范围与验收待办。
- [验证日志](docs/v0.1.0-validation.md)：历史执行证据与未验证事项。
- [版本与发布](docs/versioning.md) · [变更记录](CHANGELOG.md)：交付规则与版本变化。
