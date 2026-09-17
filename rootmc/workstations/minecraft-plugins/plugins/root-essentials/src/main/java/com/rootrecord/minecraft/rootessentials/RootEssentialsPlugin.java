package com.rootrecord.minecraft.rootessentials;

import com.rootrecord.minecraft.common.RootMcEconomyBridge;
import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcNewPlayerGrace;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.RootMcWildernessBlockNotifier;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.command.PluginCommandRegistrar;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootessentials.command.*;
import com.rootrecord.minecraft.rootessentials.config.RootEssentialsConfig;
import com.rootrecord.minecraft.rootessentials.data.*;
import com.rootrecord.minecraft.rootessentials.listener.EssentialsListener;
import com.rootrecord.minecraft.rootessentials.explode.TntChestplateListener;
import com.rootrecord.minecraft.rootessentials.listener.ExplodeDeathListener;
import com.rootrecord.minecraft.rootessentials.listener.FluidSpreadFeeListener;
import com.rootrecord.minecraft.rootessentials.listener.NewPlayerGraceListener;
import com.rootrecord.minecraft.rootessentials.listener.PlaceholderApiHookListener;
import com.rootrecord.minecraft.rootessentials.service.HeldLightService;
import com.rootrecord.minecraft.rootessentials.service.NewPlayerGraceService;
import com.rootrecord.minecraft.rootessentials.service.PlayerStateService;
import com.rootrecord.minecraft.rootessentials.service.TeleportWarmupService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

/**
 * QoL Essentials replacement (homes, teleport, kits, spawn/banner/potions).
 * Gold economy lives in {@code Root-Economy}.
 */
