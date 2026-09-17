package com.rootrecord.minecraft.roothaste;

/** Active minigame skin — torch (default / all-ages) or joint (18+ when unlocked). */
public enum PassTheme {
    TORCH,
    JOINT;

    public boolean isJoint() {
        return this == JOINT;
    }

    public String recordsFile() {
        return this == JOINT ? "root-haste-records.yml" : "root-torch-records.yml";
    }

    public String defaultChatTag() {
        return this == JOINT ? "&a[J]&r " : "&6[T]&r ";
    }

    public String defaultTreasuryChannel() {
        return this == JOINT ? "service-fee:joint" : "service-fee:torch";
    }

    public String discordKind() {
        return this == JOINT ? "joint" : "torch";
    }

    public String lightCommand() {
        return this == JOINT ? "joint" : "torch";
    }

    public String passCommand() {
        return this == JOINT ? "pass" : "torchpass";
    }

    /** Map logical message keys used in code to theme-specific yml keys. */
    public String messageKey(String logical) {
        if (logical == null) {
            return "";
        }
        return switch (logical) {
            case "light" -> this == JOINT ? "sparked" : "lit";
            case "light-fee" -> this == JOINT ? "sparked-fee" : "lit-fee";
            case "milk" -> this == JOINT ? "milk-dropped" : "milk-spilt";
            default -> logical;
        };
    }

    public String messagesPath() {
        return this == JOINT ? "messages.joint" : "messages.torch";
    }
}
