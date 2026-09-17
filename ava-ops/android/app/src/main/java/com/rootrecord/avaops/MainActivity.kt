package com.rootrecord.avaops

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rootrecord.avaops.ops.AvaBoardSnapshot
import com.rootrecord.avaops.ops.BluetoothOpsProvider
import com.rootrecord.avaops.ops.BoardApiClient
import com.rootrecord.avaops.ops.OpsConfig
import com.rootrecord.avaops.ops.OpsConfigStore
import com.rootrecord.avaops.ops.OpsServer
import com.rootrecord.avaops.ops.OpsTransport
import com.rootrecord.avaops.ops.ProviderKind
import com.rootrecord.avaops.ops.ServerAction
import com.rootrecord.avaops.ops.ServerStatus
import com.rootrecord.avaops.ops.createOpsProvider
import com.rootrecord.avaops.ui.screens.AvaSettingsScreen
import com.rootrecord.avaops.ui.screens.ConnectionAttempt
import com.rootrecord.avaops.ui.screens.EarthquakeDeskScreen
import com.rootrecord.avaops.ui.screens.EcoFlowPowerScreen
import com.rootrecord.avaops.ui.screens.HomeScreen
import com.rootrecord.avaops.ui.screens.MessagingScreen
import com.rootrecord.avaops.ui.screens.MinecraftLiveScreen
import com.rootrecord.avaops.ui.screens.ObsMediaScreen
import com.rootrecord.avaops.ui.screens.OperationsApiScreen
import com.rootrecord.avaops.ui.screens.ReportsBoardScreen
import com.rootrecord.avaops.ui.screens.RootRecordServicesScreen
import com.rootrecord.avaops.ui.screens.SystemMetricsScreen
import com.rootrecord.avaops.ui.screens.VolcanoMonitorScreen
import com.rootrecord.avaops.ui.screens.WeatherScreen
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaBackground
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.avaColorScheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AvaOpsApp() }
    }
}

private object Routes {
    const val HOME = "home"
    const val SERVICES = "services"
    const val OPS_API = "ops_api"
    const val WEATHER = "weather"
    const val VOLCANO = "volcano"
    const val EARTHQUAKE = "earthquake"
    const val POWER = "power"
    const val REPORTS = "reports"
    const val MEDIA = "media"
    const val MESSAGING = "messaging"
    const val MINECRAFT = "minecraft"
    const val METRICS = "metrics"
    const val SETTINGS = "settings"
}

