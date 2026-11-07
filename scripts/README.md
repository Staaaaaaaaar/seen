# 手动验证约定

界面修改后先由用户确认。代理不得自行运行构建、check、测试或安装；以下命令仅供用户明确选择后手动执行，不属于编辑步骤。

- 日常 UI 调整：`ANDROID_SERIAL=隔离设备 bash scripts/test-manual.sh ui`，只跑筛选/排序、添加入口与统计的关键交互；Gradle 自动构建所需 APK，不另跑 assemble。
- 业务规则：`bash scripts/test-manual.sh unit`，仅 JVM。
- 发布或用户要求完整验收：`ANDROID_SERIAL=隔离设备 bash scripts/test-manual.sh full`，构建/JVM/lint 一次、设备套件一次；不再叠加 UI 子集或截图套件。
- 截图、大字号、崩溃恢复专项按需单独选择，不作为每轮 UI 修改的重复门槛。报告、收据和“未执行”须区分；不要在用户正在存放个人记录的模拟器上运行集成测试。

以下保留原有脚本作为手动工具。

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
