package com.puretv.twitch.desktop.channel

/**
 * The small slice of Markdown Twitch panels actually use: headings, bullet lines,
 * **bold**, *italic*, [links](url) and bare URLs. Anything else stays literal text.
 * Pure data so it can be tested without Compose; the UI turns it into styled text.
 */
sealed interface MdInline {
    data class Text(val text: String, val bold: Boolean = false, val italic: Boolean = false) : MdInline
    data class Link(val label: String, val url: String) : MdInline
}

sealed interface MdBlock {
    data class Heading(val inlines: List<MdInline>) : MdBlock
    data class Bullet(val inlines: List<MdInline>) : MdBlock
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data object Blank : MdBlock
}

private val HEADING = Regex("^#{1,6}\\s+(.*)$")
private val BULLET = Regex("^\\s*(?:[-*+•]|\\d+[.)])\\s+(.*)$")
private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
private val BARE_URL = Regex("(?:https?://|www\\.)[^\\s<>()\\[\\]]+", RegexOption.IGNORE_CASE)
private val HR = Regex("^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$")

fun parseMiniMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    for (raw in text.replace("\r\n", "\n").split('\n')) {
        val line = raw.trimEnd()
        when {
            line.isBlank() || HR.matches(line) -> if (blocks.isNotEmpty() && blocks.last() != MdBlock.Blank) blocks += MdBlock.Blank
            HEADING.matches(line) -> blocks += MdBlock.Heading(parseInlines(HEADING.find(line)!!.groupValues[1]))
            BULLET.matches(line) -> blocks += MdBlock.Bullet(parseInlines(BULLET.find(line)!!.groupValues[1]))
            else -> blocks += MdBlock.Paragraph(parseInlines(line.trim()))
        }
    }
    while (blocks.lastOrNull() == MdBlock.Blank) blocks.removeAt(blocks.lastIndex)
    return blocks
}

/** Links (explicit or bare) first, then bold/italic markers inside the plain runs between them. */
fun parseInlines(line: String): List<MdInline> {
    val out = mutableListOf<MdInline>()
    var i = 0
    while (i < line.length) {
        val explicit = LINK.find(line, i)
        val bare = BARE_URL.find(line, i)
        val next = listOfNotNull(explicit, bare).minByOrNull { it.range.first }
        if (next == null) {
            out += styled(line.substring(i))
            break
        }
        if (next.range.first > i) out += styled(line.substring(i, next.range.first))
        if (next === explicit) {
            val url = normalizeUrl(next.groupValues[2])
            val label = next.groupValues[1].replace("**", "").replace("*", "")
            out += if (url != null) MdInline.Link(label, url) else MdInline.Text(label)
            i = next.range.last + 1
        } else {
            // Trailing punctuation belongs to the sentence, not the URL.
            val matched = next.value.trimEnd('.', ',', ';', ':', '!', '?', '\'', '"')
            val url = normalizeUrl(matched)
            out += if (url != null) MdInline.Link(matched, url) else MdInline.Text(matched)
            i = next.range.first + matched.length
        }
    }
    return merge(out)
}

/** Splits a plain run on ** (bold) and single * (italic) markers. Unpaired markers stay literal. */
private fun styled(run: String): List<MdInline> {
    if (!run.contains('*')) return listOf(MdInline.Text(run))
    val out = mutableListOf<MdInline>()
    var bold = false
    var italic = false
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotEmpty()) out += MdInline.Text(buf.toString(), bold, italic)
        buf.clear()
    }
    var i = 0
    while (i < run.length) {
        if (run.startsWith("**", i) && (bold || run.indexOf("**", i + 2) >= 0)) {
            flush(); bold = !bold; i += 2; continue
        }
        if (run[i] == '*' && (italic || run.indexOf('*', i + 1) >= 0)) {
            flush(); italic = !italic; i += 1; continue
        }
        buf.append(run[i]); i++
    }
    flush()
    return out
}

private fun merge(parts: List<MdInline>): List<MdInline> {
    val out = mutableListOf<MdInline>()
    for (p in parts) {
        val last = out.lastOrNull()
        if (p is MdInline.Text && p.text.isEmpty()) continue
        if (p is MdInline.Text && last is MdInline.Text && last.bold == p.bold && last.italic == p.italic) {
            out[out.lastIndex] = last.copy(text = last.text + p.text)
        } else {
            out += p
        }
    }
    return out
}
