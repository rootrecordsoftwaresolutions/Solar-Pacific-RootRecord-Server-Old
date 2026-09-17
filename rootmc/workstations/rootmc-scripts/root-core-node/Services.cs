using System.Diagnostics;
using System.Text.Json;

namespace RootMc.CoreNode;

internal sealed class NodeConfig
{
    public string WorkspaceRoot { get; set; } = @"D:\.1 Work Stations\RootMC";
    public string EdgeRoot { get; set; } = @"D:\.1 Work Stations\RootMC\scripts\local-edge";
    public string DevWorkstationRoot { get; set; } = @"D:\.1 Work Stations\RootMC\scripts\dev-workstation";
    public string StateDir { get; set; } = @"C:\Users\store\AppData\Local\RootMC\local-edge\state";
    /// <summary>Local Paper test server root (Desktop SSD). Empty = auto-resolve.</summary>
    public string TestServerRoot { get; set; } = "";
    public string PhpMyAdminUrl { get; set; } = "http://127.0.0.1:8080";
    public int MysqlPort { get; set; } = 3307;
    public int TestServerPort { get; set; } = 25565;
    public string TestServerTimesUrl { get; set; } = "http://127.0.0.1:8765/";
    public string GatewayHealthUrl { get; set; } = "http://127.0.0.1:8791/__edge/health";
    public string ApiHealthUrl { get; set; } = "http://127.0.0.1:8787/health";
    public string EdgeTerminalUrl { get; set; } = "http://127.0.0.1:18792/status";
    public int RefreshSeconds { get; set; } = 5;
    public int MysqlReplicaIntervalMinutes { get; set; } = 15;
    public string ApiBase { get; set; } = "https://api.rootmc.net";
    /// <summary>Node-local session / RCON settings (portable beside exe when blank).</summary>
    public string NodeStateDir { get; set; } = "";
    /// <summary>When true, launch Start-LocalEdge (+ presence) automatically after UI loads.</summary>
    public bool AutoStartEdgeOnLaunch { get; set; } = true;
    /// <summary>When true (or --minimized), hide to tray after launch (used by logon autostart).</summary>
    public bool StartMinimized { get; set; } = false;

    public string ResolveTestServerRoot()
    {
        if (!string.IsNullOrWhiteSpace(TestServerRoot) && Directory.Exists(TestServerRoot))
            return TestServerRoot;
        var desktop = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
            "RootMC test server");
        if (Directory.Exists(desktop)) return desktop;
        var workspace = Path.Combine(WorkspaceRoot, "test server");
        if (Directory.Exists(workspace)) return workspace;
        return desktop;
    }

    public static string ConfigPathBesideExe()
    {
        var baseDir = AppContext.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        return Path.Combine(baseDir, "config.json");
    }

    public static NodeConfig Load()
    {
        var path = ConfigPathBesideExe();
        NodeConfig cfg;
        if (!File.Exists(path))
        {
            cfg = new NodeConfig();
            File.WriteAllText(path, JsonSerializer.Serialize(cfg, new JsonSerializerOptions { WriteIndented = true }));
        }
        else
        {
            var json = File.ReadAllText(path);
            cfg = JsonSerializer.Deserialize<NodeConfig>(json, new JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true,
            }) ?? new NodeConfig();
        }
        if (string.IsNullOrWhiteSpace(cfg.NodeStateDir))
        {
            cfg.NodeStateDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "RootMC",
                "root-core-node",
                "state");
        }
        Directory.CreateDirectory(cfg.NodeStateDir);
        return cfg;
    }

    public string PreferencePath() => Path.Combine(StateDir, "current_connection_preference.json");

    public string MysqlReplicaStatusPath() => Path.Combine(StateDir, "mysql_replica_status.json");

    public string EdgeConfigPath() => Path.Combine(EdgeRoot, "config.json");
}

