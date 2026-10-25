# 版本与发布约定

用户已授权 v1.0.0 第一阶段收束、提交推送、annotated tag 与首个正式 GitHub Release。v0.x 仅创建标签；v1.0.0 起同时发布正式安装包。后续版本范围仍需明确授权。

## 版本与身份

- 版本号采用 SemVer，versionCode 单调递增；v1.0.0 为 7。标签 `v版本号` 必须与 Gradle versionName 相同。
- 正式包 `com.seen.film` 使用仓库外长期密钥；调试包 `com.seen.film.debug` 不能直接覆盖迁移到正式包，使用 JSON 导出/恢复。
- Room schema v1 和备份协议保持向后兼容；升级时不得 destructive migration 或通过卸载清数据绕过签名问题。
- 不提交本机路径、SDK、密码、凭据、密钥、数据库、真实记录和构建产物。签名、安装与升级操作见 [发布流程](release.md)。

## 交付顺序

1. 按职责整理 Conventional Commits，更新 CHANGELOG、当前说明、升级文档和验证日志。
2. 实际运行脚本回归、构建/JVM/lint、隔离 API 36、正式签名构建及升级检查；保留命令、源码/APK/证书摘要、报告及未验项。
3. `git diff --check`，核对暂存范围和历史 schema。验证期间使用基线 commit 的收据不得冒充最终提交；可在工作区干净且源码/APK 摘要不变时显式绑定最终 commit，保留原验证时间与原 commit。
4. 推送最终提交，核对对应 head SHA 的基础 CI 成功；本地设备与 Release 证据仍是独立门槛。
5. 创建 annotated tag 并推送，核对远端 peeled commit 等于已验证 HEAD；不强推、不移动已发布标签。
6. 对该标签创建正式 GitHub Release，附签名 APK、SHA256SUMS、公开证书指纹、更新与迁移说明；核对上传资产及下载摘要。密钥、密码和私有数据不得作为附件。

```bash
git status --short --branch
git diff --check
git push origin main
gh run list --workflow android.yml --branch main --limit 5
# 选择 headSha 等于 git rev-parse HEAD 的 RUN_ID
gh run watch RUN_ID --exit-status
git tag -a v1.0.0 -m 'Seen v1.0.0 — first stable release'
git push origin refs/tags/v1.0.0
git ls-remote origin refs/tags/v1.0.0 'refs/tags/v1.0.0^{}'
# 按发布流程准备资产与说明后，再执行 gh release create --verify-tag
```

## 历史例外

v0.2.1 曾由用户明确要求不等待 CI 即打标签，属于该补丁的单次例外，不适用于当前版本。历史版本实际执行与限制保留在各版本验证日志中，不改写为当前验收成功。
