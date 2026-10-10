# 当前修复 0.3.1：阅读空间、断流恢复与挖空质量

2026-10-10。用户反馈真实 AI 请求慢/易中断与阅读区被顶部配置挤占；不能用此前模拟网络速度代替真实模型表现。

- [x] 导入后收起顶部制卡配置；一行工具栏、文档标题与自适应阅读区；设置展开时独立限高滚动，避免挤掉整页原文。
- [x] 编辑面板跳过半展开状态；扩大原文选区；保存选区状态用于页面重建。仍为段落阅读，不虚构 PDF 坐标。
- [x] 修正响应头收到后读取响应体发生 IOException 被误报为不可恢复格式错误；现在归类为可重试网络中断。
- [x] 网络/暂时性服务错误有限自动恢复，指数退避从 10 秒起；跳过已完成批次，取消不恢复；持续失败上限后明确报错。
- [x] 新任务默认问答卡，每批 4 张；混合挖空需主动选择。AI 挖空增加上下文、1–2 空、短答案、遮盖比例检查；旧/手动卡片不受新增 AI 规则影响。
- [x] 合格卡片逐张保留，不因同批另一张不合格而重做整批；过滤原因写入草稿警告，不用静默删除冒充全量完成。
- [x] 最终 0.3.1 Debug/unsigned Release 构建成功，Lint 0 errors、15 warnings、2 hints；47 项 JVM 全通过，含真实断流响应体读取错误分类。Debug code 6、签名通过，交付 `app/build/outputs/apk/debug/jidiu-0.3.1-debug.apk`。`tools/build-0.3.1.txt`。
- [x] API 36 核心回归 10 项与跨进程 seed→force-stop→冷启动→verify 全通过。`tools/device-0.3.1-tests.txt`。
- [x] 飞行模式确认为 1 的四项 UI 全通过，断网 PDF 选取/挖空保存、读取区域超过页面一半、批量保存、六模板与取消/页面重建；测试后恢复网络。`tools/upgrade-0.3.1-tests.txt`。
- [x] 模拟器系统界面 ANR 弹窗遮挡了首张截图，未采用为视觉证据；处理系统弹窗后单独阅读回归再次通过，检查无遮挡的 `tools/document-reader-0.3.1.png`。`tools/reader-0.3.1-regression.txt`。
- [x] 模拟 HTTPS 故障/质量验证通过：503 失败返回自动排队，恢复后成功且再次执行无重复；第 3 次恢复仍失败时明确 FAILED；同批合格问答+整句挖空仅保留问答，4 块保持 4 次请求，附过滤警告；429 冷却、取消继续通过。真实 API 速度不由这些模拟断言替代。
- [ ] 真实供应商速度、实际设备断连根因：等待具体模型 ID/错误记录；无真实 API Key，未验证。

---
# 历史升级 0.3.0：三种制卡与六模板

2026-10-09；以下 0.2.0、0.1.x 为历史验收。用户确认“预览入口相当于复习入口，实际为卡片预览”，不恢复评分功能。

