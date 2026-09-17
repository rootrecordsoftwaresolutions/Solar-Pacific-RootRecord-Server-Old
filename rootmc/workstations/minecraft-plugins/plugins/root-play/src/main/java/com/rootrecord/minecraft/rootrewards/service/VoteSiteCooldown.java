package com.rootrecord.minecraft.rootrewards.service;

import com.rootrecord.minecraft.rootrewards.config.RewardsConfig.VoteLink;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Computes when a player can vote again on a listing site. */
public final class VoteSiteCooldown {

    private VoteSiteCooldown() {}

    public static Instant nextEligibleAt(VoteLink link, Instant lastVote) {
        if (lastVote == null) {
            return Instant.EPOCH;
        }
        if (link.resetMode() == VoteLink.ResetMode.DAILY) {
            return nextDailyReset(lastVote, link.dailyAt(), link.timezone());
        }
        return lastVote.plus(Duration.ofHours(Math.max(1, link.rollingHours())));
    }

    public static boolean available(VoteLink link, Instant lastVote, Instant now) {
        if (lastVote == null) {
            return true;
        }
        Instant next = nextEligibleAt(link, lastVote);
        return !now.isBefore(next);
    }

    public static String formatRemaining(Duration remaining) {
        if (remaining == null || remaining.isNegative() || remaining.isZero()) {
            return "now";
        }
        long totalSeconds = remaining.getSeconds();
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return seconds + "s";
    }

    private static Instant nextDailyReset(Instant lastVote, LocalTime at, ZoneId zone) {
        ZonedDateTime voted = lastVote.atZone(zone);
        ZonedDateTime periodStart = voted.toLocalDate().atTime(at).atZone(zone);
        if (!voted.isBefore(periodStart)) {
            return periodStart.plusDays(1).toInstant();
        }
        return periodStart.toInstant();
    }
}
