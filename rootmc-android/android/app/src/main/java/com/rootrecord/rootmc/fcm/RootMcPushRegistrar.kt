package com.rootrecord.rootmc.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.RootRecordAuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Best-effort FCM token → api.rootmc.net `/api/me/push-token`. No-op when Firebase is not configured. */
@Singleton
class RootMcPushRegistrar @Inject constructor(
    private val authRepo: RootRecordAuthRepository,
    private val prefs: RootMcPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentTokenAsync() {
        scope.launch {
            if (!prefs.authSignedIn.first()) return@launch
            runCatching {
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        scope.launch {
                            authRepo.registerPushToken(token).onFailure { err ->
                                Log.w(TAG, "registerPushToken: ${err.message}")
                            }
                        }
                    }
                    .addOnFailureListener { err ->
                        Log.w(TAG, "FCM token unavailable: ${err.message}")
                    }
            }.onFailure { err ->
                Log.w(TAG, "Firebase not configured: ${err.message}")
            }
        }
    }

    private companion object {
        const val TAG = "RootMcPush"
    }
}
