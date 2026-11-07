# 详情页资料更新 Implementation Plan

**Goal:** 从电影详情菜单完整更新当前电影的 TMDB 资料。
**Architecture:** 扩展既有协调器的可选电影 ID 筛选；ViewModel 管理操作与目标，详情页显示对话框。
**Tech Stack:** Kotlin、Compose、Room、现有 TMDB 客户端。
**Spec:** docs/superpowers/specs/2026-11-08-metadata-refresh-design.md

## Constraints
不新增依赖或迁移；保留个人记录；简体中文；不自动构建、测试、安装或提交。

## Tasks
- [x] 协调器增加单片筛选并检查电影存在及 TMDB ID；保留既有整批调用兼容。
- [x] ViewModel 增加启动与取消接口，检查凭据、草稿、恢复和并发状态。
- [x] 详情菜单增加更新资料，对话框显示启动、进行中、成功、失败与重试状态。
- [x] 更新说明并静态阅读差异。设备验收待用户手动触发，覆盖成功、超时重试、取消、无凭据和个人内容保留。
