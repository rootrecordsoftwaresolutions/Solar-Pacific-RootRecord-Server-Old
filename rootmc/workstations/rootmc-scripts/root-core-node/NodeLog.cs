namespace RootMc.CoreNode;

/// <summary>Verbose ops console — every send/receive/file/script detail.</summary>
internal static class NodeLog
{
    private static readonly object Gate = new();
    private static Action<string>? _sink;
    private static string? _filePath;
    private const int MaxBodyChars = 8000;

    public static void Bind(Action<string> sink) => _sink = sink;

    /// <summary>Also append every line to a rotating text log (beside the exe / test server).</summary>
    public static void BindFile(string path)
    {
        try
        {
            var dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            _filePath = path;
            File.AppendAllText(path, Environment.NewLine + "── NodeLog open " + DateTime.Now.ToString("o") + " ──" + Environment.NewLine);
        }
        catch { /* ignore */ }
    }

    public static void Write(string line)
    {
        var stamp = DateTime.Now.ToString("HH:mm:ss.fff");
        var full = $"[{stamp}] {line}";
        var sink = _sink;
        if (sink != null)
        {
            try { sink(line); } catch { /* UI disposed */ }
        }
        var file = _filePath;
        if (file != null)
        {
            lock (Gate)
            {
                try
                {
                    File.AppendAllText(file, full + Environment.NewLine);
                    var fi = new FileInfo(file);
                    if (fi.Exists && fi.Length > 4_000_000)
                    {
                        var bak = file + ".1";
                        try { if (File.Exists(bak)) File.Delete(bak); File.Move(file, bak); } catch { /* ignore */ }
                    }
                }
                catch { /* ignore */ }
            }
        }
    }

    public static void Section(string title) => Write("── " + title + " ──");

    public static void Tx(string channel, string detail) => Write("→ [" + channel + "] " + detail);

    public static void Rx(string channel, string detail) => Write("← [" + channel + "] " + detail);

    public static void Info(string channel, string detail) => Write("· [" + channel + "] " + detail);

    public static void Err(string channel, string detail) => Write("! [" + channel + "] " + detail);

    public static void FileRead(string path, string? preview = null)
    {
        Write("← [file] READ " + path + (preview == null ? "" : "\n    " + Truncate(preview)));
    }

    public static void FileWrite(string path, string? preview = null)
    {
        Write("→ [file] WRITE " + path + (preview == null ? "" : "\n    " + Truncate(RedactSecrets(preview))));
    }

    public static void FileDelete(string path) => Write("→ [file] DELETE " + path);

    public static string Truncate(string? text, int max = MaxBodyChars)
    {
        if (string.IsNullOrEmpty(text)) return "(empty)";
        var t = text.Replace("\r\n", "\n").Replace('\r', '\n');
        if (t.Length <= max) return t;
        return t[..max] + $"\n    … ({t.Length - max} more chars truncated)";
    }

    public static string RedactSecrets(string? text)
    {
        if (string.IsNullOrEmpty(text)) return "";
        var t = text;
        t = System.Text.RegularExpressions.Regex.Replace(
            t,
            @"(Bearer\s+)[A-Za-z0-9\-_\.]+",
            "$1***",
            System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        t = System.Text.RegularExpressions.Regex.Replace(
            t,
            @"(""token""\s*:\s*"")[^""]+("")",
            "$1***$2",
            System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        t = System.Text.RegularExpressions.Regex.Replace(
            t,
            @"(""password""\s*:\s*"")[^""]*("")",
            "$1***$2",
            System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        t = System.Text.RegularExpressions.Regex.Replace(
            t,
            @"(token=)[^&\s]+",
            "$1***",
            System.Text.RegularExpressions.RegexOptions.IgnoreCase);
        return t;
    }

    public static string PreviewToken(string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return "(none)";
        var t = token.Trim();
        if (t.Length <= 12) return "***";
        return t[..6] + "…" + t[^4..] + $" (len={t.Length})";
    }
}
