# Seen Unified Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Seen 改造成影库与统计两栏的本地电影档案，支持独立评分、观看与普通留言、完整 TMDB 详情和资料语言原子切换。

**Architecture:** 保留单 app、Compose → ViewModel → Repository → Room 和手动注入。Room v3 承载独立评分、普通留言、类型/演职人员及生效语言；语言准备在私有暂存区完成，最终通过一个 Room 事务更新资料列和图片引用。先交付可验证的数据契约，再接入 UI；各任务是同一升级的依赖环节，不作为独立版本发布。

**Tech Stack:** Kotlin 2.3.21、Compose BOM 2025.10.01、Material 3、Room 2.8.5、协程 1.10.2、现有 HttpURLConnection/AtomicFile，无新增网络、DI、图表框架。

**Spec:** [已确认设计](../specs/2026-11-07-library-redesign-design.md)。先读该文件、README、docs/development.md、docs/roadmap.md 和 docs/v1.1.0-validation.md。

## Global Constraints

- 单 app、Kotlin/Compose、UI → ViewModel → Repository → Room、手动依赖注入。
- JDK 17、SDK 36、Build Tools 35.0.0、仓库 Gradle Wrapper；依赖集中 gradle/libs.versions.toml。
- 显式迁移并保留 v1/v2 历史 schema；禁止 destructive migration。
- 0.5–5 星、半星精度；null 表示未评分。观看正文可空，普通留言正文必填。
- 地点 200、正文 50,000 Unicode 码点；保存失败保留输入。时间 null 不能转为午夜。
- 影库/统计两栏；设置右上角，影库搜索紧邻设置，右下悬浮添加。
- 普通留言和评分不决定是否已看；没有观看记录就是未看。
- 统计仅数量、月频率、类型；无回顾、推荐、分组和社交。
- TMDB 仅默认海报，无选图器；中文/英文准备完成后统一切换。
- 不提交本机路径、凭据、数据库、真实记录、构建产物或临时原型。
- 当前 app 仍为 1.1.0 / 8；本计划不擅自选新版本号、推送、打标签或发布。

## Review Focus

1. 最新历史观看无评分而更早有评分：迁移后仍未评分，旧评分原值不丢失（任务 1/3）。
2. 语言准备时同时保存留言/评分、导出与尝试新增电影：允许个人写入和一致导出，拒绝目录变更；发布不覆盖个人内容（任务 5）。
3. 同一电影同日有缺时间、重复姓名/不同角色、无类型/无海报：稳定排序、不误去重、不伪造数据（任务 2/4/7/8）。
4. 进程在准备、媒体落盘或提交附近退出：只看到完整旧版或新版，无新语言配旧海报（任务 5/9）。
5. 长中文片名、emoji、大字、旋转和键盘：码点限制正确，评分/FAB 不遮挡操作，日期保持对齐（任务 2/6/7/9）。

---

## File Structure and Decisions

现有 `Database.kt` 保留 Room 入口与轻量实体；扩展资料表单独放 `MovieMetadata.kt`。`SeenViewModel.kt` 继续做页面协调，不拆成新的架构或模块；纯规则移到 domain。过渡期间保留旧 API 适配器，直到调用方在任务 6/7 切换完，任何任务结束时都必须可编译。一次升级按依赖顺序执行，不能只发布中间迁移。

| 文件 | 职责 |
| --- | --- |
| `data/Database.kt`, `data/Migrations.kt` | v3 实体、DAO、v2→v3 迁移；保留 v1→v2 |
| `data/MovieMetadata.kt` | `MovieGenre`、`MovieCredit`、`ArchivedPoster`、`LibraryMetadataState` |
| `domain/RecordDraft.kt`, `domain/RecordRules.kt` | 独立留言/观看草稿和纯规则 |
| `domain/Library.kt`, `domain/Statistics.kt` | 筛选、搜索、排序、时间线与统计 |
| `data/LibraryPreferences.kt` | 本机筛选/排序偏好；不存生效资料语言 |
| `data/MetadataRefreshCoordinator.kt` | 准备、取消、进度、发布与启动清理 |
| `ui/LibraryControls.kt`, `ui/SeenNavigation.kt` | 筛选/排序面板与两项底栏 |
| `ui/RecordEditorScreen.kt`, `ui/StatisticsScreen.kt` | 记录编辑与轻量统计 |
| `ui/SeenIcons.kt`, `ui/Theme.kt` | 统一图标、浅深色、字体、间距与字号 |

