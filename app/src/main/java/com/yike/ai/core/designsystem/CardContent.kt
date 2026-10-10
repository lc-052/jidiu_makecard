package com.yike.ai.core.designsystem

import android.text.Html
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yike.ai.core.common.*

@Composable
fun CardContent(
    text: String,
    question: Boolean = false,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    fontSize: Int = 18,
) {
    val readable =
        remember(text) {
            if (
                Regex(
                        "</?(?:p|div|br|b|strong|i|em|span|ul|ol|li)(?:\\s[^>]*|/?)>",
                        RegexOption.IGNORE_CASE,
                    )
                    .containsMatchIn(text)
            )
                Html.fromHtml(
                        text.replace(Regex("</?(b|strong)>", RegexOption.IGNORE_CASE), "**"),
                        Html.FROM_HTML_MODE_COMPACT,
                    )
                    .toString()
                    .trim()
            else text
        }
    val blocks = remember(readable) { CardFormatting.blocks(readable) }
    val primary = accent
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { b ->
            val styled = buildAnnotatedString {
                CardFormatting.spans(b.text).forEach { s ->
                    withStyle(
                        SpanStyle(
                            fontWeight = if (s.emphasis) FontWeight.SemiBold else null,
                            color =
                                if (s.emphasis) primary
                                else androidx.compose.ui.graphics.Color.Unspecified,
                            fontFamily = if (s.code) FontFamily.Monospace else null,
                        )
                    ) {
                        append(s.text)
                    }
                }
            }
            val style =
                when {
                    b.kind == CardBlockKind.HEADING ->
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 28.sp,
                        )
                    b.kind == CardBlockKind.CODE ->
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 25.sp,
                        )
                    question ->
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 34.sp,
                        )
                    else ->
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = fontSize.sp,
                            lineHeight = 29.sp,
                        )
                }
            if (b.kind == CardBlockKind.BULLET || b.kind == CardBlockKind.NUMBERED) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(b.marker, color = primary, style = style)
                    Text(styled, Modifier.weight(1f), style = style)
                }
            } else
                Text(
                    styled,
                    style = style,
                    color =
                        if (b.kind == CardBlockKind.QUOTE)
                            LocalContentColor.current.copy(alpha = .85f)
                        else LocalContentColor.current,
                )
        }
    }
}

@Composable
fun CardPreview(
    front: String,
    back: String,
    cloze: String = "",
    explanation: String = "",
    type: String = "BASIC",
    templateId: String? = null,
    deckTemplateId: String? = null,
    metadata: String = "",
) {
    var showAnswer by remember(front, back, cloze) { mutableStateOf(false) }
    var showExplanation by remember(explanation) { mutableStateOf(false) }
    val definition =
        CardTemplateRepository.resolve(templateId, deckTemplateId, LocalDefaultTemplate.current)
    val style = definition.style
    val animations =
        LocalCardAnimations.current &&
            android.provider.Settings.Global.getFloat(
                androidx.compose.ui.platform.LocalContext.current.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
    Surface(
        Modifier.fillMaxWidth()
            .heightIn(min = 220.dp)
            .shadow(
                style.elevation.dp,
                RoundedCornerShape(style.radius.dp),
                ambientColor = style.border.copy(alpha = .25f),
                spotColor = style.border.copy(alpha = .25f),
            ),
        color = if (showAnswer) style.backBackground else style.background,
        contentColor = style.foreground,
        shape = RoundedCornerShape(style.radius.dp),
        border = BorderStroke(style.borderWidth.dp, style.border),
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier.drawBehind {
                    if (definition.id == CardTemplateId.PAPER) {
                        val spacing = 32.dp.toPx()
                        var y = spacing
                        while (y < size.height) {
                            drawLine(
                                style.border.copy(alpha = .12f),
                                androidx.compose.ui.geometry.Offset(0f, y),
                                androidx.compose.ui.geometry.Offset(size.width, y),
                                1f,
                            )
                            y += spacing
                        }
                    }
                }
                .padding(style.padding.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${if (showAnswer) "答案" else "问题"} · ${if(type=="CLOZE") "挖空" else if(type=="COMPARISON") "对比" else "问答"}",
                color = style.accent,
                style = MaterialTheme.typography.labelLarge,
            )
            HorizontalDivider(color = style.border.copy(alpha = .6f))
            Crossfade(
                showAnswer,
                animationSpec = tween(if (animations) style.animationMs else 0),
                label = "卡片正反面",
            ) { answer ->
                CardContent(
                    if (type == "CLOZE") CardFormatting.clozePreview(cloze, answer)
                    else if (answer) back else front,
                    question = !answer,
                    accent = style.accent,
                    fontSize = style.fontSize,
                )
            }
            TextButton(
                { showAnswer = !showAnswer },
                colors = ButtonDefaults.textButtonColors(contentColor = style.accent),
            ) {
                Text(if (showAnswer) "显示问题" else "显示答案")
            }
            if (showAnswer && explanation.isNotBlank()) {
                HorizontalDivider(color = style.border)
                TextButton(
                    { showExplanation = !showExplanation },
                    colors = ButtonDefaults.textButtonColors(contentColor = style.accent),
                ) {
                    Text(if (showExplanation) "收起解释" else "查看解释")
                }
                if (showExplanation) CardContent(explanation, accent = style.accent)
            }
            if (style.showMetadata && metadata.isNotBlank())
                Text(metadata, style = MaterialTheme.typography.labelSmall)
        }
    }
}
