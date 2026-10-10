package com.yike.ai.feature.generation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yike.ai.core.database.*
import com.yike.ai.core.designsystem.*
import com.yike.ai.core.document.GenerationBatchPlanner
import com.yike.ai.core.network.AiClient
import com.yike.ai.domain.model.*
import com.yike.ai.feature.AppViewModel
import com.yike.ai.feature.deck.DeckEditor

@Composable
fun GenerationScreen(
    vm: AppViewModel,
    onModels: () -> Unit,
    onAudit: (String) -> Unit,
    onProgress: (String) -> Unit,
) {
    val document by vm.document.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var paste by rememberSaveable { mutableStateOf("") }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var count by
        rememberSaveable(prefs.generationCount) { mutableStateOf(prefs.generationCount.toString()) }
    var deckId by rememberSaveable { mutableStateOf<String?>(null) }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(prefs.generationMode) { mutableStateOf(prefs.generationMode) }
    var language by
        rememberSaveable(prefs.generationLanguage) { mutableStateOf(prefs.generationLanguage) }
    var difficulty by rememberSaveable { mutableStateOf("2") }
    var cardMode by rememberSaveable { mutableStateOf("问答（推荐）") }
    var cloze by
        rememberSaveable(prefs.generationCloze) { mutableStateOf(prefs.generationCloze.toFloat()) }
    var explain by rememberSaveable { mutableStateOf(true) }
    var sources by rememberSaveable { mutableStateOf(true) }
    var tags by rememberSaveable { mutableStateOf(true) }
    var chapter by rememberSaveable(document?.id) { mutableStateOf("全部章节") }
    var batchSize by rememberSaveable { mutableStateOf("8") }
    var wholeDocument by rememberSaveable { mutableStateOf(true) }
    var createDocumentDeck by rememberSaveable { mutableStateOf(true) }
    val wholePlan =
        remember(document, batchSize) {
            document?.let {
                runCatching { GenerationBatchPlanner.wholeDocumentPlan(it, batchSize.toInt()) }
                    .getOrNull()
            }
        }
    var fastMode by rememberSaveable { mutableStateOf(true) }
    var consent by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf<String?>(null) }
    var deckEditor by remember { mutableStateOf<DeckEntity?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.importDocument(it) }
        }
    LaunchedEffect(decks, providers) {
        if (decks.none { it.id == deckId }) deckId = decks.firstOrNull()?.id
        if (providers.none { it.id == providerId })
            providerId = (providers.firstOrNull { it.isDefault } ?: providers.firstOrNull())?.id
    }
    val blockers = buildList {
        if (document == null) add("请先导入资料并解析出预览；粘贴文本后需要点击“解析并预览”。")
        if (!(wholeDocument && createDocumentDeck) && decks.none { it.id == deckId })
            add("请先创建或选择目标牌组，用于保存审核后的卡片。")
        if (providers.none { it.id == providerId }) add("请先保存一个 AI 模型配置。")
        if (!wholeDocument && count.toIntOrNull() !in 1..500) add("目标笔记数必须为 1–500。")
        if (wholeDocument && document != null && wholePlan == null) add("资料超过整套处理范围，请按章节拆分文件。")
        if (language.isBlank()) add("请填写生成语言。")
        if (busy) add("正在处理资料或请求，请等待当前操作完成。")
    }
    Page {
        Heading("从资料到长期记忆")
        Text("导入资料，生成清晰的卡片草稿，审核后保存到牌组。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "1 资料选择  →  2 制卡参数  →  3 生成进度  →  4 卡片审核",
            style = MaterialTheme.typography.labelMedium,
        )
        Heading("资料选择")
        if (providers.isEmpty()) {
            EmptyState("配置你的 AI 模型", "支持官方 OpenAI 与兼容 HTTPS 接口；密钥由你提供。手动制卡无需模型或网络。")
            Button(onModels) { Text("配置模型") }
        }
        OutlinedButton(
            {
                picker.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain",
                        "text/markdown",
                        "application/octet-stream",
                    )
                )
            },
            Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Text("导入 PDF / DOCX / TXT / Markdown")
        }
        Field("或粘贴资料", paste, { paste = it }, 4)
        TextButton({ vm.paste(paste) }, enabled = paste.isNotBlank() && !busy) { Text("解析并预览") }
        if (busy) Text("正在读取或处理，请稍候；结果与错误会显示在此页面。")
        document?.let { d ->
            Panel {
                Text(
                    "${d.filename} · ${d.characterCount} 字 · ${d.blocks.map{it.section}.distinct().size} 个章节",
                    style = MaterialTheme.typography.titleMedium,
                )
                d.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    d.blocks.forEach { b ->
                        Text(
                            "${b.section}${b.page?.let{" · 第 $it 页"}?:""}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(b.text)
                    }
                }
                TextButton({ vm.clearDocument() }) { Text("清除预览") }
            }
        }
        Heading("制卡参数")
        Choice(
            "制作范围",
            if (wholeDocument) "整份资料 · 自动制作整套" else "自定义数量",
            listOf("整份资料 · 自动制作整套", "自定义数量"),
        ) {
            wholeDocument = it.startsWith("整份资料")
        }
        if (wholeDocument) {
            Toggle("按文件名自动创建牌组", createDocumentDeck) { createDocumentDeck = it }
            wholePlan?.let { plan ->
                Text("预计 ${plan.sumOf { it.target }} 张 · ${plan.count { it.target > 0 }} 批 · 全文处理")
            }
            Text(
                "按资料密度自动分配，不强制凑数。分批结果持续保存；取消或断网后可以恢复，最终确认保存整套。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!wholeDocument)
            document?.let {
                Choice(
                    "生成范围",
                    chapter,
                    listOf("全部章节") + it.blocks.map { b -> b.section }.distinct(),
                ) {
                    chapter = it
                }
            }
        Choice("每批最多张数", batchSize, listOf("4", "8", "12", "16", "24")) { batchSize = it }
        if (!(wholeDocument && createDocumentDeck)) {
            if (decks.isEmpty()) EmptyState("还没有目标牌组", "卡片审核后需要保存到牌组。可以直接在这里创建，无需重新导入资料。")
            OutlinedButton({ deckEditor = DeckEntity(name = "") }, enabled = !busy) {
                Text("创建目标牌组")
            }
            if (decks.isNotEmpty())
                Choice(
                    "目标牌组",
                    decks.find { it.id == deckId }?.name ?: "选择",
                    decks.map { it.name },
                ) {
                    deckId = decks.find { d -> d.name == it }?.id
                }
        }
        if (providers.isNotEmpty())
            Choice(
                "模型",
                providers.find { it.id == providerId }?.name ?: "选择",
                providers.map { it.name },
            ) {
                providerId = providers.find { p -> p.name == it }?.id
            }
        Choice("制卡模式", mode, listOf("基础理解", "期末考试", "深入理解")) { mode = it }
        if (!wholeDocument) Field("目标笔记数（1–500，不强制凑数）", count, { count = it })
        Field("生成语言", language, { language = it })
        Choice("难度", difficulty, listOf("1", "2", "3", "4", "5")) { difficulty = it }
        Choice("卡型", cardMode, listOf("问答（推荐）", "问答与挖空混合")) { cardMode = it }
        if (cardMode == "问答与挖空混合") {
            Text("挖空最多 ${cloze.toInt()}%，只用于适合的短术语与关键短语")
            Slider(cloze, { cloze = it }, valueRange = 0f..100f, steps = 9)
        } else Text("只生成可翻面的问答卡，适合解释、比较、原因和步骤。", style = MaterialTheme.typography.bodySmall)
        Toggle("生成解释", explain) { explain = it }
        Toggle("展示来源信息", sources) { sources = it }
        Toggle("生成标签", tags) { tags = it }
        if (providers.find { it.id == providerId }?.let(AiClient::supportsFastGeneration) == true) {
            Toggle("快速制卡", fastMode) { fastMode = it }
            Text("关闭本次请求的深度思考；仍核对原文和卡片结构。复杂资料可关闭此选项。", style = MaterialTheme.typography.bodySmall)
        }
        if (blockers.isNotEmpty()) {
            Panel {
                Text("生成前还需完成", style = MaterialTheme.typography.titleMedium)
                blockers.forEach { Text(it) }
            }
        }
        Button({ consent = true }, Modifier.fillMaxWidth(), enabled = blockers.isEmpty()) {
            Text("确认资料并生成")
        }
        if (tasks.isNotEmpty()) {
            Heading("生成任务与草稿")
            tasks.forEach { t ->
                TaskProgressPanel(vm, t, onAudit, onProgress)
                Row {
                    TextButton({ onProgress(t.id) }) { Text("打开生成进度") }
                    TextButton({ discard = t.id }) { Text("丢弃") }
                }
            }
        }
    }
    deckEditor?.let { newDeck ->
        DeckEditor(newDeck, decks, { deckEditor = null }) { deck ->
            vm.saveDeck(deck) {
                deckId = deck.id
                deckEditor = null
            }
        }
    }
    if (consent)
        Confirm(
            "上传所选资料？",
            "预览中的文本将发送给 ${providers.find{it.id==providerId}?.name?:"所选服务商"}，由该服务商处理，可能产生费用。只上传你确认的文本。请勿上传未经授权的敏感资料。",
            { consent = false },
        ) {
            consent = false
            val n = if (wholeDocument) wholePlan?.sumOf { it.target } else count.toIntOrNull()
            if (n == null || n !in 1..500) vm.notify("目标数量必须为 1–500")
            else
                vm.generate(
                    GenerationConfig(
                        deckId = deckId ?: "",
                        targetCount = n,
                        language = language,
                        clozePercent = if (cardMode == "问答（推荐）") 0 else cloze.toInt(),
                        mode = mode,
                        difficulty = difficulty.toInt(),
                        explanations = explain,
                        sources = sources,
                        tags = tags,
                        batchSize = batchSize.toInt(),
                        sections =
                            if (wholeDocument || chapter == "全部章节") emptyList()
                            else listOf(chapter),
                        fastMode = fastMode,
                        wholeDocument = wholeDocument,
                        createDocumentDeck = wholeDocument && createDocumentDeck,
                    ),
                    providerId ?: "",
                ) {
                    onProgress(it)
                }
        }
    discard?.let { id ->
        Confirm("丢弃任务及草稿？", "已保存的正式卡片会保留；该任务的未保存草稿将删除。", { discard = null }) {
            vm.deleteTask(id)
            discard = null
        }
    }
}