以上新增路径均相对 `app/src/main/java/com/seen/film/`。测试路径在各任务明确。字体放 `app/src/main/res/font/`，可再分发许可放 `docs/licenses/`。删除 `WatchlistScreen.kt` 必须等调用和测试迁移完成。

### Task 1: 数据结构与无损升级

**Files:** Modify `data/Database.kt`, `SeenApplication.kt`, `app/src/androidTest/java/com/seen/film/SeenTestRunner.kt`; create `data/MovieMetadata.kt`, `data/Migrations.kt`, `app/src/androidTest/java/com/seen/film/data/LibraryMigrationTest.kt`; generate `app/schemas/com.seen.film.data.SeenDatabase/3.json`。

**Interfaces:** `Movie.personalRatingHalfStars: Int?`, `Movie.overview: String?`, `Movie.metadataLanguage: String?`; `MovieHistory.viewings`, `.notes: List<MovieNote>`, `.genres: List<MovieGenre>`, `.credits: List<MovieCredit>`。`MovieNote(id,movieId,recordedDate,recordedTime?,location?,body,createdAt,updatedAt)`；`MovieGenre(movieId,genreId,name,position)` 主键 `(movieId,genreId)`；`MovieCredit(movieId,creditKey,personId,name,isCast,department?,job?,character?,profilePath?,position)` 主键 `(movieId,creditKey)`。`ArchivedPoster(movieId,posterKey)` 保留升级前图片。`LibraryMetadataState(id=1,activeLanguage="zh-CN",generation=0L)` 与电影资料在同一 DB。

- [ ] **Step 1 — 写迁移失败用例。** 从历史 JSON 实际建 v1/v2 库：旧电影两次观看分别评分 8、null；另一电影最新评分 7；含旧 wantToWatch、空 notes、图片引用。断言 `assertNull(first.personalRatingHalfStars)`、`assertEquals(7, second.personalRatingHalfStars)`、全部旧记录/评分/ID/createdAt/wantToWatch/图片原样保留，v1 可经 v2 升级；所有新关联为空。
- [ ] **Step 2 — 确认红灯。** `source scripts/env.sh` 后编译测试 APK，并在隔离 API 36 运行 `LibraryMigrationTest`；预期因 v3/新字段或迁移不存在失败，而非忽略运行器错误。
- [ ] **Step 3 — 实现结构与迁移。** v2→v3 用 SQL 按原 `viewingOrder` 取最新评分（包括 null），为每个现有非空 posterKey 建归档引用；不重写旧 Viewing 或 wantToWatch。独立普通留言表以电影外键关联；扩展 DAO 的快照/清空顺序包括所有子表。应用和测试 Application 都注册完整迁移链。
- [ ] **Step 4 — 验证。** 同一迁移类全部通过；`:app:assembleDebug :app:assembleDebugAndroidTest` 成功；比较旧 schema 文件无改动，v3 由 KSP 生成而非手写。
- [ ] **Step 5 — 本地提交。** 审查暂存范围和 `git diff --check`，提交 `feat: migrate library data for independent ratings and notes`；不推送。

### Task 2: 电影、评分与两种记录的独立保存

**Files:** Create `domain/RecordDraft.kt`, `domain/RecordRules.kt`; modify `domain/ViewingDraft.kt`, `data/SeenRepository.kt`, `data/DraftStore.kt`; create `app/src/test/java/com/seen/film/domain/RecordRulesTest.kt`, `app/src/androidTest/java/com/seen/film/data/LibraryRepositoryTest.kt`; adapt `app/src/test/java/com/seen/film/domain/ViewingRulesTest.kt`。

**Interfaces:** `enum RecordKind { VIEWING, NOTE }`; `RecordDraft(key,movieId,kind,isEditing,date,time,location,body)`。`SeenRepository.addMovie(draft: ViewingDraft): String`, `setPersonalRating(movieId: String, halfStars: Int?): Unit`, `saveRecord(draft: RecordDraft): String`, `deleteRecord(kind: RecordKind,id: String): Unit`。`DraftStore.readRecord(): RecordDraft?`, `writeRecord(draft)`, `clearRecord()`；现有建片草稿仍用原 read/write 方法。新草稿协议 v2，有明确 `kind`。