- [x] Phase 1：检查现有源码；确认双请求重复资料、串行批次和固定 90% 的流程问题；记录解析/清洗/分块/请求/首批/JSON校验/保存/重试与用量指标。
- [x] Phase 2：六种独立模板定义、三级覆盖、真实网格/大图/恢复默认；统一 CardPreview；Room v2→v3 Migration，无 destructive migration。
- [x] Phase 3：空白、文档辅助、Tab/CSV/自定义批量解析；段落阅读/原文选区/底部编辑/离线挖空/来源/继续保存。模拟器飞行模式下三项 UI 回归通过（PDF 辅助、六模板+无评分预览、批量校验保存）。
- [x] Phase 4：默认融合分析与生成，精简输出元信息由应用填充；1–4 有限并发、429 降为 1；持久化批次、全局去重、取消、恢复未完成批次。初轮 HTTPS 同条件性能/取消/旧检查点/PDF 3 项测试通过。
- [x] Phase 5：资料/参数/独立进度/审核；首批立即可见、实际批次/章节/草稿数量、按章节生成、可折叠耗时明细与失败重试。
- [x] Phase 6：六种独立 scoped HTML/CSS 视觉类；保持 Basic/Cloze 共享模型和原稳定 Note/GUID/Card ID，不因视觉切换创建重复模型。
- [x] Phase 7：最终 Debug/Release 构建成功；Lint 0 errors、15 warnings、2 hints；43 项 JVM 全通过。Debug 签名通过，0.3.0 / code 5，交付 `app/build/outputs/apk/debug/jidiu-0.3.0-debug.apk`；Release 为未签名包。
- [x] 最新 API 36 设备回归：AppFlow 8 + PDF/检查点 2 全通过；另有 seed→force-stop→冷启动→verify 的独立进程持久化验证。保存后查看牌组的两项失败已修复并复测；生成按钮测试适配已存在的牌组，不清空数据制造空状态。证据 `tools/device-tests.txt` 最新记录。
- [x] 飞行模式实际为 1 的四项升级 UI 测试通过（PDF 文档辅助挖空、批量 CSV 校验保存、六模板及只读预览、首批实际计数/取消/页面重建/审核）。测试后飞行模式恢复 0。`tools/upgrade-final-offline-ui-tests.txt`；六模板真实截图 `tools/template-*.png`，已检查纸质与霓虹预览。
- [x] 最新 HTTPS 模拟性能/429 降并发/数字 Retry-After 冷却/恢复/取消测试通过。相同四块和 150ms 响应延迟，三轮中位：总耗时 1916→571ms，首批 460→245ms，请求 8→4；不是实际模型提速。`tools/upgrade-final-network-tests.txt`、`tools/generation-benchmark.json`。
- [x] Android 实际导出的六模板 APKG 经官方 Anki 26.9.3 后端导入/渲染：12 notes、18 cards、2 models；Basic/Cloze、中文、重点/列表/解释、重复导入 0 重复、模板切换 GUID/ID 不变。`tools/anki-validation/templates-result.json`。
- [x] AnkiDroid 2.25.1 实际导入最新 Basic/Cloze 包，更新原 2 条笔记，无新增重复；Cloze 答案实际呈现霓虹边框与解释折叠。`tools/upgrade-droid-result.xml`、`tools/upgrade-droid-answer.png`。全部六模板客户端逐一检查仍未完成。
- [ ] 真实服务商性能/质量、Anki Desktop GUI、实体手机、全部模板 AnkiDroid 人工视觉检查：未验证。尚无真实 API Key。

最终 43 项 JVM 通过，包括 v2→v3 保留旧笔记、Card 全部调度值和 ReviewLog，模板改动后旧 Card 对象完全一致。UI 连续预览前后再次核对 Card 与 ReviewLog 未改变。外部验证限制与原生图片/公式渲染限制见 README.md；不将未完成的客户端视觉、真实模型或发布签名验证计为通过。

详细诊断、测量口径和原始样本见 UPGRADE.md 与 tools/generation-benchmark.json。

---
# 当前版本 0.2.0：完全制卡软件

用户 2026-10-09 明确取消学习功能。下方 0.1.x / Phase 1–7 是历史记录，历史学习功能不代表当前产品功能。

- [x] 移除复习、评分、学习统计页面、导航与提醒设置/通知权限；升级取消旧提醒，保留旧数据库与备份兼容。
- [x] 首页改为制卡工作台，真实牌组/卡片/草稿计数、最近制作与编辑。
- [x] 新增独立离线手动制卡页：问答、对比、多挖空、解释、标签、选择/创建牌组、校验、预览与保存。
- [x] 原生卡片排版：分段、标题、列表、重点、代码、问题/答案切换，解释和原文依据折叠。
- [x] AI 生成与单张编辑使用统一排版提示；APKG 转换 Markdown 为 HTML，模板显示列表/重点和折叠解释。
- [x] 新增 7 项格式化单元测试和手动挖空 UI 回归；跨进程持久化测试改为验证手动笔记、多序号与排版字段。
- [x] README、PLAN、ARCHITECTURE、PRIVACY 按当前制卡定位重写。
- [x] JVM 37 项全部通过（新增排版 7 项）；API 36 首轮 9 项 UI/生成/PDF 回归通过，另通过 seed→force-stop→冷启动→独立 verify 的手动多挖空持久化验证。`tools/device-tests.txt`。
- [x] Debug 0.2.0 与 unsigned Release 构建成功；Lint 无 Error；Debug 签名验证通过。`app/build/outputs/apk/debug/jidiu-0.2.0-debug.apk`，versionCode=4。
- [x] 新版 APKG 官方 Anki 26.9.3 后端导入 2 笔记/3 卡片；实际渲染包含中文、重点、列表、MathJax 和折叠解释；再次导入 0 重复。`tools/authoring-anki-validation.txt`、`tools/anki-validation/result.json`。
- [x] 新增排版 UI 测试通过：列表/重点与解释折叠，合计 10 项设备回归通过；实际截图检查无文字堆叠。`tools/authoring-preview-test.txt`、`tools/authoring-preview.png`。
- [x] AnkiDroid 2.25.1 本轮真实导入更新现有 2 笔记、无新增重复；Cloze c2 答案及解释折叠/展开实际截图通过。新版 Basic 的列表/公式/解释由官方后端渲染验证，本轮未另做 AnkiDroid Basic 页面人工检查。`tools/authoring-anki-result.xml`、`tools/authoring-anki-answer.png`、`tools/authoring-anki-explanation.png`。
- [ ] 真实付费 API、Anki Desktop GUI、实体手机、正式签名：未验证/未配置。

