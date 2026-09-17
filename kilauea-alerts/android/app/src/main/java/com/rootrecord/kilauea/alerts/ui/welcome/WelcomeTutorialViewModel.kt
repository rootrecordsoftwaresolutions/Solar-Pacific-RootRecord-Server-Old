package com.rootrecord.kilauea.alerts.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeTutorialViewModel @Inject constructor(
    private val prefs: KilaueaPreferences,
) : ViewModel() {

    val welcomeTutorialComplete =
        prefs.welcomeTutorialComplete.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markWelcomeTutorialComplete() {
        viewModelScope.launch { prefs.setWelcomeTutorialComplete(true) }
    }
}
