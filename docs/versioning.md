# 版本与发布约定

从 **v0.1.0 起发布 GitHub annotated tag**，**v1.0.0 正式发布 GitHub Release**。用户已授权本轮最终检查、整理、commit、push 与 v0.1.0 tag。开发分版见 [开发规划](roadmap.md)。

目标仓库：`git@github.com:Staaaaaaaaar/seen.git`。

| 版本 | 交付 |
| --- | --- |
| v0.1.0 | 经验证的离线 MVP 提交、annotated tag、Debug APK 与验证记录；不创建手工 GitHub Release |
| v0.2.0 | 同上，增加 TMDB 资料能力 |
| v0.3.0 | 同上，增加备份恢复和迁移质量 |
| v1.0.0 | 真机回归完成的签名 Release APK、校验和、CHANGELOG、tag、正式 GitHub Release |

每版源码、构建结果和人工验收完成后才打 tag。不能把版本号写进 Gradle 就视作该版已发布。当前本地构建、单测、lint、模拟器自动化、Studio Sync 与 Preview 已验证。交付顺序：提交并推送最终源码 → 确认该提交的远端 CI 的构建与 API 36 测试全部成功 → 创建并推送 annotated tag → 核对远端 tag 与对应提交。

## v0.1 tag 前检查

- [ ] 有效、可写的 Git 仓库，取得本次提交/推送/tag 授权后核对 `origin` 和远端已有历史；不能在未知远端上强推或创建无关历史。
- [ ] 最终源码通过 `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug`，同一提交的 GitHub CI 成功。
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
git tag -a v0.1.0 -m 'Seen v0.1.0 — offline movie library'
git push origin refs/tags/v0.1.0
git ls-remote origin refs/tags/v0.1.0 'refs/tags/v0.1.0^{}'
```

不得使用 `--force` 更新已发布 tag。tag 触发 CI，Debug APK 从 Actions artifact 获取或从本机构建交付。GitHub 可能显示自动生成的 tag/source 页面，它不同于手工发布的正式 Release。

## v1.0 签名与正式 Release

在仓库外安全生成并备份长期签名 keystore，密码通过环境/CI secrets 提供，不放入文件跟踪或控制台日志。v1.0 阶段再配置 `signingConfigs.release`；目前 release 变体没有签名配置，不应当作正式安装包分发。

正式发布前须验证相同包名、相同签名的覆盖安装保留数据；调试包与正式包的跨包迁移走已验证的 JSON 备份恢复。确认后创建 tag，上传签名 APK、SHA-256、变更和升级说明。不得为了升级测试清除真实数据或卸载用户实例。