---
# 开发状态与真实验收

更新：2026-10-09。勾选表示实际实现及相应检查，不代表所有外部发布条件已满足。

## 0.1.2 修复记录

- [x] 实际复现首页快捷进入 AI 后点击首页仍显示制卡内容；新增导航回归在旧版超时失败。
- [x] 统一底部导航与首页快捷入口；首页回到保留的起始页，不恢复错误关联的制卡返回栈；重复点击当前入口不堆叠页面。
- [x] 修复后六项 UI 测试全部通过，新增测试连续首页/AI 往返三次并断言对应内容存在、另一页内容不存在。独立 UI dump 再次核对首页统计与制卡资料界面不同。
- [x] 0.1.2 Debug 构建、Lint、APK 签名验证通过；文件 `app/build/outputs/apk/debug/jidiu-0.1.2-debug.apk`。测试记录 `tools/navigation-regression.txt`。Release 仍为 0.1.0，未更新。

## 0.1.1 修复记录

- [x] 修复制卡按钮无牌组时灰色却没有解释的问题：显示所有缺失条件，在制卡页直接创建并选中目标牌组，保留资料预览。
- [x] 已删除的模型/牌组选择自动恢复到有效项；未选择模型时优先默认模型；无效数量/空语言明确提示。
- [x] Debug 0.1.1 构建与 Lint 通过；Compose 5 项回归全部通过，新增场景验证“配置模型+解析资料+无牌组→原因提示→页内建牌组→按钮启用→上传确认”。未调用付费 API。
- [x] APK：`app/build/outputs/apk/debug/jidiu-0.1.1-debug.apk`，versionCode=2，可使用相同 Debug 签名覆盖升级；签名校验通过。现有 Release 文件仍是 0.1.0，未重新发布 Release。

## Phase 1

- [x] Kotlin、Compose、Material 3、四入口 Navigation、主题、Version Catalog。
- [x] MVVM、StateFlow、Repository、UseCase、构造器注入容器。
- [x] Room 索引/外键/事务、schema v1/v2、真实迁移测试；Debug 安装并在 API 36 启动。

## Phase 2

- [x] 牌组 CRUD/层级/搜索/排序/统计/删除确认，笔记 CRUD/批量移动删除/章节标签筛选。
- [x] Basic、Comparison、多 Cloze 卡片；编辑已有卡片保留调度。
- [x] FSRS-6 四评分、间隔预览、撤销、保持率、每日额度、到期队列、日志与事务。
- [x] 实际首页、连续学习、用时、7/30 天活动、学习历史。
- [x] 设备 UI 创建→保存→Activity 重建→翻面→评分，数据库持久化与状态测试。

## Phase 3

- [x] 多服务商、任意模型 ID、默认模型、公开配置、真实连接测试调用。
- [x] Keystore AES/GCM/noBackupFilesDir；设备验证密文无原始密钥。
- [x] Chat / Responses 适配、严格 Schema/JSON 降级、有限重试、SSE、可选温度、用量。
- [x] 认证、路径、限流、服务器、网络、空体、损坏 JSON、截断和拒绝错误映射；MockWebServer 验证。
- [ ] 真实付费服务商端到端生成：无账号/密钥，未验证。无可靠价格数据时不估算费用。

## Phase 4

- [x] SAF、文件大小/权限检查、PDF 文本/页码、DOCX 章节、TXT/Markdown/粘贴、预览确认。
- [x] 清洗/重复块、Token 预算分块/重叠、知识点分析/数量分配/生成/校验/出处定位/去重/覆盖警告。
- [x] WorkManager 持久任务、章节阶段进度、取消重试、分析及完成块检查点。
- [x] 审核编辑、来源、选择删除、重生成/简化/拆分/例子、选中保存后才建立正式卡片。
- [x] 设备真实文本 PDF 提取；HTTPS 模拟首块成功、次块失败、断点恢复且未审核不入队测试。
- [ ] OCR/扫描 PDF/PPTX：后续功能，未实现。

