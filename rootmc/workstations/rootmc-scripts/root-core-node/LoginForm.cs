namespace RootMc.CoreNode;

using System.Diagnostics;

/// <summary>Discord gate — must succeed before MainForm opens.</summary>
internal sealed class LoginForm : Form
{
    private readonly NodeConfig _cfg;
    private readonly AuthService _auth;
    private readonly RootMcApiClient _api;
    private readonly Label _status;
    private readonly Button _loginBtn;
    private readonly System.Windows.Forms.Timer _poll;
    private LocalAuthListener? _listener;
    private bool _completing;

    public LoginForm(NodeConfig cfg, AuthService auth, RootMcApiClient api)
    {
        _cfg = cfg;
        _auth = auth;
        _api = api;

        Text = "Root-Core-Node — Discord login";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        Size = new Size(460, 260);
        Padding = new Padding(20);

        var layout = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 4,
        };
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48f));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 56f));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48f));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
        Controls.Add(layout);

        layout.Controls.Add(new Label
        {
            Text = "Sign in with Discord to register this RootMC Network Node.",
            Dock = DockStyle.Fill,
            Font = new Font(Font.FontFamily, 11f, FontStyle.Bold),
            TextAlign = ContentAlignment.MiddleLeft,
        }, 0, 0);

        layout.Controls.Add(new Label
        {
            Text = "Your developer servers and player profile load after login.\nOps controls stay locked until then.",
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleLeft,
        }, 0, 1);

        _loginBtn = new Button
        {
            Text = "Login with Discord",
            AutoSize = true,
            Padding = new Padding(16, 8, 16, 8),
            MinimumSize = new Size(180, 36),
        };
        _loginBtn.Click += async (_, _) => await StartLoginAsync();
        var tips = new ToolTip { AutoPopDelay = 10000, InitialDelay = 400, ShowAlways = true };
        tips.SetToolTip(_loginBtn, "Open Discord in your browser to authorize this RootMC Network Node. The main panel opens after login succeeds.");
        var btnRow = new FlowLayoutPanel { Dock = DockStyle.Fill };
        btnRow.Controls.Add(_loginBtn);
        var quit = new Button { Text = "Quit", AutoSize = true, Padding = new Padding(12, 8, 12, 8) };
        quit.Click += (_, _) =>
        {
            DialogResult = DialogResult.Cancel;
            Close();
        };
        tips.SetToolTip(quit, "Close Root-Core-Node without signing in.");
        btnRow.Controls.Add(quit);
        layout.Controls.Add(btnRow, 0, 2);

        _status = new Label
        {
            Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.TopLeft,
            Text = "Waiting for Discord…",
            ForeColor = Color.DimGray,
        };
        layout.Controls.Add(_status, 0, 3);

        _poll = new System.Windows.Forms.Timer { Interval = 1200 };
        _poll.Tick += async (_, _) => await CheckPendingAsync();
        _poll.Start();

        FormClosing += (_, e) =>
        {
            if (DialogResult != DialogResult.OK && DialogResult != DialogResult.Cancel)
            {
                DialogResult = DialogResult.Cancel;
            }
            _listener?.Dispose();
            _poll.Stop();
        };
    }

    private async Task StartLoginAsync()
    {
        try
        {
            _loginBtn.Enabled = false;
            _status.Text = "Opening Discord…";
            ProtocolRegistration.EnsureRegistered();
            _listener?.Dispose();
            _listener = new LocalAuthListener();
            _listener.TokenReceived += token =>
            {
                BeginInvoke(async () => await CompleteWithTokenAsync(token));
            };
            _listener.Start();

            var url = await _api.StartDiscordLoginAsync();
            Process.Start(new ProcessStartInfo { FileName = url, UseShellExecute = true });
            _status.Text = "Complete Discord in your browser.\nThis window unlocks when login finishes.";
        }
        catch (Exception ex)
        {
            _status.Text = "Login failed: " + ex.Message;
            _status.ForeColor = Color.Firebrick;
            _loginBtn.Enabled = true;
        }
    }

    private async Task CheckPendingAsync()
    {
        if (_completing) return;
        var pending = _auth.ConsumePendingToken();
        if (pending != null)
        {
            await CompleteWithTokenAsync(pending);
        }
    }

    private async Task CompleteWithTokenAsync(string token)
    {
        if (_completing) return;
        _completing = true;
        try
        {
            _status.Text = "Verifying session…";
            _status.ForeColor = Color.DimGray;
            var me = await _api.GetDeveloperMeAsync(token);
            if (me?.SignedIn != true)
            {
                _auth.Clear();
                _status.Text = "Discord session invalid — try again.";
                _status.ForeColor = Color.Firebrick;
                _loginBtn.Enabled = true;
                _completing = false;
                return;
            }
            _auth.SaveToken(token);
            var name = me.DiscordGlobalName ?? me.DiscordUsername ?? "Discord";
            _status.Text = "Signed in as " + name;
            _status.ForeColor = Color.ForestGreen;
            DialogResult = DialogResult.OK;
            Close();
        }
        catch (Exception ex)
        {
            _status.Text = "Verify failed: " + ex.Message;
            _status.ForeColor = Color.Firebrick;
            _loginBtn.Enabled = true;
            _completing = false;
        }
    }
}
