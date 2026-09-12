package app.still.ui.update

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em

private const val UrlTag = "URL"

@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current
    val content = remember(markdown, linkColor) { markdownToAnnotatedString(markdown, linkColor) }

    @Suppress("DEPRECATION")
    ClickableText(
        text = content,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            content.getStringAnnotations(UrlTag, offset, offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        },
    )
}

internal fun markdownToAnnotatedString(
    markdown: String,
    linkColor: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    var inCodeBlock = false
    val lines = markdown.lines()
    lines.forEachIndexed { index, sourceLine ->
        val trimmed = sourceLine.trim()
        when {
            trimmed.startsWith("```") -> inCodeBlock = !inCodeBlock
            inCodeBlock -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(sourceLine) }
            trimmed.matches(Regex("^#{1,6}\\s+.*")) -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val heading = trimmed.drop(level).trimStart()
                withStyle(
                    SpanStyle(
                        fontSize = when (level) {
                            1 -> 1.45.em
                            2 -> 1.3.em
                            else -> 1.15.em
                        },
                        fontWeight = FontWeight.Bold,
                    ),
                ) { appendMarkdownInline(heading, linkColor) }
            }
            trimmed.matches(Regex("^([-*_])(?:\\s*\\1){2,}$")) -> append("────────")
            sourceLine.matches(Regex("^\\s*[-+*]\\s+.*")) -> {
                append("• ")
                appendMarkdownInline(sourceLine.replaceFirst(Regex("^\\s*[-+*]\\s+"), ""), linkColor)
            }
            sourceLine.matches(Regex("^\\s*\\d+[.)]\\s+.*")) -> {
                val marker = Regex("^\\s*(\\d+[.)])\\s+").find(sourceLine)!!
                append(marker.groupValues[1])
                append(' ')
                appendMarkdownInline(sourceLine.substring(marker.range.last + 1), linkColor)
            }
            sourceLine.matches(Regex("^\\s*>\\s?.*")) -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append("│ ")
                    appendMarkdownInline(sourceLine.replaceFirst(Regex("^\\s*>\\s?"), ""), linkColor)
                }
            }
            else -> appendMarkdownInline(sourceLine, linkColor)
        }
        if (index < lines.lastIndex && !trimmed.startsWith("```")) append('\n')
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    source: String,
    linkColor: androidx.compose.ui.graphics.Color,
) {
    var cursor = 0
    while (cursor < source.length) {
        val token = nextInlineToken(source, cursor)
        if (token == null) {
            append(source.substring(cursor))
            break
        }
        if (token.start > cursor) append(source.substring(cursor, token.start))
        when (token.kind) {
            InlineKind.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.label) }
            InlineKind.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.label) }
            InlineKind.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(token.label) }
            InlineKind.Link -> {
                pushStringAnnotation(UrlTag, token.destination.orEmpty())
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(token.label) }
                pop()
            }
        }
        cursor = token.end
    }
}

private enum class InlineKind { Bold, Italic, Code, Link }

private data class InlineToken(
    val start: Int,
    val end: Int,
    val kind: InlineKind,
    val label: String,
    val destination: String? = null,
)

private fun nextInlineToken(source: String, startAt: Int): InlineToken? {
    val patterns = listOf(
        InlineKind.Link to Regex("\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)"),
        InlineKind.Bold to Regex("\\*\\*([^*\\n]+)\\*\\*|__([^_\\n]+)__"),
        InlineKind.Code to Regex("`([^`\\n]+)`"),
        InlineKind.Italic to Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|(?<!_)_([^_\\n]+)_(?!_)"),
    )
    return patterns.mapNotNull { (kind, regex) ->
        regex.find(source, startAt)?.let { match ->
            val label = match.groupValues.drop(1).first { it.isNotEmpty() }
            InlineToken(
                start = match.range.first,
                end = match.range.last + 1,
                kind = kind,
                label = label,
                destination = if (kind == InlineKind.Link) match.groupValues[2] else null,
            )
        }
    }.minByOrNull { it.start }
}