## Phase 5

- [x] SQLite v11 + ZIP 真 APKG、稳定 ID/GUID、Basic/Cloze、中文/HTML/数学字段/标签/层级。
- [x] 当前/子树/全部导出、SAF 保存、系统分享实现；结构/异常输出/空牌组/重复导出测试。
- [x] 官方 Anki 26.9.3 后端：2 笔记/3 卡片，Cloze 渲染、层级、重复导入 0 新增。
- [x] AnkiDroid 2.25.1 / API 36 系统选择器导入：2 笔记/3 卡片，中文/MathJax/Cloze c1、c2、提示及解释正确。
- [x] AnkiDroid 分享与重复导入：FileProvider/ClipData 授权成功；第二次导入更新现有 2 笔记，无新增重复笔记。
- [ ] Anki Desktop GUI 人工导入：未验证。官方后端通过不代替 GUI。
- [ ] APKG 完整导入、媒体和跨应用复习进度同步：后续功能。

## Phase 6

- [x] 版本化学习 JSON 备份、全量校验后事务覆盖、损坏文件不清空原库、CSV。
- [x] 主题/字体/安全区域/翻面/导航列表反馈、空错误状态、进度取消重试、隐私帮助许可证。
- [x] 提醒时间、启用时请求通知权限、后台 WorkManager 官方通知。
- [ ] JSON 备份扩展到全文、任务、草稿、模型公开配置和偏好：当前只包含学习数据。
- [ ] 实体设备长期提醒、跨时区送达、广泛大字体/无障碍/长文档压力测试：未验证。

## Phase 7

- [x] JVM 30 项：Core 14、Database 5、ExportAndDocument 5、Migration 1、Network 5，全部通过。
- [x] FSRS 对照固定 py-fsrs 6.3.0 的 840 组参考向量通过。
- [x] 首轮 Compose 设备测试 4 项通过，新增 PDF / HTTPS 恢复任务 2 项通过。
- [x] Debug / unsigned Release 构建成功；Lint 无 Error。
- [x] README、PLAN、ARCHITECTURE、隐私、依赖许可和测试源码；核心无 TODO/空实现/固定 AI 响应。
- [x] 最终设备回归 6 项通过；另通过 seed→force-stop→冷启动→独立 verify 进程，卡片状态、稳定性、到期时间和日志保持一致；分享授权通过。
- [ ] 正式 Release 签名：未配置个人签名材料；已生成 unsigned APK。
- [ ] 真实手机、商店发布审查及 Anki Desktop GUI：未验证。

## 证据

### 0.3.6 最终真机回归

- [x] 用户手机实体测试设备覆盖安装0.3.6（versionCode11）。
- [x] 英文自动行内代码显示、两处等宽样式、正反面切换通过；临时示例不写库。
- [x] 真实11张未保存草稿：末张滚动、编辑打开取消、顶部保存按钮、内容hash与选择/保存状态一致。
- [x] 真实20张已保存草稿：末张浏览、已保存标识、返回顶部、内容与状态一致。
- 首轮长卡编辑按钮需显式滚至操作区，修正测试；随后发现20张已被保存，因此测试按saved状态检查只读标识，编辑操作在11张未保存任务验证。失败日志保留，不掩盖测试调整。
- 不调用真实AI，不删除、保存或修改用户草稿。测试过程中20张任务观察到已保存，正式卡片数现21；本测试仅只读，既有复习记录0。
- 证据：`tools/phone-style-0.3.6.txt`、`tools/phone-audit-11-0.3.6.txt`、`tools/phone-audit-20-0.3.6-state.txt`及对应统计JSON。

### 0.3.6 AI纯文本英文排版

- [x] 生成提示补充：无Markdown样式字段，中文语境英文术语使用行内代码。
- [x] 本地格式补全覆盖新生成及单张AI修改，原始引用、标签、标识及Cloze表达式不变。
- [x] 已有样式、链接、邮箱、公式和纯英文内容保护，重复处理不重复包裹。
- [x] 新增6项格式测试、完整66项单元测试及Debug构建通过；APK签名验证通过。
- 原Cloze测试整段不变断言因新排版规则失效，改为检查外部CPU样式及原引用、挖空序号不变，再次回归全部通过。记录：`tools/build-0.3.6-final.txt`。
- APK：`tools/artifacts/jidiu-0.3.6-debug.apk`；随后已完成真机安装和3项只读UI回归；未调用真实AI，新版Release未验证。
- 旧卡片不批量改写；无需额外AI请求。

