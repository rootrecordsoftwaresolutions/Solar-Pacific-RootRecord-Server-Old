package com.rootrecord.minecraft.rootroad;

/** Ordered Root-Road tutorial steps. Incomplete quits reset to WELCOME. */
public enum RoadStep {
    WELCOME,
    WILD_DANGER,
    RTP,
    LOAN,
    CLAIM,
    COMPLETE;

    public RoadStep next() {
        return switch (this) {
            case WELCOME -> WILD_DANGER;
            case WILD_DANGER -> RTP;
            case RTP -> LOAN;
            case LOAN -> CLAIM;
            case CLAIM, COMPLETE -> COMPLETE;
        };
    }

    public boolean isComplete() {
        return this == COMPLETE;
    }

    public boolean isActive() {
        return this != COMPLETE;
    }
}
