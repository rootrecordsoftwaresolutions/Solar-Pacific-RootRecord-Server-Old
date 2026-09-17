package com.rootrecord.rootmc.util

object MarkdownUtils {

    /** Strip common Markdown syntax for plain-text preview and FTS indexing. */
    fun stripMarkdown(markdown: String): String {
        var text = markdown
        text = text.replace(Regex("```[\\s\\S]*?```"), " ")
        text = text.replace(Regex("`([^`]+)`"), "$1")
        text = text.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")
        text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
        text = text.replace(Regex("_([^_]+)_"), "$1")
        text = text.replace(Regex("~~([^~]+)~~"), "$1")
        text = text.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
        text = text.replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
        text = text.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        text = text.replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
        text = text.replace(Regex("!\\[[^\\]]*]\\([^)]+\\)"), "")
        text = text.replace(Regex("\\s+"), " ")
        return text.trim()
    }
}
