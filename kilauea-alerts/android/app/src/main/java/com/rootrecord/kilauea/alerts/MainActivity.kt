package com.rootrecord.kilauea.alerts

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.messaging.FirebaseMessaging
import com.rootrecord.kilauea.alerts.ads.KilaueaAdManager
import com.rootrecord.kilauea.alerts.ui.KilaueaNavRoutes
import com.rootrecord.kilauea.alerts.ui.components.AdMobBanner
import com.rootrecord.kilauea.alerts.ui.screens.AiAnalysisScreen
import com.rootrecord.kilauea.alerts.ui.screens.AlertsScreen
import com.rootrecord.kilauea.alerts.ui.screens.EarthquakesScreen
import com.rootrecord.kilauea.alerts.ui.screens.FeedbackScreen
import com.rootrecord.kilauea.alerts.ui.screens.HomeScreen
import com.rootrecord.kilauea.alerts.ui.screens.LiveFeedsScreen
import com.rootrecord.kilauea.alerts.ui.screens.MoreScreen
import com.rootrecord.kilauea.alerts.ui.screens.PhotosScreen
import com.rootrecord.kilauea.alerts.ui.screens.SituationScreen
import com.rootrecord.kilauea.alerts.ui.screens.WeatherDetailScreen
import com.rootrecord.kilauea.alerts.ui.screens.WeatherScreen
import com.rootrecord.kilauea.alerts.ui.theme.KilaueaTheme
import com.rootrecord.kilauea.alerts.ui.upsell.UpsellEvents
import com.rootrecord.kilauea.alerts.ui.upsell.UpsellOverlay
import com.rootrecord.kilauea.alerts.ui.welcome.WelcomeTutorialOverlay
import com.rootrecord.kilauea.alerts.work.WorkEnqueue
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.RootRecordAuthRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: KilaueaPreferences
    @Inject lateinit var authRepo: RootRecordAuthRepository
    @Inject lateinit var adManager: KilaueaAdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adManager.setActivity(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        WorkEnqueue.schedulePeriodic(applicationContext)

        if (savedInstanceState == null) {
            WorkEnqueue.enqueueAlertPollOnLaunch(applicationContext)
        }

        // Pro upsell counter — only bump on real launches, not config-change recreates.
        // First open is silent; #2, #4, #6, … trigger the overlay for free accounts. Feature
        // gates (e.g. non-Volcano location taps) fire UpsellEvents.trigger() independently.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val n = prefs.incrementAppOpenCount()
                val pro = prefs.authProUnlocked.first()
                if (!pro && n >= 2 && n % 2 == 0) {
                    UpsellEvents.trigger()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                authRepo.refreshAccountAccess()
            }
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                authRepo.registerPushToken(token)
            }
        }

        setContent {
            val proUnlocked by prefs.authProUnlocked.collectAsStateWithLifecycle(initialValue = false)
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = KilaueaPreferences.THEME_SYSTEM)
            val fontScale by prefs.fontScale.collectAsStateWithLifecycle(initialValue = 1f)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                KilaueaPreferences.THEME_LIGHT -> false
                KilaueaPreferences.THEME_DARK -> true
                else -> systemDark
            }
            KilaueaTheme(
                darkTheme = darkTheme,
                fontScale = fontScale,
            ) {
                KilaueaApp(
                    initialTab = intent?.getStringExtra(EXTRA_OPEN_TAB),
                    openSituation = intent?.getBooleanExtra(EXTRA_OPEN_SITUATION, false) == true,
                    showBannerAds = !proUnlocked,
                    onRecordAdAction = {
                        lifecycleScope.launch { adManager.recordAction() }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        adManager.setActivity(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_ALERTS = "alerts"
        const val EXTRA_OPEN_SITUATION = "open_situation"
    }
}

private data class TabSpec(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
private fun KilaueaApp(
    initialTab: String?,
    openSituation: Boolean,
    showBannerAds: Boolean,
    onRecordAdAction: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(initialTab) {
        if (initialTab == MainActivity.TAB_ALERTS) {
            navController.navigate(KilaueaNavRoutes.Alerts) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(openSituation) {
        if (openSituation) {
            navController.navigate(KilaueaNavRoutes.Situation) {
                launchSingleTop = true
            }
        }
    }

    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                WorkEnqueue.enqueueAlertPollIfDue(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val tabs = listOf(
        TabSpec(KilaueaNavRoutes.Home, R.string.nav_home, Icons.Default.Home),
        TabSpec(KilaueaNavRoutes.Earthquakes, R.string.nav_earthquakes, Icons.Default.Terrain),
        TabSpec(KilaueaNavRoutes.Weather, R.string.nav_weather, Icons.Default.Cloud),
        TabSpec(KilaueaNavRoutes.LiveFeeds, R.string.nav_live_feeds, Icons.Default.LiveTv),
        TabSpec(KilaueaNavRoutes.Alerts, R.string.nav_alerts, Icons.Default.Warning),
        TabSpec(KilaueaNavRoutes.More, R.string.nav_more, Icons.Default.Menu),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val current = navBackStackEntry?.destination
    val currentRoute = current?.route
    var lastAdActionRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute != lastAdActionRoute) {
            if (lastAdActionRoute != null) {
                onRecordAdAction()
            }
            lastAdActionRoute = currentRoute
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
            ),
            bottomBar = {
                Column(Modifier.fillMaxWidth()) {
                    if (showBannerAds) {
                        AdMobBanner()
                    }
                    NavigationBar {
                        val itemColors = NavigationBarItemDefaults.colors()
                        tabs.forEach { tab ->
                            val selected = current?.hierarchy?.any { it.route == tab.route } == true
                            val labelText = stringResource(tab.labelRes)
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = labelText,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        text = labelText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                alwaysShowLabel = false,
                                colors = itemColors,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = KilaueaNavRoutes.Home,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(KilaueaNavRoutes.Home) { HomeScreen(navController = navController) }
                composable(KilaueaNavRoutes.Earthquakes) { EarthquakesScreen() }
                composable(KilaueaNavRoutes.Weather) {
                    WeatherScreen(navController = navController)
                }
                composable(
                    "weather_detail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    WeatherDetailScreen(
                        locationId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(KilaueaNavRoutes.LiveFeeds) { LiveFeedsScreen() }
                composable(KilaueaNavRoutes.Alerts) { AlertsScreen(navController = navController) }
                composable(KilaueaNavRoutes.AiAnalysis) {
                    AiAnalysisScreen(onBack = { navController.popBackStack() })
                }
                composable(KilaueaNavRoutes.Situation) {
                    SituationScreen(onBack = { navController.popBackStack() })
                }
                composable(KilaueaNavRoutes.More) { MoreScreen(navController = navController) }
                composable(KilaueaNavRoutes.Photos) {
                    PhotosScreen(onBack = { navController.popBackStack() })
                }
                composable(KilaueaNavRoutes.Feedback) {
                    FeedbackScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        WelcomeTutorialOverlay()
        UpsellOverlay()
    }
}