### 真机审核与0.3.5

- [x] 手机安装0.3.4，数据库聚合确认20/11/1草稿及1正式卡片。
- [x] 真实20草稿滑至末张、编辑打开取消；只读流程没有保存或删除草稿。
- [ ] 0.3.4整体真机用例：返回顶部后保存按钮未显示，整体断言失败，不标记通过。
- [x] 0.3.5长任务提示三行折叠修复，Debug/测试APK构建及60单元测试通过。
- [ ] 0.3.5真机完整回归：再次安装前手机断开，等待重连。
- [x] 0.3.5 unsigned Release与Lint构建通过，Lint无Error。
- 记录：`tools/PHONE_AUDIT_0.3.5.md`。

### 0.3.4 多卡预览与审核滚动

- [x] 进度预览序号/总数、上一张/下一张，替换仅显示第一张的行为。
- [x] 审核页单一懒加载滚动容器，覆盖章节折叠，卡片与当前批次章节摘要。
- [x] 页面缓存解析结果，牌组与任务配置移出每条卡片订阅。
- [x] 首页明确“已保存卡片”，与未审核草稿区分。
- [x] 多卡 JSON 接收测试通过；60 项单元测试通过。
- [x] 12草稿/100章节界面专项：切换预览、滑至末张、编辑、重建及保存12条笔记通过。
- [x] 9 项界面测试全部通过；Debug、unsigned Release及Lint通过（0 Error，16 Warning，2 Hint）。APK v2签名验证通过。
- [ ] 用户手机实际任务验证：手机未连接。保留旧数据，没有重跑真实模型。
- 记录：`tools/AUDIT_SCROLL_0.3.4.md`。

### 0.3.3 引用校验修复

- [x] 连续跨段精确引用定位，跨页保持未知，跨章节保留真实来源。
- [x] 来源/挖空/结构校验分类重试提示，排除应用章节标记。
- [x] 新增 6 项引用与不可信重试信息回归测试。
- [x] 59 项单元测试，Debug/unsigned Release 构建及 Lint（0 Error）通过。
- [x] 13 项设备/界面测试通过：网络专项1、基础界面8、进度1、文档/批量/模板3。首轮两个测试入口类名错误，已用实际类名补跑4项通过；日志保留。
- [ ] 手机只恢复失败批次，检查原草稿和已完成批次不变。
- 手机在安装前断开，未安装0.3.3、未重跑付费请求；已请求重连。Debug APK 已签名验证，位于 `tools/artifacts/jidiu-0.3.3-debug.apk`。
- 记录：`tools/AI_OPTIMIZATION_0.3.3.md`。

### 2026-10-10 Flutter 文档真实手机诊断

- [x] 用户选定 Markdown 的生产解析/分块测试：短段未合并，20 张分成 20 次单张请求。
- [x] 手机已有模型真实生成：1 张问答草稿、首批约 250 秒、两次 90 秒超时。
- [ ] 完整 20 张生成、真实 Cloze 质量与优化后对照：未完成。本轮仅诊断，未宣称性能修复。
- 记录见 `tools/FLUTTER_REAL_AI_TEST.md`。

### 0.3.2 优化交付

- [x] GenerationBatchPlanner 合并连续题目/段落，独立输入预算，按批配额，不重复上传短资料凑数。
- [x] 有效请求动态补位队列，零配额不占并发；429、取消、恢复与全局去重保留。
- [x] 按批输出预算、截断有限增加；官方已知 Qwen 混合模型的可关闭快速模式；记录思考用量。
- [x] 来源从原始段落定位；后台队列等待提示；进度分母只含有配额批次。
- [x] 同一 Flutter 文档生产规划 20 请求 → 6 批（4/4/3/3/3/3），目标配额总计 20。
- [x] 53 单元测试、16 项独立设备/界面回归通过；新增慢请求与零配额交错队列专项再次通过。
- [x] Debug、unsigned Release、Lint 0 Error；Debug APK v2 签名验证通过，交付 `tools/artifacts/jidiu-0.3.2-debug.apk`。
- [x] 优化后真实手机样本：同文档/qwen3.8-flash，首批 22916 ms，整轮 73387 ms，8 次请求（6 批+2 次校验重试），无网络超时，保留 11 张草稿。
- [ ] 完整 AI 生成验收：5/6 批完成，1 批校验失败，状态 FAILED；挖空事实准确性与视觉质量尚未人工审核。不能把测试脚本执行成功当作生成任务全部成功。
- 证据：`tools/validation-0.3.2.json`、`tools/flutter-document-plan-0.3.2.json`、`tools/generation-benchmark-0.3.2.json`、`tools/network-0.3.2-final.txt`。

