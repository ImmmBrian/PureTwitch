package com.puretv.twitch.desktop.channel

import kotlin.test.Test
import kotlin.test.assertEquals

class MiniMarkdownTest {
    private fun t(s: String, bold: Boolean = false, italic: Boolean = false) = MdInline.Text(s, bold, italic)

    @Test fun explicit_and_bare_links() {
        assertEquals(
            listOf(t("Join the "), MdInline.Link("Discord", "https://discord.gg/abc"), t(" or see "), MdInline.Link("https://cohh.tv/store", "https://cohh.tv/store"), t(".")),
            parseInlines("Join the [Discord](https://discord.gg/abc) or see https://cohh.tv/store."),
        )
    }

    @Test fun www_links_get_a_scheme_and_bad_schemes_are_dropped() {
        assertEquals(listOf(MdInline.Link("www.site.com", "https://www.site.com")), parseInlines("www.site.com"))
        // A javascript: link must never become clickable.
        assertEquals(emptyList(), parseInlines("[click](javascript:alert(1))").filterIsInstance<MdInline.Link>())
    }

    @Test fun bold_and_italic() {
        assertEquals(
            listOf(t("a "), t("big", bold = true), t(" and "), t("slanted", italic = true), t(" deal")),
            parseInlines("a **big** and *slanted* deal"),
        )
        // A lone asterisk stays literal.
        assertEquals(listOf(t("5 * 3")), parseInlines("5 * 3"))
    }

    @Test fun blocks() {
        val md = "# Schedule\n\n- Mon 7pm\n- Wed 7pm\n\n\nPlain line\n---\nEnd\n\n"
        assertEquals(
            listOf(
                MdBlock.Heading(listOf(t("Schedule"))),
                MdBlock.Blank,
                MdBlock.Bullet(listOf(t("Mon 7pm"))),
                MdBlock.Bullet(listOf(t("Wed 7pm"))),
                MdBlock.Blank,
                MdBlock.Paragraph(listOf(t("Plain line"))),
                MdBlock.Blank,
                MdBlock.Paragraph(listOf(t("End"))),
            ),
            parseMiniMarkdown(md),
        )
    }

    @Test fun italic_line_is_not_a_bullet() {
        assertEquals(listOf(MdBlock.Paragraph(listOf(t("note", italic = true)))), parseMiniMarkdown("*note*"))
    }

    @Test fun url_helpers() {
        assertEquals("https://cohh.tv/store", normalizeUrl("cohh.tv/store"))
        assertEquals(null, normalizeUrl("ftp://x.y"))
        assertEquals(null, normalizeUrl("not a url"))
        assertEquals("youtube.com", displayHost("https://www.youtube.com/c/pokimane"))
    }

    @Test fun rules_are_tidied() {
        assertEquals(listOf("<READ>", "", "Be nice"), tidyRules(listOf("", "<READ>", "", "", "Be nice", "")))
    }
}
