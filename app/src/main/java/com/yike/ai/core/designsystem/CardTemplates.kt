package com.yike.ai.core.designsystem

import androidx.compose.ui.graphics.Color

enum class CardTemplateId {
    CLASSIC,
    NEON,
    SWEET,
    FOREST,
    PAPER,
    MINIMAL,
}

data class CardStyleConfig(
    val background: Color,
    val backBackground: Color,
    val foreground: Color,
    val accent: Color,
    val border: Color,
    val borderWidth: Float = 1.5f,
    val radius: Int = 24,
    val elevation: Int = 4,
    val padding: Int = 22,
    val fontSize: Int = 18,
    val centered: Boolean = false,
    val showMetadata: Boolean = true,
    val animationMs: Int = 220,
)

data class CardTemplateDefinition(
    val id: CardTemplateId,
    val name: String,
    val subtitle: String,
    val style: CardStyleConfig,
)

object CardTemplateRepository {
    val all =
        listOf(
            CardTemplateDefinition(
                CardTemplateId.CLASSIC,
                "经典学习",
                "清晰蓝紫 · 浅色细边框",
                CardStyleConfig(
                    Color(0xFFF4F6FF),
                    Color(0xFFEBEEFF),
                    Color(0xFF20243A),
                    Color(0xFF5146BC),
                    Color(0xFF929CD1),
                ),
            ),
            CardTemplateDefinition(
                CardTemplateId.NEON,
                "霓虹科技",
                "深色蓝紫 · 强调描边",
                CardStyleConfig(
                    Color(0xFF1B2035),
                    Color(0xFF232B47),
                    Color(0xFFF2F3FF),
                    Color(0xFFB3A5FF),
                    Color(0xFF9B87F5),
                    2f,
                    elevation = 8,
                ),
            ),
            CardTemplateDefinition(
                CardTemplateId.SWEET,
                "软糖甜心",
                "粉色淡紫 · 柔和圆角",
                CardStyleConfig(
                    Color(0xFFFFEEF6),
                    Color(0xFFF2EBFF),
                    Color(0xFF513345),
                    Color(0xFF97426C),
                    Color(0xFFC785A9),
                    radius = 32,
                ),
            ),
            CardTemplateDefinition(
                CardTemplateId.FOREST,
                "森系静谧",
                "深绿薄荷 · 安静舒适",
                CardStyleConfig(
                    Color(0xFF15342C),
                    Color(0xFF1E4037),
                    Color(0xFFEDF8F1),
                    Color(0xFFA3E5C4),
                    Color(0xFF6FA98E),
                ),
            ),
            CardTemplateDefinition(
                CardTemplateId.PAPER,
                "纸质笔记",
                "笔记横线 · 清晰轮廓",
                CardStyleConfig(
                    Color(0xFFF5F4ED),
                    Color(0xFFEFEEE7),
                    Color(0xFF343D47),
                    Color(0xFF456377),
                    Color(0xFF969DA4),
                    radius = 10,
                    elevation = 2,
                ),
            ),
            CardTemplateDefinition(
                CardTemplateId.MINIMAL,
                "极简专注",
                "黑白灰 · 细线留白",
                CardStyleConfig(
                    Color(0xFFFAFAFA),
                    Color(0xFFF1F1F1),
                    Color(0xFF222222),
                    Color(0xFF444444),
                    Color(0xFF858585),
                    1f,
                    radius = 12,
                    elevation = 0,
                ),
            ),
        )

    fun get(id: String?) = all.find { it.id.name == id } ?: all[1]

    fun resolve(note: String?, deck: String?, global: String) =
        all.find { it.id.name == note } ?: all.find { it.id.name == deck } ?: get(global)

    // A single shared Anki model with immutable, scoped CSS per visual class.
    // The wrapper class is a presentation field value, so stable note/model IDs survive changes.
    fun html(id: String, content: String) =
        "<div class='jt jt-${get(id).id.name.lowercase()}'>$content</div>"

    fun css(): String =
        all.joinToString("\n") { d ->
            val s = d.style
            fun hex(c: Color) = "#%06x".format((c.value shr 32).toLong() and 0xFFFFFF)
            ".jt-${d.id.name.lowercase()}{background:${hex(s.background)};color:${hex(s.foreground)};border:${s.borderWidth}px solid ${hex(s.border)};border-radius:${s.radius}px;padding:${s.padding}px;margin:12px 0;line-height:1.7;font-size:${s.fontSize}px;overflow-wrap:anywhere;box-sizing:border-box;}.jt-back .jt-${d.id.name.lowercase()}{background:${hex(s.backBackground)}}.jt-${d.id.name.lowercase()} strong,.jt-${d.id.name.lowercase()} .cloze{color:${hex(s.accent)}} .jt img{max-width:100%;height:auto}.jt pre{white-space:pre-wrap;overflow-wrap:anywhere}.jt details{margin-top:16px}"
        }
}