JVM 报告：`app/build/reports/tests/testDebugUnitTest/index.html`；Lint：`app/build/reports/lint-results-debug.html`。依赖更新、Bouncy Castle 内部 TrustManager 等 Warning 保留；应用 HTTPS 使用正常系统证书验证，无信任所有证书配置。

设备：Pixel_8_API_36 / emulator-5554 / Android 16。实际 APKG：`tools/compatibility.apkg`，后端脚本 `tools/verify_apkg_with_anki.py`，结果 `tools/anki-validation/result.json`。FSRS 脚本 `tools/generate_fsrs_vectors.py`，固定测试资源 `app/src/test/resources/fsrs-vectors.json`。

最终验证摘要及 APK SHA-256：`tools/validation-results.json`。设备复现脚本：`tools/verify_device.ps1`；执行记录 `tools/device-tests.txt` 保留了发现问题和修复后成功的历史。最终构建所有请求任务通过，Lint Error=0，Debug APK 签名验证通过。真实最终首页截图：`tools/home.png`。

未使用 clean 或批量文件删除；下载测试客户端、参考运行时、密钥和签名不进入 Git。尚有以上功能边界与外部条件，因此不把全部发布验收标为完全完成。





## 0.4.0 整套牌组升级

- [x] 全文密度规划，超过20张且纳入每个章节；500配额上限。
- [x] 默认文件名牌组与完整任务记录在事务中创建。
- [x] 原任务补齐入口：复用原资料/模型/牌组，上传确认。
- [x] 同源内容哈希跨任务去重；弃选草稿不消耗配额。
- [x] 合并相邻小章节到有界批次，保留来源及全文。
- [x] 保留草稿审核、最终保存、失败恢复、取消及 APKG 导出。
- [x] 72项 JVM 测试通过；模拟 HTTPS 去重/恢复/取消回归及制卡页 UI 测试通过（2项，tools/instrument-0.4.0-final.txt）。
- [x] 用户 Flutter 文档本地规划通过：28,496字符、200文本块、54解析章节、146张配额，不调用真实 API。
- [x] 合并批次实际文件规划测试通过：146张配额 / 54章节 / 21请求批次；原独立章节规划54批。不宣称真实模型提速比例。
- [ ] 百炼全文付费生成未验证；本次未发起真实模型请求。
- [ ] 新版本真机安装未完成：系统 INSTALL_FAILED_ABORTED/User rejected permissions；保留旧应用与原数据。

第一次模拟去重测试缺失 DocumentEntity 测试夹具导致失败，补齐与真实流程一致的文档记录后回归通过；失败日志保留。Room表结构未变，无 destructive migration。

最终交付：0.4.0 versionCode12；Debug/Release构建成功（Release未签名），72单元测试0失败，2项模拟网络/UI回归及1项实际文档本地规划通过。最新Debug签名验证通过，模拟器安装启动成功。安装包 tools/artifacts/jidiu-0.4.0-debug.apk；构建日志 tools/build-0.4.0-delivery.txt。手机安装被系统拒绝，未改动手机现有数据。未测试百炼全文件付费生成。

## GitHub 发布 0.4.0

- [x] README 重写为安装、功能、整套制卡、模型配置、Anki 导出、构建和发布签名说明。
- [x] 本地独立 Release 密钥与可选 release-signing.properties 签名配置；两者均忽略，不上传。
- [x] 签名 Release 构建与 apksigner 验证通过，72 JVM 测试0失败；Release未进行新真机安装验收。
- [x] Git 提交排除本机截图、报告、日志、下载依赖和构建产物；源码包含 Room schema 与测试。
- [x] 创建公开 GitHub 仓库 lc-052/jidiu_makecard。

发布签名与此前 Debug 不同，不能直接覆盖；先备份正式卡片，未保存草稿和API Key不在备份中。发布密钥仅在本地 tools/signing/，密码配置在 release-signing.properties，必须独立安全备份。
