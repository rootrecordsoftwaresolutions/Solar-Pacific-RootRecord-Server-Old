package com.rootrecord.minecraft.roottry;

import org.bukkit.configuration.file.FileConfiguration;

public final class TryConfig {

    private final boolean enabled;
    private final String hostOverride;
    private final double rewardG;
    private final boolean reminderEnabled;
    private final int reminderIntervalSeconds;
    private final String prefix;
    private final String msgCompleted;
    private final String msgAlready;
    private final String msgNext;
    private final String msgProgress;
    private final String msgNoneLeft;
    private final String msgDisabled;
    private final String msgNoTreasury;
    private final String msgAdminSkipped;
    private final String msgAdminForced;
    private final String msgIncompleteStatus;

    public TryConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        this.hostOverride = str(cfg.getString("host"), "");
        this.rewardG = Math.max(0, cfg.getDouble("reward-g", 1.0));
        this.reminderEnabled = cfg.getBoolean("reminder.enabled", true);
        this.reminderIntervalSeconds = Math.max(30, cfg.getInt("reminder.interval-seconds", 180));
        this.prefix = str(cfg.getString("messages.prefix"), "");
        this.msgCompleted = str(cfg.getString("messages.completed"), "&aTry complete: &f{title}&a — &f+{gold} G");
        this.msgAlready = str(cfg.getString("messages.already"), "&7Already completed: &f{title}");
        this.msgNext = str(cfg.getString("messages.next"), "&eNext try: &f{title}&7 — {hint}");
        this.msgProgress = str(cfg.getString("messages.progress"), "&7Progress: &f{done}&7/&f{total}");
        this.msgNoneLeft = str(cfg.getString("messages.none-left"), "&aYou've finished every try on this host.");
        this.msgDisabled = str(cfg.getString("messages.disabled"), "&cRoot-Try is disabled.");
        this.msgNoTreasury = str(cfg.getString("messages.no-treasury"), "&cCould not pay reward (treasury unavailable).");
        this.msgAdminSkipped = str(cfg.getString("messages.admin-skipped"), "&7Skipped try &f{id}");
        this.msgAdminForced = str(cfg.getString("messages.admin-forced"), "&7Force-completed &f{id}");
        this.msgIncompleteStatus = str(
                cfg.getString("messages.incomplete-status"),
                "Test this command for {gold}g");
    }

    private static String str(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    public boolean enabled() {
        return enabled;
    }

    public String hostOverride() {
        return hostOverride;
    }

    public double rewardG() {
        return rewardG;
    }

    public boolean reminderEnabled() {
        return reminderEnabled;
    }

    public int reminderIntervalSeconds() {
        return reminderIntervalSeconds;
    }

    public String prefix() {
        return prefix;
    }

    public String msgCompleted() {
        return msgCompleted;
    }

    public String msgAlready() {
        return msgAlready;
    }

    public String msgNext() {
        return msgNext;
    }

    public String msgProgress() {
        return msgProgress;
    }

    public String msgNoneLeft() {
        return msgNoneLeft;
    }

    public String msgDisabled() {
        return msgDisabled;
    }

    public String msgNoTreasury() {
        return msgNoTreasury;
    }

    public String msgAdminSkipped() {
        return msgAdminSkipped;
    }

    public String msgAdminForced() {
        return msgAdminForced;
    }

    public String msgIncompleteStatus() {
        return msgIncompleteStatus;
    }
}
