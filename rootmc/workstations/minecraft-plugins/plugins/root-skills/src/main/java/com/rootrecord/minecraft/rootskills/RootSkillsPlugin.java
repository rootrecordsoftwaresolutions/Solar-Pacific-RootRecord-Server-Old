package com.rootrecord.minecraft.rootskills;

import com.rootrecord.minecraft.rootskills.api.RootSkillsAPI;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.boosters.BoosterManager;
import com.rootrecord.minecraft.rootskills.classes.ClassManager;
import com.rootrecord.minecraft.rootskills.command.ClassCommand;
import com.rootrecord.minecraft.rootskills.command.RootSkillsAdminCommand;
import com.rootrecord.minecraft.rootskills.command.SkillsCommand;
import com.rootrecord.minecraft.rootskills.command.TalentsCommand;
import com.rootrecord.minecraft.rootskills.engine.AbilitySessions;
import com.rootrecord.minecraft.rootskills.engine.ConditionEval;
import com.rootrecord.minecraft.rootskills.engine.CooldownService;
import com.rootrecord.minecraft.rootskills.engine.EffectRunner;
import com.rootrecord.minecraft.rootskills.engine.TriggerBus;
import com.rootrecord.minecraft.rootskills.gui.SkillsHubGui;
import com.rootrecord.minecraft.rootskills.gui.TalentLoadoutGui;
import com.rootrecord.minecraft.rootskills.listener.PlayerConnectionListener;
import com.rootrecord.minecraft.rootskills.mana.ManaManager;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import com.rootrecord.minecraft.rootskills.party.PartyManager;
import com.rootrecord.minecraft.rootskills.placeholders.RootSkillsExpansion;
import com.rootrecord.minecraft.rootskills.prestige.PrestigeManager;
import com.rootrecord.minecraft.rootskills.skills.SkillCatalog;
import com.rootrecord.minecraft.rootskills.skills.SkillListenerRegistrar;
import com.rootrecord.minecraft.rootskills.skills.XpFormula;
import com.rootrecord.minecraft.rootskills.skills.XpService;
import com.rootrecord.minecraft.rootskills.storage.Database;
import com.rootrecord.minecraft.rootskills.storage.McMmoMigrator;
import com.rootrecord.minecraft.rootskills.storage.SchemaManager;
import com.rootrecord.minecraft.rootskills.storage.SkillsRepository;
import com.rootrecord.minecraft.rootskills.talents.TalentManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

public final class RootSkillsPlugin extends JavaPlugin implements RootSkillsAPI {

    private static RootSkillsAPI apiInstance;

    private FileConfiguration messages;
    private Database database;
    private SchemaManager schemaManager;
    private SkillsRepository repository;
    private McMmoMigrator mcMmoMigrator;
    private XpFormula xpFormula;
    private XpService xpService;
    private SkillCatalog skillCatalog;
    private SkillListenerRegistrar listenerRegistrar;
    private TriggerBus triggerBus;
    private ConditionEval conditionEval;
    private EffectRunner effectRunner;
    private CooldownService cooldownService;
    private AbilitySessions abilitySessions;
    private TalentManager talentManager;
    private ManaManager manaManager;
    private ClassManager classManager;
    private PrestigeManager prestigeManager;
    private BoosterManager boosterManager;
    private PartyManager partyManager;
    private SkillsHubGui skillsHubGui;
    private TalentLoadoutGui talentLoadoutGui;
    private NamespacedKey talentKey;