- [ ] **Step 1 — 写失败用例。** `RecordRulesTest` 验证 `VIEWING` 正文空可保存、`NOTE` 空白失败、时间清空为 null、未来日期拒绝、200/201 个 emoji 地点与 50,000/50,001 码点正文的边界。设备断言：只建片 `viewings.size == 0`；普通留言不变；评分 9 与 null 可独立写；删最后观看仍保留电影/评分/普通留言；重复 TMDB ID 返回原 ID；写入失败草稿不丢。
- [ ] **Step 2 — 确认红灯。** `./gradlew :app:testDebugUnitTest --tests '*RecordRulesTest'`；随后运行隔离设备 `LibraryRepositoryTest`，预期缺新 API。
- [ ] **Step 3 — 实现。** 建片只操作 Movie，不维护待看标记；`saveRecord` 按 kind 写 Viewing 或 MovieNote，评分不写回 Viewing。整片删除事务先删除全部子表与图片归档引用再删除 Movie。保留旧 `save(ViewingDraft)` 作过渡适配，不让旧调用新增独立评分副作用；最终在任务 7 删除未用适配。
- [ ] **Step 4 — 草稿兼容与绿灯。** 旧 `forWatchlist=true` 草稿转建片流程；旧观看草稿原样保留未提交评分，并在完成该次旧草稿保存时一次性初始化电影评分，后续新记录不携带评分。旧草稿 key 已提交则恢复成编辑，不重复创建。上述 JVM/设备测试全部通过，测试旧草稿损坏时不覆盖文件。
- [ ] **Step 5 — 提交。** `feat: decouple movie ratings from viewing and note records`。

### Task 3: 备份协议 v2 与历史恢复

**Files:** Modify `data/BackupCodec.kt`, `data/BackupRules.kt`, `data/BackupRepository.kt`, `docs/backup-format.md`; extend `app/src/test/java/com/seen/film/data/BackupRulesTest.kt`, `app/src/androidTest/java/com/seen/film/data/BackupTest.kt`, `BackupCrashTest.kt`。

**Interfaces:** 新 `BackupData` 包含 `movies,viewings,notes,genres,credits,archivedPosters,assets`；读取 schemaVersion 1/2，写入 2。Movie 承载独立评分及 metadataLanguage。`BackupRepository` 的 prepareExport/preview/restore 对外签名不变。

- [ ] **Step 1 — 写失败用例。** v2 往返精确保留评分 null、普通留言时间 null、扩展资料、归档图片；v1 最新评分 null 不被更早非空覆盖；不含 wantToWatch 的 v1 默认 false。新协议未知版本拒绝，重复留言 ID/越界类型 ID/失联图片拒绝；故障恢复数据库与文件回滚，现有凭证/偏好不变。
- [ ] **Step 2 — 确认红灯。** 运行 `BackupRulesTest` 与隔离设备 `BackupTest`，定位到格式/新实体断言失败。
- [ ] **Step 3 — 实现格式和恢复。** 按现有流式严格解析方式扩展，不一次把全部 JSON/图片载入内存。v1 先转换 DTO 再预检；复用任务 1 的旧评分选择规则。保留 100 MiB JSON、60 MiB JPEG 总量、2 MiB 单图、10,000 Movie、100,000 Viewing；新增 Note 上限 100,000。个人文本与扩展资料合计 UTF-8 沿用当前 `BackupRules.MAX_BYTES` 的 100 MiB 预检边界，最终整个 JSON 仍不得超过 100 MiB；超限拒绝而非截断，不能新设更低旧文本上限而拒绝原合法备份。归档图片计入同一图片预算。
- [ ] **Step 4 — 兼容验证。** 人员头像只存远程路径、不导出可再下载头像缓存，离线恢复显示姓名占位。运行 JVM/设备备份回归及独立 `scripts/verify-backup-crash.py`，验证新旧格式和任意中断无半库。
- [ ] **Step 5 — 提交。** `feat: preserve library notes and ratings in backup v2`。

### Task 4: TMDB 扩展资料与语言参数

**Files:** Modify `data/TmdbRepository.kt`, `SeenApplication.kt`, `app/src/androidTest/java/com/seen/film/SeenTestRunner.kt`; create `domain/MetadataLanguage.kt`, `app/src/androidTest/java/com/seen/film/data/TmdbMetadataTest.kt`; extend `TmdbRepositoryTest.kt`, `TmdbPosterTest.kt`。

