package com.rootrecord.rootmc



import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

import android.net.Uri

import android.os.Bundle

import android.os.Process

import android.util.Log

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Button

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import androidx.lifecycle.Lifecycle

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.lifecycleScope

import androidx.lifecycle.repeatOnLifecycle

import com.rootrecord.rootmc.ads.RootMcAdManager

import com.rootrecord.rootmc.data.local.RootMcPreferences

import com.rootrecord.rootmc.data.local.DatabaseResetRequiredException

import com.rootrecord.rootmc.data.local.StartupBootstrap

import com.rootrecord.rootmc.data.repository.RootRecordAuthRepository

import com.rootrecord.rootmc.ui.RootMcApp

import com.rootrecord.rootmc.ui.splash.RootMcSplashScreen

import com.rootrecord.rootmc.ui.theme.RootMCTheme

import com.rootrecord.rootmc.ui.upsell.UpsellEvents

import com.rootrecord.rootmc.ui.upsell.UpsellOverlay

import com.rootrecord.rootmc.sync.AccountSyncScheduler

import com.rootrecord.rootmc.fcm.RootMcPushRegistrar

import com.rootrecord.rootmc.work.WorkEnqueue

import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.launch

import java.util.concurrent.atomic.AtomicBoolean

import javax.inject.Inject



@AndroidEntryPoint

class MainActivity : ComponentActivity() {



    @Inject lateinit var startupBootstrap: StartupBootstrap

    @Inject lateinit var prefs: RootMcPreferences

    @Inject lateinit var authRepo: RootRecordAuthRepository

    @Inject lateinit var adManager: RootMcAdManager

    @Inject lateinit var accountSyncScheduler: AccountSyncScheduler

    @Inject lateinit var pushRegistrar: RootMcPushRegistrar



    private var openCoordAdd by mutableStateOf(false)

    private val keepSplashOnScreen = AtomicBoolean(true)

    private var showLoadingSplash by mutableStateOf(true)

    private var bootstrapReady by mutableStateOf(false)

    private var startupError by mutableStateOf<String?>(null)



    override fun onCreate(savedInstanceState: Bundle?) {

        val splashScreen = installSplashScreen()

        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen.get() }

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        handleIntent(intent)

        adManager.setActivity(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
            }
        }

        pushRegistrar.registerCurrentTokenAsync()



        if (savedInstanceState == null) {

            lifecycleScope.launch {

                authRepo.ensureValidSession()
                authRepo.notifyAppSessionStart()

            }

            lifecycleScope.launch {

                val opens = prefs.incrementAppOpenCount()

                val pro = prefs.authProUnlocked.first()

                if (!pro && opens >= 2 && opens % 2 == 0) {

                    UpsellEvents.trigger()

                }

            }

        }



        lifecycleScope.launch {

            val started = System.currentTimeMillis()

            val result = startupBootstrap.run()

            result.fold(

                onSuccess = {

                    bootstrapReady = true

                    startupError = null

                    lifecycleScope.launch {

                        if (prefs.authSignedIn.first()) accountSyncScheduler.requestSync()

                    }

                },

                onFailure = { error ->

                    if (error is DatabaseResetRequiredException) {

                        restartAfterDatabaseReset()

                        return@launch

                    }

                    Log.e(TAG, "Startup bootstrap failed", error)

                    startupError = error.message ?: "Could not open local database"

                    bootstrapReady = false

                },

            )

            val elapsed = System.currentTimeMillis() - started

            if (elapsed < MIN_SPLASH_MS) delay(MIN_SPLASH_MS - elapsed)

            keepSplashOnScreen.set(false)

            showLoadingSplash = false

        }

        try {

            WorkEnqueue.schedulePeriodic(applicationContext)

        } catch (e: Exception) {

            Log.w(TAG, "WorkManager schedule skipped", e)

        }



        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.RESUMED) {

                authRepo.refreshAccountAccess()

                pushRegistrar.registerCurrentTokenAsync()

            }

        }



        setContent {

            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(

                initialValue = RootMcPreferences.THEME_SYSTEM,

            )

            val proUnlocked by prefs.authProUnlocked.collectAsStateWithLifecycle(initialValue = false)

            val darkTheme = when (themeMode) {

                RootMcPreferences.THEME_DARK -> true

                RootMcPreferences.THEME_LIGHT -> false

                else -> isSystemInDarkTheme()

            }

            RootMCTheme(darkTheme = darkTheme) {

                Box(Modifier.fillMaxSize()) {

                    when {

                        showLoadingSplash -> RootMcSplashScreen()

                        startupError != null -> StartupErrorScreen(

                            message = startupError!!,

                            onRetry = { recreate() },

                        )

                        bootstrapReady -> {

                            RootMcApp(

                                openCoordAddOnLaunch = openCoordAdd,

                                onOpenCoordAddConsumed = { openCoordAdd = false },

                                showBannerAds = !proUnlocked,

                                onRecordAdAction = {

                                    lifecycleScope.launch { adManager.recordAction() }

                                },

                            )

                            UpsellOverlay()

                        }

                    }

                }

            }

        }

    }



    private fun restartAfterDatabaseReset() {

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        }

        finishAffinity()

        if (launchIntent != null) {

            startActivity(launchIntent)

        }

        Process.killProcess(Process.myPid())

    }



    private companion object {

        const val MIN_SPLASH_MS = 900L

        const val TAG = "RootMCMain"

        const val REQ_POST_NOTIFICATIONS = 4101

    }



    override fun onStop() {

        super.onStop()

        lifecycleScope.launch {

            if (prefs.authSignedIn.first()) accountSyncScheduler.requestSync()

        }

    }



    override fun onDestroy() {

        adManager.setActivity(null)

        super.onDestroy()

    }



    override fun onNewIntent(intent: Intent) {

        super.onNewIntent(intent)

        setIntent(intent)

        handleIntent(intent)

    }



    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        when {
            data.scheme == "rootmc" && data.host == "coord" -> openCoordAdd = true
            data.scheme == "rootmc" && data.host == "auth" -> handleAuthDeepLink(data)
        }
    }

    private fun handleAuthDeepLink(uri: Uri) {
        val token = uri.getQueryParameter("token")
        lifecycleScope.launch {
            if (!token.isNullOrBlank()) {
                authRepo.establishOAuthSession(token).onSuccess {
                    accountSyncScheduler.requestSync()
                    pushRegistrar.registerCurrentTokenAsync()
                }
            }
        }
    }

}



@Composable

private fun StartupErrorScreen(message: String, onRetry: () -> Unit) {

    Column(

        modifier = Modifier

            .fillMaxSize()

            .padding(24.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),

        horizontalAlignment = Alignment.CenterHorizontally,

    ) {

        Text(

            "RootMC could not start",

            style = MaterialTheme.typography.titleLarge,

            textAlign = TextAlign.Center,

        )

        Text(

            message,

            style = MaterialTheme.typography.bodyMedium,

            color = MaterialTheme.colorScheme.onSurfaceVariant,

            textAlign = TextAlign.Center,

        )

        Button(onClick = onRetry) {

            Text("Try again")

        }

    }

}