    public static RootSkillsAPI api() {
        return apiInstance;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateProp01FormulaIfNeeded();
        migrateLocalhostMysqlToShared();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("effects.yml");
        saveResourceIfMissing("triggers.yml");
        loadMessages();
        talentKey = new NamespacedKey(this, "talent_id");

        xpFormula = loadFormula();
        String prefix = getConfig().getString("table-prefix", "root_skills_");

        database = new Database(this);
        boolean dbOk = database.open();
        schemaManager = new SchemaManager(this, prefix);
        if (dbOk) {
            schemaManager.init(database);
        } else {
            getLogger().severe("Running memory-only — profiles will not persist until MySQL is available.");
        }

        repository = new SkillsRepository(this, database, schemaManager);
        mcMmoMigrator = new McMmoMigrator(this);
        xpService = new XpService(this, repository, xpFormula);
        skillCatalog = new SkillCatalog(this);
        abilitySessions = new AbilitySessions();
        cooldownService = new CooldownService();
        conditionEval = new ConditionEval();
        effectRunner = new EffectRunner(this);
        triggerBus = new TriggerBus(this);
        talentManager = new TalentManager(this);
        manaManager = new ManaManager(this);
        classManager = new ClassManager(this);
        prestigeManager = new PrestigeManager(this);
        boosterManager = new BoosterManager(this);
        partyManager = new PartyManager(this);
        skillsHubGui = new SkillsHubGui(this);
        talentLoadoutGui = new TalentLoadoutGui(this);
        listenerRegistrar = new SkillListenerRegistrar(this);

        skillCatalog.load();
        talentManager.load();
        classManager.load();
        boosterManager.init();
        manaManager.startRegenTask();
        listenerRegistrar.registerAll();
        skillsHubGui.register();
        talentLoadoutGui.register();
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        bind("skills", new SkillsCommand(this));
        bind("talents", new TalentsCommand(this));
        bind("class", new ClassCommand(this));
        bind("rootskills", new RootSkillsAdminCommand(this));

        apiInstance = this;
        Bukkit.getScheduler().runTask(this, this::registerPlaceholderExpansionIfPresent);
        Bukkit.getScheduler().runTaskLater(this, this::registerPlaceholderExpansionIfPresent, 40L);

        getLogger().info("Root-Skills ready v" + getPluginMeta().getVersion()
                + " (mysql=" + dbOk + ", talents=" + talentManager.all().size() + ")");
    }

    @Override
    public void onDisable() {
        if (manaManager != null) {
            manaManager.stop();
        }
        if (repository != null) {
            repository.saveAllSync();
        }
        if (database != null) {
            database.close();
        }
        apiInstance = null;
        getLogger().info("Root-Skills disabled");
    }

    public void reloadLocal() {
        reloadConfig();
        migrateProp01FormulaIfNeeded();
        loadMessages();
        xpFormula = loadFormula();
        if (xpService != null) {
            xpService.setFormula(xpFormula);
        }
        if (skillCatalog != null) {
            skillCatalog.reload();
        }
        if (talentManager != null) {
            talentManager.reload();
        }
        if (classManager != null) {
            classManager.reload();
        }
        if (manaManager != null) {
            manaManager.startRegenTask();
        }
    }

    /**
     * PROP-01: old defaults (0.12 / 2.05 / 2800) felt flat 1–100 because base dominated.
     * Auto-upgrade that exact legacy triple so live configs pick up the proportional climb.
     */
    private void migrateProp01FormulaIfNeeded() {
        FileConfiguration cfg = getConfig();
        double m = cfg.getDouble("formula.multiplier", Double.NaN);
        double e = cfg.getDouble("formula.exponent", Double.NaN);
        double b = cfg.getDouble("formula.base", Double.NaN);
        if (Math.abs(m - 0.12) < 1e-9
                && Math.abs(e - 2.05) < 1e-9
                && Math.abs(b - 2800.0) < 1e-6) {
            cfg.set("formula.multiplier", 1.6);
            cfg.set("formula.exponent", 2.35);
            cfg.set("formula.base", 500);
            saveConfig();
            getLogger().info(
                    "PROP-01: migrated flat XP formula → proportional (multiplier=1.6 exponent=2.35 base=500)");
        }
    }

    /**
     * Clear jar-default localhost so Skills inherits {@code plugins/RootMC/database.yml}
     * (same Shockbyte MySQL as the rest of the suite).
     */
    private void migrateLocalhostMysqlToShared() {
        FileConfiguration cfg = getConfig();
        String host = cfg.getString("mysql.host", "");
        if (host == null) {
            return;
        }
        String h = host.trim().toLowerCase();
        if (!(h.equals("127.0.0.1") || h.equals("localhost") || h.equals("::1") || h.equals("0.0.0.0"))) {
            return;
        }
        cfg.set("mysql.host", "");
        saveConfig();
        getLogger().info(
                "MySQL: cleared localhost host — using shared plugins/RootMC/database.yml");
    }