fun stageName(s: String) =
    when (s) {
        "QUEUED" -> "等待网络与执行"
        "EXTRACTING" -> "提取候选知识点"
        "ALLOCATING" -> "按知识密度分配数量"
        "GENERATING" -> "按章节生成"
        "VALIDATING" -> "验证与去重"
        "REVIEW" -> "等待人工审核"
        "FAILED" -> "失败"
        "CANCELLED" -> "已取消，可恢复"
        else -> s
    }

@Composable
fun AuditScreen(vm: AppViewModel, id: String) {
    val drafts by remember(id) { vm.drafts(id) }.collectAsStateWithLifecycle(emptyList())
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val task = tasks.find { it.id == id }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GenerationDraftEntity?>(null) }
    var deleting by remember { mutableStateOf<GenerationDraftEntity?>(null) }
    var refine by remember { mutableStateOf<Pair<GenerationDraftEntity, String>?>(null) }
    val candidates =
        remember(drafts) { drafts.map { codec.decodeFromString<Candidate>(it.payload) } }
    val decks by vm.decks.collectAsStateWithLifecycle()
    val config =
        remember(task?.configuration) {
            task?.let {
                runCatching { codec.decodeFromString<GenerationConfig>(it.configuration) }
                    .getOrNull()
            }
        }
    var showCoverage by rememberSaveable(id) { mutableStateOf(false) }
    var showTaskMessage by rememberSaveable(id) { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("audit-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${drafts.size} 条草稿 · Basic ${candidates.count{it.type=="BASIC"}} · Cloze ${candidates.count{it.type=="CLOZE"}} · 对比 ${candidates.count{it.type=="COMPARISON"}}",
                    style = MaterialTheme.typography.titleMedium,
                )
                val sections =
                    candidates
                        .flatMap { it.sourceSection.lines() }
                        .filter { it.isNotBlank() }
                        .distinct()
                TextButton({ showCoverage = !showCoverage }) {
                    Text("覆盖 ${sections.size} 个章节 · ${if(showCoverage) "收起" else "展开"}")
                }
                if (showCoverage)
                    Text(sections.joinToString("\n"), style = MaterialTheme.typography.labelMedium)
                task?.let { t ->
                    Text("${stageName(t.status)} · ${t.progress}%")
                    t.errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = if (showTaskMessage) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton({ showTaskMessage = !showTaskMessage }) {
                            Text(if (showTaskMessage) "收起任务提示" else "查看完整任务提示")
                        }
                    }
                }
                FlowRow {
                    Button(
                        { vm.saveDrafts(id) },
                        enabled = drafts.any { it.selected && !it.saved } && !busy,
                    ) {
                        Text("保存选中卡片")
                    }
                    TextButton({
                        val unsaved = drafts.filter { !it.saved }
                        val select = unsaved.any { !it.selected }
                        unsaved.filter { it.selected != select }.forEach { vm.selectDraft(it) }
                    }) {
                        Text("全选/取消")
                    }
                }
                if (drafts.isEmpty()) EmptyState("尚无可审核草稿", "生成的卡片会按分块保存。返回 AI 制卡可取消或恢复任务。")
            }
        }
        items(drafts, key = { it.id }) { d ->
            val c = remember(d.payload) { codec.decodeFromString<Candidate>(d.payload) }
            var showSource by remember(d.payload) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().animateItem()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Checkbox(d.selected, { vm.selectDraft(d) }, enabled = !d.saved)
                        Text(if (d.saved) "已保存" else c.type, Modifier.padding(top = 12.dp))
                    }
                    CardPreview(
                        c.front,
                        c.back,
                        c.clozeText,
                        c.explanation,
                        c.type,
                        templateId = c.templateId,
                        deckTemplateId = decks.find { it.id == config?.deckId }?.templateId,
                        metadata =
                            c.sourceSection.lines().firstOrNull().orEmpty() +
                                (c.sourcePage?.let { " · 第 $it 页" } ?: ""),
                    )
                    Text(
                        "${c.sourceSection.lines().firstOrNull().orEmpty()} ${c.sourcePage?.let{"· 第 $it 页"}?:""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (c.sourceExcerpt.isNotBlank()) {
                        TextButton({ showSource = !showSource }) {
                            Text(if (showSource) "收起原文依据" else "查看原文依据")
                        }
                        if (showSource)
                            Text(c.sourceExcerpt, style = MaterialTheme.typography.bodyMedium)
                    }
                    c.validationWarnings.forEach {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text("标签：${c.tags.joinToString()}")
                    if (!d.saved) {
                        Row {
                            TextButton({ editing = d }) { Text("编辑") }
                            TextButton({ deleting = d }) { Text("删除") }
                        }
                        Choice("AI 修改", "选择操作", listOf("重新生成", "简化", "拆分", "添加例子")) {
                            refine = d to it
                        }
                    }
                }
            }
        }
    }
    editing?.let { d ->
        val decks by vm.decks.collectAsStateWithLifecycle()
        val deckId = task?.let { codec.decodeFromString<GenerationConfig>(it.configuration).deckId }
        CandidateEditor(
            codec.decodeFromString(d.payload),
            { editing = null },
            decks.find { it.id == deckId }?.templateId,
        ) {
            vm.editDraft(d, it) { editing = null }
        }
    }
    deleting?.let { d ->
        Confirm("删除草稿？", "仅删除此草稿，不影响其他已审核卡片。", { deleting = null }) {
            vm.deleteDraft(d)
            deleting = null
        }
    }
    refine?.let { (d, action) ->
        Confirm(
            "AI $action？",
            "将原卡和相关资料上传给原任务服务商，结果只替换此未保存草稿。拆分会追加新草稿；新增事实必须人工核对。",
            { refine = null },
        ) {
            refine = null
            vm.refine(d, action)
        }
    }
}

