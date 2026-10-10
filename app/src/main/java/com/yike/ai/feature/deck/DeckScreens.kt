package com.yike.ai.feature.deck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yike.ai.core.database.*
import com.yike.ai.core.designsystem.*
import com.yike.ai.feature.AppViewModel

@Composable
fun DecksScreen(vm: AppViewModel, onDeck: (String) -> Unit) {
    val decks by vm.deckStats.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("最近更新") }
    var editor by remember { mutableStateOf<DeckEntity?>(null) }
    var deleting by remember { mutableStateOf<DeckEntity?>(null) }
    val filtered =
        decks
            .filter {
                it.deck.name.contains(search, true) || it.deck.description.contains(search, true)
            }
            .let {
                when (sort) {
                    "名称" -> it.sortedBy { it.deck.name }
                    "卡片数量" -> it.sortedByDescending { it.cardCount }
                    else -> it
                }
            }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Field("搜索牌组", search, { search = it })
        Choice("排序", sort, listOf("最近更新", "名称", "卡片数量")) { sort = it }
        Button({ editor = DeckEntity(name = "") }, Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null)
            Text("创建牌组")
        }
        if (filtered.isEmpty()) EmptyState("还没有匹配的牌组", "创建一个牌组，保存你想记住的内容。")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered, key = { it.deck.id }) { s ->
                Card(
                    onClick = { onDeck(s.deck.id) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(s.deck.name, style = MaterialTheme.typography.titleLarge)
                        if (s.deck.description.isNotBlank()) Text(s.deck.description)
                        Text("${s.noteCount} 条笔记 · ${s.cardCount} 张卡片")
                        Row {
                            TextButton({ editor = s.deck }) { Text("编辑") }
                            TextButton({ deleting = s.deck }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
    editor?.let { d ->
        DeckEditor(d, vm.decks.collectAsStateWithLifecycle().value, { editor = null }) {
            vm.saveDeck(it) { editor = null }
        }
    }
    deleting?.let { d ->
        Confirm("删除 ${d.name}？", "牌组、子牌组及其中的所有卡片将被删除。", { deleting = null }) {
            vm.deleteDeck(d.id)
            deleting = null
        }
    }
}

@Composable
fun DeckEditor(
    d: DeckEntity,
    decks: List<DeckEntity>,
    dismiss: () -> Unit,
    save: (DeckEntity) -> Unit,
) {
    var name by remember(d.id) { mutableStateOf(d.name) }
    var description by remember(d.id) { mutableStateOf(d.description) }
    var parent by remember(d.id) { mutableStateOf(d.parentDeckId) }
    var template by remember(d.id) { mutableStateOf(d.templateId) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("牌组信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("名称", name, { name = it })
                Field("描述", description, { description = it }, 3)
                TemplatePicker(template) { template = it }
                Choice(
                    "上级牌组",
                    decks.find { it.id == parent }?.name ?: "无",
                    listOf("无") +
                        decks.filter { it.id != d.id && it.parentDeckId != d.id }.map { it.name },
                ) { value ->
                    parent = decks.find { it.name == value }?.id
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    save(
                        d.copy(
                            name = name,
                            description = description,
                            parentDeckId = parent,
                            templateId = template,
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}

@Composable
fun DeckDetailScreen(
    vm: AppViewModel,
    id: String,
    onAdd: () -> Unit,
    onPreview: () -> Unit,
    onExport: (String) -> Unit,
    onShare: () -> Unit,
) {
    val notes by remember(id) { vm.notes(id) }.collectAsStateWithLifecycle(emptyList())
    val decks by vm.decks.collectAsStateWithLifecycle()
    val deck = decks.find { it.id == id }
    var editor by remember { mutableStateOf<NoteEntity?>(null) }
    var viewing by remember { mutableStateOf<NoteEntity?>(null) }
    var search by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("全部章节") }
    var tag by remember { mutableStateOf("全部标签") }
    val chapters = notes.map { it.sourceSection }.filter { it.isNotBlank() }.distinct()
    val tags =
        notes.flatMap { it.tags.split(Regex("[,，\\s]+")) }.filter { it.isNotBlank() }.distinct()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(deck?.name ?: "牌组", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onAdd) { Text("添加卡片") }
            OutlinedButton(onPreview) { Text("预览全部卡片") }
        }
        Field("搜索问题、标签或章节", search, { search = it })
        if (chapters.isNotEmpty()) Choice("章节", chapter, listOf("全部章节") + chapters) { chapter = it }
        if (tags.isNotEmpty()) Choice("标签", tag, listOf("全部标签") + tags) { tag = it }
        Row {
            TextButton({ onExport("APKG") }) { Text("APKG") }
            TextButton({ onExport("CSV") }) { Text("CSV") }
            TextButton(onShare) { Text("分享") }
            TextButton({
                selected =
                    if (selected.size == notes.size) emptySet() else notes.map { it.id }.toSet()
            }) {
                Text("全选")
            }
        }
        if (selected.isNotEmpty())
            Row {
                Text("已选 ${selected.size}", Modifier.weight(1f))
                TextButton({ move = true }) { Text("移动") }
                TextButton({ confirmDelete = true }) { Text("删除") }
            }
        if (notes.isEmpty()) EmptyState("这个牌组还没有卡片", "添加一条问答笔记，或在 AI 制卡中选中此牌组。")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                notes.filter {
                    listOf(it.front, it.clozeText, it.tags, it.sourceSection).any { v ->
                        v.contains(search, true)
                    } &&
                        (chapter == "全部章节" || it.sourceSection == chapter) &&
                        (tag == "全部标签" || tag in it.tags.split(Regex("[,，\\s]+")))
                },
                key = { it.id },
            ) { n ->
                Card(Modifier.fillMaxWidth().animateItem()) {
                    Row(Modifier.padding(12.dp)) {
                        Checkbox(
                            n.id in selected,
                            { checked ->
                                selected = if (checked) selected + n.id else selected - n.id
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (n.noteType == "CLOZE") n.clozeText else n.front,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                "${n.noteType} · ${n.tags} ${n.sourceSection}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Row {
                                TextButton({ viewing = n }) { Text("预览") }
                                TextButton({ editor = n }) { Text("编辑") }
                            }
                        }
                    }
                }
            }
        }
    }
    editor?.let { n ->
        NoteEditor(n, { editor = null }, deck?.templateId) { vm.saveNote(it) { editor = null } }
    }
    viewing?.let { n ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("卡片预览") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    CardPreview(
                        n.front,
                        n.back,
                        n.clozeText,
                        n.explanation,
                        n.noteType,
                        n.templateId,
                        deck?.templateId,
                        n.sourceSection + (n.sourcePage?.let { " · 第 $it 页" } ?: ""),
                    )
                }
            },
            confirmButton = { TextButton({ viewing = null }) { Text("关闭") } },
        )
    }
    if (confirmDelete)
        Confirm("删除选中的笔记？", "将删除 ${selected.size} 条笔记及其所有挖空卡片和关联数据。", { confirmDelete = false }) {
            vm.deleteNotes(selected.toList())
            selected = emptySet()
            confirmDelete = false
        }
    if (move)
        AlertDialog(
            onDismissRequest = { move = false },
            title = { Text("移动到牌组") },
            text = {
                Column {
                    decks
                        .filter { it.id != id }
                        .forEach { d ->
                            TextButton({
                                vm.moveNotes(selected.toList(), d.id)
                                selected = emptySet()
                                move = false
                            }) {
                                Text(d.name)
                            }
                        }
                }
            },
            confirmButton = { TextButton({ move = false }) { Text("取消") } },
        )
}

@Composable
fun NoteEditor(
    n: NoteEntity,
    dismiss: () -> Unit,
    deckTemplate: String? = null,
    save: (NoteEntity) -> Unit,
) {
    var type by remember(n.id) { mutableStateOf(n.noteType) }
    var front by remember(n.id) { mutableStateOf(n.front) }
    var back by remember(n.id) { mutableStateOf(n.back) }
    var cloze by remember(n.id) { mutableStateOf(n.clozeText) }
    var explanation by remember(n.id) { mutableStateOf(n.explanation) }
    var tags by remember(n.id) { mutableStateOf(n.tags) }
    var template by remember(n.id) { mutableStateOf(n.templateId) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("编辑卡片") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).then(Modifier).androidScroll(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Choice("卡型", type, listOf("BASIC", "CLOZE", "COMPARISON")) { type = it }
                if (type == "CLOZE") {
                    Field("挖空文本", cloze, { cloze = it }, 3)
                    Text("语法：{{c1::内容}}；不同序号产生不同卡片")
                } else {
                    Field("问题", front, { front = it }, 2)
                    Field("答案", back, { back = it }, 3)
                }
                Field("解释", explanation, { explanation = it }, 2)
                Field("标签（空格分隔）", tags, { tags = it })
                TemplatePicker(template, deckTemplate ?: LocalDefaultTemplate.current) {
                    template = it
                }
                CardPreview(
                    front,
                    back,
                    cloze,
                    explanation,
                    type,
                    templateId = template,
                    deckTemplateId = deckTemplate,
                )
            }
        },
        confirmButton = {
            TextButton({
                save(
                    n.copy(
                        noteType = type,
                        front = front,
                        back = back,
                        clozeText = cloze,
                        explanation = explanation,
                        tags = tags,
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

@Composable private fun Modifier.androidScroll() = this.verticalScroll(rememberScrollState())
