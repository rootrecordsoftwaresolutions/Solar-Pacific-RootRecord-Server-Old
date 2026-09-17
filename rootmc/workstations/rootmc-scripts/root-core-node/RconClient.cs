namespace RootMc.CoreNode;

using System.Net.Sockets;
using System.Text;
using System.Text.Json;

internal sealed class ServerRconSettings
{
    public bool RconEnabled { get; set; }
    public string Host { get; set; } = "127.0.0.1";
    public int Port { get; set; } = 25575;
    public string Password { get; set; } = "";
}

internal static class ServerSettingsStore
{
    public static string PathFor(NodeConfig cfg, string serverId)
    {
        var dir = Path.Combine(cfg.NodeStateDir, "servers");
        Directory.CreateDirectory(dir);
        var safe = string.Concat(serverId.Where(c => char.IsLetterOrDigit(c) || c is '-' or '_'));
        if (string.IsNullOrWhiteSpace(safe)) safe = "server";
        return Path.Combine(dir, safe + ".json");
    }

    public static ServerRconSettings Load(NodeConfig cfg, string serverId)
    {
        try
        {
            var path = PathFor(cfg, serverId);
            if (!File.Exists(path))
            {
                NodeLog.Info("rcon-settings", $"no file for {serverId} → defaults ({path})");
                return new ServerRconSettings();
            }
            var raw = File.ReadAllText(path);
            NodeLog.FileRead(path, NodeLog.RedactSecrets(raw));
            return JsonSerializer.Deserialize<ServerRconSettings>(raw)
                ?? new ServerRconSettings();
        }
        catch (Exception ex)
        {
            NodeLog.Err("rcon-settings", $"load {serverId}: {ex.Message}");
            return new ServerRconSettings();
        }
    }

    public static void Save(NodeConfig cfg, string serverId, ServerRconSettings settings)
    {
        var path = PathFor(cfg, serverId);
        var json = JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(path, json);
        NodeLog.FileWrite(path, json);
    }
}

/// <summary>Minimal Source RCON (Minecraft) client.</summary>
internal static class RconClient
{
    private const int ServerDataAuth = 3;
    private const int ServerDataAuthResponse = 2;
    private const int ServerDataExecCommand = 2;
    private const int ServerDataResponseValue = 0;

    public static async Task<string> ExecuteAsync(
        string host,
        int port,
        string password,
        string command,
        CancellationToken ct = default)
    {
        NodeLog.Section($"RCON {host}:{port}");
        NodeLog.Tx("rcon", $"TCP connect {host}:{port}");
        using var client = new TcpClient();
        await client.ConnectAsync(host, port, ct);
        NodeLog.Rx("rcon", "TCP connected");
        await using var stream = client.GetStream();

        NodeLog.Tx("rcon", $"AUTH id=1 type={ServerDataAuth} password_len={(password ?? "").Length}");
        await SendPacketAsync(stream, 1, ServerDataAuth, password ?? "", ct);
        var auth = await ReadPacketAsync(stream, ct);
        NodeLog.Rx("rcon", $"AUTH response id={auth.Id} type={auth.Type} body_len={auth.Body.Length} body={NodeLog.Truncate(auth.Body, 500)}");
        if (auth.Id == -1)
        {
            NodeLog.Err("rcon", "auth failed (id=-1)");
            throw new InvalidOperationException("RCON auth failed (bad password).");
        }

        NodeLog.Tx("rcon", $"EXEC id=2 type={ServerDataExecCommand} cmd={command}");
        await SendPacketAsync(stream, 2, ServerDataExecCommand, command ?? "", ct);
        var resp = await ReadPacketAsync(stream, ct);
        NodeLog.Rx("rcon", $"EXEC response id={resp.Id} type={resp.Type} body={NodeLog.Truncate(resp.Body)}");
        client.ReceiveTimeout = 400;
        try
        {
            if (stream.DataAvailable)
            {
                var more = await ReadPacketAsync(stream, ct);
                NodeLog.Rx("rcon", $"EXEC trailing id={more.Id} type={more.Type} body={NodeLog.Truncate(more.Body)}");
                if (!string.IsNullOrEmpty(more.Body))
                {
                    return (resp.Body + more.Body).Trim();
                }
            }
        }
        catch (Exception ex)
        {
            NodeLog.Info("rcon", "trailing drain: " + ex.Message);
        }
        return (resp.Body ?? "").Trim();
    }

    private static async Task SendPacketAsync(NetworkStream stream, int id, int type, string body, CancellationToken ct)
    {
        var payload = Encoding.UTF8.GetBytes(body ?? "");
        var len = 4 + 4 + payload.Length + 2;
        var buf = new byte[4 + len];
        BitConverter.GetBytes(len).CopyTo(buf, 0);
        BitConverter.GetBytes(id).CopyTo(buf, 4);
        BitConverter.GetBytes(type).CopyTo(buf, 8);
        Buffer.BlockCopy(payload, 0, buf, 12, payload.Length);
        buf[12 + payload.Length] = 0;
        buf[13 + payload.Length] = 0;
        NodeLog.Tx("rcon-pkt", $"write bytes={buf.Length} len={len} id={id} type={type} payload_bytes={payload.Length}");
        await stream.WriteAsync(buf, ct);
        await stream.FlushAsync(ct);
    }

    private static async Task<(int Id, int Type, string Body)> ReadPacketAsync(NetworkStream stream, CancellationToken ct)
    {
        var lenBuf = await ReadExactAsync(stream, 4, ct);
        var len = BitConverter.ToInt32(lenBuf, 0);
        NodeLog.Rx("rcon-pkt", $"header len={len}");
        if (len < 10 || len > 4096)
        {
            throw new InvalidOperationException("Invalid RCON packet length: " + len);
        }
        var bodyBuf = await ReadExactAsync(stream, len, ct);
        var id = BitConverter.ToInt32(bodyBuf, 0);
        var type = BitConverter.ToInt32(bodyBuf, 4);
        var strLen = Math.Max(0, len - 10);
        var text = strLen > 0 ? Encoding.UTF8.GetString(bodyBuf, 8, strLen) : "";
        return (id, type, text);
    }

    private static async Task<byte[]> ReadExactAsync(NetworkStream stream, int count, CancellationToken ct)
    {
        var buf = new byte[count];
        var off = 0;
        while (off < count)
        {
            var n = await stream.ReadAsync(buf.AsMemory(off, count - off), ct);
            if (n <= 0) throw new EndOfStreamException("RCON connection closed");
            off += n;
        }
        NodeLog.Rx("rcon-pkt", $"read exact {count} bytes");
        return buf;
    }
}
