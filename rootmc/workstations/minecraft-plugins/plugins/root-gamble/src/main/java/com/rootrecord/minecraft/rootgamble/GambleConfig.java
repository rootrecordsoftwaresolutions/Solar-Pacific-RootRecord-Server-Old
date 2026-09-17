package com.rootrecord.minecraft.rootgamble;

import org.bukkit.configuration.file.FileConfiguration;

/** Tunables from root-gamble.yml. */
public final class GambleConfig {

    private final boolean enabled;
    private final String currency;
    private final int challengeTimeoutSeconds;
    private final double broadcastWinsOver;

    private final double rouletteMin;
    private final double rouletteMax;
    private final double roulettePayoutEven;
    private final double roulettePayoutStraight;

    private final double hiloMin;
    private final double hiloMax;
    private final double hiloPayout;
    private final boolean hiloFairRoll;
    private final double hiloPlayerWinChance;

    private final double coinMin;
    private final double coinMax;
    private final double coinChallengerWinChance;

    private final double diceMin;
    private final double diceMax;
    private final int diceSides;

    private final int lottoMin;
    private final int lottoMax;
    private final double lottoTicketPrice;
    private final double lottoStartingJackpot;
    private final int lottoMaxTicketsPerPlayer;
    private final boolean lottoFreeAddsToPot;
    private final long lottoPollSeconds;

    private final String guiTitle;

    private final boolean reserveLimitEnabled;
    private final int reserveLimitMaxPlays;
    private final int reserveLimitWindowHours;

    public GambleConfig(FileConfiguration cfg) {
        enabled = cfg.getBoolean("enabled", true);
        currency = cfg.getString("currency", "G");
        challengeTimeoutSeconds = Math.max(10, cfg.getInt("challenge-timeout-seconds", 60));
        broadcastWinsOver = Math.max(0, cfg.getDouble("broadcast-wins-over", 100.0));

        rouletteMin = Math.max(0.01, cfg.getDouble("roulette.min-stake", 1.0));
        rouletteMax = Math.max(rouletteMin, cfg.getDouble("roulette.max-stake", 10000.0));
        roulettePayoutEven = Math.max(1.0, cfg.getDouble("roulette.payout-even", 2.0));
        roulettePayoutStraight = Math.max(1.0, cfg.getDouble("roulette.payout-straight", 36.0));

        hiloMin = Math.max(0.01, cfg.getDouble("hilo.min-stake", 1.0));
        hiloMax = Math.max(hiloMin, cfg.getDouble("hilo.max-stake", 10000.0));
        hiloPayout = Math.max(1.0, cfg.getDouble("hilo.payout", 2.0));
        hiloFairRoll = cfg.getBoolean("hilo.use-fair-roll", true);
        hiloPlayerWinChance = clamp01(cfg.getDouble("hilo.player-win-chance", 0.49));

        coinMin = Math.max(0.01, cfg.getDouble("coin.min-stake", 1.0));
        coinMax = Math.max(coinMin, cfg.getDouble("coin.max-stake", 10000.0));
        coinChallengerWinChance = clamp01(cfg.getDouble("coin.challenger-win-chance", 0.5));

        diceMin = Math.max(0.01, cfg.getDouble("dice.min-stake", 1.0));
        diceMax = Math.max(diceMin, cfg.getDouble("dice.max-stake", 10000.0));
        diceSides = Math.max(2, cfg.getInt("dice.sides", 6));

        lottoMin = Math.max(1, cfg.getInt("lotto.min-number", 1));
        lottoMax = Math.max(lottoMin, cfg.getInt("lotto.max-number", 500));
        lottoTicketPrice = Math.max(0.01, cfg.getDouble("lotto.ticket-price", 25.0));
        lottoStartingJackpot = Math.max(0, cfg.getDouble("lotto.starting-jackpot", 1000.0));
        lottoMaxTicketsPerPlayer = Math.max(1, cfg.getInt("lotto.max-tickets-per-player", 50));
        lottoFreeAddsToPot = cfg.getBoolean("lotto.free-ticket-adds-to-pot", false);
        lottoPollSeconds = Math.max(5L, cfg.getLong("lotto.poll-seconds", 30L));

        guiTitle = cfg.getString("gui.title", "RootMC Gamble");

        reserveLimitEnabled = cfg.getBoolean("reserve-limit.enabled", true);
        reserveLimitMaxPlays = Math.max(1, cfg.getInt("reserve-limit.max-plays", 12));
        reserveLimitWindowHours = Math.max(1, cfg.getInt("reserve-limit.window-hours", 12));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    public boolean enabled() {
        return enabled;
    }

    public String currency() {
        return currency;
    }

    public int challengeTimeoutSeconds() {
        return challengeTimeoutSeconds;
    }

    public double broadcastWinsOver() {
        return broadcastWinsOver;
    }

    public double rouletteMin() {
        return rouletteMin;
    }

    public double rouletteMax() {
        return rouletteMax;
    }

    public double roulettePayoutEven() {
        return roulettePayoutEven;
    }

    public double roulettePayoutStraight() {
        return roulettePayoutStraight;
    }

    public double hiloMin() {
        return hiloMin;
    }

    public double hiloMax() {
        return hiloMax;
    }

    public double hiloPayout() {
        return hiloPayout;
    }

    public boolean hiloFairRoll() {
        return hiloFairRoll;
    }

    public double hiloPlayerWinChance() {
        return hiloPlayerWinChance;
    }

    public double coinMin() {
        return coinMin;
    }

    public double coinMax() {
        return coinMax;
    }

    public double coinChallengerWinChance() {
        return coinChallengerWinChance;
    }

    public double diceMin() {
        return diceMin;
    }

    public double diceMax() {
        return diceMax;
    }

    public int diceSides() {
        return diceSides;
    }

    public int lottoMin() {
        return lottoMin;
    }

    public int lottoMax() {
        return lottoMax;
    }

    public double lottoTicketPrice() {
        return lottoTicketPrice;
    }

    public double lottoStartingJackpot() {
        return lottoStartingJackpot;
    }

    public int lottoMaxTicketsPerPlayer() {
        return lottoMaxTicketsPerPlayer;
    }

    public boolean lottoFreeAddsToPot() {
        return lottoFreeAddsToPot;
    }

    public long lottoPollSeconds() {
        return lottoPollSeconds;
    }

    public String guiTitle() {
        return guiTitle;
    }

    public boolean reserveLimitEnabled() {
        return reserveLimitEnabled;
    }

    public int reserveLimitMaxPlays() {
        return reserveLimitMaxPlays;
    }

    public int reserveLimitWindowHours() {
        return reserveLimitWindowHours;
    }
}
