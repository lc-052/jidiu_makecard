package com.yike.ai.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yike.ai.BuildConfig
import com.yike.ai.core.database.*
import com.yike.ai.core.designsystem.*
import com.yike.ai.feature.AppViewModel

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onModels: () -> Unit,
    onExport: (String) -> Unit,
    onRestore: () -> Unit,
) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var about by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val licenses = remember {
        context.assets
            .list("licenses")
            .orEmpty()
            .filter { it.endsWith(".txt") }
            .joinToString("\n\n") { name ->
                "$name\n" +
                    context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
            }
    }
    var generationCount by
        remember(prefs.generationCount) { mutableStateOf(prefs.generationCount.toString()) }
    Page {
        Heading("AI 服务")
        Button(onModels, Modifier.fillMaxWidth()) { Text("服务商、模型与连接测试") }
        Field("默认目标笔记数", generationCount, { generationCount = it })
        Button({
            generationCount.toIntOrNull()?.let { vm.savePrefs(prefs.copy(generationCount = it)) }
                ?: vm.notify("请输入有效数量")
        }) {
            Text("保存制卡默认数量")
        }
        Choice("默认制卡模式", prefs.generationMode, listOf("基础理解", "期末考试", "深入理解")) {
            vm.savePrefs(prefs.copy(generationMode = it))
        }
        Text("默认挖空占比 ${prefs.generationCloze}%")
        Slider(
            prefs.generationCloze.toFloat(),
            { vm.savePrefs(prefs.copy(generationCloze = it.toInt())) },
            valueRange = 0f..100f,
            steps = 9,
        )
        Heading("外观")
        TemplatePicker(prefs.defaultTemplate) {
            vm.savePrefs(prefs.copy(defaultTemplate = it ?: "NEON"))
        }
        Choice(
            "主题",
            when (prefs.theme) {
                "LIGHT" -> "浅色"
                "SYSTEM" -> "跟随系统"
                else -> "深色"
            },
            listOf("深色", "浅色", "跟随系统"),
        ) {
            vm.savePrefs(
                prefs.copy(
                    theme =
                        when (it) {
                            "浅色" -> "LIGHT"
                            "跟随系统" -> "SYSTEM"
                            else -> "DARK"
                        }
                )
            )
        }
        Text("应用字体 ${(prefs.fontScale*100).toInt()}%（叠加系统字体设置）")
        Slider(
            prefs.fontScale,
            { vm.savePrefs(prefs.copy(fontScale = it)) },
            valueRange = 0.85f..1.3f,
            steps = 8,
        )
        Toggle("卡片动画", prefs.animations) { vm.savePrefs(prefs.copy(animations = it)) }
        Heading("数据")
        OutlinedButton({ onExport("JSON") }, Modifier.fillMaxWidth()) { Text("本地备份 / JSON 导出") }
        OutlinedButton(onRestore, Modifier.fillMaxWidth()) { Text("从备份恢复（校验后覆盖）") }
        OutlinedButton({ onExport("APKG") }, Modifier.fillMaxWidth()) { Text("导出全部牌组 APKG") }
        OutlinedButton({ onExport("CSV") }, Modifier.fillMaxWidth()) { Text("导出全部笔记 CSV") }
        Heading("关于 · ${BuildConfig.VERSION_NAME}")
        TextButton({ about = "隐私" }) { Text("隐私说明") }
        TextButton({ about = "帮助" }) { Text("使用帮助") }
        TextButton({ about = "许可" }) { Text("开源许可证") }
    }
    about?.let { which ->
        AlertDialog(
            onDismissRequest = { about = null },
            title = { Text(which) },
            text = {
                Text(
                    when (which) {
                        "隐私" ->
                            "制卡数据保存在设备本地，不需要账户。只有明确确认的 AI 资料会发送到你配置的 HTTPS 服务商。API Key 由 Android Keystore 加密，排除备份；越狱、恶意软件或被控制的设备仍可能泄露移动端密钥。请为个人密钥设置额度并按需撤销。备份文件包含卡片内容，请自行妥善保管。"
                        "帮助" ->
                            "1. 首页进入手动制卡，填写问题、答案或挖空，预览后保存。\n2. 使用空行、- 列表和 **重点** 排版，解释单独填写。\n3. 设置中配置模型与密钥，AI 制卡导入资料后生成并审核。\n4. 在牌组中编辑、整理和导出 APKG，导入 AnkiDroid 使用。"
                        else ->
                            "AndroidX / Kotlin / Coroutines / Serialization / OkHttp / PDFBox-Android：Apache 2.0。FSRS 官方参考与 genanki APKG 格式参考：MIT。Bouncy Castle：MIT。\n\n$licenses"
                    },
                    modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton({ about = null }) { Text("关闭") } },
        )
    }
}

