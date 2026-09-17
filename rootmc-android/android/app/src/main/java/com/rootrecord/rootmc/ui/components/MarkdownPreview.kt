package com.rootrecord.rootmc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MarkdownSegment {
    data class Heading(val level: Int, val text: String) : MarkdownSegment
    data class Bullet(val text: String) : MarkdownSegment
    data class Paragraph(val text: String) : MarkdownSegment
}

private fun parseMarkdownSegments(markdown: String): List<MarkdownSegment> {
    if (markdown.isBlank()) return emptyList()
    return markdown.lines().mapNotNull { line ->
        val trimmed = line.trimEnd()
        when {
            trimmed.isBlank() -> null
            trimmed.startsWith("# ") -> MarkdownSegment.Heading(1, trimmed.removePrefix("# ").trim())
            trimmed.startsWith("## ") -> MarkdownSegment.Heading(2, trimmed.removePrefix("## ").trim())
            trimmed.startsWith("### ") -> MarkdownSegment.Heading(3, trimmed.removePrefix("### ").trim())
            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                MarkdownSegment.Bullet(trimmed.drop(2).trim())
            else -> MarkdownSegment.Paragraph(trimmed)
        }
    }
}

private fun annotateInlineMarkdown(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val segments = remember(markdown) { parseMarkdownSegments(markdown) }
    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Heading -> {
                    val style = when (segment.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = segment.text,
                        style = style,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                is MarkdownSegment.Bullet -> {
                    Text(
                        text = annotateInlineMarkdown("• ${segment.text}"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )
                }
                is MarkdownSegment.Paragraph -> {
                    Text(
                        text = annotateInlineMarkdown(segment.text),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
