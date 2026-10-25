# 版本与发布约定

用户已于 2026-10-25 查看 v0.4.0 模拟器并授权整理仓库、commit、push 与 annotated tag。本版遵循默认 CI 门槛；实际状态以远端记录为准。

从 **v0.1.0 起发布 GitHub annotated tag**，**v1.0.0 正式发布 GitHub Release**。v0.3.0 的真机反馈仅作为该版历史记录，不代替 v0.4.0 真机验收。v0.2.1 的不等待 CI 例外仅适用于该补丁。开发分版见 [开发规划](roadmap.md)。

目标仓库：`git@github.com:Staaaaaaaaar/seen.git`。

| 版本 | 交付 |
| --- | --- |
| v0.1.0 | 经验证的离线 MVP 提交、annotated tag、Debug APK 与验证记录；不创建手工 GitHub Release |
| v0.2.0 | 同上，增加 TMDB 资料能力 |
| v0.2.1 | TMDB 可达性维护补丁，优先新域名、受限回退、分类网络错误；annotated tag 与本地 Debug APK |
| v0.3.0 | 同上，增加备份恢复和迁移质量 |
| v0.4.0 | 底部入口与海报电影库、记录流程和视觉调整；annotated tag、Debug APK 与验证记录 |
| v1.0.0 | 真机回归完成的签名 Release APK、校验和、CHANGELOG、tag、正式 GitHub Release |

每版源码、构建结果和人工验收完成后才打 tag。不能把版本号写进 Gradle 就视作该版已发布。各版本实际验收范围见对应验证日志；v0.2 使用独立 API 36 模拟器验收，真机与本轮 Studio Preview 未测，不沿用 v0.1 的人工观察作为新版本证明。交付顺序：提交并推送最终源码 → 确认该提交的远端构建/单测/lint CI 成功，并核对本地 API 36 设备验收记录 → 创建并推送 annotated tag → 核对远端 tag 与对应提交。

## v0.2.1 本次交付例外

用户明确要求“commit，然后推送远程，并打上 tag。不必等 CI 检查”。本次保留本地构建、单测、lint、API 36 及摘要核对，提交/推送后直接创建并推送 annotated tag，不阻塞等待远端 CI。CI 仍按既有工作流自动触发，未查看或未完成不能写为通过；此例外不改变其他版本默认门槛。v0.3 备份协议草稿不混入维护补丁。

## v0.x tag 前检查

- [ ] 有效、可写的 Git 仓库，取得本次提交/推送/tag 授权后核对 `origin` 和远端已有历史；不能在未知远端上强推或创建无关历史。
- [ ] 最终源码通过 `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug`，同一提交的 GitHub 基础 CI 成功（不运行云模拟器）。
- [ ] API 36 模拟器 `connectedDebugAndroidTest` 通过。用户已选择先用模拟器验证，Redmi 真机验收后续另记；未测项目不标为已通过。
- [ ] Room version 1 schema、标准 Gradle Wrapper、Gradle 生成的依赖锁文件与校验元数据已审查并提交。
- [ ] README、CHANGELOG 和实际验证记录同步；APK 路径/校验和可复查。
- [ ] 没有凭据、签名密钥、备份、真实观影数据和构建产物入库。
- [ ] 提交已在远端核对的工作分支上，工作区干净，tag 尚不存在。

在已核对远端历史且完成本地验收的仓库执行；先推送提交并检查 CI，再打 tag：

```bash
git remote -v
git fetch origin --tags
git diff --check
# 审查并提交本版文件后，推送当前 main：
git push origin main
# 从列表选择 headSha 与 git rev-parse HEAD 一致的 workflow run：
gh run list --workflow android.yml --branch main --limit 5
# RUN_ID 替换成上述运行 ID；失败时先修复，不执行后面的 tag 命令。
gh run watch RUN_ID --exit-status
git status --short --branch
git tag -a v0.4.0 -m 'Seen v0.4.0 — poster library and bottom navigation'
git push origin refs/tags/v0.4.0
git ls-remote origin refs/tags/v0.4.0 'refs/tags/v0.4.0^{}'
```

不得使用 `--force` 更新已发布 tag。tag 触发 CI，Debug APK 从 Actions artifact 获取或从本机构建交付。GitHub 可能显示自动生成的 tag/source 页面，它不同于手工发布的正式 Release。

## v1.0 签名与正式 Release

在仓库外安全生成并备份长期签名 keystore，密码通过环境/CI secrets 提供，不放入文件跟踪或控制台日志。v1.0 阶段再配置 `signingConfigs.release`；目前 release 变体没有签名配置，不应当作正式安装包分发。

正式发布前须验证相同包名、相同签名的覆盖安装保留数据；调试包与正式包的跨包迁移走已验证的 JSON 备份恢复。确认后创建 tag，上传签名 APK、SHA-256、变更和升级说明。不得为了升级测试清除真实数据或卸载用户实例。
