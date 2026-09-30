package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.channel.MdBlock
import com.puretv.twitch.desktop.channel.MdInline
import com.puretv.twitch.desktop.channel.parseMiniMarkdown
import com.puretv.twitch.desktop.platform.openInBrowser
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * Renders the Markdown subset Twitch panels use (see parseMiniMarkdown). Links
 * open in the system browser.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = PureTvTheme.colors.onSurfaceVariant,
) {
    val c = PureTvTheme.colors
    val blocks = remember(text) { parseMiniMarkdown(text) }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = c.primary, textDecoration = TextDecoration.Underline),
        hoveredStyle = SpanStyle(color = c.primary, textDecoration = TextDecoration.None),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    annotated(block.inlines, linkStyles),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onSurface,
                )
                is MdBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = style, color = color)
                    Text(annotated(block.inlines, linkStyles), style = style, color = color)
                }
                is MdBlock.Paragraph -> Text(annotated(block.inlines, linkStyles), style = style, color = color)
                MdBlock.Blank -> Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun annotated(inlines: List<MdInline>, linkStyles: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    inlines.forEach { part ->
        when (part) {
            is MdInline.Text -> withStyle(
                SpanStyle(
                    fontWeight = if (part.bold) FontWeight.Bold else null,
                    fontStyle = if (part.italic) FontStyle.Italic else null,
                ),
            ) { append(part.text) }
            is MdInline.Link -> withLink(
                LinkAnnotation.Clickable(
                    tag = part.url,
                    styles = linkStyles,
                    linkInteractionListener = { openInBrowser(part.url) },
                ),
            ) { append(part.label) }
        }
    }
}
