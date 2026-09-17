package com.rootrecord.kilauea.alerts.ui.upsell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide upsell trigger. Composables observe [show]; anywhere in the app can call
 * [trigger] to force the modal open (used by the Weather screen when a free user taps a
 * non-Volcano location).
 *
 * The every-other-open path is driven separately from `MainActivity.onCreate` — it calls
 * [trigger] when the launch count is an even number >= 2 and the user is free.
 */
object UpsellEvents {
    private val _show = MutableStateFlow(false)
    val show: StateFlow<Boolean> = _show

    fun trigger() { _show.value = true }
    fun dismiss() { _show.value = false }
}
