package com.yike.ai.feature.card

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yike.ai.core.common.BatchCardParser
import com.yike.ai.core.common.CardRules
import com.yike.ai.core.database.*
import com.yike.ai.core.designsystem.*
import com.yike.ai.domain.model.Candidate
import com.yike.ai.feature.AppViewModel
import com.yike.ai.feature.deck.DeckEditor

@Composable
fun ManualCardScreen(vm: AppViewModel, initialDeck: String?, onSaved: (String) -> Unit) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    var deckId by rememberSaveable { mutableStateOf(initialDeck) }
    var type by rememberSaveable { mutableStateOf("BASIC") }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var cloze by rememberSaveable { mutableStateOf("") }
    var explanation by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var newDeck by remember { mutableStateOf<DeckEntity?>(null) }
    var mode by rememberSaveable { mutableStateOf("空白制卡") }
    var template by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceSection by rememberSaveable { mutableStateOf("") }
    var sourcePage by rememberSaveable { mutableStateOf<Int?>(null) }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var batchText by rememberSaveable { mutableStateOf("") }
    var separator by rememberSaveable { mutableStateOf("Tab") }
    var customSeparator by rememberSaveable { mutableStateOf("|") }
    var batchRows by remember { mutableStateOf(emptyList<com.yike.ai.core.common.BatchRow>()) }
    val document by vm.document.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val reading = mode == "文档辅助" && document != null
    var lastDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(document?.id) { document?.let { lastDocumentId = it.id } }
    LaunchedEffect(Unit) { if (document == null) lastDocumentId?.let(vm::loadDocument) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(vm::importDocument)
        }
    LaunchedEffect(decks) { if (decks.none { it.id == deckId }) deckId = decks.firstOrNull()?.id }
    val error =
        runCatching {
                CardRules.validate(
                    Candidate(type = type, front = front, back = back, clozeText = cloze)
                )
            }
            .exceptionOrNull()
            ?.message
    Page(scrollable = !reading) {
        if (reading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ settingsOpen = !settingsOpen }) {
                    Text(if (settingsOpen) "收起设置" else "制卡设置")
                }
                TextButton({
                    picker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/*",
                        )
                    )
                }) {
                    Text("更换文档")
                }
            }
        }
        if (!reading || settingsOpen) {
            Column(
                if (reading) Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                else Modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Heading("手动制卡")
                Choice("制卡方式", mode, listOf("空白制卡", "文档辅助", "批量文本")) { mode = it }
                Text(
                    "一张卡片，一个知识点。先写清楚问题，再给出简短答案。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (decks.isEmpty()) EmptyState("先创建一个牌组", "牌组用于整理和导出卡片，不需要 AI 或网络。")
                else
                    Choice(
                        "目标牌组",
                        decks.find { it.id == deckId }?.name ?: "选择",
                        decks.map { it.name },
                    ) { name ->
                        deckId = decks.find { it.name == name }?.id
                    }
                OutlinedButton({ newDeck = DeckEntity(name = "") }) { Text("创建目标牌组") }
                OutlinedButton({ deckId?.let(onSaved) }, enabled = deckId != null && !saving) {
                    Text("查看牌组卡片")
                }
                TemplatePicker(
                    template,
                    decks.find { it.id == deckId }?.templateId ?: LocalDefaultTemplate.current,
                ) {
                    template = it
                }
            }
        }
        if (mode == "文档辅助") {
            if (!reading) {
                OutlinedButton({
                    picker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain",
                            "text/markdown",
                            "application/octet-stream",
                        )
                    )
                }) {
                    Text("导入文档辅助制卡")
                }
            }
            document?.let { d ->
                DocumentCardReader(
                    d,
                    modifier = Modifier.weight(1f),
                    onUse = { target, text, block ->
                        when (target) {
                            "问题" -> front = text
                            "答案" -> back = text
                            "解释" -> explanation = text
                            "挖空" -> {
                                type = "CLOZE"
                                cloze = text
                            }
                        }
                        sourceId = d.id
                        sourceSection = block.section
                        sourcePage = block.page
                        vm.notify("已填入$target，可继续编辑并保存")
                    },
                    editor = { block ->
                        Text("编辑卡片", style = MaterialTheme.typography.titleMedium)
                        Choice(
                            "卡片类型",
                            if (type == "CLOZE") "挖空" else "基础问答",
                            listOf("基础问答", "挖空"),
                        ) {
                            type = if (it == "挖空") "CLOZE" else "BASIC"
                        }
                        if (type == "CLOZE") Field("挖空", cloze, { cloze = it }, 3)
                        else {
                            Field("问题", front, { front = it }, 2)
                            Field("答案", back, { back = it }, 3)
                        }
                        Field("解释", explanation, { explanation = it }, 2)
                        Field("标签（空格分隔）", tags, { tags = it })
                        CardPreview(
                            front,
                            back,
                            cloze,
                            explanation,
                            type,
                            template,
                            decks.find { it.id == deckId }?.templateId,
                            "${d.filename} · ${block.section}${block.page?.let{" · 第 $it 页"} ?: ""}",
                        )
                        Button(
                            {
                                val target = deckId ?: return@Button
                                saving = true
                                vm.saveManualNote(
                                    NoteEntity(
                                        deckId = target,
                                        noteType = type,
                                        front = front,
                                        back = back,
                                        clozeText = cloze,
                                        explanation = explanation,
                                        tags = tags,
                                        templateId = template,
                                        sourceDocumentId = d.id,
                                        sourceSection = block.section,
                                        sourcePage = block.page,
                                    )
                                ) { ok ->
                                    saving = false
                                    if (ok) {
                                        front = ""
                                        back = ""
                                        cloze = ""
                                        explanation = ""
                                        vm.notify("已保存，继续阅读或选取下一段")
                                    }
                                }
                            },
                            enabled = error == null && deckId != null && !saving,
                        ) {
                            Text("保存并继续阅读")
                        }
                    },
                )
            }
            return@Page
        }
        if (mode == "批量文本") {
            Text("每行一条，列顺序：问题/挖空、答案、解释（可选）、标签（可选）、类型（可选）。Cloze 第二列可留空。")
            Choice("分隔格式", separator, listOf("Tab", "CSV", "自定义")) {
                separator = it
                batchRows = emptyList()
            }
            if (separator == "自定义")
                Field(
                    "自定义分隔符",
                    customSeparator,
                    {
                        customSeparator = it
                        batchRows = emptyList()
                    },
                )
            Field(
                "批量问答文本",
                batchText,
                {
                    batchText = it
                    batchRows = emptyList()
                },
                6,
            )
            OutlinedButton({
                runCatching {
                        BatchCardParser.parse(
                            batchText,
                            when (separator) {
                                "Tab" -> "\t"
                                "CSV" -> ","
                                else -> customSeparator
                            },
                        )
                    }
                    .onSuccess { batchRows = it }
                    .onFailure { vm.notify(it.message ?: "解析失败") }
            }) {
                Text("预览解析结果")
            }
            Text(
                "${batchRows.count{it.error==null}} 条有效 · ${batchRows.count{it.error!=null}} 条需修正",
                style = MaterialTheme.typography.labelMedium,
            )
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(batchRows) { row ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "第 ${row.line} 条：${row.error ?: (row.candidate?.front?.ifBlank{row.candidate.clozeText})}",
                                color =
                                    if (row.error != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            row.candidate?.let {
                                Text(
                                    "答案：${it.back}",
                                    maxLines = 4,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${it.type} · ${it.tags.joinToString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            Button(
                {
                    val target = deckId ?: return@Button
                    saving = true
                    vm.saveBatch(batchRows.mapNotNull { it.candidate }, target, template) {
                        saving = false
                        if (it) {
                            batchText = ""
                            batchRows = emptyList()
                        }
                    }
                },
                enabled =
                    batchRows.isNotEmpty() &&
                        batchRows.none { it.error != null } &&
                        deckId != null &&
                        !saving,
            ) {
                Text("保存全部有效卡片")
            }
            return@Page
        }
        val types = mapOf("基础问答" to "BASIC", "挖空" to "CLOZE", "对比" to "COMPARISON")
        Choice("卡片类型", types.entries.first { it.value == type }.key, types.keys.toList()) {
            type = types.getValue(it)
        }
        if (type == "CLOZE") {
            Text("用 {{c1::内容}} 挖空；c1、c2 等不同序号会生成独立的 Anki 卡片。")
            Field("挖空", cloze, { cloze = it }, 4)
        } else {
            Field("问题", front, { front = it }, 2)
            Field("答案", back, { back = it }, 4)
        }
        Text("排版支持：**重点**、换行列表（- 内容）、空行分段、`公式或代码`。", style = MaterialTheme.typography.bodySmall)
        Field("解释（可选，预览时展开查看）", explanation, { explanation = it }, 3)
        Field("标签（空格分隔）", tags, { tags = it })
        Toggle("显示卡片预览", preview) { preview = it }
        if (preview)
            CardPreview(
                front,
                back,
                cloze,
                explanation,
                type,
                template,
                decks.find { it.id == deckId }?.templateId,
                sourceSection + (sourcePage?.let { " · 第 $it 页" } ?: ""),
            )
        if (error != null)
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Button(
            {
                val target = deckId ?: return@Button
                saving = true
                vm.saveManualNote(
                    NoteEntity(
                        deckId = target,
                        noteType = type,
                        front = front,
                        back = back,
                        clozeText = cloze,
                        explanation = explanation,
                        tags = tags,
                        templateId = template,
                        sourceDocumentId = sourceId,
                        sourceSection = sourceSection,
                        sourcePage = sourcePage,
                    )
                ) { success ->
                    saving = false
                    if (success) {
                        front = ""
                        back = ""
                        cloze = ""
                        explanation = ""
                        vm.notify("已保存，可以继续创建下一张")
                    }
                }
            },
            Modifier.fillMaxWidth(),
            enabled = error == null && decks.any { it.id == deckId } && !saving,
        ) {
            Text(if (saving) "正在保存" else "保存卡片")
        }
        Spacer(Modifier.height(8.dp))
    }
    newDeck?.let { d ->
        DeckEditor(d, decks, { newDeck = null }) {
            vm.saveDeck(it) {
                deckId = it.id
                newDeck = null
            }
        }
    }
}
