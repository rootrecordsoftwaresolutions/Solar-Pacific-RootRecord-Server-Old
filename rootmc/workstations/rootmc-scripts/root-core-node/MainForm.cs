namespace RootMc.CoreNode;

using System.Diagnostics;
using System.Text.Json;

internal sealed class MainForm : Form
{
    private readonly NodeConfig _cfg;
    private readonly AuthService _auth;
    private readonly RootMcApiClient _apiClient;
    private readonly NotifyIcon _tray;
    private readonly System.Windows.Forms.Timer _timer;
    private readonly System.Windows.Forms.Timer _authPoll;
    private LocalAuthListener? _authListener;

    private readonly TabControl _tabs;
    private readonly ToolTip _tips;
    private readonly Label _badge;
    private readonly Label _mysql;
    private readonly Label _apiStatus;
    private readonly Label _gateway;
    private readonly Label _prefReason;
    private readonly Label _sync;
    private readonly Label _dbCopy;
    private readonly ListView _serversList;
    private readonly TextBox _log;
    private readonly Label _mysqlTabBody;
    private readonly Label _pmaTabStatus;
    private readonly Label _localTestingBody;
    private bool _mysqlReplicaStarted;
    private bool _edgeEnsureStarted;
    private bool _presenceStarted;
    private Process? _presenceProc;
    private Process? _testServerProc;
    private bool _loggedMysqlSettings;

    private readonly Label _accountStatus;
    private readonly Label _profileBody;
    private string? _token;
    private AccountMe? _account;
    private List<DevServerHealth> _servers = new();

