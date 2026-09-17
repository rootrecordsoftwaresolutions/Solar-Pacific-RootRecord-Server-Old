package com.rootrecord.minecraft.common.host;

/**
 * Shockbyte / Pterodactyl Wings owns the Java process. Paper {@code restart()} plus
 * {@code restart-helper.sh} starting a second JVM does not bring the panel server back —
 * Wings sees the original PID exit and marks the machine stopped.
 */
public final class PanelHosts {

    private PanelHosts() {}

    public static boolean isPterodactyl() {
        return notBlank(env("P_SERVER_UUID"))
                || notBlank(env("P_SERVER_LOCATION"))
                || (notBlank(env("SERVER_JARFILE")) && notBlank(env("STARTUP")));
    }

    private static String env(String key) {
        try {
            return System.getenv(key);
        } catch (SecurityException ex) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
