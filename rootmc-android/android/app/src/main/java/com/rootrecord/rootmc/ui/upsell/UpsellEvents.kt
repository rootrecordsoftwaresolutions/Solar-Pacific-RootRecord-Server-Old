package com.rootrecord.rootmc.ui.upsell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide membership upsell trigger (same pattern as Kīlauea Alerts). */
object UpsellEvents {
    private val _show = MutableStateFlow(false)
    val show: StateFlow<Boolean> = _show

    private val _openSignIn = MutableStateFlow(false)
    val openSignIn: StateFlow<Boolean> = _openSignIn

    fun trigger() {
        _show.value = true
    }

    fun dismiss() {
        _show.value = false
    }

    /** Close upsell and open the in-app sign-in screen. */
    fun navigateToSignIn() {
        _show.value = false
        _openSignIn.value = true
    }

    fun consumeSignInNavigation() {
        _openSignIn.value = false
    }
}
