package com.yike.ai.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val LocalDefaultTemplate = compositionLocalOf { "NEON" }
val LocalCardAnimations = compositionLocalOf { true }

@Composable
fun TemplatePicker(
    value: String?,
    inherit: String = LocalDefaultTemplate.current,
    onChange: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var selection by remember(value) { mutableStateOf(value) }
    OutlinedButton({
        selection = value
        open = true
    }) {
        Text(
            "卡片模板：${if (value == null) "跟随默认 · " else ""}${CardTemplateRepository.get(value ?: inherit).name}"
        )
    }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("选择卡片模板") },
            text = {
                Column(
                    Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CardTemplateRepository.all.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { d ->
                                val s = d.style
                                Surface(
                                    Modifier.weight(1f).clickable { selection = d.id.name },
                                    color = s.background,
                                    contentColor = s.foreground,
                                    shape =
                                        androidx.compose.foundation.shape.RoundedCornerShape(
                                            s.radius.dp
                                        ),
                                    border =
                                        BorderStroke(
                                            if ((selection ?: inherit) == d.id.name) 3.dp
                                            else s.borderWidth.dp,
                                            s.border,
                                        ),
                                ) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            d.name,
                                            color = s.accent,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        HorizontalDivider(color = s.border)
                                        Text("问题", style = MaterialTheme.typography.labelSmall)
                                        Text("什么是主动回忆？", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            if ((selection ?: inherit) == d.id.name) "已选择"
                                            else "点击预览",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    CardPreview("什么是主动回忆？", "**主动提取**记忆中的知识。", templateId = selection ?: inherit)
                }
            },
            confirmButton = {
                TextButton({
                    onChange(selection)
                    open = false
                }) {
                    Text("应用模板")
                }
            },
            dismissButton = {
                TextButton({
                    onChange(null)
                    open = false
                }) {
                    Text("恢复默认")
                }
            },
        )
}
