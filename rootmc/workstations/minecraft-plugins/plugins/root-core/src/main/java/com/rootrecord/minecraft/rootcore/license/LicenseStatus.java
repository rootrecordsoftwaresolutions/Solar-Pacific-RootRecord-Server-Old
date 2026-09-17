package com.rootrecord.minecraft.rootcore.license;

/** Snapshot of commercial entitlement for a Root plugin id. */
public enum LicenseStatus {
    /** Operator mode — commercial enforce disabled. */
    OPERATOR,
    /** Key verified / lease valid (future). */
    LICENSED,
    /** Enforce mode, no key yet — allow with warning this pass. */
    UNVERIFIED,
    /** Signed offline lease still within grace window (future). */
    GRACE,
    /** Entitlement expired or revoked (future hard-block). */
    DENIED
}
