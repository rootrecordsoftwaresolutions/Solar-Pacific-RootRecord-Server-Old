package com.rootrecord.kilauea.alerts.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeStreamEmbed(
    loadUrl: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val packageName = ctx.packageName
    val target = remember(loadUrl) { resolveYoutubeEmbedTarget(loadUrl) }
    val baseUrl = remember(packageName) { "https://$packageName/" }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = FullscreenYoutubeChromeClient(c)
                webViewClient = WebViewClient()
                applyYoutubeEmbed(this, target, baseUrl)
            }
        },
        update = { view ->
            applyYoutubeEmbed(view, target, baseUrl)
        },
    )
}

private class FullscreenYoutubeChromeClient(private val context: Context) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility: Int? = null

    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        val activity = context.findActivity()
        val decorView = activity?.window?.decorView as? ViewGroup
        if (activity == null || decorView == null || view == null || customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback
        originalSystemUiVisibility = decorView.systemUiVisibility
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        decorView.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    override fun onHideCustomView() {
        val decorView = context.findActivity()?.window?.decorView as? ViewGroup
        customView?.let { decorView?.removeView(it) }
        originalSystemUiVisibility?.let { decorView?.systemUiVisibility = it }
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        originalSystemUiVisibility = null
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private sealed interface YoutubeEmbedTarget {
    data class Iframe(val src: String) : YoutubeEmbedTarget
    data class FullPage(val url: String) : YoutubeEmbedTarget
}

private fun applyYoutubeEmbed(webView: WebView, target: YoutubeEmbedTarget, baseUrl: String) {
    val key = when (target) {
        is YoutubeEmbedTarget.Iframe -> "iframe:${target.src}"
        is YoutubeEmbedTarget.FullPage -> "page:${target.url}"
    }
    if (webView.tag == key) return
    webView.tag = key

    val refererHeaders = mapOf("Referer" to baseUrl)
    when (target) {
        is YoutubeEmbedTarget.Iframe -> {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <meta name="referrer" content="strict-origin-when-cross-origin">
                  <style>
                    html, body { margin: 0; padding: 0; background: #000; height: 100%; }
                    iframe { border: 0; width: 100%; height: 100%; }
                  </style>
                </head>
                <body>
                  <iframe
                    src="${target.src}"
                    referrerpolicy="strict-origin-when-cross-origin"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    allowfullscreen>
                  </iframe>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }
        is YoutubeEmbedTarget.FullPage -> {
            webView.loadUrl(target.url, refererHeaders)
        }
    }
}

private fun resolveYoutubeEmbedTarget(loadUrl: String): YoutubeEmbedTarget {
    val trimmed = loadUrl.trim()
    parseChannelLiveVideoIdToken(trimmed)?.let { channelId ->
        val src =
            "https://www.youtube.com/embed/live_stream?channel=$channelId&autoplay=1&playsinline=1&rel=0&modestbranding=1&enablejsapi=1"
        return YoutubeEmbedTarget.Iframe(src)
    }
    extractYoutubeVideoId(trimmed)?.let { id ->
        val src =
            "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&modestbranding=1&enablejsapi=1"
        return YoutubeEmbedTarget.Iframe(src)
    }
    if (trimmed.contains("/embed/", ignoreCase = true)) {
        return YoutubeEmbedTarget.Iframe(trimmed)
    }
    return YoutubeEmbedTarget.FullPage(trimmed)
}

private fun parseChannelLiveVideoIdToken(value: String): String? {
    val raw = value.trim()
    if (!raw.startsWith("live:", ignoreCase = true)) return null
    val channelId = raw.substring(5).trim()
    return channelId.takeIf { it.matches(Regex("""UC[\w-]{20,}""", RegexOption.IGNORE_CASE)) }
}

private fun extractYoutubeVideoId(url: String): String? {
    Regex("""[?&]v=([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)?.let { return it }
    Regex("""youtu\.be/([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)?.let { return it }
    Regex("""/embed/([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

fun LiveFeedEmbedUrl(feed: com.rootrecord.kilauea.alerts.domain.LiveFeed): String {
    feed.embedUrl?.takeIf { it.isNotBlank() }?.let { return it }
    parseChannelLiveVideoIdToken(feed.youtubeVideoId.orEmpty())?.let { channelId ->
        return "https://www.youtube.com/embed/live_stream?channel=$channelId&autoplay=1&playsinline=1&rel=0&modestbranding=1"
    }
    val vid = feed.youtubeVideoId?.trim().orEmpty()
    if (vid.isNotEmpty()) {
        return "https://www.youtube.com/embed/$vid?autoplay=1&playsinline=1&rel=0&modestbranding=1"
    }
    return feed.watchUrl
}
