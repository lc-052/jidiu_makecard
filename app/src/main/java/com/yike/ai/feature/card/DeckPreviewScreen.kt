package com.yike.ai.feature.card

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yike.ai.core.designsystem.*
import com.yike.ai.feature.AppViewModel

@Composable
fun DeckPreviewScreen(vm: AppViewModel, deckId: String) {
    val notes by remember(deckId) { vm.notes(deckId) }.collectAsStateWithLifecycle(emptyList())
    val decks by vm.decks.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(0) }
    Page {
        Heading(decks.find { it.id == deckId }?.name ?: "卡片预览")
        Text("仅预览卡片，不评分，不改变复习进度。", style = MaterialTheme.typography.bodySmall)
        if (notes.isEmpty()) EmptyState("还没有卡片", "先手动制卡或保存 AI 草稿。")
        else {
            val current = index.coerceIn(0, notes.lastIndex)
            val n = notes[current]
            Text("${current+1} / ${notes.size}")
            key(n.id) {
                CardPreview(
                    n.front,
                    n.back,
                    n.clozeText,
                    n.explanation,
                    n.noteType,
                    n.templateId,
                    decks.find { it.id == deckId }?.templateId,
                    "${n.tags} ${n.sourceSection}${n.sourcePage?.let{" · 第 $it 页"} ?: ""}",
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton({ index = current - 1 }, enabled = current > 0) { Text("上一张") }
                Button({ index = current + 1 }, enabled = current < notes.lastIndex) { Text("下一张") }
            }
        }
    }
}