    private XpFormula loadFormula() {
        FileConfiguration cfg = getConfig();
        XpFormula d = XpFormula.defaults();
        return new XpFormula(
                cfg.getDouble("formula.multiplier", d.multiplier()),
                cfg.getDouble("formula.exponent", d.exponent()),
                cfg.getDouble("formula.base", d.base()),
                cfg.getBoolean("formula.cumulative", d.cumulative()),
                cfg.getDouble("formula.global-xp-multiplier", d.globalXpMultiplier()));
    }

    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    private void saveResourceIfMissing(String path) {
        File out = new File(getDataFolder(), path);
        if (!out.exists()) {
            saveResource(path, false);
        }
    }

    public String msg(String path) {
        String prefix = messages != null ? messages.getString("prefix", "") : "";
        String body = messages != null ? messages.getString(path) : null;
        return colorize((prefix == null ? "" : prefix) + (body == null ? path : body));
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public void registerPlaceholderExpansionIfPresent() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new RootSkillsExpansion(this).register();
        } catch (Throwable ex) {
            getLogger().warning("PlaceholderAPI expansion register failed: " + ex.getMessage());
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) {
            cmd.setExecutor(ce);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }

    // --- RootSkillsAPI ---

    @Override
    public Optional<PlayerSkillsProfile> profile(UUID playerId) {
        return repository == null ? Optional.empty() : repository.find(playerId);
    }

    @Override
    public int getLevel(UUID playerId, SkillId skill) {
        return repository.getOrCreate(playerId).skill(skill).level();
    }

    @Override
    public long getXp(UUID playerId, SkillId skill) {
        return repository.getOrCreate(playerId).skill(skill).xp();
    }

    @Override
    public void addXp(UUID playerId, SkillId skill, long amount) {
        xpService.addXp(playerId, skill, amount);
    }

    @Override
    public void setLevel(UUID playerId, SkillId skill, int level) {
        repository.setLevel(playerId, skill, level);
    }

    @Override
    public int getPowerLevel(UUID playerId) {
        return repository.getOrCreate(playerId).powerLevel();
    }

    @Override
    public double getMana(UUID playerId) {
        return repository.getOrCreate(playerId).mana();
    }

    @Override
    public String getClassId(UUID playerId) {
        return repository.getOrCreate(playerId).classId();
    }

    @Override
    public int getPrestige(UUID playerId, SkillId skill) {
        return repository.getOrCreate(playerId).skill(skill).prestige();
    }

    @Override
    public RootSkillsPlugin plugin() {
        return this;
    }

    // --- accessors ---

    public Database database() {
        return database;
    }

    public SkillsRepository repository() {
        return repository;
    }

    public McMmoMigrator mcMmoMigrator() {
        return mcMmoMigrator;
    }

    public XpService xpService() {
        return xpService;
    }

    public SkillCatalog skillCatalog() {
        return skillCatalog;
    }

    public SchemaManager schemaManager() {
        return schemaManager;
    }

    public TriggerBus triggerBus() {
        return triggerBus;
    }

    public ConditionEval conditionEval() {
        return conditionEval;
    }

    public EffectRunner effectRunner() {
        return effectRunner;
    }

    public CooldownService cooldownService() {
        return cooldownService;
    }

    public AbilitySessions abilitySessions() {
        return abilitySessions;
    }

    public TalentManager talentManager() {
        return talentManager;
    }

    public ManaManager manaManager() {
        return manaManager;
    }

    public ClassManager classManager() {
        return classManager;
    }

    public PrestigeManager prestigeManager() {
        return prestigeManager;
    }

    public BoosterManager boosterManager() {
        return boosterManager;
    }

    public PartyManager partyManager() {
        return partyManager;
    }

    public SkillsHubGui skillsHubGui() {
        return skillsHubGui;
    }

    public TalentLoadoutGui talentLoadoutGui() {
        return talentLoadoutGui;
    }

    public SkillListenerRegistrar listenerRegistrar() {
        return listenerRegistrar;
    }

    public NamespacedKey talentKey() {
        return talentKey;
    }
}
