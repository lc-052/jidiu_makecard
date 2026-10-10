# 系统架构 · 制卡版

0.3.1：文档阅读模式使用有界 Column + weight LazyColumn，占用剩余屏幕而非外层滚动页中的固定 320dp；配置收起、展开时独立限高滚动。底部编辑面板跳过半展开状态，TextFieldValue 保存原文选区。

网络响应读取阶段的 IOException 与连接阶段保持同样的可重试分类；暂时性网络/服务失败暂停后续批次，WorkManager 从 10 秒指数退避，至多 3 次任务恢复。已提交的草稿/检查点保持事务一致，恢复仅请求未完成批次，用户取消不会触发恢复。认证/参数错误及持续故障仍明确失败。

新任务 UI 默认问答、每批 4 张。CardRules.validateGenerated 在原有语法/来源规则上添加 AI 挖空上下文、短答案、空数与遮盖比例限制；逐张校验，合格草稿保留，过滤原因附加为质量警告。旧笔记与手动笔记继续使用标准 validate，不改历史内容或 FSRS 字段。

单模块按 core/data/domain/feature 分包，Compose → StateFlow ViewModel → Repository/UseCase → Room。AppContainer 构造器注入；本地数据库是事实来源。

## 制卡数据流

手动页输入 → CardRules 校验 → Repository 事务保存 Note 和对应 Card → Room Flow 更新牌组与首页。Basic/Comparison 一条笔记一张卡，Cloze 每个独立序号一张卡，供 Anki 导出使用。无需网络或模型。

CardFormatting 是纯 Kotlin 轻量排版解析器，保存字段不变。Compose CardContent 展示段落、列表、标题和行内重点；CardPreview 切换问题/答案，解释折叠。旧长段落只在完整句子边界视觉分段，代码、公式及 Cloze 不自动拆分。APKG 使用同一解析器转换 HTML，统一列表、重点与解释模板。

文档通过 SAF → DocumentContent → 用户预览确认 → Token 预算分块 → WorkManager 知识点分析/分配/生成检查点 → 反序列化、结构/来源/去重校验 → GenerationDraft → 独立审核、编辑和选中保存。全文放私有文件，数据库存元信息与任务分块；资料是不可信输入。格式提示要求核心答案简短、详细解释独立，不能通过截断或模型自评分证明准确性。

## 数据兼容

schema v3 通过 v1→v2→v3 或 v2→v3 Migration 升级，保留 Note/Card 关系、稳定 ID、UTC epoch 时间与外键。旧 ReviewLog 和调度字段、内部参考算法及测试保留用于旧数据库/备份兼容；当前没有评分、学习设置或提醒 Worker。牌组连续预览只读 Note/Card，不产生 ReviewLog。升级时通过幂等 AppInitializer 初始化 WorkManager，取消旧 daily-reminder；移除通知权限。避免破坏性迁移，不删除已有牌组或卡片。

版本化 JSON 逻辑备份在完整校验后事务覆盖，失败不改原库；密钥、模型、全文、任务、草稿和偏好不迁移。旧关联记录可以保留但不进入当前产品操作。

## 网络和安全

公开模型参数存 Room。API Key 使用 Keystore AES/GCM 加密后存 noBackupFilesDir，排除数据库、DataStore 和导出。Chat Completions/Responses 通过 ProviderAdapter 分别适配；仅 HTTPS、无凭据日志拦截器、禁用重定向。连接测试、生成与单张 AI 编辑是真实请求。

WorkManager 为生成任务保存阶段和分块结果，失败重试跳过已完成块。设备测试可注入 MockWebServer HTTPS 客户端，不替代真实供应商验证。

## Anki

独立 SQLite legacy v11 collection.anki2 + media ZIP，不修改 Anki 集合。稳定 GUID/Note/Card/Model/Deck ID，Basic 与 Cloze 两种模型，Comparison 映射 Basic；层级牌组、中文、标签、HTML 与 MathJax 源码。导出为新卡，不同步历史调度。解释使用 details/summary，答案保持短段落与列表。

## 工具链与设计选择

AGP 8.13.2 / Kotlin 2.2.20 / Gradle 8.13 / JDK 17 / SDK 36。Compose BOM 2026.02.01 与当前 SDK/AGP 兼容；较新组合要求更高工具链，未混用。Room 2.8.5、WorkManager 2.11.2、OkHttp 4.12。

DOCX 使用受限 ZIP/XML 正文解析以避免桌面 POI 兼容问题，PDFBox Android 提取文本。不开 WebView 构建页面。主题、字体、安全区和可重用组件统一在 designsystem；尊重系统字体缩放。

## 0.3.0 模板与文档辅助升级

CardTemplateRepository 定义六套表现配置，CardPreview 统一展示编辑、手动、AI 草稿、牌组与连续预览；未知模板回退到有效牌组或默认配置。内容字段保持原样；单卡/牌组只存模板 ID，全局默认在 DataStore。翻面遵守系统 animator duration scale 和应用动画开关，长文本在父页面滚动，不限定卡片最大高度。

