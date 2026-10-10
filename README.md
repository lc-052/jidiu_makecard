# 寄丢制卡

一款原生 Android 制卡工具。把资料整理成可审核、可编辑、可导出到 Anki 的卡片；手动制卡无需账号和网络，AI 制卡使用你自己的 API Key。

**版本：0.4.0 · Android 8.0 / API 26 及以上**

## 下载

安装包见本仓库的 [Releases](https://github.com/lc-052/jidiu_makecard/releases)。请选择 `jidiu-0.4.0-release.apk`。

> Release 与此前开发时安装的 Debug 包使用不同签名，不能直接覆盖安装。请先在旧版备份已保存的牌组；备份不包含 API Key、文档全文和未保存草稿。不要在未备份时卸载旧版。后续 Release 更新需要使用同一发布密钥。

## 可以做什么

- **一份文件制作一套牌组**：导入文本型 PDF、DOCX、TXT 或 Markdown，默认以文件名创建牌组；按全文密度自动分配数量，按 Token 预算分批处理。
- **继续补齐资料**：原任务只生成了一部分时，打开任务并点“补齐整份资料”，沿用原文、模型和牌组；已保存卡片与已选草稿参与去重。
- **三种手动制卡**：空白问答/挖空卡、文档阅读辅助制卡、Tab/CSV/自定义分隔符批量文本制卡。文档辅助模式可点击段落、选取原文并填入问题、答案或解释，断网也能使用。
- **AI 草稿审核**：查看来源、编辑、删除、选择保存；支持单张重新生成、简化、拆分和添加例子。未经确认的草稿不进入正式牌组。
- **六种视觉模板**：Classic、Neon、Sweet、Forest、Paper、Minimal。单张卡片设置优先于牌组，再优先于全局默认；编辑、审核和预览共用卡片组件。
- **整理与预览**：牌组层级、搜索、排序、章节与标签筛选；卡片编辑、批量移动和删除；连续翻面预览。
- **导出与备份**：Basic/Cloze APKG、CSV、版本化 JSON 本地备份与校验后恢复。API Key 不进入普通备份。
- **可恢复生成任务**：显示实际章节、批次、草稿数量、首批结果及调用用量；取消、失败重试和已完成批次检查点。

本软件目前专注制卡，**没有学习评分或间隔复习入口**。旧数据库的历史 FSRS 字段保留用于兼容，不被卡片预览和模板切换改写。

## 快速使用

### 一份资料制作整套牌组

1. 在设置中新增模型，保存并测试连接。
2. 打开 AI 制卡，导入文件或粘贴资料，查看解析预览。
3. 保持“整份资料 · 自动制作整套”及“按文件名自动创建牌组”，也可关闭自动建牌组选已有牌组。
4. 选择制卡模式和模板，阅读上传提示后确认生成。
5. 可以立即查看返回的批次；结束后核对来源和内容，点击“保存选中卡片”。
6. 打开牌组预览或导出 APKG。已有部分内容的任务可用“补齐整份资料”继续。

整套模式的数量是文本密度估算，**不强制凑数，也不证明知识点已全部覆盖**。单次最多500个解析章节和500张配额；超过范围请拆分资料。弃选的旧草稿不消耗补齐配额；旧任务尚未保存的草稿仍需在原审核页保存。

### 模型与 API Key

设置 → 服务商与模型 → 新增模型。填写 HTTPS Base URL、API Key、任意 Model ID，选择 Chat Completions 或 Responses 协议；超时、输出上限、严格 Schema、温度与流式选项按供应商能力配置。可设置默认模型，并测试连接。

自定义 OpenAI-compatible 服务应填写包含 API 前缀的 Base URL，避免重复拼接接口路径。接口不支持严格 Schema 时可关闭该选项。连接测试、生成与 AI 修改都会实际调用供应商 API，可能产生费用。

API Key 使用 Android Keystore 支持的 AES/GCM 加密，保存在不参与系统备份的目录，不写入 Room 或普通偏好。BYOK 不能消除移动端密钥风险；设备被控制或应用运行时被攻击时，密钥仍可能泄露。

### 手动与文档辅助制卡

首页 → 手动制卡，或从牌组详情添加卡片。空白模式填写问题、答案、解释与标签；文档辅助模式导入后以原文阅读为主，点击段落打开编辑面板，可把选文填入字段并继续阅读。PDF 使用解析出的页码与段落，不提供精确坐标选区。挖空语法示例：

```text
{{c1::主动回忆}}需要从{{c2::记忆}}中提取信息。
```

### AnkiDroid

牌组详情 → 保存 APKG，或通过系统分享发送给 AnkiDroid → 在 AnkiDroid 中导入。导出包含 Basic/Cloze、中文、标签、层级名称和模板静态 HTML/CSS。笔记标识稳定，重复导入行为可预测；不同步两个应用的 FSRS 状态和复习历史。

## 构建

Android Studio 打开仓库根目录。使用 JDK 17，安装 Android SDK 36。Gradle Wrapper 版本为8.13，依赖由 `gradle/libs.versions.toml` 管理。

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat`。本地 SDK 路径写入 `local.properties`，不提交到仓库。标准输出目录为 `app/build/outputs/apk/`；本工作区额外提供 `tools/isolated-build.gradle.kts`，用于隔离已有构建产物。


## 架构

Kotlin、Jetpack Compose、Material 3、MVVM、Repository、UseCase；Room 本地存储、DataStore 偏好、WorkManager 持久化任务、OkHttp API 适配、Android Keystore 密钥保护、SAF 文件授权。

- `core/`：数据库、网络、安全、文档解析、通用卡片模板、APKG 与兼容调度实现。
- `data/`、`domain/`：Repository、数据模型及 UseCase。
- `feature/`、`navigation/`：首页、牌组、手动制卡、AI 生成与审核、预览、设置及导航。
- `app/schemas/`：Room 导出 schema；迁移不使用 destructive migration。
- `app/src/test/`、`app/src/androidTest/`：单元、数据库、网络、导出与 UI 测试。

详细资料：[ARCHITECTURE.md](ARCHITECTURE.md)、[PLAN.md](PLAN.md)、[TASKS.md](TASKS.md)、[隐私说明](PRIVACY.md)、[第三方许可证](THIRD_PARTY_LICENSES.md)。

## 验证与已知限制

0.4.0 的72项 JVM 测试通过；模拟 HTTPS 的跨任务去重、取消与恢复、制卡页 UI 和实际文件本地规划共3项 Android 测试通过。Debug 和 Release 均可构建，Debug 在 Android 16 模拟器安装启动成功。

实际文档的本地规划样本为146张配额、54章节、21批请求；独立短章节请求合并前为54批。只证明请求数量减少，不能推导真实模型提速百分比。此前版本有真机真实调用记录，但不代表新版整套生成已通过真实模型验收。

- AI 可能返回错误、拒绝或不完整内容，来源与结构校验不能证明事实准确，仍需人工核对。
- 扫描 PDF、图片 OCR、PPTX、媒体附件与 APKG 导入未实现；单文档/解压内容限制20 MB，PDF 最多1000页。
- DOCX 提取文本与基本章节，不复现复杂版式。原生预览支持轻量 Markdown；公式源码保留，MathJax 由 Anki 渲染，HTML 图片引用本机不渲染。
- 备份主要包含正式牌组、笔记、卡片及兼容记录；不迁移模型密钥、文档全文、生成任务、草稿与偏好。
- APKG 已有结构与参考后端测试、历史 AnkiDroid 兼容测试；Anki Desktop GUI 及所有客户端版本未验证。
- 发布版使用独立签名，不保证可覆盖不同签名的开发安装包。

第三方组件许可见许可证清单；此仓库未另行声明项目整体授权许可证。