@Composable
fun CandidateEditor(
    c: Candidate,
    dismiss: () -> Unit,
    deckTemplate: String? = null,
    save: (Candidate) -> Unit,
) {
    var front by remember { mutableStateOf(c.front) }
    var back by remember { mutableStateOf(c.back) }
    var cloze by remember { mutableStateOf(c.clozeText) }
    var explanation by remember { mutableStateOf(c.explanation) }
    var tags by rememberSaveable { mutableStateOf(c.tags.joinToString(" ")) }
    var template by remember { mutableStateOf(c.templateId) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("编辑草稿") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (c.type == "CLOZE") Field("挖空", cloze, { cloze = it }, 3)
                else {
                    Field("问题", front, { front = it }, 2)
                    Field("答案", back, { back = it }, 3)
                }
                Field("解释", explanation, { explanation = it }, 3)
                Field("标签", tags, { tags = it })
                TemplatePicker(template, deckTemplate ?: LocalDefaultTemplate.current) {
                    template = it
                }
                CardPreview(front, back, cloze, explanation, c.type, template, deckTemplate)
            }
        },
        confirmButton = {
            TextButton({
                save(
                    c.copy(
                        front = front,
                        back = back,
                        clozeText = cloze,
                        explanation = explanation,
                        tags = tags.split(Regex("[,，\\s]+")),
                        templateId = template,
                    )
                )
            }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}
