package com.rootrecord.kilauea.alerts.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rootrecord.kilauea.alerts.BuildConfig
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.ui.KilaueaNavRoutes
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.more.MoreViewModel

private const val SimpleWeatherPackage = "com.rootrecord.weathermanager"
private const val SimpleWeatherPlayUrl = "https://play.google.com/store/apps/details?id=com.rootrecord.weathermanager&pcampaignid=web_share"
private const val VisitingHawaiiUrl = "https://rootrecord.online/visiting-hawaii.html"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MoreScreen(
    navController: NavController,
    vm: MoreViewModel = hiltViewModel(),
) {
    val nv by vm.notifyVolcano.collectAsState()
    val nvBypassDnd by vm.notifyVolcanoBypassDnd.collectAsState()
    val nvAlarm by vm.notifyVolcanoAlarmSound.collectAsState()
    val nvElevatedOnly by vm.notifyVolcanoElevatedOnly.collectAsState()
    val nn by vm.notifyNws.collectAsState()
    val ne by vm.notifyEq.collectAsState()
    val nl by vm.notifyLive.collectAsState()
    val th by vm.eqThreshold.collectAsState()

    val signedIn by vm.authSignedIn.collectAsState()
    val authEmail by vm.authEmail.collectAsState()
    val authPro by vm.authProUnlocked.collectAsState()
    val loginBusy by vm.loginBusy.collectAsState()
    val loginError by vm.loginError.collectAsState()
    val latestDev by vm.latestDevMessage.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val fontScale by vm.fontScale.collectAsState()

    var emailField by rememberSaveable { mutableStateOf("") }
    var passwordField by rememberSaveable { mutableStateOf("") }
    var confirmPasswordField by rememberSaveable { mutableStateOf("") }
    var createAccountMode by rememberSaveable { mutableStateOf(false) }
    var showPushProNotice by rememberSaveable { mutableStateOf(false) }
    var simpleWeatherInstalled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    if (showPushProNotice) {
        AlertDialog(
            onDismissRequest = { showPushProNotice = false },
            title = { Text("Membership") },
            text = {
                Text(
                    "Kīlauea volcano push alerts are included. Some alert categories and dashboard features require additional resources and are limited to members only.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPushProNotice = false
                    uriHandler.openUri("https://rootrecord.online/billing")
                }) {
                    Text("See plans")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPushProNotice = false }) {
                    Text("OK")
                }
            },
        )
    }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            passwordField = ""
            confirmPasswordField = ""
            createAccountMode = false
        }
    }

    LaunchedEffect(Unit) {
        simpleWeatherInstalled = context.packageManager.getLaunchIntentForPackage(SimpleWeatherPackage) != null
    }

    val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    val notificationPermissionGranted = postNotif == null || postNotif.status.isGranted

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DisclaimerBanner(compact = false)

        NavigationHubCard(navController = navController)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Display", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use your phone theme by default, or choose a larger app text size for easier reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChoiceButton(
                        label = "System",
                        selected = themeMode == KilaueaPreferences.THEME_SYSTEM,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setThemeMode(KilaueaPreferences.THEME_SYSTEM) },
                    )
                    ChoiceButton(
                        label = "Light",
                        selected = themeMode == KilaueaPreferences.THEME_LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setThemeMode(KilaueaPreferences.THEME_LIGHT) },
                    )
                    ChoiceButton(
                        label = "Dark",
                        selected = themeMode == KilaueaPreferences.THEME_DARK,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setThemeMode(KilaueaPreferences.THEME_DARK) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ChoiceButton(
                        label = "Standard",
                        selected = fontScale.near(1.0f),
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setFontScale(1.0f) },
                    )
                    ChoiceButton(
                        label = "Large",
                        selected = fontScale.near(1.15f),
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setFontScale(1.15f) },
                    )
                    ChoiceButton(
                        label = "Extra large",
                        selected = fontScale.near(1.3f),
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setFontScale(1.3f) },
                    )
                }
            }
        }

        Text("Account (optional)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sign in is optional.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (signedIn && authEmail != null) {
            Text(authEmail!!, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (authPro) {
                    "Membership active on this account."
                } else {
                    "Signed in. Some additional alert categories and resource-heavy features are limited to members only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out")
            }
        } else {
            Text(
                if (createAccountMode) {
                    "Create a Root Record account to sync access across Kīlauea Alerts and other Root Record apps."
                } else {
                    "Sign in with your Root Record account, or create one here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = emailField,
                onValueChange = { emailField = it; vm.clearLoginError() },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentType = if (createAccountMode) ContentType.NewUsername else ContentType.Username
                    },
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !loginBusy,
            )
            OutlinedTextField(
                value = passwordField,
                onValueChange = { passwordField = it; vm.clearLoginError() },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentType = if (createAccountMode) ContentType.NewPassword else ContentType.Password
                    },
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !loginBusy,
            )
            if (createAccountMode) {
                OutlinedTextField(
                    value = confirmPasswordField,
                    onValueChange = { confirmPasswordField = it; vm.clearLoginError() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.NewPassword },
                    singleLine = true,
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !loginBusy,
                )
                Text(
                    "Use at least 8 characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            loginError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            val canSubmit = emailField.isNotBlank() &&
                passwordField.isNotBlank() &&
                (!createAccountMode || (passwordField.length >= 8 && passwordField == confirmPasswordField))
            Button(
                onClick = {
                    if (createAccountMode) {
                        vm.createAccount(emailField, passwordField)
                    } else {
                        vm.login(emailField, passwordField)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy && canSubmit,
            ) {
                Text(
                    when {
                        loginBusy && createAccountMode -> "Creating account…"
                        loginBusy -> "Signing in…"
                        createAccountMode -> "Create account"
                        else -> "Sign in"
                    },
                )
            }
            TextButton(
                onClick = {
                    createAccountMode = !createAccountMode
                    confirmPasswordField = ""
                    vm.clearLoginError()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy,
            ) {
                Text(if (createAccountMode) "I already have an account" else "Create a new account")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("Notification categories", style = MaterialTheme.typography.titleMedium)
        Text(
            if (authPro) {
                "Kīlauea volcano alerts are included. Choose any additional alerts you want on this phone."
            } else {
                "Kīlauea volcano alerts are included. Some alert categories require additional resources and are limited to members only."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (postNotif != null && !postNotif.status.isGranted) {
            Text(
                "Android 13+ requires notification permission — grant when prompted after enabling alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        RowToggle(
            label = "USGS volcano / Kīlauea (free)",
            checked = notificationPermissionGranted && nv,
            onCheckedChange = { on ->
                if (on && !notificationPermissionGranted) {
                    postNotif?.launchPermissionRequest()
                }
                vm.setVolcano(on)
            },
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Volcano alert delivery", style = MaterialTheme.typography.titleSmall)
                Text(
                    "For sleep or Do Not Disturb: allow the urgent channel in Android settings. Alarm-style sound applies to orange/red or eruptive USGS notices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RowToggle(
                    label = "Break Do Not Disturb / Bedtime (urgent alerts)",
                    checked = notificationPermissionGranted && nv && nvBypassDnd,
                    enabled = notificationPermissionGranted && nv,
                    onCheckedChange = { on ->
                        if (on && !notificationPermissionGranted) {
                            postNotif?.launchPermissionRequest()
                        } else {
                            vm.setVolcanoBypassDnd(on)
                        }
                    },
                )
                RowToggle(
                    label = "Alarm-style sound (urgent alerts)",
                    checked = notificationPermissionGranted && nv && nvAlarm,
                    enabled = notificationPermissionGranted && nv,
                    onCheckedChange = { on ->
                        if (on && !notificationPermissionGranted) {
                            postNotif?.launchPermissionRequest()
                        } else {
                            vm.setVolcanoAlarmSound(on)
                        }
                    },
                )
                RowToggle(
                    label = "Only orange/red or eruptive activity",
                    checked = notificationPermissionGranted && nv && nvElevatedOnly,
                    enabled = notificationPermissionGranted && nv,
                    onCheckedChange = { on ->
                        if (on && !notificationPermissionGranted) {
                            postNotif?.launchPermissionRequest()
                        } else {
                            vm.setVolcanoElevatedOnly(on)
                        }
                    },
                )
                OutlinedButton(
                    onClick = { vm.openVolcanoNotificationSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = notificationPermissionGranted && nv,
                ) {
                    Text("Open urgent alert channel in Android settings")
                }
                TextButton(
                    onClick = { vm.openAppNotificationSettings() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("All Kīlauea notification settings")
                }
            }
        }
        RowToggle(
            label = "NWS Hawaiʻi alerts",
            checked = authPro && notificationPermissionGranted && nn,
            onCheckedChange = { on ->
                if (!authPro) showPushProNotice = true
                else {
                    if (on && !notificationPermissionGranted) postNotif?.launchPermissionRequest()
                    vm.setNws(on)
                }
            },
        )
        RowToggle(
            label = "Strong earthquakes (USGS)",
            checked = authPro && notificationPermissionGranted && ne,
            onCheckedChange = { on ->
                if (!authPro) showPushProNotice = true
                else {
                    if (on && !notificationPermissionGranted) postNotif?.launchPermissionRequest()
                    vm.setEq(on)
                }
            },
        )
        Text("Earthquake notify magnitude ≥ ${"%.1f".format(th)}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = th.coerceIn(1f, 6f),
            onValueChange = { v ->
                if (!authPro) showPushProNotice = true
                else vm.setThreshold(v.coerceIn(1f, 6f))
            },
            valueRange = 1f..6f,
            steps = 5,
            enabled = authPro,
        )
        RowToggle(
            label = "When the live stream list changes (optional)",
            checked = authPro && notificationPermissionGranted && nl,
            onCheckedChange = { on ->
                if (!authPro) showPushProNotice = true
                else {
                    if (on && !notificationPermissionGranted) postNotif?.launchPermissionRequest()
                    vm.setLive(on)
                }
            },
        )

        // Latest team update — rendered immediately above the "Desktop version" card so the
        // signal-to-noise is good (settings/toggles above, outbound links below). Only shows when
        // there's actually a message to display; collapses cleanly otherwise.
        latestDev?.let { msg ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Latest update from the team", style = MaterialTheme.typography.titleMedium)
                    if (msg.createdAt.isNotBlank()) {
                        Text(
                            formatDeveloperMessageWhen(msg.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coming soon · Visiting Hawaiʻi", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Planning beyond the volcano? RootRecord is building an offline-first travel guide for all six visitor islands — local beaches, hikes, culture, food, and mālama ʻāina ratings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { uriHandler.openUri(VisitingHawaiiUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Learn more & join waitlist")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Desktop version", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Members can open the full Kīlauea Alerts dashboard in any desktop or laptop browser at kilauea.rootrecord.info.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { uriHandler.openUri("https://rootrecord.online/") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open kilauea.rootrecord.info")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local weather", style = MaterialTheme.typography.titleMedium)
                Text(
                    "For local, user-specific detailed weather, try Simple Weather, our Weather Manager app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(SimpleWeatherPackage)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            uriHandler.openUri(SimpleWeatherPlayUrl)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (simpleWeatherInstalled) "Go to Weather App" else "Get Simple Weather")
                }
            }
        }

        // ---- About & Help ---------------------------------------------------------------
        // Layout mirrors Business Manager's About page
        // (`Web/apps/business-manager-web/src/components/modules/Settings.jsx`) so the five
        // RootRecord products read the same way; copy here is Kilauea-Alerts-specific.
        //
        // Version comes from `BuildConfig.VERSION_NAME` (sourced from `app/build.gradle.kts`
        // `defaultConfig.versionName`). This is what the release builder
        // (`Mobile/kilauea-alerts-android/bump-and-build-release.bat`) reads when naming the
        // APK/AAB artifacts, so any version bump in build.gradle.kts flows into both the
        // outputs and this screen automatically — never hard-code a version here.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("About & Help", style = MaterialTheme.typography.titleMedium)

                Text(
                    "Root Record Kīlauea Alerts",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Mobile build · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "USGS volcano notices, NWS Hawaiʻi alerts, AccuWeather forecasts, and earthquake feeds — focused on Hawaiʻi Island.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    "What Root Record Stands For",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "• Reliability first\n• Clarity over cleverness\n• Operability in the real world\n• Composable services\n• Respectful communication",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Plans", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Core access includes the Volcano view, live USGS/NWS/HVO data, and Kīlauea volcano push alerts. Some features require additional resources, including every Big Island location, advanced alert categories, and the full dashboard at kilauea.rootrecord.info.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Custom App Development", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Need something built for your workflow, team, or customers? Tell us purpose, platforms, scope, and timeline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { uriHandler.openUri("https://rootrecord.online/app-build-request") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("rootrecord.info/app-build-request")
                }

                Text("Where to Get Help", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Use Submit Feedback below for bug reports, ideas, or membership questions. Visit rootrecord.info for the latest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(
                    onClick = { uriHandler.openUri("https://rootrecord.online/") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Root Record Website")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { uriHandler.openUri("https://rootrecord.online/privacy.html") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Privacy")
                    }
                    TextButton(
                        onClick = { uriHandler.openUri("https://rootrecord.online/terms.html") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Terms")
                    }
                }
                TextButton(
                    onClick = { navController.navigate(KilaueaNavRoutes.Feedback) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Submit Feedback")
                }

                TextButton(
                    onClick = { navController.navigate(KilaueaNavRoutes.Photos) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Volcano Photo Gallery & Uploads")
                }

                if (signedIn && authEmail?.trim()?.lowercase() == "rootrecord@outlook.com") {
                    TextButton(
                        onClick = { uriHandler.openUri("https://rootrecord.online/photos-admin.html") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Photo approvals (admin)")
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationHubCard(navController: NavController) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Menu", style = MaterialTheme.typography.titleMedium)
            Text(
                "Browse Kīlauea Alerts by category.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NavigationSection("Official Updates") {
                NavButton("Home", Modifier.weight(1f)) {
                    navController.navigateMain(KilaueaNavRoutes.Home)
                }
                NavButton("Alerts", Modifier.weight(1f)) {
                    navController.navigateMain(KilaueaNavRoutes.Alerts)
                }
            }
            NavigationSection("Conditions") {
                NavButton("Weather", Modifier.weight(1f)) {
                    navController.navigateMain(KilaueaNavRoutes.Weather)
                }
                NavButton("Earthquakes", Modifier.weight(1f)) {
                    navController.navigateMain(KilaueaNavRoutes.Earthquakes)
                }
            }
            NavigationSection("Media & Community") {
                NavButton("Live Feeds", Modifier.weight(1f)) {
                    navController.navigateMain(KilaueaNavRoutes.LiveFeeds)
                }
                NavButton("Photos", Modifier.weight(1f)) {
                    navController.navigate(KilaueaNavRoutes.Photos)
                }
            }
            NavigationSection("Analysis & Support") {
                NavButton("AI Analysis", Modifier.weight(1f)) {
                    navController.navigate(KilaueaNavRoutes.AiAnalysis)
                }
                NavButton("Submit Feedback", Modifier.weight(1f)) {
                    navController.navigate(KilaueaNavRoutes.Feedback)
                }
            }
        }
    }
}

@Composable
private fun NavigationSection(
    title: String,
    content: @Composable RowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun NavButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label)
    }
}

private fun NavController.navigateMain(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun RowToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}

private fun Float.near(other: Float): Boolean = kotlin.math.abs(this - other) < 0.01f

/** Render a developer-message ISO timestamp as a short local date/time, falling back to raw. */
private fun formatDeveloperMessageWhen(iso: String): String {
    return runCatching {
        val instant = java.time.Instant.parse(iso)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        zoned.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }.getOrElse { iso }
}