    public MainForm()
    {
        _cfg = NodeConfig.Load();
        _auth = new AuthService(_cfg);
        _apiClient = new RootMcApiClient(_cfg);
        _token = _auth.LoadToken();

        // File log beside install (Desktop test server\Root-Core-Node\logs)
        NodeLog.BindFile(Path.Combine(AppContext.BaseDirectory, "logs", "node.log"));

        Text = "Root-Core-Node — RootMC Network";
        MinimumSize = new Size(720, 780);
        Size = new Size(780, 820);
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.Sizable;
        MaximizeBox = true;

        _tips = new ToolTip
        {
            AutoPopDelay = 12000,
            InitialDelay = 400,
            ReshowDelay = 200,
            ShowAlways = true,
        };

        _tabs = new TabControl { Dock = DockStyle.Fill, Padding = new Point(8, 8) };
        Controls.Add(_tabs);

        // --- Ops tab ---
        var ops = new TabPage("Ops") { Padding = new Padding(8) };
        _tabs.TabPages.Add(ops);

        var split = new SplitContainer
        {
            Dock = DockStyle.Fill,
            Orientation = Orientation.Horizontal,
            SplitterWidth = 6,
            FixedPanel = FixedPanel.Panel1,
            Panel1MinSize = 360,
            Panel2MinSize = 140,
        };
        ops.Controls.Add(split);

        var top = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 4,
            Padding = new Padding(4),
        };
        top.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100f));
        top.RowStyles.Add(new RowStyle(SizeType.Absolute, 44f));
        top.RowStyles.Add(new RowStyle(SizeType.Absolute, 148f));
        top.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
        top.RowStyles.Add(new RowStyle(SizeType.Absolute, 72f));
        split.Panel1.Controls.Add(top);

        _badge = new Label
        {
            Dock = DockStyle.Fill,
            Margin = new Padding(0, 0, 0, 8),
            TextAlign = ContentAlignment.MiddleCenter,
            Font = new Font(Font.FontFamily, 13f, FontStyle.Bold),
            Text = "Network: …",
            BackColor = Color.FromArgb(70, 70, 70),
            ForeColor = Color.White,
        };
        top.Controls.Add(_badge, 0, 0);

        var status = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 6,
            Margin = new Padding(0, 0, 0, 8),
            Padding = new Padding(6, 4, 6, 4),
        };
        status.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 96f));
        status.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100f));
        for (var i = 0; i < 6; i++) status.RowStyles.Add(new RowStyle(SizeType.Percent, 100f / 6f));
        _mysql = AddStatusRow(status, 0, "MySQL", "…");
        _apiStatus = AddStatusRow(status, 1, "Local API", "…");
        _gateway = AddStatusRow(status, 2, "Gateway", "…");
        _prefReason = AddStatusRow(status, 3, "Reason", "…");
        _sync = AddStatusRow(status, 4, "Pref sync", "…");
        _dbCopy = AddStatusRow(status, 5, "DB copy", "…");
        top.Controls.Add(status, 0, 1);

        var serversBox = new GroupBox
        {
            Text = "Servers connected",
            Dock = DockStyle.Fill,
            Padding = new Padding(8, 6, 8, 8),
            Margin = new Padding(0, 0, 0, 8),
        };
        _serversList = new ListView
        {
            Dock = DockStyle.Fill,
            View = View.Details,
            FullRowSelect = true,
            GridLines = true,
            HeaderStyle = ColumnHeaderStyle.Nonclickable,
            MultiSelect = false,
        };
        _serversList.Columns.Add("Server", 160);
        _serversList.Columns.Add("Connection", 90);
        _serversList.Columns.Add("Health", 80);
        _serversList.Columns.Add("Players", 60);
        _serversList.Columns.Add("Last seen", 140);
        _serversList.DoubleClick += (_, _) => OpenSelectedServerTab();
        serversBox.Controls.Add(_serversList);
        top.Controls.Add(serversBox, 0, 2);

        var buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            WrapContents = true,
            AutoScroll = true,
            FlowDirection = FlowDirection.LeftToRight,
            Padding = new Padding(0, 2, 0, 0),
        };
        buttons.Controls.Add(Btn("Start Edge", StartEdge,
            "Start local-edge (API/gateway/preference) plus the MySQL replica loop that keeps rootmc_claims/rootmc_towny updated from Shockbyte."));
        buttons.Controls.Add(Btn("Stop Edge", StopEdge,
            "Stop local-edge processes. Preference will fall back toward Cloudflare when edge is down."));
        buttons.Controls.Add(Btn("Force RootMC Network", () => ForcePref("local"),
            "Write force_mode=local so Paper/API prefer this Node over Cloudflare until cleared."));
        buttons.Controls.Add(Btn("Force Cloudflare", () => ForcePref("cloudflare"),
            "Force Cloudflare as the active provider even if local edge is healthy."));
        buttons.Controls.Add(Btn("Auto preference", () => ForcePref("auto"),
            "Clear the force file and let health checks choose RootMC Network vs Cloudflare automatically."));
        buttons.Controls.Add(Btn("Install autostart", InstallAutostart,
            "Register Root-Core-Node.exe as the Windows logon runner (replaces the old PowerShell edge console task)."));
        buttons.Controls.Add(Btn("Open logs folder", OpenLogs,
            "Open the local-edge logs folder in Explorer."));
        top.Controls.Add(buttons, 0, 3);

        var consoleBox = new GroupBox
        {
            Text = "Console",
            Dock = DockStyle.Fill,
            Padding = new Padding(10, 8, 10, 10),
        };
        _log = new TextBox
        {
            Dock = DockStyle.Fill,
            Multiline = true,
            ReadOnly = true,
            ScrollBars = ScrollBars.Both,
            WordWrap = false,
            Font = new Font("Consolas", 9.25f),
            BackColor = Color.FromArgb(24, 24, 24),
            ForeColor = Color.FromArgb(210, 210, 210),
            BorderStyle = BorderStyle.FixedSingle,
            MaxLength = 0,
        };
        consoleBox.Controls.Add(_log);
        split.Panel2.Controls.Add(consoleBox);
        NodeLog.Bind(Log);
        _tips.SetToolTip(_badge, "Node connected (local edge) or Fallback Servers when the Node is down / forced off.");
        _tips.SetToolTip(_serversList, "Developer servers linked to your Discord account. Double-click a row to open that server’s tab.");
        _tips.SetToolTip(_log, "Live verbose log of every Node send/receive, health probe, file write, and script output.");

        // --- MySQL tab ---
        var mysqlPage = new TabPage("MySQL") { Padding = new Padding(12), Name = "tabMysql" };
        _tabs.TabPages.Add(mysqlPage);
        var mysqlLayout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 3,
        };
        mysqlLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 36f));
        mysqlLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48f));
        mysqlLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
        mysqlPage.Controls.Add(mysqlLayout);
        mysqlLayout.Controls.Add(new Label
        {
            Text = "Local MySQL (:3307) — full Claims/Towny replicas (mcMMO, votes, playtime, economy, shops).",
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
        }, 0, 0);
        var mysqlBtns = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = true };
        mysqlBtns.Controls.Add(Btn("Start MySQL", StartMysql,
            "Start (or install) local MySQL on port " + _cfg.MysqlPort + "."));
        mysqlBtns.Controls.Add(Btn("Sync MySQL now", SyncMysqlNow,
            "Pull full Claims + Towny Shockbyte databases into rootmc_claims / rootmc_towny."));
        mysqlBtns.Controls.Add(Btn("Start replica loop", EnsureMysqlReplicaLoop,
            "Ensure the automatic MySQL replica loop is running (interval from config)."));
        mysqlBtns.Controls.Add(Btn("Refresh status", () =>
        {
            RefreshMysqlTab();
            LogMysqlConnectionSettings(force: true);
        }, "Reload local MySQL connection settings and status into this tab and the console."));
        mysqlLayout.Controls.Add(mysqlBtns, 0, 1);
        _mysqlTabBody = new Label
        {
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.TopLeft,
            Font = new Font("Consolas", 9.5f),
            Text = "Loading MySQL settings…",
            AutoSize = false,
        };
        mysqlLayout.Controls.Add(_mysqlTabBody, 0, 2);

        // --- phpMyAdmin tab ---
        var pmaPage = new TabPage("phpMyAdmin") { Padding = new Padding(12), Name = "tabPhpMyAdmin" };
        _tabs.TabPages.Add(pmaPage);
        var pmaLayout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 3,
        };
        pmaLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 36f));
        pmaLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48f));
        pmaLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
        pmaPage.Controls.Add(pmaLayout);
        pmaLayout.Controls.Add(new Label
        {
            Text = "Browse local MySQL in the browser (rootmc_claims, rootmc_towny, rootmc_network).",
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
        }, 0, 0);
        var pmaBtns = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = true };
        pmaBtns.Controls.Add(Btn("Start phpMyAdmin", StartPhpMyAdmin,
            "Start the local PHP built-in server for phpMyAdmin."));
        pmaBtns.Controls.Add(Btn("Stop phpMyAdmin", StopPhpMyAdmin,
            "Stop the local phpMyAdmin PHP server."));
        pmaBtns.Controls.Add(Btn("Open phpMyAdmin", () => OpenUrl(_cfg.PhpMyAdminUrl),
            "Open " + _cfg.PhpMyAdminUrl + " in your browser."));
        pmaLayout.Controls.Add(pmaBtns, 0, 1);
        _pmaTabStatus = new Label
        {
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.TopLeft,
            Font = new Font("Consolas", 9.5f),
            Text = "URL: " + _cfg.PhpMyAdminUrl,
            AutoSize = false,
        };
        pmaLayout.Controls.Add(_pmaTabStatus, 0, 2);

        // --- Local Testing tab (Desktop Paper test server) ---
        var localPage = new TabPage("Local Testing") { Padding = new Padding(12), Name = "tabLocalTesting" };
        _tabs.TabPages.Add(localPage);
        var localLayout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 3,
        };
        localLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 40f));
        localLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 84f));
        localLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
        localPage.Controls.Add(localLayout);
        localLayout.Controls.Add(new Label
        {
            Text = "ROOTMC DEV — local Paper on Desktop SSD (playit → Claims/Towny /test).",
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
        }, 0, 0);
        var localBtns = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = true };
        localBtns.Controls.Add(Btn("Start server", StartTestServer,
            "Launch Paper (start.bat) in the Desktop test server folder."));
        localBtns.Controls.Add(Btn("Boot once", BootTestServerOnce,
            "Boot until Done, then stop (boot-once.ps1)."));
        localBtns.Controls.Add(Btn("Stop server", StopTestServer,
            "Stop the local Paper process started from this tab (or matching paper jar)."));
        localBtns.Controls.Add(Btn("Sync plugins", SyncTestServerPlugins,
            "Copy all jars from Towny + Claims handoffs into the combined local test server."));
        localBtns.Controls.Add(Btn("Ensure MySQL schema", EnsureTestServerMysql,
            "Create/refresh rootmc_dev + DEV identity for local testing."));
        localBtns.Controls.Add(Btn("Open folder", OpenTestServerFolder,
            "Open the Desktop test server directory in Explorer."));
        localBtns.Controls.Add(Btn("Open Times web", () => OpenUrl(_cfg.TestServerTimesUrl),
            "Open Root-Times local web UI (" + _cfg.TestServerTimesUrl + ")."));
        localBtns.Controls.Add(Btn("Refresh", RefreshLocalTestingTab,
            "Reload path, port, playit address, and process status."));
        localLayout.Controls.Add(localBtns, 0, 1);
        _localTestingBody = new Label
        {
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.TopLeft,
            Font = new Font("Consolas", 9.5f),
            Text = "Loading…",
            AutoSize = false,
        };
        localLayout.Controls.Add(_localTestingBody, 0, 2);

        // --- Account tab ---
        var accountPage = new TabPage("Account") { Padding = new Padding(12) };
        _tabs.TabPages.Add(accountPage);
        var accountLayout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 4,
        };
        accountLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 36f));
        accountLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 40f));
        accountLayout.RowStyles.Add(new RowStyle(SizeType.Absolute, 40f));
        accountLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
        accountPage.Controls.Add(accountLayout);

        accountLayout.Controls.Add(new Label
        {
            Text = "Register this Node with your RootMC Discord account.",
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
        }, 0, 0);

        var accountBtns = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false };
        accountBtns.Controls.Add(Btn("Login with Discord", () => _ = LoginDiscordAsync(),
            "Open Discord OAuth to (re)register this Node with your RootMC developer account."));
        accountBtns.Controls.Add(Btn("Sign out", SignOut,
            "Clear the local session and return to the Discord login gate."));
        accountBtns.Controls.Add(Btn("Refresh", () => _ = RefreshAccountAsync(),
            "Reload Discord account, linked Minecraft profile, and connected server health from the API."));
        accountLayout.Controls.Add(accountBtns, 0, 1);

        _accountStatus = new Label
        {
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            Text = "Not signed in.",
        };
        accountLayout.Controls.Add(_accountStatus, 0, 2);

        // --- Profile tab (created after login) ---
        var profilePage = new TabPage("Profile") { Padding = new Padding(12), Name = "tabProfile" };
        _tabs.TabPages.Add(profilePage);
        _profileBody = new Label
        {
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.TopLeft,
            Text = "Sign in to load linked Minecraft stats.",
            AutoSize = false,
        };
        profilePage.Controls.Add(_profileBody);

        Shown += async (_, _) =>
        {
            try { split.SplitterDistance = Math.Max(split.Panel1MinSize, 420); } catch { /* ignore */ }
            if (Program.StartMinimized || _cfg.StartMinimized)
            {
                BeginInvoke(() =>
                {
                    WindowState = FormWindowState.Minimized;
                    Hide();
                });
            }
            EnsureEdgeAndScripts();
            RefreshMysqlTab();
            RefreshPhpMyAdminTab();
            LogMysqlConnectionSettings(force: true);
            await RefreshAccountAsync();
        };

        _tray = new NotifyIcon
        {
            Text = "Root-Core-Node",
            Visible = true,
            Icon = SystemIcons.Application,
        };
        _tray.DoubleClick += (_, _) =>
        {
            Show();
            WindowState = FormWindowState.Normal;
            Activate();
        };
        var menu = new ContextMenuStrip();
        menu.Items.Add("Show", null, (_, _) => { Show(); WindowState = FormWindowState.Normal; });
        menu.Items.Add("Start Edge", null, (_, _) => StartEdge());
        menu.Items.Add("Stop Edge", null, (_, _) => StopEdge());
        menu.Items.Add("Exit", null, (_, _) =>
        {
            StopPresenceHeartbeat();
            _tray.Visible = false;
            Application.Exit();
        });
        _tray.ContextMenuStrip = menu;

        _timer = new System.Windows.Forms.Timer { Interval = Math.Max(2, _cfg.RefreshSeconds) * 1000 };
        _timer.Tick += async (_, _) => await RefreshStatusAsync();
        _timer.Start();
        Shown += async (_, _) => await RefreshStatusAsync();

        _authPoll = new System.Windows.Forms.Timer { Interval = 1500 };
        _authPoll.Tick += (_, _) =>
        {
            var pending = _auth.ConsumePendingToken();
            if (pending == null) return;
            _auth.SaveToken(pending);
            _token = pending;
            Log("Discord login received.");
            _ = RefreshAccountAsync();
        };
        _authPoll.Start();

        Resize += (_, _) =>
        {
            if (WindowState == FormWindowState.Minimized) Hide();
        };
        FormClosing += HideOnClose;

        Log("Root-Core-Node ready. Workspace: " + _cfg.WorkspaceRoot);
        Log("API: " + _apiClient.ApiBase);
        Log("Node state: " + _cfg.NodeStateDir);
        Log("Edge state: " + _cfg.StateDir);
        Log("Verbose console: all TX/RX/file/script/health details enabled.");
    }

    private void HideOnClose(object? sender, FormClosingEventArgs e)
    {
        if (e.CloseReason == CloseReason.UserClosing && !Program.ReloginRequested)
        {
            e.Cancel = true;
            Hide();
        }
    }

    private async Task LoginDiscordAsync()
    {
        try
        {
            ProtocolRegistration.EnsureRegistered();
            _authListener?.Dispose();
            _authListener = new LocalAuthListener();
            _authListener.TokenReceived += token =>
            {
                BeginInvoke(() =>
                {
                    _auth.SaveToken(token);
                    _token = token;
                    Log("Discord login via localhost catcher.");
                    _ = RefreshAccountAsync();
                });
            };
            _authListener.Start();

            var url = await _apiClient.StartDiscordLoginAsync();
            Log("Opening Discord authorize…");
            OpenUrl(url);
            MessageBox.Show(
                this,
                "Complete Discord login in your browser.\n\n"
                    + "If the app does not return automatically, ensure rootmc:// is registered,\n"
                    + "or copy the token redirect into this window if prompted.",
                "Root-Core-Node",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "Discord login failed");
            Log("Login error: " + ex.Message);
        }
    }

    private void SignOut()
    {
        _auth.Clear();
        ClearNodeOperator();
        _token = null;
        _account = null;
        _servers = new();
        RebuildServerTabs();
        _accountStatus.Text = "Not signed in.";
        _profileBody.Text = "Sign in to load linked Minecraft stats.";
        Log("Signed out — returning to Discord login.");
        Program.ReloginRequested = true;
        _tray.Visible = false;
        // Allow real close (bypass hide-to-tray)
        FormClosing -= HideOnClose;
        Close();
    }

    private async Task RefreshAccountAsync()
    {
        NodeLog.Section("Account refresh");
        _token ??= _auth.LoadToken();
        if (string.IsNullOrWhiteSpace(_token))
        {
            _accountStatus.Text = "Not signed in.";
            NodeLog.Info("account", "no token — skip API");
            RebuildServerTabs();
            return;
        }
        try
        {
            var me = await _apiClient.GetDeveloperMeAsync(_token);
            var account = await _apiClient.GetAccountMeAsync(_token);
            var servers = await _apiClient.GetDeveloperServersAsync(_token);
            _account = account;
            _servers = servers?.Servers ?? new List<DevServerHealth>();

            var discord = account?.DiscordGlobalName
                ?? account?.DiscordUsername
                ?? me?.DiscordGlobalName
                ?? me?.DiscordUsername
                ?? "(Discord)";
            var mc = account?.MinecraftLinked == true
                ? $"{account.MinecraftUsername} ({account.MinecraftUuid})"
                : "Minecraft not linked";
            _accountStatus.Text = $"Signed in as {discord}\n{mc}\nServers: {_servers.Count}";

            foreach (var s in _servers)
            {
                NodeLog.Info("server",
                    $"{s.ServerName ?? s.ServerId}: connection={s.Connection} health={s.Health} players={s.OnlinePlayers} last={s.RootmcLastSeenAt} addr={s.Address} ver={s.Version}");
            }

            PublishNodeOperator(discord, account?.DiscordUsername, account?.DiscordGlobalName, me?.AccountId);
            await LoadProfileStatsAsync();
            RebuildServerTabs();
            Log($"Account refreshed — {_servers.Count} server(s).");
        }
        catch (Exception ex)
        {
            _accountStatus.Text = "Session error: " + ex.Message;
            Log("Account refresh failed: " + ex.Message);
            if (ex.Message.Contains("401") || ex.Message.Contains("Unauthorized"))
            {
                SignOut();
            }
        }
    }

    private void PublishNodeOperator(string display, string? username, string? globalName, string? accountId)
    {
        try
        {
            // Preserve Control Panel / operator-set node name across Discord login refreshes.
            string? nodeName = null;
            foreach (var existingPath in new[]
                     {
                         Path.Combine(_cfg.NodeStateDir, "node_operator.json"),
                         Path.Combine(_cfg.StateDir, "node_operator.json"),
                         @"E:\.Ava_Ivy\data\node-identity.json",
                         @"E:\.Ava_Ivy\data\host-site.json",
                     })
            {
                if (!File.Exists(existingPath)) continue;
                try
                {
                    using var doc = System.Text.Json.JsonDocument.Parse(File.ReadAllText(existingPath));
                    if (doc.RootElement.TryGetProperty("node_name", out var nn) &&
                        nn.ValueKind == System.Text.Json.JsonValueKind.String &&
                        !string.IsNullOrWhiteSpace(nn.GetString()))
                    {
                        nodeName = nn.GetString()!.Trim();
                        break;
                    }
                    if (doc.RootElement.TryGetProperty("label", out var label) &&
                        label.ValueKind == System.Text.Json.JsonValueKind.String &&
                        !string.IsNullOrWhiteSpace(label.GetString()))
                    {
                        nodeName = label.GetString()!.Trim();
                        break;
                    }
                }
                catch
                {
                    /* ignore parse */
                }
            }

            var payload = new Dictionary<string, object?>
            {
                ["discord_username"] = string.IsNullOrWhiteSpace(username) ? display : username,
                ["discord_global_name"] = string.IsNullOrWhiteSpace(globalName) ? display : globalName,
                ["account_id"] = accountId,
                ["updated_at"] = DateTimeOffset.UtcNow.ToString("o"),
            };
            if (!string.IsNullOrWhiteSpace(nodeName))
                payload["node_name"] = nodeName;

            var json = System.Text.Json.JsonSerializer.Serialize(payload, new System.Text.Json.JsonSerializerOptions { WriteIndented = true });
            var paths = new[]
            {
                Path.Combine(_cfg.NodeStateDir, "node_operator.json"),
                Path.Combine(_cfg.StateDir, "node_operator.json"),
            };
            foreach (var path in paths)
            {
                var dir = Path.GetDirectoryName(path);
                if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                File.WriteAllText(path, json);
                NodeLog.FileWrite(path, json);
            }
        }
        catch (Exception ex)
        {
            Log("node_operator write failed: " + ex.Message);
        }
    }

    private void ClearNodeOperator()
    {
        foreach (var path in new[]
                 {
                     Path.Combine(_cfg.NodeStateDir, "node_operator.json"),
                     Path.Combine(_cfg.StateDir, "node_operator.json"),
                 })
        {
            try { if (File.Exists(path)) File.Delete(path); } catch { /* ignore */ }
        }
    }

    private async Task LoadProfileStatsAsync()
    {
        if (_account?.MinecraftLinked != true || string.IsNullOrWhiteSpace(_account.MinecraftUuid))
        {
            _profileBody.Text = "No linked Minecraft account on this Discord session.";
            return;
        }
        try
        {
            var stats = await _apiClient.GetPlayerStatsAsync(_account.MinecraftUuid);
            var s = stats?.Stats;
            var lines = new List<string>
            {
                $"Player: {_account.MinecraftUsername}",
                $"UUID: {_account.MinecraftUuid}",
                $"Verified: {s?.Verified == true}",
            };
            if (s?.Playtime is JsonElement pt && pt.ValueKind is not JsonValueKind.Undefined and not JsonValueKind.Null)
            {
                lines.Add("Playtime: " + pt);
            }
            if (s?.NetWorth is JsonElement nw && nw.ValueKind is not JsonValueKind.Undefined and not JsonValueKind.Null)
            {
                lines.Add("Net worth: " + nw);
            }
            _profileBody.Text = string.Join(Environment.NewLine + Environment.NewLine, lines);
        }
        catch (Exception ex)
        {
            _profileBody.Text = "Stats unavailable: " + ex.Message;
        }
    }

    private void RebuildServerTabs()
    {
        // Remove dynamic server tabs (keep Ops, MySQL, phpMyAdmin, Local Testing, Account, Profile)
        for (var i = _tabs.TabPages.Count - 1; i >= 0; i--)
        {
            var p = _tabs.TabPages[i];
            if (p.Tag is string)
            {
                _tabs.TabPages.RemoveAt(i);
                p.Dispose();
            }
        }
        foreach (var server in _servers)
        {
            var id = server.ServerId ?? "";
            if (string.IsNullOrWhiteSpace(id)) continue;
            var name = string.IsNullOrWhiteSpace(server.ServerName) ? id : server.ServerName!;
            var page = new TabPage(name) { Tag = id, Padding = new Padding(10) };
            page.Controls.Add(BuildServerPanel(server));
            _tabs.TabPages.Add(page);
        }
        RefreshServersList();
    }

    private void RefreshServersList()
    {
        void apply()
        {
            _serversList.BeginUpdate();
            _serversList.Items.Clear();
            foreach (var server in _servers)
            {
                var id = server.ServerId ?? "";
                if (string.IsNullOrWhiteSpace(id)) continue;
                var name = string.IsNullOrWhiteSpace(server.ServerName) ? id : server.ServerName!;
                var conn = server.Connection ?? "—";
                var health = server.Health ?? "—";
                var players = server.OnlinePlayers?.ToString() ?? "—";
                var seen = string.IsNullOrWhiteSpace(server.RootmcLastSeenAt) ? "—" : server.RootmcLastSeenAt!;
                var item = new ListViewItem(new[] { name, conn, health, players, seen }) { Tag = id };
                var online = string.Equals(conn, "online", StringComparison.OrdinalIgnoreCase)
                    || string.Equals(health, "ok", StringComparison.OrdinalIgnoreCase)
                    || string.Equals(health, "healthy", StringComparison.OrdinalIgnoreCase);
                item.ForeColor = online ? Color.FromArgb(20, 110, 50) : Color.FromArgb(120, 50, 40);
                _serversList.Items.Add(item);
            }
            _serversList.EndUpdate();
            if (_serversList.Parent is GroupBox box)
            {
                box.Text = _servers.Count == 0
                    ? "Servers connected (none)"
                    : $"Servers connected ({_servers.Count})";
            }
        }
        if (_serversList.IsDisposed) return;
        if (_serversList.InvokeRequired) _serversList.BeginInvoke(apply);
        else apply();
    }

    private void OpenSelectedServerTab()
    {
        if (_serversList.SelectedItems.Count == 0) return;
        var id = _serversList.SelectedItems[0].Tag as string;
        if (string.IsNullOrWhiteSpace(id)) return;
        foreach (TabPage page in _tabs.TabPages)
        {
            if (page.Tag is string tag && tag == id)
            {
                _tabs.SelectedTab = page;
                return;
            }
        }
    }

    private Control BuildServerPanel(DevServerHealth server)
    {
        var id = server.ServerId!;
        var settings = ServerSettingsStore.Load(_cfg, id);

        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 4,
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 72f));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 160f));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 40f));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));

        var health = new Label
        {
            Dock = DockStyle.Fill,
            Text =
                $"Server ID: {id}\n"
                + $"Connection: {server.Connection ?? "?"} · Health: {server.Health ?? "?"}\n"
                + $"Online: {server.OnlinePlayers?.ToString() ?? "—"} · Last seen: {server.RootmcLastSeenAt ?? "—"}",
        };
        root.Controls.Add(health, 0, 0);

        var settingsBox = new GroupBox
        {
            Text = "Server settings — RCON",
            Dock = DockStyle.Fill,
            Padding = new Padding(8),
        };
        var grid = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 2,
            RowCount = 4,
        };
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 110f));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100f));
        for (var i = 0; i < 4; i++) grid.RowStyles.Add(new RowStyle(SizeType.Absolute, 28f));

        var enable = new CheckBox { Text = "Enable RCON mode", Checked = settings.RconEnabled, Dock = DockStyle.Fill, AutoSize = true };
        grid.Controls.Add(enable, 0, 0);
        grid.SetColumnSpan(enable, 2);

        var host = new TextBox { Text = settings.Host, Dock = DockStyle.Fill };
        var port = new NumericUpDown { Minimum = 1, Maximum = 65535, Value = Math.Clamp(settings.Port, 1, 65535), Dock = DockStyle.Left, Width = 90 };
        var pass = new TextBox { Text = settings.Password, Dock = DockStyle.Fill, UseSystemPasswordChar = true };
        grid.Controls.Add(new Label { Text = "Host", Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleLeft }, 0, 1);
        grid.Controls.Add(host, 1, 1);
        grid.Controls.Add(new Label { Text = "Port", Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleLeft }, 0, 2);
        grid.Controls.Add(port, 1, 2);
        grid.Controls.Add(new Label { Text = "Password", Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleLeft }, 0, 3);
        grid.Controls.Add(pass, 1, 3);
        settingsBox.Controls.Add(grid);
        root.Controls.Add(settingsBox, 0, 1);

        var cmdRow = new FlowLayoutPanel { Dock = DockStyle.Fill, WrapContents = false };
        var cmd = new TextBox { Width = 360, Height = 28 };
        var send = new Button { Text = "Send RCON", AutoSize = true, Enabled = settings.RconEnabled };
        var save = new Button { Text = "Save settings", AutoSize = true };
        _tips.SetToolTip(send, "Send the command to this server over Source RCON (requires Enable RCON + saved host/port/password).");
        _tips.SetToolTip(save, "Save RCON host, port, password, and enable flag for this server into local Node state.");
        _tips.SetToolTip(cmd, "RCON command to run on the Minecraft server (e.g. list, say hello).");
        _tips.SetToolTip(enable, "Allow RCON commands for this server tab after you Save settings.");
        _tips.SetToolTip(host, "RCON host — usually 127.0.0.1 on the machine running Paper, or the server’s public IP.");
        _tips.SetToolTip(port, "RCON port from server.properties (default 25575).");
        _tips.SetToolTip(pass, "RCON password from server.properties (stored only in local Node state).");
        var rconLog = new TextBox
        {
            Dock = DockStyle.Fill,
            Multiline = true,
            ReadOnly = true,
            ScrollBars = ScrollBars.Vertical,
            Font = new Font("Consolas", 9f),
            BackColor = Color.FromArgb(24, 24, 24),
            ForeColor = Color.FromArgb(210, 210, 210),
        };
        _tips.SetToolTip(rconLog, "RCON command output for this server.");

        enable.CheckedChanged += (_, _) => send.Enabled = enable.Checked;
        save.Click += (_, _) =>
        {
            var next = new ServerRconSettings
            {
                RconEnabled = enable.Checked,
                Host = host.Text.Trim(),
                Port = (int)port.Value,
                Password = pass.Text,
            };
            ServerSettingsStore.Save(_cfg, id, next);
            send.Enabled = next.RconEnabled;
            rconLog.AppendText($"[{DateTime.Now:HH:mm:ss}] Settings saved.\r\n");
            Log($"RCON settings saved for {server.ServerName}");
        };
        send.Click += async (_, _) =>
        {
            if (!enable.Checked)
            {
                MessageBox.Show(this, "Enable RCON mode and Save settings first.");
                return;
            }
            try
            {
                send.Enabled = false;
                var result = await RconClient.ExecuteAsync(host.Text.Trim(), (int)port.Value, pass.Text, cmd.Text.Trim());
                rconLog.AppendText($"[{DateTime.Now:HH:mm:ss}] > {cmd.Text}\r\n{result}\r\n");
            }
            catch (Exception ex)
            {
                rconLog.AppendText($"[{DateTime.Now:HH:mm:ss}] ERROR: {ex.Message}\r\n");
            }
            finally
            {
                send.Enabled = enable.Checked;
            }
        };
        cmdRow.Controls.Add(cmd);
        cmdRow.Controls.Add(send);
        cmdRow.Controls.Add(save);
        root.Controls.Add(cmdRow, 0, 2);
        root.Controls.Add(rconLog, 0, 3);
        return root;
    }

    private static Label AddStatusRow(TableLayoutPanel table, int row, string label, string value)
    {
        var l = new Label
        {
            Text = label,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            Margin = new Padding(0, 0, 8, 0),
            Font = new Font(SystemFonts.MessageBoxFont!.FontFamily, SystemFonts.MessageBoxFont.Size, FontStyle.Bold),
            AutoSize = false,
        };
        var v = new Label
        {
            Text = value,
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
            AutoSize = false,
            AutoEllipsis = true,
        };
        table.Controls.Add(l, 0, row);
        table.Controls.Add(v, 1, row);
        return v;
    }

    private Button Btn(string text, Action action, string? tip = null)
    {
        var b = new Button
        {
            Text = text,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            Margin = new Padding(0, 0, 8, 8),
            Padding = new Padding(12, 5, 12, 5),
            MinimumSize = new Size(0, 32),
            UseVisualStyleBackColor = true,
        };
        b.Click += (_, _) =>
        {
            try { action(); }
            catch (Exception ex) { MessageBox.Show(ex.Message, "Root-Core-Node"); }
        };
        if (!string.IsNullOrWhiteSpace(tip))
        {
            _tips.SetToolTip(b, tip);
        }
        return b;
    }

    private void Log(string line)
    {
        if (IsDisposed || _log.IsDisposed) return;
        void append()
        {
            var stamp = DateTime.Now.ToString("HH:mm:ss.fff");
            var text = line ?? "";
            foreach (var part in text.Replace("\r\n", "\n").Split('\n'))
            {
                _log.AppendText($"[{stamp}] {part}{Environment.NewLine}");
            }
            // Keep UI responsive if log grows huge
            if (_log.TextLength > 900_000)
            {
                _log.Text = _log.Text[^600_000..];
                _log.SelectionStart = _log.TextLength;
                _log.ScrollToCaret();
            }
        }
        if (_log.InvokeRequired) _log.BeginInvoke(append);
        else append();
    }

    private async Task RefreshStatusAsync()
    {
        NodeLog.Section("Status poll");
        var mysqlOk = Health.TcpOpen("127.0.0.1", _cfg.MysqlPort);
        var apiOk = await Health.UrlOkAsync(_cfg.ApiHealthUrl);
        var gwOk = await Health.UrlOkAsync(_cfg.GatewayHealthUrl);
        var (pref, reason, sync) = Health.ReadPreference(_cfg.PreferencePath());

        void apply()
        {
            _mysql.Text = mysqlOk ? "up : " + _cfg.MysqlPort : "down";
            _apiStatus.Text = apiOk ? "ok" : "down";
            _gateway.Text = gwOk ? "ok" : "down";
            _prefReason.Text = string.IsNullOrWhiteSpace(reason) ? "—" : reason;
            _sync.Text = string.IsNullOrWhiteSpace(sync) ? "(none)" : sync;
            _dbCopy.Text = ReadMysqlReplicaStatusLine();
            RefreshMysqlTab();
            RefreshPhpMyAdminTab();
            RefreshLocalTestingTab();
            LogMysqlConnectionSettings(force: false);

            var label = PrefDisplayName(pref);
            _badge.Text = label;
            _badge.BackColor = PrefIsRootMc(pref)
                ? Color.FromArgb(40, 120, 70)
                : string.Equals(pref, "cloudflare", StringComparison.OrdinalIgnoreCase)
                    ? Color.FromArgb(140, 90, 30)
                    : Color.FromArgb(70, 70, 70);
            _badge.ForeColor = Color.White;
            _tray.Text = "Root-Core-Node — " + label;
            NodeLog.Info("status", $"UI mysql={_mysql.Text} api={_apiStatus.Text} gw={_gateway.Text} badge={label}");
        }

        if (InvokeRequired) BeginInvoke(apply);
        else apply();
    }

    private void StartEdge()
    {
        var script = Path.Combine(_cfg.EdgeRoot, "Start-LocalEdge.ps1");
        if (!File.Exists(script)) throw new FileNotFoundException(script);
        ScriptRunner.StartPowerShell(script, _cfg.EdgeRoot, "-ConfigPath", _cfg.EdgeConfigPath(), "-SkipTerminal");
        Log("Started Start-LocalEdge.ps1 (includes MySQL replica loop)");
        _mysqlReplicaStarted = true;
        _edgeEnsureStarted = true;
        EnsurePresenceHeartbeat();
    }

    /// <summary>
    /// Root-Core-Node is the script runner: bring up local-edge + presence when configured / --autostart.
    /// </summary>
    private void EnsureEdgeAndScripts()
    {
        var shouldAuto = _cfg.AutoStartEdgeOnLaunch || Program.AutostartLaunch;
        if (!shouldAuto)
        {
            EnsureMysqlReplicaLoop();
            return;
        }
        if (_edgeEnsureStarted) return;
        _edgeEnsureStarted = true;

        _ = Task.Run(async () =>
        {
            try
            {
                var apiOk = await Health.UrlOkAsync(_cfg.ApiHealthUrl);
                var gwOk = await Health.UrlOkAsync(_cfg.GatewayHealthUrl);
                if (apiOk && gwOk)
                {
                    BeginInvoke(() =>
                    {
                        Log("Local edge already healthy — skipping Start-LocalEdge restart.");
                        _mysqlReplicaStarted = true;
                        EnsureMysqlReplicaLoop();
                        EnsurePresenceHeartbeat();
                    });
                    return;
                }
                BeginInvoke(() =>
                {
                    Log("Auto-starting local-edge stack (Root-Core-Node script runner)…");
                    try { StartEdge(); }
                    catch (Exception ex) { Log("Auto Start Edge failed: " + ex.Message); }
                    EnsureMysqlReplicaLoop();
                });
            }
            catch (Exception ex)
            {
                BeginInvoke(() => Log("Edge ensure failed: " + ex.Message));
            }
        });
    }

    private void EnsurePresenceHeartbeat()
    {
        if (_presenceStarted) return;
        var candidates = new[]
        {
            Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "RootMC", "dev-workstation", "dev-workstation-startup.ps1"),
            Path.Combine(_cfg.DevWorkstationRoot, "dev-workstation-startup.ps1"),
        };
        string? script = candidates.FirstOrDefault(File.Exists);
        if (script == null)
        {
            Log("Presence script not found (dev-workstation-startup.ps1) — Discord heartbeat skipped.");
            return;
        }
        var configPath = Path.Combine(Path.GetDirectoryName(script)!, "config.json");
        if (!File.Exists(configPath))
        {
            configPath = Path.Combine(_cfg.DevWorkstationRoot, "config.json");
        }
        try
        {
            var args = new List<string> { "-SkipMutex", "-Headless", "-Status", "Online" };
            if (File.Exists(configPath))
            {
                args.Add("-ConfigPath");
                args.Add(configPath);
            }
            _presenceProc = ScriptRunner.StartPowerShell(script, Path.GetDirectoryName(script)!, args.ToArray());
            _presenceStarted = true;
            Log("Presence heartbeat started (background): " + Path.GetFileName(script));
        }
        catch (Exception ex)
        {
            Log("Presence start failed: " + ex.Message);
        }
    }

    private void StopEdge()
    {
        var script = Path.Combine(_cfg.EdgeRoot, "Stop-LocalEdge.ps1");
        ScriptRunner.StartPowerShell(script, _cfg.EdgeRoot, "-ConfigPath", _cfg.EdgeConfigPath());
        Log("Stopped local edge");
        _mysqlReplicaStarted = false;
        _edgeEnsureStarted = false;
        StopPresenceHeartbeat();
    }

    private void StopPresenceHeartbeat()
    {
        if (_presenceProc == null) return;
        try
        {
            if (!_presenceProc.HasExited)
            {
                _presenceProc.Kill(entireProcessTree: true);
                Log("Presence heartbeat stopped.");
            }
        }
        catch { /* ignore */ }
        finally
        {
            try { _presenceProc.Dispose(); } catch { /* ignore */ }
            _presenceProc = null;
            _presenceStarted = false;
        }
    }

    private void StartMysql()
    {
        var script = Path.Combine(_cfg.EdgeRoot, "Start-LocalMySQL.ps1");
        if (!File.Exists(script)) script = Path.Combine(_cfg.EdgeRoot, "Install-LocalMySQL.ps1");
        if (!File.Exists(script)) throw new FileNotFoundException("MySQL start/install script missing");
        ScriptRunner.StartPowerShell(script, _cfg.EdgeRoot);
        Log("MySQL script launched: " + Path.GetFileName(script));
        EnsureMysqlReplicaLoop();
    }

    private void SyncMysqlNow()
    {
        var script = Path.Combine(_cfg.EdgeRoot, "Update-MysqlReplicaLoop.ps1");
        if (!File.Exists(script)) throw new FileNotFoundException(script);
        NodeLog.Section("MySQL full replica (manual)");
        ScriptRunner.StartPowerShell(
            script,
            _cfg.EdgeRoot,
            "-WorkspaceRoot", _cfg.WorkspaceRoot,
            "-Once");
        Log("MySQL replica sync started (Claims+Towny full DB incl. mcMMO/votes/playtime/economy)");
    }

    private void EnsureMysqlReplicaLoop()
    {
        if (_mysqlReplicaStarted) return;
        var script = Path.Combine(_cfg.EdgeRoot, "Update-MysqlReplicaLoop.ps1");
        if (!File.Exists(script))
        {
            Log("MySQL replica loop script missing: " + script);
            return;
        }
        // Avoid duplicate loops if Start Edge already spawned one.
        if (IsMysqlReplicaLoopAlive())
        {
            _mysqlReplicaStarted = true;
            Log("MySQL replica loop already running.");
            return;
        }
        var mins = Math.Max(5, _cfg.MysqlReplicaIntervalMinutes);
        ScriptRunner.StartPowerShell(
            script,
            _cfg.EdgeRoot,
            "-WorkspaceRoot", _cfg.WorkspaceRoot,
            "-IntervalMinutes", mins.ToString());
        _mysqlReplicaStarted = true;
        Log($"MySQL replica loop started (every {mins}m) -> rootmc_claims + rootmc_towny");
    }

    private bool IsMysqlReplicaLoopAlive()
    {
        try
        {
            var pidFile = Path.Combine(_cfg.StateDir, "pids.json");
            if (File.Exists(pidFile))
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(pidFile));
                if (doc.RootElement.ValueKind == JsonValueKind.Array)
                {
                    foreach (var el in doc.RootElement.EnumerateArray())
                    {
                        if (el.TryGetProperty("name", out var n)
                            && string.Equals(n.GetString(), "mysql-replica", StringComparison.OrdinalIgnoreCase)
                            && el.TryGetProperty("pid", out var pidEl)
                            && pidEl.TryGetInt32(out var pid))
                        {
                            try
                            {
                                var p = Process.GetProcessById(pid);
                                if (!p.HasExited) return true;
                            }
                            catch { /* gone */ }
                        }
                    }
                }
            }
            var lockPath = Path.Combine(_cfg.StateDir, "mysql_replica.lock");
            if (File.Exists(lockPath) && DateTime.Now - File.GetLastWriteTime(lockPath) < TimeSpan.FromMinutes(45))
            {
                return true;
            }
        }
        catch { /* ignore */ }
        return false;
    }

    private string ReadMysqlReplicaStatusLine()
    {
        try
        {
            var path = _cfg.MysqlReplicaStatusPath();
            if (!File.Exists(path)) return "waiting (loop starts with Node / Start Edge)";
            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            var root = doc.RootElement;
            var running = root.TryGetProperty("running", out var r) && r.ValueKind == JsonValueKind.True;
            if (running) return "syncing…";
            var ok = !root.TryGetProperty("ok", out var o) || o.ValueKind != JsonValueKind.False;
            var checkedAt = root.TryGetProperty("checked_at", out var c) ? c.GetString() : null;
            var age = "";
            if (!string.IsNullOrWhiteSpace(checkedAt) && DateTimeOffset.TryParse(checkedAt, out var dto))
            {
                var mins = (int)(DateTimeOffset.UtcNow - dto).TotalMinutes;
                age = mins <= 1 ? "just now" : mins + "m ago";
            }
            var townyMcmmo = "?";
            if (root.TryGetProperty("coverage", out var cov)
                && cov.TryGetProperty("rootmc_towny", out var tw)
                && tw.TryGetProperty("mcmmo", out var mm))
            {
                townyMcmmo = mm.ToString();
            }
            var tables = "";
            if (root.TryGetProperty("results", out var results) && results.ValueKind == JsonValueKind.Array)
            {
                var parts = new List<string>();
                foreach (var row in results.EnumerateArray())
                {
                    var schema = row.TryGetProperty("schema", out var s) ? s.GetString() : "?";
                    var n = row.TryGetProperty("tables", out var t) ? t.ToString() : "?";
                    parts.Add($"{schema}:{n}");
                }
                tables = string.Join(" · ", parts);
            }
            if (!ok)
            {
                var msg = root.TryGetProperty("message", out var m) ? m.GetString() : "failed";
                return "FAIL " + msg;
            }
            return string.Join(" · ", new[] { age, tables, "mcmmo=" + townyMcmmo }.Where(x => !string.IsNullOrWhiteSpace(x)));
        }
        catch (Exception ex)
        {
            return "status err: " + ex.Message;
        }
    }

    private void StartPhpMyAdmin()
    {
        var start = Path.Combine(_cfg.EdgeRoot, "Start-PhpMyAdmin.ps1");
        if (!File.Exists(start)) throw new FileNotFoundException(start);
        ScriptRunner.StartPowerShell(start, _cfg.EdgeRoot);
        Log("phpMyAdmin start requested → " + _cfg.PhpMyAdminUrl);
        RefreshPhpMyAdminTab();
    }

    private void StopPhpMyAdmin()
    {
        var stop = Path.Combine(_cfg.EdgeRoot, "Stop-PhpMyAdmin.ps1");
        if (!File.Exists(stop)) throw new FileNotFoundException(stop);
        ScriptRunner.StartPowerShell(stop, _cfg.EdgeRoot);
        Log("phpMyAdmin stop requested");
        RefreshPhpMyAdminTab();
    }

    private void RefreshPhpMyAdminTab()
    {
        if (_pmaTabStatus.IsDisposed) return;
        var pidFile = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "RootMC", "phpmyadmin", "phpmyadmin.pid");
        var running = false;
        var pidText = "stopped";
        if (File.Exists(pidFile))
        {
            try
            {
                var pid = int.Parse(File.ReadAllText(pidFile).Trim());
                try
                {
                    var p = Process.GetProcessById(pid);
                    if (!p.HasExited)
                    {
                        running = true;
                        pidText = "running pid=" + pid;
                    }
                }
                catch { /* not running */ }
            }
            catch { /* ignore */ }
        }
        var text =
            $"URL: {_cfg.PhpMyAdminUrl}\n"
            + $"Status: {(running ? "up" : "down")} ({pidText})\n"
            + "Browse schemas: rootmc_claims · rootmc_towny · rootmc_network";
        void apply() => _pmaTabStatus.Text = text;
        if (_pmaTabStatus.InvokeRequired) _pmaTabStatus.BeginInvoke(apply);
        else apply();
    }

    private void RefreshMysqlTab()
    {
        if (_mysqlTabBody.IsDisposed) return;
        var info = ReadLocalMysqlInfo();
        var replica = ReadMysqlReplicaStatusLine();
        var text =
            $"Host: {info.Host}\n"
            + $"Port: {info.Port}\n"
            + $"User: {info.User}\n"
            + $"Database (edge): {info.Database}\n"
            + $"TCP: {(info.Up ? "up" : "down")}\n"
            + $"Connection: {info.UrlMasked}\n"
            + $"Bootstrap: {info.BootstrapPath}\n"
            + $"Replica: {replica}\n"
            + "Local schemas: rootmc_claims · rootmc_towny · rootmc_network";
        void apply() => _mysqlTabBody.Text = text;
        if (_mysqlTabBody.InvokeRequired) _mysqlTabBody.BeginInvoke(apply);
        else apply();
    }

    private void LogMysqlConnectionSettings(bool force)
    {
        if (!force && _loggedMysqlSettings) return;
        var info = ReadLocalMysqlInfo();
        NodeLog.Section("Local MySQL");
        NodeLog.Info("mysql", $"host={info.Host} port={info.Port} user={info.User} db={info.Database}");
        NodeLog.Info("mysql", $"tcp={(info.Up ? "up" : "down")} url={info.UrlMasked}");
        NodeLog.Info("mysql", $"bootstrap={info.BootstrapPath}");
        NodeLog.Info("mysql", "replica=" + ReadMysqlReplicaStatusLine());
        _loggedMysqlSettings = true;
    }

    private (string Host, int Port, string User, string Database, string UrlMasked, string BootstrapPath, bool Up) ReadLocalMysqlInfo()
    {
        var host = "127.0.0.1";
        var port = _cfg.MysqlPort;
        var user = "rootmc_edge";
        var database = "rootmc_network";
        var bootstrap = Path.Combine(_cfg.WorkspaceRoot, "scripts", "local-edge", "state", "mysql-bootstrap.env");
        try
        {
            if (File.Exists(bootstrap))
            {
                foreach (var line in File.ReadAllLines(bootstrap))
                {
                    var t = line.Trim();
                    if (t.StartsWith("ROOTMC_LOCAL_MYSQL_HOST=", StringComparison.OrdinalIgnoreCase))
                        host = t[(t.IndexOf('=') + 1)..].Trim();
                    else if (t.StartsWith("ROOTMC_LOCAL_MYSQL_PORT=", StringComparison.OrdinalIgnoreCase)
                             && int.TryParse(t[(t.IndexOf('=') + 1)..].Trim(), out var p))
                        port = p;
                    else if (t.StartsWith("ROOTMC_LOCAL_MYSQL_USER=", StringComparison.OrdinalIgnoreCase))
                        user = t[(t.IndexOf('=') + 1)..].Trim();
                    else if (t.StartsWith("ROOTMC_LOCAL_MYSQL_DATABASE=", StringComparison.OrdinalIgnoreCase))
                        database = t[(t.IndexOf('=') + 1)..].Trim();
                }
            }
        }
        catch { /* defaults */ }
        var up = false;
        try
        {
            using var c = new System.Net.Sockets.TcpClient();
            var ar = c.BeginConnect(host, port, null, null);
            up = ar.AsyncWaitHandle.WaitOne(TimeSpan.FromMilliseconds(600)) && c.Connected;
            try { c.EndConnect(ar); } catch { up = false; }
        }
        catch { up = false; }
        var url = $"mysql://{user}:***@{host}:{port}/{database}";
        return (host, port, user, database, url, bootstrap, up);
    }

    private void ForcePref(string mode)
    {
        Directory.CreateDirectory(_cfg.StateDir);
        var force = Path.Combine(_cfg.StateDir, "force_mode.txt");
        if (mode.Equals("auto", StringComparison.OrdinalIgnoreCase))
        {
            if (File.Exists(force))
            {
                File.Delete(force);
                NodeLog.FileDelete(force);
            }
            Log("Preference force cleared (auto)");
            return;
        }
        File.WriteAllText(force, mode.Equals("local", StringComparison.OrdinalIgnoreCase) ? "local" : mode);
        NodeLog.FileWrite(force, mode.Equals("local", StringComparison.OrdinalIgnoreCase) ? "local" : mode);
        Log("Forced preference: " + (mode.Equals("local", StringComparison.OrdinalIgnoreCase) ? "RootMC Network" : mode));
    }

    private static bool PrefIsRootMc(string? pref) =>
        string.Equals(pref, "rootmc", StringComparison.OrdinalIgnoreCase)
        || string.Equals(pref, "local", StringComparison.OrdinalIgnoreCase)
        || string.Equals(pref, "solar", StringComparison.OrdinalIgnoreCase);

    private static string PrefDisplayName(string? pref)
    {
        if (PrefIsRootMc(pref)) return "Node";
        if (string.Equals(pref, "cloudflare", StringComparison.OrdinalIgnoreCase)) return "Fallback Servers";
        return string.IsNullOrWhiteSpace(pref) ? "Node: unknown" : "Node: " + pref;
    }

    private void InstallAutostart()
    {
        var install = Path.Combine(_cfg.WorkspaceRoot, "scripts", "root-core-node", "install-root-core-node.ps1");
        if (!File.Exists(install))
            install = Path.Combine(AppContext.BaseDirectory, "install-root-core-node.ps1");
        if (File.Exists(install))
        {
            ScriptRunner.StartPowerShell(
                install,
                Path.GetDirectoryName(install)!,
                "-ExePath", Application.ExecutablePath,
                "-SkipPublish");
            Log("Ran install-root-core-node.ps1 (Root-Core-Node logon autostart)");
            return;
        }

        // Fallback: scheduled task + Startup shortcut for this exe
        var exe = Application.ExecutablePath;
        var workDir = AppContext.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        var startup = Environment.GetFolderPath(Environment.SpecialFolder.Startup);
        var lnk = Path.Combine(startup, "Root-Core-Node.lnk");
        var taskName = "RootMC Root-Core-Node";
        var ps = $@"
$ErrorActionPreference = 'Stop'
Unregister-ScheduledTask -TaskName 'RootMC Dev Workstation' -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName '{taskName}' -Confirm:$false -ErrorAction SilentlyContinue
$action = New-ScheduledTaskAction -Execute '{exe.Replace("'", "''")}' -Argument '--autostart' -WorkingDirectory '{workDir.Replace("'", "''")}'
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$trigger.Delay = 'PT30S'
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 2)
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName '{taskName}' -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description 'Root-Core-Node — local-edge script runner + tray UI' | Out-Null
$ws = New-Object -ComObject WScript.Shell
$s = $ws.CreateShortcut('{lnk.Replace("'", "''")}')
$s.TargetPath = '{exe.Replace("'", "''")}'
$s.Arguments = '--autostart'
$s.WorkingDirectory = '{workDir.Replace("'", "''")}'
$s.Save()
Write-Host 'Installed autostart: {taskName}'
";
        var tmp = Path.Combine(Path.GetTempPath(), "rcn-autostart.ps1");
        File.WriteAllText(tmp, ps);
        ScriptRunner.RunPowerShellWait(tmp, workDir);
        Log("Autostart installed: task '" + taskName + "' + " + lnk);
    }

    private void RefreshLocalTestingTab()
    {
        void apply()
        {
            var root = _cfg.ResolveTestServerRoot();
            var port = _cfg.TestServerPort;
            var listening = Health.TcpOpen("127.0.0.1", port);
            var jar = Directory.Exists(root)
                ? Directory.GetFiles(root, "paper-*.jar").Select(Path.GetFileName).FirstOrDefault() ?? "(no paper-*.jar)"
                : "(folder missing)";
            var playitPath = Path.Combine(root, "playit-address.txt");
            var playit = File.Exists(playitPath)
                ? File.ReadAllText(playitPath).Trim()
                : "(no playit-address.txt)";
            var props = Path.Combine(root, "server.properties");
            var motd = "";
            var onlineMode = "";
            if (File.Exists(props))
            {
                foreach (var line in File.ReadLines(props))
                {
                    if (line.StartsWith("motd=", StringComparison.OrdinalIgnoreCase))
                        motd = line["motd=".Length..].Trim();
                    if (line.StartsWith("online-mode=", StringComparison.OrdinalIgnoreCase))
                        onlineMode = line["online-mode=".Length..].Trim();
                }
            }
            var procNote = _testServerProc is { HasExited: false }
                ? "Node-started PID " + _testServerProc.Id
                : FindTestServerPids().Count > 0
                    ? "Detected PID(s): " + string.Join(", ", FindTestServerPids())
                    : "not running from Node / no matching java";

            var lines = new List<string>
            {
                "Path:     " + root,
                "Exists:   " + (Directory.Exists(root) ? "yes" : "NO"),
                "Paper:    " + jar,
                "Bind:     127.0.0.1:" + port + " — " + (listening ? "LISTENING" : "closed"),
                "Process:  " + procNote,
                "Playit:   " + playit,
                "MOTD:     " + (string.IsNullOrWhiteSpace(motd) ? "(unset)" : motd),
                "Online:   " + (string.IsNullOrWhiteSpace(onlineMode) ? "(unset)" : onlineMode),
                "MySQL:    rootmc_dev @ 127.0.0.1:" + _cfg.MysqlPort,
                "Times UI: " + _cfg.TestServerTimesUrl,
                "Junction: " + Path.Combine(_cfg.WorkspaceRoot, "test server"),
                "",
                "Use Start for interactive Paper. Boot once = Done then stop.",
                "Sync plugins pulls jars from Towny handoff.",
            };
            _localTestingBody.Text = string.Join(Environment.NewLine, lines);
        }

        if (IsDisposed || _localTestingBody.IsDisposed) return;
        if (_localTestingBody.InvokeRequired) _localTestingBody.BeginInvoke(apply);
        else apply();
    }

    private List<int> FindTestServerPids()
    {
        var found = new List<int>();
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = "powershell.exe",
                Arguments = $"-NoProfile -Command \"(Get-NetTCPConnection -LocalPort {_cfg.TestServerPort} -State Listen -ErrorAction SilentlyContinue).OwningProcess | Select-Object -Unique\"",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                CreateNoWindow = true,
            };
            using var proc = Process.Start(psi);
            if (proc == null) return found;
            var output = proc.StandardOutput.ReadToEnd();
            proc.WaitForExit(3000);
            foreach (var line in output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries))
            {
                if (int.TryParse(line.Trim(), out var pid) && pid > 0 && !found.Contains(pid))
                    found.Add(pid);
            }
        }
        catch { /* ignore */ }
        return found;
    }

    private void StartTestServer()
    {
        var root = _cfg.ResolveTestServerRoot();
        var bat = Path.Combine(root, "start.bat");
        if (!File.Exists(bat)) throw new FileNotFoundException("start.bat missing", bat);
        if (Health.TcpOpen("127.0.0.1", _cfg.TestServerPort))
        {
            Log("Test server already listening on :" + _cfg.TestServerPort);
            RefreshLocalTestingTab();
            return;
        }
        var psi = new ProcessStartInfo
        {
            FileName = bat,
            WorkingDirectory = root,
            UseShellExecute = true,
            WindowStyle = ProcessWindowStyle.Normal,
        };
        _testServerProc = Process.Start(psi);
        NodeLog.Tx("test-server", "start.bat cwd=" + root + " pid=" + (_testServerProc?.Id.ToString() ?? "?"));
        Log("Test server starting: " + root);
        RefreshLocalTestingTab();
    }

    private void BootTestServerOnce()
    {
        var root = _cfg.ResolveTestServerRoot();
        var script = Path.Combine(root, "boot-once.ps1");
        if (!File.Exists(script)) throw new FileNotFoundException(script);
        ScriptRunner.StartPowerShell(script, root);
        Log("Test server boot-once launched (stops after Done)");
        RefreshLocalTestingTab();
    }

    private void StopTestServer()
    {
        var killed = 0;
        if (_testServerProc is { HasExited: false })
        {
            try
            {
                _testServerProc.Kill(entireProcessTree: true);
                killed++;
            }
            catch (Exception ex) { Log("Stop test server (owned): " + ex.Message); }
            finally
            {
                try { _testServerProc.Dispose(); } catch { /* ignore */ }
                _testServerProc = null;
            }
        }
        foreach (var pid in FindTestServerPids())
        {
            try
            {
                using var p = Process.GetProcessById(pid);
                p.Kill(entireProcessTree: true);
                killed++;
            }
            catch (Exception ex) { Log("Stop PID " + pid + ": " + ex.Message); }
        }
        Log(killed > 0
            ? "Stopped test server process(es): " + killed
            : "No listening test server process found on :" + _cfg.TestServerPort);
        RefreshLocalTestingTab();
    }

    private void SyncTestServerPlugins()
    {
        var root = _cfg.ResolveTestServerRoot();
        var script = Path.Combine(root, "sync-plugins.ps1");
        if (!File.Exists(script)) throw new FileNotFoundException(script);
        ScriptRunner.StartPowerShell(script, root);
        Log("Test server plugin sync started (Towny + Claims combined profile)");
    }

    private void EnsureTestServerMysql()
    {
        var script = Path.Combine(_cfg.EdgeRoot, "Ensure-RootMcDevMysql.ps1");
        if (!File.Exists(script)) throw new FileNotFoundException(script);
        var root = _cfg.ResolveTestServerRoot();
        ScriptRunner.StartPowerShell(
            script,
            _cfg.EdgeRoot,
            "-WorkspaceRoot", _cfg.WorkspaceRoot,
            "-TestServerRoot", root);
        Log("Ensure-RootMcDevMysql launched for " + root);
    }

    private void OpenTestServerFolder()
    {
        var root = _cfg.ResolveTestServerRoot();
        Directory.CreateDirectory(root);
        Process.Start(new ProcessStartInfo
        {
            FileName = "explorer.exe",
            Arguments = "\"" + root + "\"",
            UseShellExecute = true,
        });
    }

    private void OpenLogs()
    {
        var logs = Path.Combine(_cfg.EdgeRoot, "logs");
        Directory.CreateDirectory(logs);
        Process.Start(new ProcessStartInfo { FileName = logs, UseShellExecute = true });
    }

    private void OpenUrl(string url)
    {
        NodeLog.Tx("shell", "OpenUrl " + NodeLog.RedactSecrets(url));
        Process.Start(new ProcessStartInfo { FileName = url, UseShellExecute = true });
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            StopPresenceHeartbeat();
            _timer.Dispose();
            _authPoll.Dispose();
            _authListener?.Dispose();
            _tray.Visible = false;
            _tray.Dispose();
        }
        base.Dispose(disposing);
    }
}
