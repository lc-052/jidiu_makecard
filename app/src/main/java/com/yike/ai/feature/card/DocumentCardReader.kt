package com.yike.ai.feature.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.yike.ai.core.common.BatchCardParser
import com.yike.ai.core.designsystem.*
import com.yike.ai.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCardReader(
    document: DocumentContent,
    modifier: Modifier = Modifier,
    onUse: (String, String, TextBlock) -> Unit,
    editor: @Composable (TextBlock) -> Unit,
) {
    var blockIndex by rememberSaveable(document.id) { mutableStateOf(-1) }
    val paragraphs =
        remember(document) {
            document.blocks.flatMap { b ->
                b.text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.map { b.copy(text = it) }
            }
        }
    var selected by
        rememberSaveable(document.id, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }
    var gap by rememberSaveable { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(document.filename, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Text("点击段落制卡 · 长按面板原文选择文字", style = MaterialTheme.typography.bodySmall)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).testTag("document-reading-area"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(paragraphs) { index, b ->
                OutlinedCard(
                    Modifier.fillMaxWidth().clickable {
                        blockIndex = index
                        selected = TextFieldValue(b.text, TextRange(0, b.text.length))
                        gap = ""
                    }
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${b.section}${b.page?.let{" · 第 $it 页"} ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(b.text)
                    }
                }
            }
        }
    }
    if (blockIndex in paragraphs.indices)
        ModalBottomSheet(
            onDismissRequest = { blockIndex = -1 },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.heightIn(max = 700.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val b = paragraphs[blockIndex]
                Text(
                    "选取原文 · ${b.section}${b.page?.let{" · 第 $it 页"} ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    selected,
                    { selected = it },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
                    label = { Text("长按选取文字") },
                )
                val excerpt =
                    if (selected.selection.collapsed) selected.text
                    else selected.text.substring(selected.selection.min, selected.selection.max)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("问题", "答案", "解释").forEach { target ->
                        TextButton({ onUse(target, excerpt, b) }) { Text("填入$target") }
                    }
                }
                Field("挖空内容（必须是原文中的连续文字）", gap, { gap = it }, 2)
                val error =
                    runCatching { BatchCardParser.cloze(b.text, gap) }.exceptionOrNull()?.message
                if (error != null) Text(error, style = MaterialTheme.typography.bodySmall)
                Button(
                    { onUse("挖空", BatchCardParser.cloze(b.text, gap), b) },
                    enabled = error == null,
                ) {
                    Text("选中文字生成填空卡")
                }
                HorizontalDivider()
                editor(b)
                Spacer(Modifier.height(20.dp))
            }
        }
}
