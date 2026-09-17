package com.rootrecord.rootmc.util

object MapUrlUtils {

    /** Normalize user input to a full https URL for WebView. */
    fun normalizeMapUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            url = "https://$url"
        }
        return url.trimEnd('/')
    }

    /**
     * Guess a Dynmap-style URL from a server address.
     * play.earthmc.net → https://map.earthmc.net
     */
    fun suggestMapUrl(serverAddress: String): String? {
        val host = parseHost(serverAddress) ?: return null
        if (host.startsWith("map.", ignoreCase = true)) {
            return normalizeMapUrl(host)
        }
        val core = stripHostPrefix(host, "play.")
            ?: stripHostPrefix(host, "mc.")
            ?: stripHostPrefix(host, "server.")
            ?: host
        if (core.isEmpty()) return null
        return "https://map.$core"
    }

    private fun stripHostPrefix(host: String, prefix: String): String? {
        if (host.length <= prefix.length) return null
        if (host.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            return host.substring(prefix.length)
        }
        return null
    }

    fun parseHost(serverAddress: String): String? {
        var raw = serverAddress.trim()
        if (raw.isEmpty()) return null
        if (raw.regionMatches(0, "minecraft://", 0, 12, ignoreCase = true)) {
            raw = raw.substring(12)
        }
        raw = raw.substringBefore('/').substringBefore(':').trim()
        return raw.takeIf { it.isNotEmpty() }
    }
}
