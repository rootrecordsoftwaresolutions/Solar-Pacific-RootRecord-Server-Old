package com.rootrecord.minecraft.rootgamble.game;

import java.util.concurrent.ThreadLocalRandom;

/** Single playing card for HiLo (rank 2–14, Ace high). */
public record PlayingCard(int rank, Suit suit) {

    public enum Suit {
        HEARTS("♥", "&c"),
        DIAMONDS("♦", "&c"),
        CLUBS("♣", "&8"),
        SPADES("♠", "&8");

        private final String glyph;
        private final String color;

        Suit(String glyph, String color) {
            this.glyph = glyph;
            this.color = color;
        }

        public String glyph() {
            return glyph;
        }

        public String color() {
            return color;
        }
    }

    public static PlayingCard draw() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new PlayingCard(r.nextInt(2, 15), Suit.values()[r.nextInt(Suit.values().length)]);
    }

    public String rankLabel() {
        return switch (rank) {
            case 14 -> "A";
            case 13 -> "K";
            case 12 -> "Q";
            case 11 -> "J";
            default -> String.valueOf(rank);
        };
    }

    /** e.g. &cA♥ with legacy color codes for GambleHelp / HiLoGame. */
    public String legacyDisplay() {
        return suit.color() + rankLabel() + suit.glyph();
    }

    public int compareRank(PlayingCard other) {
        return Integer.compare(rank, other.rank);
    }
}
