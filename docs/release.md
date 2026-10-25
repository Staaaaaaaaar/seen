# 正式安装包与维护发布

v1.0.0 是第一阶段的首个正式 Release。应用使用 `com.seen.film`，数据保存在本机；长期签名证书与每个 APK 的 SHA-256 随 Release 公布。无需 TMDB 凭据也能手动记录，配置凭据后可选获取联网资料。

## 安装与调试版迁移

1. 从仓库 GitHub Releases 下载 `seen-v1.0.0.apk` 与 `SHA256SUMS`，核对 SHA-256 后安装。
2. 已使用 `com.seen.film.debug` 调试版时，先在原应用“设置 → 导出数据”，保存 JSON 备份；未完成编辑先保存或放弃。
3. 安装正式版后，“设置 → 导入备份”选择 JSON，核对预检统计，再确认恢复。恢复会替换正式版当前库，不合并。TMDB 凭据不在备份中，需重新配置。
4. 核对电影、观看历史及海报，保留原调试版和备份，确认迁移完整后再自行决定是否删除旧实例。正式版与调试版可并存，跨包名不能靠覆盖安装迁移。

之后的正式版用相同包名、长期签名和更高 versionCode 覆盖安装。签名不匹配时停止安装并核对来源，不通过卸载解决。删除电影会连同全部观看记录删除；建议维护可靠的本地备份。

## 维护者签名构建

密钥和密码位于仓库外受限目录，应独立备份；不能放入 Git、Release 附件或日志。Gradle 从环境读取：

| 环境变量 | 内容 |
| --- | --- |
| `SEEN_KEYSTORE_FILE` | 长期 keystore 的绝对路径 |
| `SEEN_KEYSTORE_PASSWORD` | keystore 密码 |
| `SEEN_KEY_ALIAS` | 签名别名 |
| `SEEN_KEY_PASSWORD` | 别名密码 |

在安全的本机终端显式加载私有环境文件后执行：

```bash
source /absolute/path/outside-repo/signing.env
bash scripts/release.sh
```

标准 Gradle 入口为 `./gradlew :app:assembleRelease :app:lintRelease`；无签名环境时只生成 unsigned 包，不能发布。`release.sh` 要求完整凭据，验证签名属于指定密钥、包名/版本正确且不可调试，产出 `.tools/releases/版本号/`：签名 APK、`SHA256SUMS`、`SIGNING-CERT-SHA256.txt`、`release-result.json` 和签名验证输出。不会生成密钥或回退到调试签名。

## 发布门槛

- 更新版本、CHANGELOG、README、当前验证日志；不扩展本阶段之外的功能。
- 运行 `python3 -m unittest discover -s scripts/tests -q`、`bash scripts/check.sh` 和隔离 API 36 的 `check.sh --device`。
- 运行签名构建；在隔离 API 36 设备验证实际 Release 安装启动和同包名/同签名覆盖升级，记录两端 APK、证书、源码摘要及数据保留结果。调试版迁移与真机验收分别记录，未执行不得写成通过。
- `git diff --check`，核对没有密钥、凭据、SDK、数据库、真实记录、APK 或构建目录入库。
- 分职责提交推送，确认最终 SHA 的基础 CI 通过，再创建并推送 annotated tag。发布前再次核对 tag 指向与收据源码摘要。
- 用 `gh release create TAG --verify-tag --title TITLE --notes-file NOTES APK SHA256SUMS SIGNING-CERT-SHA256.txt` 发布正式 Release；再次查询资产并下载校验 APK 字节。不上传 keystore 或私有环境文件，不覆盖已发布标签。

CI 仍为单 job：脚本、Debug 构建/JVM/lint，加上无私人密钥的 Release 编译与 lint；真实签名构建和设备验收保留在本地。验收范围见 [v1.0 日志](v1.0.0-validation.md)，发布顺序见 [版本约定](versioning.md)。
