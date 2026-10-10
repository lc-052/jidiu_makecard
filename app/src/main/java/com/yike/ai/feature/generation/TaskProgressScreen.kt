package com.yike.ai.feature.generation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yike.ai.core.database.GenerationTaskEntity
import com.yike.ai.core.designsystem.*
import com.yike.ai.domain.model.*
import com.yike.ai.feature.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GenerationStage {
    QUEUED,
    EXTRACTING,
    ALLOCATING,
    GENERATING,
    VALIDATING,
    REVIEW,
    FAILED,
    CANCELLED;

    val active
        get() = this !in listOf(REVIEW, FAILED, CANCELLED)
}

@Composable
fun TaskProgressPanel(
    vm: AppViewModel,
    task: GenerationTaskEntity,
    onAudit: (String) -> Unit,
    onProgress: (String) -> Unit = onAudit,
) {
    val chunks by remember(task.id) { vm.chunks(task.id) }.collectAsStateWithLifecycle(emptyList())
    val drafts by remember(task.id) { vm.drafts(task.id) }.collectAsStateWithLifecycle(emptyList())
    val decks by vm.decks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val work by
        remember(task.id) {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkFlow("generation-${task.id}")
            }
            .collectAsStateWithLifecycle(emptyList())
    var details by rememberSaveable(task.id) { mutableStateOf(false) }
    var completeConsent by rememberSaveable(task.id) { mutableStateOf(false) }
    var previewIndex by rememberSaveable(task.id) { mutableIntStateOf(0) }
    var name by remember(task.documentId) { mutableStateOf("资料") }
    LaunchedEffect(task.documentId) {
        name =
            withContext(Dispatchers.IO) {
                vm.app.db.generation().document(task.documentId)?.filename ?: "资料"
            }
    }
    val requested = chunks.filter { it.targetCount > 0 }
    val done = requested.count { it.completed }
    val sections =
        requested
            .flatMap { batch ->
                batch.section.lines().filter { it.isNotBlank() }.distinct().map { it to batch }
            }
            .groupBy({ it.first }, { it.second })
    val doneSections = sections.count { (_, batch) -> batch.all { it.completed } }
    val storedStage =
        runCatching { GenerationStage.valueOf(task.status) }.getOrDefault(GenerationStage.QUEUED)
    val waitingForSystem =
        storedStage.active &&
            work.isNotEmpty() &&
            work.all { it.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED) }
    val stage = if (waitingForSystem) GenerationStage.QUEUED else storedStage
    val config =
        runCatching { codec.decodeFromString<GenerationConfig>(task.configuration) }.getOrNull()
    val metrics =
        runCatching { codec.decodeFromString<GenerationMetrics>(task.metrics) }
            .getOrDefault(GenerationMetrics())
    Panel {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text(
            if (waitingForSystem) "等待后台执行或恢复" else stageName(task.status),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.titleMedium,
        )
        Text("${drafts.size} 张草稿 · 完成 $done/${requested.size} 批")
        if (waitingForSystem)
            Text("系统已暂停请求，完成的草稿仍可审核；恢复后只处理未完成批次。", style = MaterialTheme.typography.bodySmall)
        if (requested.isNotEmpty())
            LinearProgressIndicator(
                progress = { done.toFloat() / requested.size },
                modifier = Modifier.fillMaxWidth(),
            )
        else if (stage.active) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            if (chunks.isEmpty()) "正在准备资料与批次" else "进度按实际完成批次计算，单次请求耗时无法预估。",
            style = MaterialTheme.typography.bodySmall,
        )
        if (task.currentSection.isNotBlank()) {
            val current = task.currentSection.lines().filter { it.isNotBlank() }
            Text(
                "当前：${current.firstOrNull().orEmpty()}${if(current.size > 1) " 等 ${current.size} 个章节" else ""}"
            )
        }
        task.errorMessage?.let {
            Text(
                it,
                color =
                    if (stage == GenerationStage.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (drafts.isNotEmpty()) {
            val index = previewIndex.coerceIn(0, drafts.lastIndex)
            val c =
                remember(drafts[index].payload) {
                        runCatching { codec.decodeFromString<Candidate>(drafts[index].payload) }
                    }
                    .getOrNull()
            c?.let {
                Text(
                    "草稿预览 · ${index + 1}/${drafts.size}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ previewIndex = index - 1 }, enabled = index > 0) {
                        Text("上一张草稿")
                    }
                    OutlinedButton(
                        { previewIndex = index + 1 },
                        enabled = index < drafts.lastIndex,
                    ) {
                        Text("下一张草稿")
                    }
                }
                CardPreview(
                    it.front,
                    it.back,
                    it.clozeText,
                    it.explanation,
                    it.type,
                    templateId = it.templateId,
                    deckTemplateId = decks.find { it.id == config?.deckId }?.templateId,
                    metadata = it.sourceSection.lines().firstOrNull().orEmpty(),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ onAudit(task.id) }, enabled = drafts.isNotEmpty()) { Text("审核草稿") }
            if (stage.active) OutlinedButton({ vm.cancelTask(task.id) }) { Text("取消生成") }
            if (stage in listOf(GenerationStage.FAILED, GenerationStage.CANCELLED))
                OutlinedButton({ vm.retryTask(task.id) }) { Text("恢复重试") }
            if (!stage.active) OutlinedButton({ completeConsent = true }) { Text("补齐整份资料") }
        }
        TextButton({ details = !details }) { Text(if (details) "收起任务详情" else "任务详情与耗时") }
        if (details) {
            Text(
                "解析 ${metrics.parseMs} ms · 清洗 ${metrics.cleanMs} ms · 分块 ${metrics.chunkMs} ms\n首批 ${metrics.firstBatchMs?.let{"$it ms"} ?: "尚未返回"} · JSON ${metrics.jsonMs} ms · 保存 ${metrics.databaseMs} ms\n请求 ${metrics.requests.size} 次 · 自动重试 ${metrics.retries} 次 · 当前并发 ${metrics.effectiveConcurrency}",
                style = MaterialTheme.typography.bodySmall,
            )
            metrics.requests.forEach { r ->
                Text(
                    "批 ${r.batch+1} · ${r.phase} · ${r.elapsedMs} ms · ${if(r.inputTokens==0L && r.outputTokens==0L) "Token 未报告" else "Token ${r.inputTokens}/${r.outputTokens}"}${r.reasoningTokens?.let { " · 思考 $it" } ?: ""}${if(r.thinkingDisabled) " · 快速模式" else ""}${r.outputLimit?.let { " · 输出上限 $it" } ?: ""}${r.error?.let{" · $it"} ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            chunks.forEach {
                Text(
                    "${it.ordinal+1}. ${it.section} · ${if(it.completed) "已完成" else it.error ?: "等待或处理中"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (completeConsent)
        Confirm(
            "补齐整份资料",
            "将继续使用原资料、模型和目标牌组，按全文密度补充尚未达到配额的章节，并过滤已保存及已选中的草稿。需要向已配置服务商上传剩余资料，可能产生 API 费用；结果仍需确认保存。",
            { completeConsent = false },
            {
                completeConsent = false
                vm.completeDocumentDeck(task.id, onProgress)
            },
        )
}

@Composable
fun TaskProgressScreen(
    vm: AppViewModel,
    id: String,
    onAudit: (String) -> Unit,
    onProgress: (String) -> Unit = onAudit,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    Page {
        Heading("生成进度")
        tasks.find { it.id == id }?.let { TaskProgressPanel(vm, it, onAudit, onProgress) }
            ?: EmptyState("正在读取任务", "任务与批次保存在本地。")
    }
}