**Interfaces:** `MetadataLanguage { ZH_CN, EN_US }` 含 `apiCode`、`displayName`；`TmdbRepository.search(query: String,page: Int=1,language: MetadataLanguage): TmdbPage`, `details(id: Long,language: MetadataLanguage): TmdbMovie`。`TmdbMovie` 增加 `overview: String?`, `genres: List<TmdbGenre>`, `credits: List<TmdbCredit>`；DTO 不含本地 movieId，持久化时绑定。缺省语言适配旧调用至任务 7。

- [ ] **Step 1 — 写失败用例。** 固定中英响应/请求捕获：详情使用 `append_to_response=credits`；language 分别 `zh-CN`、`en-US`；保存 genre ID；同人不同角色/职务不被 personId 去重；crew 中 director 提取正确；poster_path=null、缺简介、空头像合法；取消不重试，401/429/TLS 不走地址回退。
- [ ] **Step 2 — 确认红灯。** 编译并运行 `TmdbMetadataTest` 和原 TMDB 单测，预期新增参数/DTO 断言失败。
- [ ] **Step 3 — 实现。** 采用 API 返回的默认 poster_path，不调用多海报选择接口。字段有界解析：单片 credits 最多 1,000、genres 最多 100，超限响应拒绝；字符串沿用有界 JSON 2 MiB；图仍 20 MiB 输入上限，私有存储归一化。creditKey 优先官方 credit_id，缺失时使用角色/部门/职务/序号生成可重复键，不能合并不同角色。
- [ ] **Step 4 — 绿灯。** 更新隔离固定响应，跑 TMDB 相关 JVM 与设备测试；保留取消/地址切换/下载限制和凭证不入 URL 的原测试。
- [ ] **Step 5 — 提交。** `feat: fetch localized movie details and credits from TMDB`。

### Task 5: 可取消的资料准备与原子发布

**Files:** Create `data/MetadataRefreshCoordinator.kt`; modify `data/LibraryAccess.kt`, `data/SeenRepository.kt`, `data/PosterStore.kt`, `data/BackupRepository.kt`, `SeenApplication.kt`; create `app/src/androidTest/java/com/seen/film/data/MetadataRefreshTest.kt`, `MetadataRefreshCrashTest.kt`。

**Interfaces:** `LibraryWriteKind { PERSONAL, CATALOG }`；`LibraryAccess.write(kind: LibraryWriteKind=PERSONAL,block)`、`reserveMetadata(): Any`、`publishMetadata(token,block)`、`releaseMetadata(token)`。`MetadataRefreshState` 为 `Idle/Preparing(done,total,target)/Failed(message)/Interrupted/Completed`；`MetadataRefreshCoordinator.state: StateFlow<MetadataRefreshState>`, `suspend refresh(target: MetadataLanguage)`, `suspend cancel()`, `suspend recoverInterrupted()`。生效语言来自 DAO `observeMetadataState(): Flow<LibraryMetadataState>`，不另写 SharedPreferences 生效语言。

- [ ] **Step 1 — 写失败用例。** 两片更新第二片超时/401/坏图/磁盘不足时，断言旧标题、图片引用、activeLanguage、generation 全部不变。明确无图可提交占位。取消同样不发布。准备期间写评分/普通留言成功，新增/删除电影/恢复/第二次切换失败，导出只包含旧生效资料。
- [ ] **Step 2 — 确认红灯。** 设备运行 `MetadataRefreshTest`，预期缺 coordinator/API，而非使用真实网络。
- [ ] **Step 3 — 实现准备与提交。** 短暂持 gate 取得目标 TMDB ID 集合并预约目录锁；释放 gate 后逐部 IO 准备，不用全局 ViewModel busy。媒体用私有暂存目录，有效性/解码/写入成功后再记入清单。累计暂存（JSON+图片）上限 256 MiB，最多 10,000 电影；流式处理，超限明确失败。发布前把已完成文件持久化到最终私有路径，单事务只更新资料列、关联表、海报引用、activeLanguage 和 generation，不整行替换 Movie，不动个人评分/createdAt/记录/归档图片。
- [ ] **Step 4 — 故障与绿灯。** 明确三种退出点：准备中、最终文件写好 DB 未提交、DB 提交后清理前。启动扫描 journal/引用，只清理本次无引用准备文件；不删除归档/草稿/导出占用图片。旧图只有确认所有引用消失才回收。用外部进程控制的隔离 crash 检查证明前两者旧版、后者新版，state 提示中断而不假装后台继续。资料 Flow 与语言在同一 DB 版本下读取。上述设备测试和备份回归通过。
- [ ] **Step 5 — 提交。** `feat: switch localized metadata and posters atomically`。