private val quickStatusRoutes = mapOf(
    "weather" to Routes.WEATHER,
    "volcano" to Routes.VOLCANO,
    "earthquake" to Routes.EARTHQUAKE,
    "power" to Routes.POWER,
    "reports" to Routes.REPORTS,
    "media" to Routes.MEDIA,
    "messaging" to Routes.MESSAGING,
    "minecraft" to Routes.MINECRAFT,
    "metrics" to Routes.METRICS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvaOpsApp() {
    val context = LocalContext.current
    val configStore = remember(context) { OpsConfigStore(context) }
    var configRevision by remember { mutableIntStateOf(0) }
    val provider = remember(context, configRevision) { createOpsProvider(context) }
    val boardClient = remember(context, configRevision) {
        val cfg = configStore.load()
        BoardApiClient(
            baseUrls = listOf(cfg.localApiBaseUrl, cfg.cloudflareApiBaseUrl),
            accessToken = cfg.authToken.takeIf { it.isNotBlank() },
        )
    }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    var pairedDevices by remember { mutableStateOf(emptyList<Pair<String, String>>()) }

    fun refreshPairedDevices() {
        pairedDevices = configStore.getPairedBluetoothDevices()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPairedDevices()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                ),
            )
        } else {
            refreshPairedDevices()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPairedDevices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var servers by remember { mutableStateOf(emptyList<OpsServer>()) }
    val audit = remember { mutableStateListOf("Control panel opened") }
    var rconCommand by remember { mutableStateOf("") }
    var selectedServer by remember { mutableStateOf("prod") }
    var activeTransport by remember { mutableStateOf(OpsTransport.DISCONNECTED) }
    var providerDetail by remember { mutableStateOf("Connecting to workstation") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf<AvaBoardSnapshot?>(null) }
    var boardError by remember { mutableStateOf<String?>(null) }

    fun record(message: String) {
        audit.add(0, message)
        while (audit.size > 8) audit.removeLast()
    }

    fun updateServer(id: String, action: String) {
        servers = servers.map { server ->
            if (server.id != id) server else when (action) {
                "start", "restart" -> server.copy(status = ServerStatus.STARTING, playersOnline = null)
                "stop" -> server.copy(status = ServerStatus.OFFLINE, playersOnline = 0)
                else -> server
            }
        }
        record("$action ${servers.firstOrNull { it.id == id }?.name ?: id}")
        scope.launch {
            runCatching {
                val next = provider.perform(id, ServerAction.valueOf(action.uppercase()))
                servers = servers.map { if (it.id == next.id) next else it }
                activeTransport = provider.transport
                providerDetail = "${provider.transport.displayName} control channel"
            }.onFailure { error ->
                providerDetail = error.message ?: "Provider unavailable"
                record("$action failed: ${error.message ?: "provider unavailable"}")
            }
        }
    }

    suspend fun refreshServersOnce() {
        runCatching { provider.servers() }
            .onSuccess { remoteServers ->
                if (remoteServers.isNotEmpty()) {
                    servers = remoteServers
                    if (servers.none { it.id == selectedServer }) {
                        selectedServer = servers.first().id
                    }
                }
                activeTransport = provider.transport
                providerDetail = "${provider.transport.displayName} control channel"
                record("Status refreshed via ${provider.transport.displayName}")
            }
            .onFailure { error ->
                activeTransport = provider.transport
                providerDetail = error.message ?: "Provider unavailable"
                record("Refresh failed: ${error.message ?: "provider unavailable"}")
            }
    }

    suspend fun refreshBoardOnce() {
        val cfg = configStore.load()
        val bt = BluetoothOpsProvider(
            context = context,
            deviceAddress = cfg.bluetoothDeviceAddress,
            deviceName = cfg.bluetoothDeviceName,
            token = cfg.authToken.takeIf { it.isNotBlank() },
        )
        val result = runCatching { bt.dashboard() }
            .onFailure { error ->
                record("Bluetooth ops: ${error.message ?: "connect failed"}")
            }
            .recoverCatching { boardClient.fetchDashboard() }
        result
            .onSuccess { snapshot ->
                board = snapshot
                boardError = null
                if (snapshot.servers.isNotEmpty()) {
                    servers = snapshot.servers
                    if (servers.none { it.id == selectedServer }) {
                        selectedServer = servers.first().id
                    }
                }
                record("Board refreshed ${snapshot.generatedAt ?: ""}".trim())
            }
            .onFailure { error ->
                boardError = error.message ?: "dashboard unavailable"
                record("Board refresh failed: ${boardError}")
            }
    }

    fun refreshServers() {
        scope.launch { refreshServersOnce() }
    }

    fun refreshBoard() {
        scope.launch { refreshBoardOnce() }
    }

    fun sendRcon() {
        if (rconCommand.isNotBlank()) {
            val cmd = rconCommand.trim()
            rconCommand = ""
            record("RCON sent: $cmd")
            scope.launch {
                runCatching {
                    provider.rcon(selectedServer, cmd)
                }.onSuccess { result ->
                    activeTransport = provider.transport
                    val output = result.output.ifBlank { if (result.accepted) "Accepted" else "Pending" }
                    record("RCON output: $output")
                }.onFailure { err ->
                    activeTransport = provider.transport
                    record("RCON error: ${err.message ?: "failed"}")
                }
            }
        }
    }

    LaunchedEffect(configRevision) {
        refreshServersOnce()
        refreshBoardOnce()
    }

    MaterialTheme(colorScheme = avaColorScheme()) {
        Scaffold(
            containerColor = AvaBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Ava Ops", fontWeight = FontWeight.Bold)
                            Text("Private control panel", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        Icon(Icons.Outlined.Cloud, contentDescription = "Remote operations")
                    },
                    actions = {
                        IconButton(onClick = {
                            refreshServers()
                            refreshBoard()
                        }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = {
                            refreshPairedDevices()
                            showSettingsDialog = true
                        }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AvaBackground),
                )
            },
        ) { insets ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(insets),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        transport = activeTransport,
                        transportDetail = providerDetail,
                        servers = servers,
                        selectedServerId = selectedServer,
                        onSelectServer = { selectedServer = it },
                        onServerAction = ::updateServer,
                        rconCommand = rconCommand,
                        onRconCommandChange = { rconCommand = it },
                        onRconSend = ::sendRcon,
                        audit = audit,
                        board = board,
                        boardError = boardError,
                        onOpenOperationsApi = { navController.navigate(Routes.OPS_API) },
                        onOpenServices = { navController.navigate(Routes.SERVICES) },
                        onOpenQuickStatus = { id ->
                            quickStatusRoutes[id]?.let { navController.navigate(it) }
                        },
                    )
                }
                composable(Routes.SERVICES) {
                    val server = servers.firstOrNull { it.id == selectedServer } ?: servers.firstOrNull()
                    if (server == null) {
                        RootRecordServicesScreen(
                            server = OpsServer("none", "No live server", ProviderKind.ROOTRECORD, ServerStatus.UNKNOWN),
                            origin = board?.origin,
                            procs = board?.procs,
                            tunnel = board?.tunnel,
                            minecraft = board?.minecraft,
                            rconCommand = rconCommand,
                            onCommandChange = { rconCommand = it },
                            onSend = ::sendRcon,
                            onStart = {},
                            onStop = {},
                            onRestart = {},
                            onBack = { navController.popBackStack() },
                        )
                    } else {
                        RootRecordServicesScreen(
                            server = server,
                            origin = board?.origin,
                            procs = board?.procs,
                            tunnel = board?.tunnel,
                            minecraft = board?.minecraft,
                            rconCommand = rconCommand,
                            onCommandChange = { rconCommand = it },
                            onSend = ::sendRcon,
                            onStart = { updateServer(server.id, "start") },
                            onStop = { updateServer(server.id, "stop") },
                            onRestart = { updateServer(server.id, "restart") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.OPS_API) {
                    val cfg = remember(configRevision) { configStore.load() }
                    val activeUrl = cfg.localApiBaseUrl.ifBlank { cfg.cloudflareApiBaseUrl }
                    val parsedUrl = runCatching { java.net.URL(activeUrl) }.getOrNull()
                    OperationsApiScreen(
                        transport = activeTransport,
                        host = parsedUrl?.host ?: activeUrl,
                        port = parsedUrl?.let { if (it.port > 0) it.port.toString() else if (it.protocol == "https") "443" else "80" } ?: "",
                        lastHandshake = board?.generatedAt ?: "No successful handshake yet",
                        attempts = audit.take(6).mapIndexed { index, entry ->
                            ConnectionAttempt(
                                label = if (index == 0 && activeTransport != OpsTransport.DISCONNECTED) "Success" else "Info",
                                detail = entry,
                                time = "",
                                ok = activeTransport != OpsTransport.DISCONNECTED,
                            )
                        },
                        onRetry = {
                            refreshServers()
                            refreshBoard()
                        },
                        onOpenSettings = {
                            refreshPairedDevices()
                            showSettingsDialog = true
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.WEATHER) { WeatherScreen(weather = board?.weather, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.VOLCANO) { VolcanoMonitorScreen(kilauea = board?.kilauea, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.EARTHQUAKE) { EarthquakeDeskScreen(quakes = board?.quakes, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.POWER) { EcoFlowPowerScreen(power = board?.power, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.REPORTS) { ReportsBoardScreen(reports = board?.reports, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.MEDIA) { ObsMediaScreen(media = board?.media, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.MESSAGING) { MessagingScreen(inbox = board?.inbox, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.MINECRAFT) { MinecraftLiveScreen(minecraft = board?.minecraft, onBack = { navController.popBackStack() }, onRefresh = ::refreshBoard) }
                composable(Routes.METRICS) {
                    SystemMetricsScreen(
                        host = board?.host,
                        mysql = board?.mysql,
                        origin = board?.origin,
                        tunnel = board?.tunnel,
                        ollama = board?.ollama,
                        onBack = { navController.popBackStack() },
                        onRefresh = ::refreshBoard,
                    )
                }
                composable(Routes.SETTINGS) { AvaSettingsScreen(onBack = { navController.popBackStack() }, provider = provider) }
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    currentConfig = configStore.load(),
                    pairedDevices = pairedDevices,
                    onOpenBluetoothSettings = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    },
                    onDismiss = { showSettingsDialog = false },
                    onSave = { newConfig ->
                        configStore.save(newConfig)
                        configRevision++
                        showSettingsDialog = false
                        record("Settings updated")
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    currentConfig: OpsConfig,
    pairedDevices: List<Pair<String, String>>,
    onOpenBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (OpsConfig) -> Unit,
) {
    var btAddress by remember { mutableStateOf(currentConfig.bluetoothDeviceAddress) }
    var btName by remember { mutableStateOf(currentConfig.bluetoothDeviceName) }
    var localUrl by remember { mutableStateOf(currentConfig.localApiBaseUrl) }
    var cfUrl by remember { mutableStateOf(currentConfig.cloudflareApiBaseUrl) }
    var token by remember { mutableStateOf(currentConfig.authToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Workstation Bluetooth is the primary connection. Select your paired computer below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )

                // Button to launch native phone Bluetooth settings directly
                OutlinedButton(
                    onClick = onOpenBluetoothSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Phone Bluetooth Settings")
                }

                Text("Paired Bluetooth Devices", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                // Option: Auto-Detect
                val isAutoSelected = btAddress.isBlank() && btName.isBlank()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAutoSelected) Color(0xFF1E3524) else Color(0xFF18222D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            btAddress = ""
                            btName = ""
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (isAutoSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isAutoSelected) AvaAccent else Color.White.copy(alpha = 0.4f),
                        )
                        Column {
                            Text("Auto-Detect Workstation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Connects to bonded 'Ava', 'RootRecord', or 'Desk'", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }

                // List of real paired devices from the phone
                if (pairedDevices.isNotEmpty()) {
                    pairedDevices.forEach { (name, address) ->
                        val isSelected = btAddress == address || (btAddress.isBlank() && btName.equals(name, ignoreCase = true))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1E3524) else Color(0xFF18222D)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    btAddress = address
                                    btName = name
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) AvaAccent else Color.White.copy(alpha = 0.4f),
                                )
                                Column {
                                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(address, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "No paired devices detected. Use 'Open Phone Bluetooth Settings' above to pair with your PC first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AvaWarning,
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                Text("Fallback Endpoints", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = localUrl,
                    onValueChange = { localUrl = it },
                    label = { Text("Localhost / LAN API Base URL") },
                    placeholder = { Text("http://192.168.1.66:8787") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = cfUrl,
                    onValueChange = { cfUrl = it },
                    label = { Text("Cloudflare API Base URL") },
                    placeholder = { Text("leave blank until a public ops host exists") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Auth Token (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        OpsConfig(
                            bluetoothDeviceAddress = btAddress,
                            bluetoothDeviceName = btName,
                            localApiBaseUrl = localUrl,
                            cloudflareApiBaseUrl = cfUrl,
                            authToken = token,
                        )
                    )
                }
            ) {
                Text("Save & Reconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = AvaPanel,
    )
}

private val OpsTransport.displayName: String
    get() = when (this) {
        OpsTransport.BLUETOOTH -> "Bluetooth"
        OpsTransport.LOCALHOST -> "Local"
        OpsTransport.CLOUDFLARE -> "Cloudflare"
        OpsTransport.DISCONNECTED -> "Disconnected"
    }

