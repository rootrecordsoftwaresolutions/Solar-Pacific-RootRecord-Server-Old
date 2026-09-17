package com.rootrecord.kilauea.alerts.ui.util

/** Minimal HTML tag stripper for USGS notice bodies (not a full HTML parser). */
fun stripHtmlTags(html: String): String =
    html.replace(Regex("<[^>]+>"), " ")
        .replace(Regex("&nbsp;"), " ")
        .replace(Regex("&[a-z]+;"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
