namespace RootMc.CoreNode;

using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

internal sealed class RootMcApiClient
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly NodeConfig _cfg;

    public RootMcApiClient(NodeConfig cfg)
    {
        _cfg = cfg;
    }

    public string ApiBase => (_cfg.ApiBase ?? "https://api.rootmc.net").TrimEnd('/');

    public async Task<string> StartDiscordLoginAsync(CancellationToken ct = default)
    {
        var body = """{"return_to":"/developer","mobile_app":true}""";
        var url = ApiBase + "/api/developer/auth/discord/start";
        NodeLog.Tx("http", $"POST {url}\n    body={body}");
        using var req = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(body, Encoding.UTF8, "application/json"),
        };
        using var res = await _http.SendAsync(req, ct);
        var text = await res.Content.ReadAsStringAsync(ct);
        NodeLog.Rx("http", $"POST {url} → HTTP {(int)res.StatusCode} {res.ReasonPhrase}\n    {NodeLog.Truncate(NodeLog.RedactSecrets(text))}");
        if (!res.IsSuccessStatusCode)
        {
            throw new InvalidOperationException($"Discord start HTTP {(int)res.StatusCode}: {text}");
        }
        using var doc = JsonDocument.Parse(text);
        if (!doc.RootElement.TryGetProperty("authorize_url", out var urlEl))
        {
            throw new InvalidOperationException("authorize_url missing: " + text);
        }
        var authorize = urlEl.GetString() ?? throw new InvalidOperationException("empty authorize_url");
        NodeLog.Info("auth", "authorize_url ready (length=" + authorize.Length + ")");
        return authorize;
    }

    public async Task<DeveloperMe?> GetDeveloperMeAsync(string token, CancellationToken ct = default)
    {
        var json = await GetAsync("/api/developer/me", token, ct);
        return JsonSerializer.Deserialize<DeveloperMe>(json, JsonOpts());
    }

    public async Task<DeveloperServersResponse?> GetDeveloperServersAsync(string token, CancellationToken ct = default)
    {
        var json = await GetAsync("/api/developer/servers", token, ct);
        return JsonSerializer.Deserialize<DeveloperServersResponse>(json, JsonOpts());
    }

    public async Task<AccountMe?> GetAccountMeAsync(string token, CancellationToken ct = default)
    {
        var json = await GetAsync("/api/account/me", token, ct);
        return JsonSerializer.Deserialize<AccountMe>(json, JsonOpts());
    }

    public async Task<PlayerStatsResponse?> GetPlayerStatsAsync(string uuid, CancellationToken ct = default)
    {
        var json = await GetAsync("/api/realm/minecraft/stats/" + Uri.EscapeDataString(uuid), null, ct);
        return JsonSerializer.Deserialize<PlayerStatsResponse>(json, JsonOpts());
    }

    private async Task<string> GetAsync(string path, string? token, CancellationToken ct)
    {
        var url = ApiBase + path;
        var authNote = string.IsNullOrWhiteSpace(token)
            ? "auth=none"
            : "auth=Bearer " + NodeLog.PreviewToken(token);
        NodeLog.Tx("http", $"GET {url} ({authNote})");
        using var req = new HttpRequestMessage(HttpMethod.Get, url);
        if (!string.IsNullOrWhiteSpace(token))
        {
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        try
        {
            using var res = await _http.SendAsync(req, ct);
            var text = await res.Content.ReadAsStringAsync(ct);
            NodeLog.Rx("http", $"GET {path} → HTTP {(int)res.StatusCode} {res.ReasonPhrase} ({text.Length} bytes)\n    {NodeLog.Truncate(NodeLog.RedactSecrets(text))}");
            if (!res.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"GET {path} HTTP {(int)res.StatusCode}: {text}");
            }
            return text;
        }
        catch (Exception ex) when (ex is not InvalidOperationException)
        {
            NodeLog.Err("http", $"GET {path} failed: {ex.GetType().Name}: {ex.Message}");
            throw;
        }
    }

    private static JsonSerializerOptions JsonOpts() => new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
    };
}

internal sealed class DeveloperMe
{
    public bool SignedIn { get; set; }
    public string? AccountId { get; set; }
    public string? Email { get; set; }
    public bool DiscordLinked { get; set; }
    public string? DiscordUsername { get; set; }
    public string? DiscordGlobalName { get; set; }
    public List<DevServerBrief>? Servers { get; set; }
}

internal sealed class DevServerBrief
{
    public string? ServerId { get; set; }
    public string? ServerName { get; set; }
}

internal sealed class DeveloperServersResponse
{
    public bool SignedIn { get; set; }
    public List<DevServerHealth>? Servers { get; set; }
}

internal sealed class DevServerHealth
{
    public string? ServerId { get; set; }
    public string? ServerName { get; set; }
    public string? Connection { get; set; }
    public string? Health { get; set; }
    public string? RootmcLastSeenAt { get; set; }
    public int? OnlinePlayers { get; set; }
    public string? Address { get; set; }
    public string? Version { get; set; }
}

internal sealed class AccountMe
{
    public bool SignedIn { get; set; }
    public string? AccountId { get; set; }
    public string? DiscordUsername { get; set; }
    public string? DiscordGlobalName { get; set; }
    public bool MinecraftLinked { get; set; }
    public string? MinecraftUuid { get; set; }
    public string? MinecraftUsername { get; set; }
}

internal sealed class PlayerStatsResponse
{
    public PlayerStatsBody? Stats { get; set; }
}

internal sealed class PlayerStatsBody
{
    public string? MinecraftUuid { get; set; }
    public string? MinecraftUsername { get; set; }
    public bool Verified { get; set; }
    public JsonElement? Playtime { get; set; }
    public JsonElement? NetWorth { get; set; }
}