### Task 6: 影库规则与原生列表导航

**Files:** Modify `domain/Library.kt`, `ui/SeenApp.kt`, `ui/LibraryScreen.kt`, `ui/SettingsScreen.kt`, `ui/SeenViewModel.kt`, `ui/ChooseScreen.kt`; create `data/LibraryPreferences.kt`, `ui/LibraryControls.kt`, `ui/SeenNavigation.kt`, `ui/SeenIcons.kt`; remove `ui/WatchlistScreen.kt` after all references disappear; create `app/src/test/java/com/seen/film/domain/LibraryRulesTest.kt`, `app/src/androidTest/java/com/seen/film/ui/LibraryJourneyTest.kt`; update `NavigationJourneyTest.kt`。

**Interfaces:** `LibraryFilter { ALL, SEEN, UNSEEN }`, `LibrarySort { ADDED,TITLE,YEAR,RATING,VIEWED }`, `SortDirection { ASC,DESC }`, `LibraryQuery(text,filter,sort,direction)`；`libraryMovies(all: List<MovieHistory>,query: LibraryQuery): List<MovieHistory>`。`LibraryPreferences.read(): LibraryQuery`（text 恒空）、`suspend save(query)`。现有 Screen 继续消费 ViewModel，不引入导航框架。

- [ ] **Step 1 — 写失败用例。** `LibraryRulesTest`：无观看且有评分仍未看；有普通留言仍未看；空观看正文仍已看；五种排序双方向、null 永远最后、相同值稳定、搜索与筛选交集；添加顺序不因评分/留言改变。设备：仅影库/统计底栏、统计无搜索/FAB，设置入口在右侧；筛选与排序同一行，最后一项可滚到 FAB 上方。
- [ ] **Step 2 — 确认红灯。** `./gradlew :app:testDebugUnitTest --tests '*LibraryRulesTest'`，设备 `LibraryJourneyTest` 失败在新导航断言。
- [ ] **Step 3 — 实现规则与偏好。** 按 spec 的 comparator 规则；TITLE 用资料语言 Locale 的 Collator，在 IO/Default 线程计算较大集合；ID 作最后 tie-breaker。默认 ALL/ADDED/DESC，只持久化筛选与方向，搜索只存页面状态。ViewModel 提供已保存电影、建片保存和记录跳转，TMDB 重复命中进入原详情。
- [ ] **Step 4 — 实现列表并验证。** 纵向海报/片名/年份/评分，约 56×84dp 海报，片名 17sp、辅助至少 12sp；无评分横线及读屏文案，年份旁胶片次数。顶部 Seen+搜索+设置，无页名/总数。右下 FAB，轻量两项底栏，影库筛选行右侧排序底部面板。原型 `seen-library-v4.html` 为外观参考，数据和搜索来自真实 Room。完成旋转、返回、长名/大字、空结果的设备检查；所有旧待看导航测试改为新行为而非删除覆盖。
- [ ] **Step 5 — 提交。** `feat: unify the movie library with filters and sorting`。

### Task 7: 详情、独立评分与记录编辑

**Files:** Modify `ui/MovieScreen.kt`, `ui/EditorScreen.kt`, `ui/SeenViewModel.kt`, `ui/Components.kt`, `ui/ChooseScreen.kt`, `ui/Theme.kt`; create `ui/RecordEditorScreen.kt`, `ui/RatingControl.kt`, `ui/CreditsSection.kt`, `domain/Timeline.kt`; update `app/src/debug/java/com/seen/film/LibraryPreviews.kt`, `MovieJourneyTest.kt`, `DeleteMovieJourneyTest.kt`; create `app/src/test/java/com/seen/film/domain/TimelineTest.kt`, `app/src/androidTest/java/com/seen/film/ui/NotesJourneyTest.kt`。

**Interfaces:** `TimelineEntry(kind,id,date,time?,location?,body?,createdAt)`；`movieTimeline(history: MovieHistory): List<TimelineEntry>`。`RatingControl(value: Int?,enabled: Boolean,onChange: (Int?)->Unit)`；`RecordEditorScreen(vm,draft: RecordDraft,onBack,onSaved: (String)->Unit)`；`CreditsSection(credits: List<MovieCredit>)`。原 `EditorScreen` 只负责建片，记录走新编辑器。

