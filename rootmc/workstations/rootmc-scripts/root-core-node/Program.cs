namespace RootMc.CoreNode;

using System.Diagnostics;
using System.Runtime.InteropServices;

internal static class Program
{
    private static Mutex? _mutex;

    /// <summary>When true, MainForm closed for sign-out — show login again.</summary>
    internal static bool ReloginRequested;

    /// <summary>Logon / scheduled-task launch: auto-run edge scripts and prefer tray.</summary>
    internal static bool AutostartLaunch;

    /// <summary>Force start minimized to tray.</summary>
    internal static bool StartMinimized;

    [STAThread]
    private static void Main(string[] args)
    {
        const string mutexName = "Local\\RootMC.RootCoreNode.SingleInstance";
        _mutex = new Mutex(true, mutexName, out var createdNew);

        foreach (var a in args)
        {
            if (string.Equals(a, "--autostart", StringComparison.OrdinalIgnoreCase)
                || string.Equals(a, "-autostart", StringComparison.OrdinalIgnoreCase))
            {
                AutostartLaunch = true;
                StartMinimized = true;
                continue;
            }
            if (string.Equals(a, "--minimized", StringComparison.OrdinalIgnoreCase)
                || string.Equals(a, "-minimized", StringComparison.OrdinalIgnoreCase))
            {
                StartMinimized = true;
                continue;
            }
            if (AuthService.TryParseAuthUrl(a, out var token))
            {
                var cfgEarly = NodeConfig.Load();
                var authEarly = new AuthService(cfgEarly);
                if (createdNew)
                {
                    authEarly.SaveToken(token);
                }
                else
                {
                    authEarly.WritePendingToken(token);
                    return;
                }
            }
        }

        if (!createdNew)
        {
            // Activate existing UI, or kill a hung instance and take over.
            if (TryActivateExisting())
            {
                return;
            }
            try { _mutex.ReleaseMutex(); } catch { /* ignore */ }
            _mutex.Dispose();
            Thread.Sleep(400);
            _mutex = new Mutex(true, mutexName, out createdNew);
            if (!createdNew)
            {
                MessageBox.Show(
                    "Root-Core-Node is already running.\nCheck the system tray (hidden icons).",
                    "Root-Core-Node",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
                return;
            }
        }

        ProtocolRegistration.EnsureRegistered();
        ApplicationConfiguration.Initialize();
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) =>
        {
            try
            {
                File.AppendAllText(
                    Path.Combine(AppContext.BaseDirectory, "startup-error.log"),
                    $"[{DateTime.Now:o}] UI: {e.Exception}\n");
            }
            catch { /* ignore */ }
            MessageBox.Show(e.Exception.Message, "Root-Core-Node error");
        };
        AppDomain.CurrentDomain.UnhandledException += (_, e) =>
        {
            try
            {
                File.AppendAllText(
                    Path.Combine(AppContext.BaseDirectory, "startup-error.log"),
                    $"[{DateTime.Now:o}] Fatal: {e.ExceptionObject}\n");
            }
            catch { /* ignore */ }
        };

        try
        {
            while (true)
            {
                ReloginRequested = false;
                var cfg = NodeConfig.Load();
                NodeLog.BindFile(Path.Combine(AppContext.BaseDirectory, "logs", "node.log"));
                NodeLog.Info("boot", "Root-Core-Node starting base=" + AppContext.BaseDirectory);
                var auth = new AuthService(cfg);

                // Do not block UI on API validation.
                var hasSession = !string.IsNullOrWhiteSpace(auth.LoadToken());
                if (!hasSession)
                {
                    var api = new RootMcApiClient(cfg);
                    using var login = new LoginForm(cfg, auth, api);
                    if (login.ShowDialog() != DialogResult.OK)
                    {
                        break;
                    }
                }

                Application.Run(new MainForm());
                if (!ReloginRequested)
                {
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            try
            {
                File.AppendAllText(
                    Path.Combine(AppContext.BaseDirectory, "startup-error.log"),
                    $"[{DateTime.Now:o}] Main: {ex}\n");
            }
            catch { /* ignore */ }
            MessageBox.Show(ex.ToString(), "Root-Core-Node failed to start");
        }
        finally
        {
            try { _mutex?.ReleaseMutex(); } catch { /* ignore */ }
            _mutex?.Dispose();
        }
    }

    /// <returns>true if an existing visible instance was activated (caller should exit).</returns>
    private static bool TryActivateExisting()
    {
        try
        {
            var self = Process.GetCurrentProcess().Id;
            var others = Process.GetProcessesByName("Root-Core-Node")
                .Where(p => p.Id != self)
                .ToList();
            foreach (var p in others)
            {
                if (p.MainWindowHandle != IntPtr.Zero)
                {
                    ShowWindow(p.MainWindowHandle, SwRestore);
                    SetForegroundWindow(p.MainWindowHandle);
                    return true;
                }
            }
            // Hung / tray-only with no HWND — kill so this launch can continue.
            foreach (var p in others)
            {
                try { p.Kill(entireProcessTree: true); } catch { /* ignore */ }
                try { p.WaitForExit(2000); } catch { /* ignore */ }
            }
            return false;
        }
        catch
        {
            return false;
        }
    }

    private const int SwRestore = 9;

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
}