Room schema v2→v3 只新增 decks/notes.templateId（NULL 表示继承）、generation_tasks.metrics、chunks.error、providers.concurrency。Note/Card/ReviewLog 所有旧调度值和标识保留。真实迁移测试通过旧卡片全对象对照，并在模板编辑后再次确认 Card/ReviewLog 不变。

导入文本缓存在私有文件，metadata 保存标题与页码信息；手动文档阅读使用有界 LazyColumn，段落点选和原文 TextFieldValue 选区由 Compose 提供。底部面板填入并编辑共享 Note 字段，保存继续阅读。局部章节生成不得覆盖全文缓存；手动与 AI 共享存储模型。批量解析器支持引号、多行和多字符分隔，所有行校验后整批事务保存。

GenerationWorker 默认在一次请求内完成知识分析与制卡，应用设置来源 ID/章节/页码/候选 ID，只要求模型输出必要内容与逐字摘录。保留 analyzeFirst 诊断模式用于相同条件的双阶段串行比较。1–4 并发按独立块波次执行；429 原子降并发为 1，恢复保留限流记录。Mutex + Room transaction 保证跨块去重、草稿和检查点共同提交；每次请求/等待前检查取消状态，取消传播到 OkHttp。已完成结果通过 Room Flow 立即展示。

APKG 继续使用两个原模型，字段用 jt-classic/neon/sweet/forest/paper/minimal 的 scoped HTML/CSS 包装。所有类同时存在，改变单卡 wrapper 不改共享模型的其他外观。空解释不包装，避免 Anki 的条件字段误显示空说明。CSS 对图片 max-width 和代码换行做兼容处理；无图片附件打包或本机图片渲染。模板默认或牌组更新的修改时间参与导出 mod，维持重复导入与样式更新行为。

## 0.3.2 生成批次与调度

GenerationBatchPlanner 合并带章节标识的连续段落，独立的 600–6000 估算 Token 输入预算不依赖模型输出上限。每批配额不超过 batchSize，覆盖较多批次时均匀分配；极长资料按位置选批，未覆盖知识仍报告，不伪称全面覆盖。混页批次 page=null，候选摘录通过原始 DocumentContent 再定位到真正段落，保证章节和页码来自源资料。

GenerationWorker 将零配额块事务标记完成，再以有限数量消费者从共享队列领取有效批次。快请求立即补位，429 后降至一个消费者；网络失败和服务商 Retry-After 阻止新领取，保留其他已在途结果。时间片到期不再领取，但允许在途请求结束后提交检查点，再交由 WorkManager 恢复。既有任务和草稿格式继续兼容，不改 Room schema 或 FSRS 值。

输出预算默认 600+700×每批张数，受用户上限约束，截断后有限倍增。官方 DashScope 已知混合 Qwen 的快速请求显式关闭思考，其他接口不假定扩展支持。JSON 中追加可选 reasoningTokens/outputLimit/thinkingDisabled 指标，不存原始响应或密钥。进度页面结合 WorkManager 的真实队列状态，显示系统等待，且分母只含有效批次。
# 0.3.3 引用定位补充

生成批次可以合并段落，引用校验对应允许原始文档连续段落的精确文本跨度。只使用原始 DocumentContent 构建定位文本；人工加入的章节标记不能获得引用认证。跨页定位不确定时 sourcePage=null，sourceSection 保留实际涉及章节。来源匹配通过仅证明可定位，不代替事实人工审核。重试提示来自应用固定分类，不回显服务商错误正文。数据库版本及历史 FSRS 状态不变。

## 0.4.0 整套制卡

GenerationConfig 添加有默认值的 wholeDocument/createDocumentDeck，旧 JSON 可直接解码；Room 表结构不变，不需要破坏性迁移。WholeDocumentPlan 按章节文本密度分配总配额（上限500），再沿用 Token 预算分块。ViewModel 在同一事务中创建文件名牌组、文档、任务及批次。WorkManager 沿用有界并发、断点及取消机制。GenerationExistingCards 按内容哈希及目标牌组加载既有笔记和已选/已保存草稿，排除弃选草稿；工作线程再次全局去重。不同资料的手动笔记只参与重复检查，不消耗章节配额。每个来源 ID 的原文仍保存在私有目录，补齐从原任务读取，不要求重新导入。所有新结果先保存草稿，审核后事务保存正式笔记；FSRS 记录不变。

相邻章节的计划进一步按每批数量上限及6000估算输入Token上限合并。合并不删除段落，章节名作为集合保留；进度页按拆分后的实际章节名统计完成情况，批次统计独立计算。实际Flutter资料146张配额由54次章节请求降为21次批次请求。配额仅用于规划，来源校验与质量过滤沿用原实现。