- [ ] **Step 1 — 写失败用例。** Timeline 同日不同具体时间、null 时间、创建时间相同/不同 kind 的 ID 稳定；设备：普通留言不增加次数，时间和地点两类都可写；日期起点一致、胶片在右侧；半星评分/清除后重启保持，不改变观看；长片名、无简介、无图和多人多角色均可阅读。
- [ ] **Step 2 — 确认红灯。** `TimelineTest` 与 `NotesJourneyTest` 因缺模型/入口失败；保留保存失败与删除回滚的原测试。
- [ ] **Step 3 — 实现记录和评分。** 明确区分建片草稿/记录草稿，只在用户确认保存成功后清理。评分组件触控、半星选择、清空和读屏均可用；错误显示且保持旧评分。合并时间线使用原记录 ID+kind 作 Compose key。日期与具体时间同一行，地点在下，胶片在右。用册页图标替代标题，书写入口打开两种记录选择。
- [ ] **Step 4 — 实现视觉与资料。** 海报主体/下缘渐入、主资料与可展开简介、首批导演和前 6 演员，再展开其余演员/主要 crew（Director/Writer/Screenplay/Producer/Original Music Composer/Director of Photography）。人员姓名占位不依赖头像；有 profilePath 时使用限额 20 MiB 私有 LRU 头像缓存，沿用 HTTPS/图片输入与解码上限，下载失败退姓名占位，不阻止语言提交；头像缓存不导出。字体采用可再分发的 Noto Serif CJK SC 子集或等价许可字体，覆盖应用中文、英文、数字与常用标点，生僻字符用系统回退；提交许可说明与小型离线资源，不引入运行时下载。落实 #111C2B/#152235/#202F43/#E3EAF3/#A1B4CC/#BFD1E7，保留冷白浅色等价主题。跑设备交互，实际截图核对 200% 字号/窄屏/键盘/边到边与 48dp 触控。
- [ ] **Step 5 — 提交。** `feat: redesign movie details and personal records`。清除已无调用的待看和旧记录评分 UI 适配器，但保留历史兼容字段和旧备份读取。

### Task 8: 轻量统计与语言设置接入

**Files:** Create `domain/Statistics.kt`, `ui/StatisticsScreen.kt`, `ui/MetadataLanguageControls.kt`; modify `ui/SeenApp.kt`, `ui/SeenViewModel.kt`, `ui/SettingsScreen.kt`; create `app/src/test/java/com/seen/film/domain/StatisticsTest.kt`, `app/src/androidTest/java/com/seen/film/ui/StatisticsJourneyTest.kt`, `MetadataLanguageJourneyTest.kt`。

**Interfaces:** `StatisticsPeriod { THIS_YEAR,ALL }`；`LibraryStatistics(movieCount: Int,viewingCount: Int,months: List<MonthCount>,genres: List<GenreCount>,unknownGenreMovieCount: Int)`，`MonthCount(month: YearMonth,count: Int,isFuture: Boolean)`，`GenreCount(id: Long,name: String,count: Int)`；`libraryStatistics(all: List<MovieHistory>,period: StatisticsPeriod,today: LocalDate): LibraryStatistics`。元数据刷新 ViewModel 单独暴露 coordinator.state，与个人保存 busy 分离。

- [ ] **Step 1 — 写失败用例。** 同片两次观看在今年计一部/两次；跨年按观看日期分桶；普通留言/未看/评分不影响；同类型以稳定 ID 去重、多个类型分别计数；类型未知电影单列；空库零值、全库跨年连续月份、未来月 isFuture=true。设备：年份范围、前五类展开、进度/取消/失败旧资料、刷新不阻止留言、统计无搜索/FAB。
- [ ] **Step 2 — 确认红灯。** `./gradlew :app:testDebugUnitTest --tests '*StatisticsTest'` 与两类设备 Journey 测试。
- [ ] **Step 3 — 实现纯规则与图表。** 月份按 YearMonth 连续生成，ALL 从最早观看月到当前月（旧备份有未来观看时延伸到最晚月并明确未来状态），THIS_YEAR 为 1–12 月；无数据不伪造起始观看。Compose Canvas 自绘柱状、轻量布局画类型条，容器提供完整读屏摘要和按月访问，不引入图表库。类型前 5 数量倒序、名称 tie-breaker，可展开；未知提示单独显示。
- [ ] **Step 4 — 接入设置并绿灯。** 显示生效资料语言和目标准备进度。中文/English 只影响 TMDB，不切 App 文案；手动电影不联网重写。保存有效 token 后可用“更新电影资料”；无 token 清楚说明仍可手动使用。准备期禁用目录写入/恢复与二次刷新，不禁用阅读/留言/评分；取消、重试、进程中断和后台返回状态正确。原 TmdbSettingsTest/TmdbJourneyTest 适配新的搜索与语言参数后仍通过。
- [ ] **Step 5 — 提交。** `feat: add viewing statistics and localized metadata settings`。

