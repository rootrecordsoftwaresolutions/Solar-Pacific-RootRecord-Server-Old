package com.rootrecord.minecraft.rootrestart;

/** Top-level (not nested) so the class file has no {@code $} in the jar name — safer for FTP uploads. */
public enum RestartKind {
    MANUAL,
    DAILY,
    MORNING,
    STOP,
    /** Heartbeat / Root-Core jar downloads — same Paper restart-helper as midnight. */
    UPDATE
}
