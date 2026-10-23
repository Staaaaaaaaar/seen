# Seen 开发约定

先读 README.md、docs/development.md、docs/roadmap.md 与当前版本验证日志。只实现当前授权版本；v0.1 是离线电影库，联网资料与备份分别属于 v0.2、v0.3。

- 单 app 模块，Kotlin/Compose，UI → ViewModel → Repository → Room。保持手动依赖注入，不为小功能新增框架。
- 依赖集中在 gradle/libs.versions.toml；使用仓库 Gradle Wrapper、JDK 17，SDK 36 / Build Tools 35.0.0。
- 环境配置与安装、构建、设备运行分开。标准 Studio / Gradle 流程始终可用；脚本不得静默接受许可、换依赖版本、清缓存、卸载应用或清数据。
- 不提交本机路径、代理、SDK、密钥、数据库、真实观影记录和构建产物。
- 数据结构修改必须显式迁移并保留历史 Room schema；不得 destructive migration。电影与观看是不同实体，主页按电影去重，时间视图保留每次观看。
- 保存失败保留输入；null 评分/时间有独立含义；输入上限按 Unicode 码点检查。
- 业务规则改动运行 JVM 测试；持久化/媒体/交互改动运行 API 36 集成测试。交付前运行 bash scripts/check.sh 与设备检查；配置检查不等于编译/运行成功。
- 设备测试仅使用隔离数据；签名冲突应选择隔离模拟器或匹配签名，不卸载真实用户实例。
- 验证日志区分实际执行、人工观察与待验收，保留命令、源码/APK 哈希及报告位置。无法运行的项目说明阻碍，不标记通过。
- 提交前 git diff --check；版本交付遵循 docs/versioning.md。用户已授权 v0.1 起创建 tag，v1.0 再创建正式 Release。
