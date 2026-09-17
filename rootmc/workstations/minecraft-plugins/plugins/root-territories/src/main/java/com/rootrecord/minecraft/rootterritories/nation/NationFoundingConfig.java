package com.rootrecord.minecraft.rootterritories.nation;

import org.bukkit.configuration.file.FileConfiguration;

public final class NationFoundingConfig {

    private final boolean enabled;
    private final boolean requireProposal;
    private final int minTowns;
    private final double totalCostGold;
    private final boolean snapshotGrandfatherOnEnable;
    private final String msgProposed;
    private final String msgAgreed;
    private final String msgApproved;
    private final String msgDeniedNoProposal;
    private final String msgDeniedPayment;
    private final String msgNotMayor;
    private final String msgAlreadyInNation;
    private final String msgNoTouch;
    private final String msgExecuted;

    public NationFoundingConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("nation-founding.enabled", true);
        this.requireProposal = cfg.getBoolean("nation-founding.require-proposal", true);
        this.minTowns = Math.max(2, cfg.getInt("nation-founding.min-towns", 2));
        this.totalCostGold = Math.max(0, cfg.getDouble("nation-founding.total-cost-g", 2000.0));
        this.snapshotGrandfatherOnEnable = cfg.getBoolean("nation-founding.snapshot-grandfather-on-enable", true);
        this.msgProposed = cfg.getString(
                "nation-founding.messages.proposed",
                "&aNation founding proposal &f{id} &acreated for &f{nation}&a. Partner mayors: &f/agree {id}");
        this.msgAgreed = cfg.getString(
                "nation-founding.messages.agreed",
                "&aYou signed nation founding proposal &f{id}&a. (&f{signed}&7/&f{total}&a towns)");
        this.msgApproved = cfg.getString(
                "nation-founding.messages.approved",
                "&aProposal &f{id} &ais fully signed. Capital mayor may run &f/nation new {nation}&a.");
        this.msgDeniedNoProposal = cfg.getString(
                "nation-founding.messages.denied-no-proposal",
                "&cNation creation requires an approved founding proposal. Capital mayor: &f/nationfounding propose");
        this.msgDeniedPayment = cfg.getString(
                "nation-founding.messages.denied-payment",
                "&cNation founding payment failed for &f{town}&c (&f{amount} G&c). Ensure the town bank has enough Gold.");
        this.msgNotMayor = cfg.getString(
                "nation-founding.messages.not-mayor",
                "&cOnly the mayor of &f{town} &ccan sign for that town.");
        this.msgAlreadyInNation = cfg.getString(
                "nation-founding.messages.already-in-nation",
                "&cTown &f{town} &cis already in a nation.");
        this.msgNoTouch = cfg.getString(
                "nation-founding.messages.no-touch",
                "&cTown &f{town} &cmust touch or overlap &f{capital}&c's influence before joining the proposal.");
        this.msgExecuted = cfg.getString(
                "nation-founding.messages.executed",
                "&aNation &f{nation} &afounded. &f{total} G &asplit across &f{towns} &atown banks (closed loop — tax only hits reserve ledger).");
    }

    public boolean enabled() { return enabled; }
    public boolean requireProposal() { return requireProposal; }
    public int minTowns() { return minTowns; }
    public double totalCostGold() { return totalCostGold; }
    public boolean snapshotGrandfatherOnEnable() { return snapshotGrandfatherOnEnable; }
    public String msgProposed() { return msgProposed; }
    public String msgAgreed() { return msgAgreed; }
    public String msgApproved() { return msgApproved; }
    public String msgDeniedNoProposal() { return msgDeniedNoProposal; }
    public String msgDeniedPayment() { return msgDeniedPayment; }
    public String msgNotMayor() { return msgNotMayor; }
    public String msgAlreadyInNation() { return msgAlreadyInNation; }
    public String msgNoTouch() { return msgNoTouch; }
    public String msgExecuted() { return msgExecuted; }
}