### Task 9: 整体验证、文档与交付记录

**Files:** Update `README.md`, `docs/roadmap.md`, `docs/development.md`, `docs/backup-format.md`, `CHANGELOG.md`; create `docs/library-redesign-validation.md`; update relevant previews and tests plus `scripts/check_project.py` only where v3/文件结构确实需要。临时日志/截图留 `/tmp` 或已忽略 `.tools/`。

**Interfaces:** 全部功能使用任务 1–8 的生产实现；设备证据关联本轮 sourceSha256/apkSha256，任何失败或未验项明确列出。

- [ ] **Step 1 — 回归测试。** 补充整链 Journey：添加未看且无评分电影 → 普通留言 → 独立半星评分 → 观看 → 统计 → 语言准备期间编辑个人内容 → 统一切换 → 新格式备份恢复 → 重启。断言每一步电影 ID、观看次数、评分、内容和语言一致。旧 backup crash runner 在隔离设备也实际执行。
- [ ] **Step 2 — 本地门槛。** `source scripts/env.sh`，执行 `python3 -m unittest discover -s scripts/tests -v`、`bash scripts/check.sh`，必须真实 BUILD SUCCESSFUL、JVM 与 lint 通过；不能用配置检查替代。只在新变更或失败涉及依赖时运行额外依赖检查，不清缓存。
- [ ] **Step 3 — 隔离设备门槛。** `adb devices -l` 检查 serial/AVD，确认 API 36 与测试隔离后执行 `ANDROID_SERIAL=<实际隔离设备> bash scripts/check.sh --device`。设备签名冲突不卸载真实实例。API 36、浅深色、200% 字号、TalkBack、横屏和长内容实际留证；未完成项目写待验收。真实 TMDB 网络/凭证/CDN 和正式签名覆盖升级分别列出，不以固定响应冒充。
- [ ] **Step 4 — 更新文档与自审。** README/roadmap 改为本轮实际状态而非计划成功；验证日志列命令、报告与源码/APK 哈希，核对两份收据一致。检查无本地路径/密钥/个人数据/构建产物被暂存；`git diff --check`，检查 schema v1/v2 未改，v3 和备份 v2 文档齐全。
- [ ] **Step 5 — 完成当前授权范围。** 本地提交 `docs: record unified library validation and migration`，汇报代码/验证/阻碍。版本号、正式包、远端推送、标签或 Release 依用户后续明确授权处理；不复用旧发布授权。最后做整分支独立审阅，发现具体缺陷后修复并只重跑受影响门槛。

## Self-Review and Execution Handoff

- 规格覆盖：产品/历史数据 → 任务 1–3；统一影库/排序 → 6；TMDB/语言 → 4–5/8；视觉/详情/编辑 → 7；统计 → 8；可靠性与证据 → 9。
- 五类 Review Focus 均在相应任务有具体断言；null 评分、无图、无类型、历史草稿、旧备份、并发与中断不可省略。
- 本计划只固定必要接口，媒体发布使用同库事务的字段级更新，不能改成另写偏好文件的“先改语言后更新电影”。
- 暂存、媒体和备份大小不是静默裁剪权限；任何超限返回可重试错误、保留输入及完整旧数据。
- 任务 1–5 是共享数据与协议基础，任务 6–8 使用同一契约，建议在当前会话连续执行，以减少并发改同一个 DAO/ViewModel 的冲突；完成后独立审阅整分支。
- 本计划完成时尚未开始产品实现。执行方式由用户确认。若所选执行技能不在当前技能目录，先查找可用同名技能；不能把本文头部的技能名当作已安装事实，也不因此擅自安装依赖或切换工作方式。
