# 统一影库改版验证记录

2026-11-07，本轮在隔离工作树开发，功能代码最终提交 f903851；版本暂沿用 1.1.0 / 8，不代表新发行。没有合并主工作区、推送、标签或 Release。

## 实现范围

影库/统计导航、紧凑列表与筛选排序；独立电影评分、观看与普通留言；海报详情、演职人员、中文离线字体；数量/月频率/类型统计；TMDB zh-CN / en-US 资料和默认海报统一准备发布；Room v3、备份 v2 与旧数据兼容。未保存 TMDB 建片草稿需先完成再更新语言；个人记录草稿不受此限制。

## 最终实际执行

所有命令在 JDK 17、SDK 36 / Build Tools 35.0.0 的开发环境运行；设备为隔离 Seen_Acceptance_API36、emulator-5558。没有对真实用户实例卸载、清数据或运行测试。

| 命令 | 结果 |
| --- | --- |
| `python3 -m unittest discover -s scripts/tests -v` | 13 项通过 |
| `bash scripts/check.sh` | App/测试 APK 编译成功，35 项 JVM，lint 0 errors / 16 warnings |
| `ANDROID_SERIAL=emulator-5558 bash scripts/check.sh --device` | 72 项通过，0 失败、0 跳过 |
| `ANDROID_SERIAL=emulator-5558 python3 scripts/verify-backup-crash.py` | media-ready、sql-deleted、committed 真正 SIGKILL 后记录与海报哈希核对通过 |
| `ANDROID_SERIAL=emulator-5558 python3 scripts/verify-metadata-crash.py` | preparing、media-ready、committed 真正 SIGKILL 后资料代次、海报与个人内容核对通过 |
| `git diff --check` | 通过；历史 schema 1/2 与基线无差异 |

构建时间：2026-11-07T13:15:43.425136+00:00。设备时间：2026-11-07T13:17:18.982499+00:00。两份收据均关联功能代码 f903851，源码/APK 摘要一致：

- Source SHA-256：`d27c454498d6389f16a60ccd4dcabd4be4b8d7fbf6dc8bcf3d2c7ba476f3f44d`。
- Debug APK SHA-256：`0aef36517883a23a5e433dc5a11da3dc2f960fc0f773529cbd310d094f802099`。

报告：`app/build/reports/tests/testDebugUnitTest/`、`app/build/reports/androidTests/connected/`、`app/build/reports/lint-results-debug.html`。收据：`.tools/check-result.json`、`.tools/device-result.json`；SIGKILL 证据为 `.tools/v0.3.0/crash/`、`.tools/metadata-refresh-crash/`，包括本轮 source/App APK/test APK 摘要。日志在 `/tmp/seen-final-build3.log`、`/tmp/seen-final-device3.log` 和 `/tmp/seen-final-audit.log`。最终文档提交不改变上述构建输入或验证时间。

## 新增回归及独立审阅

覆盖旧评分最新 null、历史迁移、普通留言不计数、独立评分、删除最后观看、只添加电影、混合记录日期时间地点、保留草稿、TMDB 详细资料/去重、备份 v1/v2、语言失败/取消/中断/统一发布和并发个人写入。语言完成后备份恢复保留影片 ID、观看、评分与普通留言，并在 Activity 重建后核对。

独立只读整分支审阅提出四项 Important 和一项 Minor，均先复现再修复：旧语言草稿插入；已提交建片草稿恢复误编辑；短多行文字不能展开；短屏排序不可达；首次 AtomicFile .new 中断重试。定向 24 项 API 36 回归通过，复审代码层面通过。横屏首屏片名不可见另经实际截图与失败断言复现，改为海报/文字并排，独立复审通过。

初次全量 lint 报记录编辑页直接读取 StateFlow，已改为订阅；初次修复后全量发现旧英文草稿夹具缺少当前英文语言状态，已补足。以上失败不计为最终通过；最终门槛是上述 35 JVM / 72 API36。

## 视觉实际检查

使用 `VisualReviewTest` 合成电影/海报/演员/记录，分别在系统深色、浅色、320dp 窄屏且 200% 字号、手机横屏运行完整截图流程，4 次各 1 项通过。主代理读取实际截图检查：列表末项可滚动访问、海报渐变无接缝、时间线日期起点一致且观看符号靠右、长时间线定位避让工具栏、横屏首屏片名可见、记录键盘输入可用、统计与语言设置可阅读。排序面板末项横屏点击有单独回归；半星与清除评分、图表每月访问具有语义断言。

临时截图：`/tmp/seen-acceptance-review-dark/`、`/tmp/seen-acceptance-review-light/`、`/tmp/seen-acceptance-review-large/`、`/tmp/seen-acceptance-review-landscape/`，每组含 library、detail-hero、detail-records、editor-keyboard、statistics、language。它们是合成设备画面，不是用户数据或真实 TMDB 海报，不提交仓库。

## 实现取舍与代价

- 使用独立工作树，主工作区不改动；代价是主目录仍为原版，整合另行处理。
- 备份新增实体保留明确行数与媒体上限，超限报错不丢内容；代价是超大库无法在现有限额内导出。
- 目标资料在受限内存准备，图片与日志持久化，中断后舍弃准备而非续传；代价是重试需要重新下载。
- 电影与语言由同一事务快照供给界面；代价是更新时重新读取聚合数据，换取一致性。
- 建片编辑器提前随影库落地，旧编辑器仅服务历史未完成草稿；代价是暂时保留两条兼容 UI 路径。
- 添加影片与继续个人草稿分别控制；代价是需要分别维护入口状态，保证准备期间个人记录可用。
- 待完成 TMDB 建片草稿须先处理，Repository 再校验语言；代价是语言更新多一个前置步骤，避免带回旧语言。
- 已提交建片草稿恢复为只读核对，显式进入详情后结束；代价是不顺便新增修改影片资料功能，恢复文字仍可阅读与复制。
- 横屏详情采用海报/片名并排，竖屏保留大海报；代价是两个方向的构图不同，首屏信息和海报比例均可保留。

独立审阅没有遗留的延后 Minor。审阅未判断的真实联网、签名升级、真机读屏等项目保留在下方待验收范围；全量执行与截图、摘要已由主代理实际核对。

## 验收边界

固定 TMDB 响应不等于真实账号、网络、翻译或 CDN 验收。真实手机、正式签名 APK 覆盖升级、真机 TalkBack 人工体验、Studio Preview 未在本轮完成，不标记通过；外观最终主观验收仍由用户完成。没有新增依赖、安装运行时字体或变更正式签名。

工作树 `gradlew.bat` 因历史 CRLF blob 与 text 属性显示 EOL 差异，实际字节 SHA-256 与 HEAD 完全一致，未纳入改动或提交；不为清洁状态擅自改行尾策略。功能和文档外没有其它用户文件改动。
