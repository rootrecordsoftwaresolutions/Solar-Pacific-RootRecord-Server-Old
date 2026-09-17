package com.rootrecord.rootmc.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveMapWebView(
    mapUrl: String,
    modifier: Modifier = Modifier,
) {
    val normalized = mapUrl.trim()
    if (normalized.isBlank()) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isFocusable = true
                isFocusableInTouchMode = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.userAgentString = settings.userAgentString +
                    " RootMC/1.0 (RootMC; +https://rootrecord.online)"
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val target = request?.url?.toString().orEmpty()
                        return !target.startsWith("http://") && !target.startsWith("https://")
                    }
                }
                loadUrl(normalized)
            }
        },
        update = { view ->
            if (view.url != normalized) {
                view.loadUrl(normalized)
            }
            view.requestLayout()
        },
    )
}