@Composable
fun ProvidersScreen(vm: AppViewModel) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<ProviderEntity?>(null) }
    var deleting by remember { mutableStateOf<ProviderEntity?>(null) }
    Page {
        Heading("你的模型，你的选择")
        Text("可手动输入任意模型 ID。只有服务商支持时才启用温度、Schema 和流式。密钥加密存储；移动端安全仍受设备环境限制。")
        Button({ editor = ProviderEntity() }) { Text("新增模型") }
        if (providers.isEmpty()) EmptyState("尚未配置模型", "手动制卡可离线使用。AI 功能需要服务商 HTTPS 地址和你的密钥。")
        providers.forEach { p ->
            Panel {
                Text(
                    p.name + if (p.isDefault) " · 默认" else "",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("${p.modelId} · ${p.protocol}\n${p.baseUrl}")
                Text(
                    "累计接口报告用量：输入 ${p.inputTokens} · 输出 ${p.outputTokens}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("未配置可靠价格表，不估算成本。实际费用请查看服务商。", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton({ editor = p }) { Text("编辑") }
                    TextButton({ vm.setDefault(p) }) { Text("设为默认") }
                    TextButton({ vm.testProvider(p, "") }, enabled = !busy) { Text("连接测试") }
                    TextButton({ deleting = p }) { Text("删除") }
                }
            }
        }
    }
    editor?.let { p ->
        ProviderEditor(
            p,
            busy,
            { editor = null },
            save = { value, key -> vm.saveProvider(value, key) { editor = null } },
            test = { value, key -> vm.testProvider(value, key) },
        )
    }
    deleting?.let { p ->
        Confirm("删除模型配置？", "删除 ${p.name} 和其加密密钥。已有卡片会保留；引用该模型的未完成任务将无法继续。", { deleting = null }) {
            vm.deleteProvider(p)
            deleting = null
        }
    }
}

@Composable
fun ProviderEditor(
    p: ProviderEntity,
    busy: Boolean,
    dismiss: () -> Unit,
    save: (ProviderEntity, String) -> Unit,
    test: (ProviderEntity, String) -> Unit,
) {
    var name by remember { mutableStateOf(p.name) }
    var kind by remember { mutableStateOf(p.kind) }
    var url by remember { mutableStateOf(p.baseUrl) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(p.modelId) }
    var protocol by remember { mutableStateOf(p.protocol) }
    var timeout by remember { mutableStateOf(p.timeoutSeconds.toString()) }
    var tokens by remember { mutableStateOf(p.maxTokens.toString()) }
    var temperatureOn by remember { mutableStateOf(p.temperature != null) }
    var temperature by remember { mutableStateOf(p.temperature?.toString() ?: "0.7") }
    var strict by remember { mutableStateOf(p.strictSchema) }
    var stream by remember { mutableStateOf(p.streaming) }
    var concurrency by remember { mutableStateOf(p.concurrency.toString()) }
    fun value() =
        p.copy(
            name = name.trim(),
            kind = kind,
            baseUrl = url.trim(),
            modelId = model.trim(),
            protocol = protocol,
            timeoutSeconds = timeout.toIntOrNull() ?: 0,
            maxTokens = tokens.toIntOrNull() ?: 0,
            temperature = if (temperatureOn) temperature.toDoubleOrNull() ?: -1.0 else null,
            strictSchema = strict,
            streaming = stream,
            concurrency = concurrency.toIntOrNull() ?: 0,
        )
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("模型配置") },
        text = {
            Column(
                Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Field("Provider 名称", name, { name = it })
                Choice("服务商类型", kind, listOf("OPENAI", "COMPATIBLE")) { kind = it }
                Field("Base URL（含 /v1，HTTPS）", url, { url = it })
                OutlinedTextField(
                    key,
                    { key = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("API Key（留空保留现有密钥）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Field("Model ID（任意）", model, { model = it })
                Choice("接口协议", protocol, listOf("CHAT", "RESPONSES")) { protocol = it }
                Field("超时秒数 10–600", timeout, { timeout = it })
                Field("最大输出 Token 128–64000", tokens, { tokens = it })
                Choice("并发批次（限流后自动降为 1）", concurrency, listOf("1", "2", "3", "4")) {
                    concurrency = it
                }
                Toggle("模型支持温度参数", temperatureOn) { temperatureOn = it }
                if (temperatureOn) Field("温度 0–2", temperature, { temperature = it })
                Toggle("启用严格结构化输出", strict) { strict = it }
                Toggle("启用流式（服务商需兼容 SSE）", stream) { stream = it }
                Text("不兼容严格 Schema 时关闭开关，应用仍会解析校验 JSON 并有限重试。仅发送你明确开启的可选参数。")
                OutlinedButton({ test(value(), key) }, enabled = !busy) {
                    Text("测试 API 连接（会产生一次调用）")
                }
            }
        },
        confirmButton = { TextButton({ save(value(), key) }, enabled = !busy) { Text("保存") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}