public final class RootEssentialsPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yaml;
    private RootEssentialsConfig config;
    private HomeStore homes;
    private MailStore mail;
    private ModerationStore moderation;
    private KitClaimStore kitClaims;
    private SpawnStore spawnStore;
    private WarpStore warps;
    private FirstJoinStore firstJoin;
    private NewPlayerGraceService newPlayerGrace;
    private final PlayerStateService playerState = new PlayerStateService();
    private TeleportWarmupService teleportWarmup;
    private HeldLightService heldLight;
    private final DecimalFormat moneyFmt = new DecimalFormat("0.000");
    private boolean placeholderExpansionRegistered;

    private com.rootrecord.minecraft.rootspawn.RootSpawnPlugin spawnFeature;
    private com.rootrecord.minecraft.rootbanner.RootBannerPlugin bannerFeature;
    private com.rootrecord.minecraft.rootpotions.RootPotionsPlugin potionsFeature;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        getLogger().info("Enabling Root-Essentials v" + getDescription().getVersion());
        RootMcDatabaseConfig.ensureDefaults(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_ESSENTIALS_CONFIG, RootRecordFolders.ROOT_ESSENTIALS_CONFIG);
        yaml.load();
        migrateAvaWildernessWatch();
        reloadLocalConfig();
        try {
            MySqlSupport sql = new MySqlSupport(config);
            homes = new HomeStore(sql, config.mysqlTablePrefix());
            mail = new MailStore(sql, config.mysqlTablePrefix());
            moderation = new ModerationStore(sql, config.mysqlTablePrefix());
            kitClaims = new KitClaimStore(sql, config.mysqlTablePrefix());
            spawnStore = new SpawnStore(sql, config.mysqlTablePrefix());
            warps = new WarpStore(sql, config.mysqlTablePrefix());
            firstJoin = new FirstJoinStore(sql, config.mysqlTablePrefix());
            homes.initSchema();
            mail.initSchema();
            moderation.initSchema();
            kitClaims.initSchema();
            spawnStore.initSchema();
            warps.initSchema();
            firstJoin.initSchema();
        } catch (Exception ex) {
            getLogger().severe("MySQL init failed for "
                    + config.mysqlUsername() + "@" + config.mysqlHost() + ":" + config.mysqlPort()
                    + "/" + config.mysqlDatabase()
                    + " (passwordSet=" + (config.mysqlPassword() != null && !config.mysqlPassword().isBlank())
                    + "): " + ex.getMessage());
            getLogger().severe("Fix plugins/RootMC/database.yml (do not upload handoff stubs with empty password).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        newPlayerGrace = new NewPlayerGraceService(this, firstJoin);
        newPlayerGrace.reload(yaml.config());
        newPlayerGrace.start();
        teleportWarmup = new TeleportWarmupService(this);
        reloadTeleportSettings();
        heldLight = new HeldLightService(this);
        heldLight.reload(yaml.config());
        registerCommands();
        getServer().getPluginManager().registerEvents(new EssentialsListener(this), this);
        getServer().getPluginManager().registerEvents(new NewPlayerGraceListener(this), this);
        getServer().getPluginManager().registerEvents(new FluidSpreadFeeListener(this), this);
        getServer().getPluginManager().registerEvents(new PlaceholderApiHookListener(this), this);
        getServer().getServicesManager().register(RootMcWildernessBlockNotifier.class, newPlayerGrace, this, ServicePriority.Normal);
        getServer().getServicesManager().register(RootMcNewPlayerGrace.class, newPlayerGrace, this, ServicePriority.Normal);
        getServer().getScheduler().runTask(this, this::registerPlaceholderExpansionIfPresent);
        getServer().getScheduler().runTaskLater(this, this::registerPlaceholderExpansionIfPresent, 40L);
        getServer().getScheduler().runTaskLater(this, this::registerPlaceholderExpansionIfPresent, 100L);
        enableAbsorbedFeatures();
        getLogger().info("Root Essentials enabled (QoL + spawn/banner/potions; economy via Root-Economy).");
    }

    private void enableAbsorbedFeatures() {
        spawnFeature = new com.rootrecord.minecraft.rootspawn.RootSpawnPlugin(this);
        spawnFeature.enable();
        bannerFeature = new com.rootrecord.minecraft.rootbanner.RootBannerPlugin(this);
        bannerFeature.enable();
        potionsFeature = new com.rootrecord.minecraft.rootpotions.RootPotionsPlugin(this);
        potionsFeature.enable();
    }

    public void registerPlaceholderExpansionIfPresent() {
        if (placeholderExpansionRegistered) {
            return;
        }
        var papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            getLogger().info("PlaceholderAPI not enabled yet; rootessentials expansion will retry.");
            return;
        }
        try {
            Class.forName(
                    "me.clip.placeholderapi.expansion.PlaceholderExpansion",
                    false,
                    papi.getClass().getClassLoader());
            var expansion = new com.rootrecord.minecraft.rootessentials.placeholder.RootEssentialsExpansion(this);
            if (expansion.register()) {
                placeholderExpansionRegistered = true;
                getLogger().info("PlaceholderAPI expansion registered (rootessentials).");
            } else {
                getLogger().warning("PlaceholderAPI expansion register() returned false for rootessentials.");
            }
        } catch (Throwable ex) {
            getLogger().warning("PlaceholderAPI expansion failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void registerCommands() {
        SpawnCommand spawnCommand = new SpawnCommand(this);
        bind("spawn", spawnCommand);
        var spawnCmd = getCommand("spawn");
        if (spawnCmd != null) {
            spawnCmd.setTabCompleter(spawnCommand);
        }
        bind("sethome", new HomeCommands(this, "set"));
        bind("home", new HomeCommands(this, "go"));
        bind("delhome", new HomeCommands(this, "del"));
        bind("renamehome", new RenameHomeCommand(this));
        bind("homes", new HomesCommand(this));
        bind("tpa", new TeleportCommands.Tpa(this));
        bind("tpaccept", new TeleportCommands.TpAccept(this));
        bind("tpdeny", new TeleportCommands.TpDeny(this));
        bind("tpahere", new TeleportCommands.TpaHere(this));
        bind("msg", new SocialCommands.Msg(this));
        bind("reply", new SocialCommands.Reply(this));
        bind("r", new SocialCommands.Reply(this));
        bind("mail", new SocialCommands.Mail(this));
        bind("me", new SocialCommands.Me(this));
        bind("ignore", new SocialCommands.Ignore(this));
        bind("unignore", new SocialCommands.Unignore(this));
        bind("kit", new KitCommand(this));
        bind("worth", new InfoCommands.Worth(this));
        bind("help", new InfoCommands.Help(this));
        bind("list", new InfoCommands.List(this));
        bind("motd", new InfoCommands.Motd(this));
        bind("rules", new InfoCommands.Rules(this));
        // Do not declare ping in plugin.yml — leaving it unbound NPEs on Paper when Root-Ping owns /ping.
        if (Bukkit.getPluginManager().getPlugin("Root-Ping") == null) {
            var pingCmd = PluginCommandRegistrar.register(
                    this, "ping", "Show ping", "/ping", List.of());
            if (pingCmd != null) {
                pingCmd.setExecutor(new InfoCommands.Ping(this));
            }
        } else {
            getLogger().info("Root-Ping present — /ping owned by Root-Ping (Essentials ping stub skipped).");
        }
        bind("compass", new InfoCommands.Compass(this));
        bind("depth", new InfoCommands.Depth(this));
        bind("getpos", new InfoCommands.GetPos(this));
        bind("near", new InfoCommands.Near(this));
        if (Bukkit.getPluginManager().getPlugin("Root-Times") == null) {
            bind("afk", new InfoCommands.Afk(this));
        } else {
            getLogger().info("Root-Times present — /afk owned by Times (Essentials AFK stub skipped).");
        }
        bind("nick", new CosmeticCommands.Nick(this));
        bind("hat", new CosmeticCommands.Hat(this));
        var warpCmd = new WarpCommands.Warp(this);
        bind("warp", warpCmd);
        var warpCommand = getCommand("warp");
        if (warpCommand != null) warpCommand.setTabCompleter(warpCmd);
        bind("warps", new WarpCommands.Warps(this));
        var survey = new SurveyCommand(this);
        bind("survey", survey);
        var surveyCmd = getCommand("survey");
        if (surveyCmd != null) {
            surveyCmd.setTabCompleter(survey);
        }
        var explode = new ExplodeCommand(this);
        bind("explode", explode);
        var explodeCmd = getCommand("explode");
        if (explodeCmd != null) {
            explodeCmd.setTabCompleter(explode);
        }
        getServer().getPluginManager().registerEvents(new ExplodeDeathListener(explode), this);
        getServer().getPluginManager().registerEvents(new TntChestplateListener(this), this);
        bind("feed", new PlayerUtilCommands.Feed(this));
        bind("heal", new PlayerUtilCommands.Heal(this));
        bind("repair", new PlayerUtilCommands.Repair(this));
        bind("top", new PlayerUtilCommands.Top(this));
        bind("clear", new PlayerUtilCommands.Clear(this));
        bind("clearinventory", new PlayerUtilCommands.Clear(this));
        bind("trash", new PlayerUtilCommands.Trash(this));
        bind("disposal", new PlayerUtilCommands.Trash(this));
        bind("suicide", new PlayerUtilCommands.Suicide(this));
        bind("kill", new PlayerUtilCommands.Suicide(this));
        bind("seen", new PlayerUtilCommands.Seen(this));
        bind("workbench", new ExtraCommands.Workbench(this));
        bind("craft", new ExtraCommands.Workbench(this));
        bind("tpauto", new TeleportCommands.TpAuto(this));
        bind("rtp", new RtpCommand(this));
        bind("show", new ShowCommands.ShowHand(this));
        bind("showhand", new ShowCommands.ShowHand(this));
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(executor);
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (potionsFeature != null) {
            potionsFeature.disable();
            potionsFeature = null;
        }
        if (bannerFeature != null) {
            bannerFeature.disable();
            bannerFeature = null;
        }
        if (spawnFeature != null) {
            spawnFeature.disable();
            spawnFeature = null;
        }
        if (newPlayerGrace != null) {
            newPlayerGrace.stop();
            getServer().getServicesManager().unregister(RootMcWildernessBlockNotifier.class, newPlayerGrace);
            getServer().getServicesManager().unregister(RootMcNewPlayerGrace.class, newPlayerGrace);
        }
        if (heldLight != null) {
            heldLight.stop();
            heldLight = null;
        }
    }

    /** Ava is wilderness: guardian watch + 1-minute fee recaps. */
    private void migrateAvaWildernessWatch() {
        var cfg = yaml.config();
        if (cfg.getBoolean("wilderness-destroy-fee.ava-watch-114", false)
                && cfg.isSet("messages.wilderness-destroy-fee-watch-start")) {
            return;
        }
        cfg.set("wilderness-destroy-fee.notify-bucket-seconds", 60);
        cfg.set(
                "messages.wilderness-destroy-fee-watch-start",
                "&bAva &8· &eWilderness watch is on.&7 Resource fees: &f1 G&7/block → Server Reserve. I'll recap each minute while you keep taking. &f/c new &7to claim.");
        cfg.set(
                "messages.wilderness-destroy-fee-watch-minute",
                "&bAva &8· &eWilderness recap &8· &f{blocks} &7blocks · &f{fee} {currency} &7→ reserve. Balance: &f{balance}&7.");
        cfg.set(
                "messages.wilderness-destroy-fee-cannot-afford",
                "&cAva &8· &cNeed &f{fee} {currency}&c to take from my wilderness (&f{item}&c). Balance: &f{balance}&c.");
        cfg.set(
                "messages.wilderness-destroy-fee-charged",
                "&bAva &8· &eWilderness fee &f{fee} {currency}&e → reserve for &f{item}&e. Balance: &f{balance}&e.");
        cfg.set("wilderness-destroy-fee.ava-watch-114", true);
        yaml.save();
        getLogger().info("Wilderness is Ava: guardian watch + 1-minute fee recaps.");
    }

    public void reloadLocalConfig() {
        yaml.reload();
        migrateAvaWildernessWatch();
        config = RootEssentialsConfig.from(this, yaml.config());
        reloadTeleportSettings();
        if (newPlayerGrace != null) {
            newPlayerGrace.reload(yaml.config());
        }
        if (heldLight != null) {
            heldLight.reload(yaml.config());
        }
    }

    public WarpStore warps() { return warps; }

    public PlayerStateService playerState() { return playerState; }
    public TeleportWarmupService teleportWarmup() { return teleportWarmup; }

    public NewPlayerGraceService newPlayerGrace() {
        return newPlayerGrace;
    }

    public RootMcWildernessBlockNotifier wildernessBlockNotifier() {
        return newPlayerGrace;
    }

    public RootMcNewPlayerGrace newPlayerGraceBridge() {
        return newPlayerGrace;
    }

    public boolean teleportPlayer(Player player, Location destination, Runnable onSuccess) {
        return teleportWarmup.request(player, destination, onSuccess);
    }

    public boolean teleportPlayer(Player player, java.util.function.Supplier<Location> destination, Runnable onSuccess) {
        return teleportWarmup.request(player, destination, onSuccess);
    }

    private void reloadTeleportSettings() {
        if (teleportWarmup == null) {
            return;
        }
        int warmup = yaml.config().getInt("teleport.warmup-seconds", 3);
        int combat = yaml.config().getInt("teleport.combat-tag-seconds", 30);
        teleportWarmup.reload(warmup, combat);
    }

    public MailStore mail() { return mail; }
    public ModerationStore moderation() { return moderation; }
    public KitClaimStore kitClaims() { return kitClaims; }
    public SpawnStore spawnStore() { return spawnStore; }

    public List<String> motdLines() {
        List<String> lines = config.motdLines();
        if (isTestHost()) {
            if (motdLooksProduction(lines) && !motdLooksTest(lines)) {
                return testMotd();
            }
            return lines;
        }
        if (motdLooksTest(lines)) {
            return productionMotd();
        }
        return lines;
    }

    private boolean isTestHost() {
        try {
            if (Bukkit.getPort() == 24945) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String name = RootMcServerDisplay.serverName(this);
        return name != null && name.toLowerCase(Locale.ROOT).contains("test");
    }

    private static boolean motdLooksTest(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        String blob = String.join("\n", lines).toLowerCase(Locale.ROOT);
        return blob.contains("test.rootmc.net") || blob.contains("(test)");
    }

    private static boolean motdLooksProduction(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        String blob = String.join("\n", lines).toLowerCase(Locale.ROOT);
        return blob.contains("play.rootmc.net") && !blob.contains("test.rootmc.net");
    }

    private static List<String> productionMotd() {
        return List.of(
                "&d&lRootMC&r&7 — &fAge Of Ava Begins",
                "&7Singular RootMC · &fplay.rootmc.net",
                "&7Currency is &fGold (G)&7. Redeem at the mint via &f/mint&7.",
                "&7Start: &f/rules &7· &f/cmds &7· &f/vote");
    }

    private static List<String> testMotd() {
        return List.of(
                "&d&lRootMC&r&7 — &fAge Of Ava Begins (test)",
                "&7Singular RootMC · &ftest.rootmc.net",
                "&7Currency is &fGold (G)&7. Redeem at the mint via &f/mint&7.",
                "&7Start: &f/rules &7· &f/cmds &7· &f/vote");
    }
    public List<String> rulesLines() { return config.rulesLines(); }

    public List<String> kitItems(String kit) { return config.kitItems().getOrDefault(kit.toLowerCase(Locale.ROOT), List.of()); }
    public boolean kitOneTime(String kit) { return config.kitOneTime().getOrDefault(kit.toLowerCase(Locale.ROOT), true); }
    public java.util.Set<String> kitNames() { return config.kitItems().keySet(); }

    public void ensureBalanceRow(Player player) throws Exception {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(this);
        if (eco == null) {
            return;
        }
        Plugin economyPlugin = Bukkit.getPluginManager().getPlugin("Root-Economy");
        if (economyPlugin != null && economyPlugin.isEnabled()) {
            try {
                economyPlugin.getClass().getMethod("ensureBalanceRow", Player.class).invoke(economyPlugin, player);
                return;
            } catch (ReflectiveOperationException ignored) {
                // fall through to balance touch
            }
        }
        eco.balance(player.getUniqueId());
    }

    public void grantNewbieKit(Player player) {
        String kit = config.newbieKit();
        if (kit == null || kit.isBlank()) return;
        try {
            if (kitClaims == null) {
                getLogger().warning("Newbie kit skipped — kit claim store unavailable");
                return;
            }
            if (kitClaims.hasClaimed(player.getUniqueId(), kit)) return;
            List<String> entries = kitItems(kit);
            if (entries.isEmpty()) {
                getLogger().warning(
                        "Newbie kit '" + kit + "' has no items in root-essentials.yml — not marking claimed");
                return;
            }
            int given = 0;
            for (String entry : entries) {
                String[] parts = entry.trim().split("\\s+");
                if (parts.length < 2) continue;
                Material mat = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
                if (mat == null || mat.isAir()) continue;
                int amount = Integer.parseInt(parts[1]);
                var leftover = player.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount));
                leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                given++;
            }
            if (given <= 0) {
                getLogger().warning("Newbie kit '" + kit + "' resolved to 0 items — not marking claimed");
                return;
            }
            if (kitOneTime(kit)) kitClaims.markClaimed(player.getUniqueId(), kit);
            player.sendMessage(colorize("&aStarter kit received. &7(/kit list)"));
        } catch (Exception ex) {
            getLogger().warning("Newbie kit failed for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void spyMessage(String from, String to, String body) {
        String line = "&8[Spy] &f" + from + " &7→ &f" + to + " &7»&f " + body;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (playerState.isSocialSpy(online.getUniqueId())) {
                online.sendMessage(colorize(line));
            }
        }
    }

    public String messagePrefix() {
        return yaml.config().getString("messages.prefix", "");
    }

    public String msg(String key) {
        String p = messagePrefix();
        String body = yaml.config().getString("messages." + key, key);
        return colorize(p + body);
    }

    public String rawMsg(String key) {
        return yaml.config().getString("messages." + key, key);
    }

    public String colorize(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }

    public String money(double value) {
        synchronized (moneyFmt) { return moneyFmt.format(value); }
    }

    public String currency() { return config.currencySymbol(); }
    public String defaultHomeName() { return config.defaultHomeName(); }
    public Double worth(Material material) { return config.worthByMaterial().get(material); }

    public double serviceFee(String key, double defaultAmount) {
        return Math.max(0, yaml.config().getDouble("fees." + key, defaultAmount));
    }

    /** Player already paid — sink fee into treasury closed-loop vault via Root-Economy. */
    public void sinkServiceFee(UUID playerUuid, String playerName, double amount, String channel) {
        if (amount <= 0) {
            return;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(this);
        if (treasury == null) {
            return;
        }
        treasury.settleClosedLoopPayment(
                playerUuid,
                playerName,
                amount,
                "service-fee:" + channel);
    }

    public Double itemPrice(Material material) {
        if (material == null || material.isAir()) return null;
        RootMcEconomyBridge bridge = marketBridge();
        if (bridge != null) {
            double avg = bridge.averagePrice(material.name());
            if (avg > 0) return avg;
        }
        return worth(material);
    }

    private RootMcEconomyBridge marketBridge() {
        var bn = getServer().getPluginManager().getPlugin("RootMC");
        if (bn instanceof RootMcEconomyBridge bridge) return bridge;
        return null;
    }

    public boolean marketBridgeAvailable() {
        return marketBridge() != null;
    }

    public double balance(UUID uuid, String username) throws Exception {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(this);
        if (eco == null) {
            return 0;
        }
        return eco.balance(uuid, username == null ? "player" : username);
    }

    public void deposit(UUID uuid, String username, double amount) throws Exception {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(this);
        if (eco == null || amount <= 0) {
            return;
        }
        eco.depositAccount(uuid, username == null ? "player" : username, amount);
    }

    public boolean withdraw(UUID uuid, String username, double amount) throws Exception {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(this);
        if (eco == null) {
            return false;
        }
        return eco.withdrawAccount(uuid, username == null ? "player" : username, amount);
    }

    public boolean withdrawAllowingDebt(UUID uuid, String username, double amount) throws Exception {
        Plugin economyPlugin = Bukkit.getPluginManager().getPlugin("Root-Economy");
        if (economyPlugin != null && economyPlugin.isEnabled()) {
            try {
                Object result = economyPlugin.getClass()
                        .getMethod("withdrawAllowingDebt", UUID.class, String.class, double.class)
                        .invoke(economyPlugin, uuid, username, amount);
                return Boolean.TRUE.equals(result);
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        return withdraw(uuid, username, amount);
    }

    /** Move full wallet to Server Reserve (DONATION). Returns amount seized, or 0. */
    public double seizeWalletToReserve(UUID uuid, String username) {
        Plugin economyPlugin = Bukkit.getPluginManager().getPlugin("Root-Economy");
        if (economyPlugin != null && economyPlugin.isEnabled()) {
            try {
                Object result = economyPlugin.getClass()
                        .getMethod("seizeWalletToReserve", UUID.class, String.class)
                        .invoke(economyPlugin, uuid, username);
                if (result instanceof Number n) {
                    return n.doubleValue();
                }
            } catch (ReflectiveOperationException ex) {
                getLogger().warning("seizeWalletToReserve failed: " + ex.getMessage());
            }
        }
        try {
            double bal = balance(uuid, username);
            if (bal <= 0) {
                return 0;
            }
            if (withdraw(uuid, username, bal)) {
                sinkServiceFee(uuid, username, bal, "explode");
                return bal;
            }
        } catch (Exception ex) {
            getLogger().warning("explode wallet wipe failed: " + ex.getMessage());
        }
        return 0;
    }

    public int maxHomes(Player player) {
        if (player.hasPermission("essentials.sethome.multiple.lifetime")) return config.maxHomesLifetime();
        if (player.hasPermission("essentials.sethome.multiple.pro")) return config.maxHomesPro();
        return config.maxHomesDefault();
    }

    public int homeCount(UUID uuid) throws Exception { return homes.count(uuid); }
    public boolean hasHome(UUID uuid, String name) throws Exception { return homes.get(uuid, name.toLowerCase(Locale.ROOT)) != null; }
    public void setHome(UUID uuid, String name, Location loc) throws Exception { homes.upsert(uuid, name.toLowerCase(Locale.ROOT), loc); }
    public Location getHome(UUID uuid, String name) throws Exception { return homes.get(uuid, name.toLowerCase(Locale.ROOT)); }
    public boolean deleteHome(UUID uuid, String name) throws Exception { return homes.delete(uuid, name.toLowerCase(Locale.ROOT)); }
    public boolean renameHome(UUID uuid, String from, String to) throws Exception { return homes.rename(uuid, from, to); }
    public List<String> listHomeNames(UUID uuid) throws Exception { return homes.listNames(uuid); }

    public Location spawnLocation(Player player) {
        try {
            Location stored = spawnStore.get();
            if (stored != null) return stored;
        } catch (Exception ignored) {}
        String configuredWorld = config.spawnWorld();
        var world = !configuredWorld.isBlank()
                ? getServer().getWorld(configuredWorld)
                : (player.getWorld() != null ? player.getWorld() : getServer().getWorlds().stream().findFirst().orElse(null));
        return world == null ? null : world.getSpawnLocation();
    }
}
