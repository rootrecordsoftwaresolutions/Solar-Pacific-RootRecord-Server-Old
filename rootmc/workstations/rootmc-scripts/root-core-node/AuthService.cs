namespace RootMc.CoreNode;

using System.Net;
using System.Text.Json;
using Microsoft.Win32;

internal sealed class SessionStore
{
    public string? Token { get; set; }
    public DateTimeOffset SavedAt { get; set; }
}

internal sealed class AuthService
{
    private readonly NodeConfig _cfg;
    private readonly object _gate = new();

    public AuthService(NodeConfig cfg)
    {
        _cfg = cfg;
        Directory.CreateDirectory(_cfg.NodeStateDir);
    }

    public string SessionPath => Path.Combine(_cfg.NodeStateDir, "session.json");
    public string PendingTokenPath => Path.Combine(_cfg.NodeStateDir, "pending-oauth-token.txt");

    public string? LoadToken()
    {
        try
        {
            if (!File.Exists(SessionPath))
            {
                NodeLog.Info("auth", "session missing: " + SessionPath);
                return null;
            }
            var raw = File.ReadAllText(SessionPath);
            NodeLog.FileRead(SessionPath, NodeLog.RedactSecrets(raw));
            var s = JsonSerializer.Deserialize<SessionStore>(raw);
            var token = string.IsNullOrWhiteSpace(s?.Token) ? null : s!.Token;
            NodeLog.Info("auth", "loaded token " + NodeLog.PreviewToken(token));
            return token;
        }
        catch (Exception ex)
        {
            NodeLog.Err("auth", "LoadToken: " + ex.Message);
            return null;
        }
    }