internal static class ScriptRunner
{
    public static Process StartPowerShell(string file, string workingDir, params string[] extraArgs)
    {
        var args = new List<string>
        {
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", file
        };
        args.AddRange(extraArgs);
        NodeLog.Tx("ps", $"start {file}\n    cwd={workingDir}\n    args={string.Join(" ", extraArgs)}");
        var psi = new ProcessStartInfo
        {
            FileName = "powershell.exe",
            WorkingDirectory = workingDir,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            ArgumentList = { }
        };
        foreach (var a in args) psi.ArgumentList.Add(a);
        var p = Process.Start(psi) ?? throw new InvalidOperationException("Failed to start PowerShell: " + file);
        NodeLog.Info("ps", $"pid={p.Id} {Path.GetFileName(file)}");
        p.OutputDataReceived += (_, e) =>
        {
            if (!string.IsNullOrEmpty(e.Data)) NodeLog.Rx("ps-out", e.Data);
        };
        p.ErrorDataReceived += (_, e) =>
        {
            if (!string.IsNullOrEmpty(e.Data)) NodeLog.Err("ps-err", e.Data);
        };
        p.EnableRaisingEvents = true;
        p.Exited += (_, _) => NodeLog.Info("ps", $"exit pid={p.Id} code={p.ExitCode} {Path.GetFileName(file)}");
        p.BeginOutputReadLine();
        p.BeginErrorReadLine();
        return p;
    }

    public static void RunPowerShellWait(string file, string workingDir, params string[] extraArgs)
    {
        using var p = StartPowerShell(file, workingDir, extraArgs);
        p.WaitForExit(120_000);
        NodeLog.Info("ps", $"wait done exit={p.ExitCode} {Path.GetFileName(file)}");
    }
}

internal static class Health
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(3) };

    public static async Task<(bool Ok, string Detail)> UrlProbeAsync(string url)
    {
        NodeLog.Tx("health", "GET " + url);
        try
        {
            using var res = await Http.GetAsync(url);
            var body = await res.Content.ReadAsStringAsync();
            var ok = (int)res.StatusCode is >= 200 and < 500;
            NodeLog.Rx("health", $"GET {url} â†’ HTTP {(int)res.StatusCode} ok={ok}\n    {NodeLog.Truncate(body, 1200)}");
            return (ok, $"HTTP {(int)res.StatusCode}");
        }
        catch (Exception ex)
        {
            NodeLog.Err("health", $"GET {url} â†’ {ex.GetType().Name}: {ex.Message}");
            return (false, ex.Message);
        }
    }

    public static async Task<bool> UrlOkAsync(string url)
    {
        var (ok, _) = await UrlProbeAsync(url);
        return ok;
    }

    public static bool TcpOpen(string host, int port)
    {
        NodeLog.Tx("health", $"TCP {host}:{port}");
        try
        {
            using var c = new System.Net.Sockets.TcpClient();
            var ar = c.BeginConnect(host, port, null, null);
            var ok = ar.AsyncWaitHandle.WaitOne(TimeSpan.FromMilliseconds(800));
            if (!ok)
            {
                NodeLog.Rx("health", $"TCP {host}:{port} â†’ timeout");
                return false;
            }
            c.EndConnect(ar);
            NodeLog.Rx("health", $"TCP {host}:{port} â†’ open");
            return true;
        }
        catch (Exception ex)
        {
            NodeLog.Err("health", $"TCP {host}:{port} â†’ {ex.Message}");
            return false;
        }
    }

    public static (string Preference, string Reason, string Sync, string Raw) ReadPreferenceDetailed(string path)
    {
        NodeLog.Tx("pref", "READ " + path);
        try
        {
            if (!File.Exists(path))
            {
                NodeLog.Rx("pref", "missing file");
                return ("unknown", "no_file", "", "");
            }
            var raw = File.ReadAllText(path);
            NodeLog.FileRead(path, raw);
            using var doc = JsonDocument.Parse(raw);
            var root = doc.RootElement;
            var pref = root.TryGetProperty("preference", out var p) ? p.GetString() ?? "" : "";
            if (string.Equals(pref, "local", StringComparison.OrdinalIgnoreCase)
                || string.Equals(pref, "solar", StringComparison.OrdinalIgnoreCase))
            {
                pref = "rootmc";
            }
            var reason = root.TryGetProperty("reason", out var r) ? r.GetString() ?? "" : "";
            var sync = root.TryGetProperty("sync_status", out var s) ? s.GetString() ?? "" : "";
            NodeLog.Info("pref", $"preference={pref} reason={reason} sync={sync}");
            return (pref, reason, sync, raw);
        }
        catch (Exception ex)
        {
            NodeLog.Err("pref", ex.Message);
            return ("unknown", ex.Message, "", "");
        }
    }

    public static (string Preference, string Reason, string Sync) ReadPreference(string path)
    {
        var (pref, reason, sync, _) = ReadPreferenceDetailed(path);
        return (pref, reason, sync);
    }
}
