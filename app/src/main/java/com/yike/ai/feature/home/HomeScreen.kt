package com.yike.ai.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yike.ai.core.designsystem.*
import com.yike.ai.feature.AppViewModel

@Composable
fun HomeScreen(vm: AppViewModel, onManual: () -> Unit, onDeck: (String) -> Unit, onAi: () -> Unit) {
    val decks by vm.deckStats.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val notes by vm.noteList.collectAsStateWithLifecycle()
    val drafts by vm.pendingDraftCount.collectAsStateWithLifecycle()
    Page {
        Heading("制卡工作台")
        Text("把知识整理成清晰、好看的卡片。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(cards.size.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("张已保存卡片")
                }
                Column {
                    Text(decks.size.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("个牌组")
                }
                Column {
                    Text(drafts.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("条待审核草稿")
                }
            }
            Button(onManual, Modifier.fillMaxWidth()) { Text("手动制卡") }
            OutlinedButton(onAi, Modifier.fillMaxWidth()) { Text("把资料变成记忆卡片") }
        }
        if (decks.isEmpty())
            EmptyState("开始制作第一张卡片", "手动填写问答或挖空，也可以导入资料让 AI 生成草稿。保存后按牌组整理，随时导出到 Anki。")
        else {
            Heading("最近编辑的牌组")
            decks.take(3).forEach { d ->
                Card(onClick = { onDeck(d.deck.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(d.deck.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${d.noteCount} 条笔记 · ${d.cardCount} 张卡片",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (notes.isNotEmpty()) {
            Heading("最近制作")
            notes.take(4).forEach { n ->
                Card(onClick = { onDeck(n.deckId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (n.noteType == "CLOZE") n.clozeText else n.front,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            decks.find { it.deck.id == n.deckId }?.deck?.name ?: "牌组",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
