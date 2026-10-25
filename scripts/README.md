# 开发脚本

标准 Android Studio / Gradle 流程始终可用。下列辅助脚本不自动接受许可、清缓存、卸载应用、清用户数据或升级依赖。

| 入口 | 用途 |
| --- | --- |
| `env.sh` / `doctor.sh` | 路径解析 / 只读工具链预检 |
| `setup.sh` / `install_android_sdk.sh` | 本机配置 / 显式下载 SDK，许可由使用者确认 |
| `check.sh` / `check.sh --device` | 构建、JVM、lint / 隔离 API 36 集成验收与收据 |
| `emulator.sh` / `run.sh` | 项目模拟器 / 显式设备覆盖安装启动 |
| `release.sh` | 环境变量提供正式密钥，构建签名 APK、校验证书并生成摘要；不发布 |
| `verification.py` / `check_project.py` | 从实际报告生成摘要 / 静态配置与文档检查 |
| `verify-backup-crash.py` | 隔离合成备份的外部 SIGKILL 专项 |
| `emulator-network.sh` | 显式启用/恢复项目模拟器的可选联网辅助 |
| `studio.sh` / `configure_studio.py` | 可选 Studio 启动与配置 |
| `diagnose-dependencies.sh` / `*.init.gradle` | 依赖诊断及显式 IDE 制品校验维护 |
| `git_preflight.py` | Git/远端只读预检，不提交或发布 |
| `history/` | 保留旧 v0.1 验收和 v0.3 升级脚本；依赖当时源码/界面，不用于当前验收 |

环境与设备约束见 [开发指南](../docs/development.md)，正式交付见 [发布流程](../docs/release.md)。