    public void SaveToken(string token)
    {
        lock (_gate)
        {
            Directory.CreateDirectory(_cfg.NodeStateDir);
            var store = new SessionStore { Token = token.Trim(), SavedAt = DateTimeOffset.UtcNow };
            var json = JsonSerializer.Serialize(store, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(SessionPath, json);
            NodeLog.FileWrite(SessionPath, json);
            NodeLog.Info("auth", "saved token " + NodeLog.PreviewToken(token));
            try
            {
                if (File.Exists(PendingTokenPath))
                {
                    File.Delete(PendingTokenPath);
                    NodeLog.FileDelete(PendingTokenPath);
                }
            }
            catch { /* ignore */ }
        }
    }

    public void Clear()
    {
        lock (_gate)
        {
            try
            {
                if (File.Exists(SessionPath))
                {
                    File.Delete(SessionPath);
                    NodeLog.FileDelete(SessionPath);
                }
            }
            catch { /* ignore */ }
            try
            {
                if (File.Exists(PendingTokenPath))
                {
                    File.Delete(PendingTokenPath);
                    NodeLog.FileDelete(PendingTokenPath);
                }
            }
            catch { /* ignore */ }
            NodeLog.Info("auth", "session cleared");
        }
    }

    public void WritePendingToken(string token)
    {
        Directory.CreateDirectory(_cfg.NodeStateDir);
        File.WriteAllText(PendingTokenPath, token.Trim());
        NodeLog.FileWrite(PendingTokenPath, "token=" + NodeLog.PreviewToken(token));
    }

    public string? ConsumePendingToken()
    {
        lock (_gate)
        {
            if (!File.Exists(PendingTokenPath)) return null;
            try
            {
                var t = File.ReadAllText(PendingTokenPath).Trim();
                File.Delete(PendingTokenPath);
                NodeLog.FileDelete(PendingTokenPath);
                NodeLog.Info("auth", "consumed pending token " + NodeLog.PreviewToken(t));
                return string.IsNullOrWhiteSpace(t) ? null : t;
            }
            catch (Exception ex)
            {
                NodeLog.Err("auth", "ConsumePendingToken: " + ex.Message);
                return null;
            }
        }
    }

    public static bool TryParseAuthUrl(string? url, out string token)
    {
        token = "";
        if (string.IsNullOrWhiteSpace(url)) return false;
        try
        {
            var u = url.Trim();
            string query;
            if (u.StartsWith("rootmc:", StringComparison.OrdinalIgnoreCase))
            {
                var qIdx = u.IndexOf('?');
                query = qIdx >= 0 ? u[(qIdx + 1)..] : "";
            }
            else if (Uri.TryCreate(u, UriKind.Absolute, out var http))
            {
                query = http.Query.TrimStart('?');
                if (string.IsNullOrEmpty(query) && !string.IsNullOrEmpty(http.Fragment))
                {
                    query = http.Fragment.TrimStart('#');
                }
            }
            else
            {
                return false;
            }

            foreach (var part in query.Split('&', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            {
                var eq = part.IndexOf('=');
                if (eq <= 0) continue;
                var key = Uri.UnescapeDataString(part[..eq]);
                if (!key.Equals("token", StringComparison.OrdinalIgnoreCase)) continue;
                token = Uri.UnescapeDataString(part[(eq + 1)..]);
                return !string.IsNullOrWhiteSpace(token);
            }
        }
        catch
        {
            /* ignore */
        }
        return false;
    }
}

/// <summary>Registers HKCU URL protocol rootmc:// → this exe.</summary>
internal static class ProtocolRegistration
{
    private const string Scheme = "rootmc";

    public static void EnsureRegistered()
    {
        try
        {
            var exe = Application.ExecutablePath;
            using var key = Registry.CurrentUser.CreateSubKey($@"Software\Classes\{Scheme}");
            if (key == null) return;
            key.SetValue("", "URL:RootMC Auth");
            key.SetValue("URL Protocol", "");
            using var cmd = key.CreateSubKey(@"shell\open\command");
            cmd?.SetValue("", $"\"{exe}\" \"%1\"");
            NodeLog.Info("protocol", $"registered {Scheme}:// → {exe}");
        }
        catch (Exception ex)
        {
            NodeLog.Err("protocol", ex.Message);
        }
    }
}

/// <summary>Brief localhost catcher while waiting for Discord OAuth (fallback).</summary>
internal sealed class LocalAuthListener : IDisposable
{
    private HttpListener? _listener;
    private CancellationTokenSource? _cts;

    public event Action<string>? TokenReceived;

    public int Port { get; private set; }

    public bool Start(int preferredPort = 17865)
    {
        Stop();
        for (var i = 0; i < 8; i++)
        {
            var port = preferredPort + i;
            try
            {
                var l = new HttpListener();
                l.Prefixes.Add($"http://127.0.0.1:{port}/");
                l.Start();
                _listener = l;
                Port = port;
                _cts = new CancellationTokenSource();
                _ = Task.Run(() => LoopAsync(_cts.Token));
                NodeLog.Info("oauth-listener", $"listening http://127.0.0.1:{port}/");
                return true;
            }
            catch
            {
                /* try next port */
            }
        }
        return false;
    }

    private async Task LoopAsync(CancellationToken ct)
    {
        var l = _listener;
        if (l == null) return;
        while (!ct.IsCancellationRequested && l.IsListening)
        {
            HttpListenerContext ctx;
            try
            {
                ctx = await l.GetContextAsync().WaitAsync(ct);
            }
            catch
            {
                break;
            }
            try
            {
                var url = ctx.Request.Url?.ToString() ?? "";
                NodeLog.Rx("oauth-listener", $"request {ctx.Request.HttpMethod} {NodeLog.RedactSecrets(url)}");
                var ok = AuthService.TryParseAuthUrl(url, out var token);
                var html = ok
                    ? "<html><body style='font-family:sans-serif;padding:2rem'><h2>Root-Core-Node</h2><p>Signed in. You can close this window.</p></body></html>"
                    : "<html><body style='font-family:sans-serif;padding:2rem'><h2>Root-Core-Node</h2><p>No token in redirect.</p></body></html>";
                var bytes = System.Text.Encoding.UTF8.GetBytes(html);
                ctx.Response.ContentType = "text/html; charset=utf-8";
                ctx.Response.OutputStream.Write(bytes);
                ctx.Response.Close();
                NodeLog.Tx("oauth-listener", $"response HTML {bytes.Length} bytes ok={ok}");
                if (ok)
                {
                    NodeLog.Info("oauth-listener", "token received " + NodeLog.PreviewToken(token));
                    TokenReceived?.Invoke(token);
                }
            }
            catch
            {
                try { ctx.Response.Abort(); } catch { /* ignore */ }
            }
        }
    }

    public void Stop()
    {
        try { _cts?.Cancel(); } catch { /* ignore */ }
        try { _listener?.Stop(); } catch { /* ignore */ }
        try { _listener?.Close(); } catch { /* ignore */ }
        _listener = null;
        _cts = null;
    }

    public void Dispose() => Stop();
}
