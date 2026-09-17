package com.rootrecord.minecraft.rootgamble;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootgamble.command.CoinCommand;
import com.rootrecord.minecraft.rootgamble.command.DiceCommand;
import com.rootrecord.minecraft.rootgamble.command.GambleCommand;
import com.rootrecord.minecraft.rootgamble.command.HiLoCommand;
import com.rootrecord.minecraft.rootgamble.command.LottoCommand;
import com.rootrecord.minecraft.rootgamble.command.RootGambleAdminCommand;
import com.rootrecord.minecraft.rootgamble.command.RouletteCommand;
import com.rootrecord.minecraft.rootgamble.game.CoinGame;
import com.rootrecord.minecraft.rootgamble.game.DiceGame;
import com.rootrecord.minecraft.rootgamble.game.HiLoGame;
import com.rootrecord.minecraft.rootgamble.game.HiLoQuitListener;
import com.rootrecord.minecraft.rootgamble.game.LottoGame;
import com.rootrecord.minecraft.rootgamble.game.RouletteGame;
import com.rootrecord.minecraft.rootgamble.gui.GambleMenuListener;
import com.rootrecord.minecraft.rootgamble.store.GambleStore;
import com.rootrecord.minecraft.rootgamble.task.LottoMcDayTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootGamblePlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private GambleConfig config;
    private GambleStore store;
    private GambleEconomy economy;
    private GambleHelp help;
    private RouletteGame roulette;
    private HiLoGame hilo;
    private LottoGame lotto;
    private CoinGame coin;
    private DiceGame dice;
    private LottoMcDayTask lottoTask;
    private ReservePlayLimiter reserveLimiter;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_GAMBLE_CONFIG, "root-gamble.yml");
        yaml.load();
        config = new GambleConfig(yaml.config());

        String prefix = "root_";
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(this, yaml.config());
        if (db != null && db.tablePrefix() != null && !db.tablePrefix().isBlank()) {
            prefix = db.tablePrefix();
        }
        store = new GambleStore(this, prefix);
        store.initSchema(config.lottoStartingJackpot());
        reserveLimiter = new ReservePlayLimiter(this, store::ready, prefix);
        reserveLimiter.initSchema();

        economy = new GambleEconomy(this);
        help = new GambleHelp(this);
        roulette = new RouletteGame(this);
        hilo = new HiLoGame(this);
        lotto = new LottoGame(this);
        coin = new CoinGame(this);
        dice = new DiceGame(this);

        RouletteCommand rouletteCmd = new RouletteCommand(this);
        bind("roulette", rouletteCmd, rouletteCmd);
        HiLoCommand hiloCmd = new HiLoCommand(this);
        bind("hilo", hiloCmd, hiloCmd);
        bind("lotto", new LottoCommand(this), null);
        CoinCommand coinCmd = new CoinCommand(this);
        bind("coin", coinCmd, coinCmd);
        DiceCommand diceCmd = new DiceCommand(this);
        bind("dice", diceCmd, diceCmd);
        bind("gamble", new GambleCommand(this), null);
        bind("rootgamble", new RootGambleAdminCommand(this), null);

        getServer().getPluginManager().registerEvents(new GambleMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new HiLoQuitListener(this), this);

        lottoTask = new LottoMcDayTask(this);
        lottoTask.start();

        getLogger().info("Root-Gamble enabled — /gamble · roulette · hilo · lotto · coin · dice");
    }

    @Override
    public void onDisable() {
        if (lottoTask != null) {
            lottoTask.stop();
        }
    }

    public void reloadAll() {
        if (lottoTask != null) {
            lottoTask.stop();
        }
        yaml.load();
        config = new GambleConfig(yaml.config());
        lottoTask = new LottoMcDayTask(this);
        lottoTask.start();
        getLogger().info("Root-Gamble reloaded.");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec, org.bukkit.command.TabCompleter tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(exec);
        if (tab != null) {
            cmd.setTabCompleter(tab);
        }
    }

    public RootRecordYamlConfig yaml() {
        return yaml;
    }

    public GambleConfig config() {
        return config;
    }

    public GambleStore store() {
        return store;
    }

    public GambleEconomy economy() {
        return economy;
    }

    public GambleHelp help() {
        return help;
    }

    public RouletteGame roulette() {
        return roulette;
    }

    public HiLoGame hilo() {
        return hilo;
    }

    public LottoGame lotto() {
        return lotto;
    }

    /**
     * Grant one free-play credit for a game (e.g. {@code lotto}) — restores first-unused free state.
     * Used by Root-Appreciation Free Lotto Ticket rewards.
     */
    public void grantFreePlay(java.util.UUID uuid, String gameId) {
        if (store != null) {
            store.clearFreeUsed(uuid, gameId);
        }
    }

    public CoinGame coin() {
        return coin;
    }

    public DiceGame dice() {
        return dice;
    }

    /** Reserve games only (roulette, hilo, lotto) — not player coin/dice duels. */
    public boolean allowReservePlay(org.bukkit.entity.Player player) {
        ReservePlayLimiter.CheckResult check = reserveLimiter.check(player.getUniqueId(), config);
        if (check.allowed()) {
            return true;
        }
        player.sendMessage(msg("reserve-limit")
                .replace("{max}", String.valueOf(check.max()))
                .replace("{hours}", String.valueOf(config.reserveLimitWindowHours()))
                .replace("{remaining}", formatDuration(check.retryMs())));
        return false;
    }

    public void recordReservePlay(org.bukkit.entity.Player player) {
        reserveLimiter.record(player.getUniqueId(), config);
    }

    public static String formatDuration(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        if (h > 0L) {
            return h + "h " + m + "m";
        }
        if (m > 0L) {
            return m + "m";
        }
        return sec + "s";
    }

    public String rawMsg(String key) {
        return yaml.config().getString("messages." + key, key);
    }

    public String msg(String key) {
        String p = yaml.config().getString("messages.prefix", "");
        return colorize(p + rawMsg(key));
    }

    public String colorize(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }
}
