package com.rootrecord.minecraft.rootgamble.game;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** European roulette 0–36. */
public final class RouletteWheel {

    private static final Set<Integer> RED = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private RouletteWheel() {}

    public static int spin() {
        return ThreadLocalRandom.current().nextInt(0, 37);
    }

    public static boolean isRed(int n) {
        return RED.contains(n);
    }

    public static boolean isBlack(int n) {
        return n > 0 && !RED.contains(n);
    }

    public static boolean isGreen(int n) {
        return n == 0;
    }

    public enum BetKind {
        RED,
        BLACK,
        GREEN,
        STRAIGHT
    }

    public record Bet(BetKind kind, int straightNumber) {
        public static Bet parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String s = raw.trim().toLowerCase();
            return switch (s) {
                case "red", "r" -> new Bet(BetKind.RED, -1);
                case "black", "b" -> new Bet(BetKind.BLACK, -1);
                case "green", "g", "0" -> "0".equals(s)
                        ? new Bet(BetKind.STRAIGHT, 0)
                        : new Bet(BetKind.GREEN, -1);
                default -> {
                    try {
                        int n = Integer.parseInt(s);
                        if (n < 0 || n > 36) {
                            yield null;
                        }
                        yield new Bet(BetKind.STRAIGHT, n);
                    } catch (NumberFormatException ex) {
                        yield null;
                    }
                }
            };
        }

        public boolean wins(int spun) {
            return switch (kind) {
                case RED -> isRed(spun);
                case BLACK -> isBlack(spun);
                case GREEN -> isGreen(spun);
                case STRAIGHT -> spun == straightNumber;
            };
        }

        public boolean isEvenMoney() {
            return kind == BetKind.RED || kind == BetKind.BLACK;
        }

        public String label() {
            return switch (kind) {
                case RED -> "red";
                case BLACK -> "black";
                case GREEN -> "green";
                case STRAIGHT -> String.valueOf(straightNumber);
            };
        }
    }
}
