package com.rootrecord.minecraft.rootmemberships;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class MembershipsConfig {

    public record MembershipRule(
            String id,
            boolean enabled,
            String group,
            boolean requireCloudPro,
            boolean requireLife,
            boolean lifeCountsAsPro,
            boolean requireTopActive,
            String displayPrefix
    ) {}

    private final boolean enabled;
    private final boolean syncOnJoin;
    private final int syncIntervalSeconds;
    private final List<MembershipRule> rules;
    private final boolean vouchersEnabled;
    private final Material voucherMonthMaterial;
    private final Material voucherLifeMaterial;
    private final String voucherMonthName;
    private final String voucherLifeName;
    private final List<String> voucherMonthLore;
    private final List<String> voucherLifeLore;
    private final String redeemSuccessMonth;
    private final String redeemSuccessLife;
    private final String redeemFail;

    public MembershipsConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        this.syncOnJoin = cfg.getBoolean("sync-on-join", true);
        this.syncIntervalSeconds = Math.max(0, cfg.getInt("sync-interval-seconds", 300));
        List<MembershipRule> parsed = new ArrayList<>();
        ConfigurationSection root = cfg.getConfigurationSection("memberships");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                parsed.add(new MembershipRule(
                        key,
                        sec.getBoolean("enabled", true),
                        sec.getString("group", key),
                        sec.getBoolean("require-cloud-pro", true),
                        sec.getBoolean("require-life", false),
                        sec.getBoolean("life-counts-as-pro", true),
                        sec.getBoolean("require-top-active", false),
                        sec.getString("display-prefix", "")
                ));
            }
        }
        if (parsed.isEmpty()) {
            parsed.add(new MembershipRule("pro", true, "pro", true, false, true, false, "&8[&bPro&8]&r "));
            parsed.add(new MembershipRule(
                    "top_active", true, "top_active", false, false, false, true, "&8[&6Top Active Player&8]&r "));
        }
        this.rules = List.copyOf(parsed);

        ConfigurationSection v = cfg.getConfigurationSection("vouchers");
        this.vouchersEnabled = v == null || v.getBoolean("enabled", true);
        Material month = Material.matchMaterial(v != null ? v.getString("month.material", "NAME_TAG") : "NAME_TAG");
        Material life = Material.matchMaterial(v != null ? v.getString("lifetime.material", "NAME_TAG") : "NAME_TAG");
        this.voucherMonthMaterial = month != null && !month.isAir() ? month : Material.NAME_TAG;
        this.voucherLifeMaterial = life != null && !life.isAir() ? life : Material.NAME_TAG;
        this.voucherMonthName = v != null ? v.getString("month.display-name", "&bPro Voucher &7(1 Month)") : "&bPro Voucher &7(1 Month)";
        this.voucherLifeName = v != null ? v.getString("lifetime.display-name", "&6Pro Voucher &7(Lifetime)") : "&6Pro Voucher &7(Lifetime)";
        this.voucherMonthLore = List.copyOf(v != null ? v.getStringList("month.lore") : List.of(
                "&7Right-click to redeem 30 days of Pro.",
                "&8Tradeable until redeemed."));
        this.voucherLifeLore = List.copyOf(v != null ? v.getStringList("lifetime.lore") : List.of(
                "&7Right-click to redeem Lifetime Pro.",
                "&8Tradeable until redeemed."));
        this.redeemSuccessMonth = v != null ? v.getString("messages.redeem-month", "&aRedeemed &b1 Month Pro&a!")
                : "&aRedeemed &b1 Month Pro&a!";
        this.redeemSuccessLife = v != null ? v.getString("messages.redeem-lifetime", "&aRedeemed &6Lifetime Pro&a!")
                : "&aRedeemed &6Lifetime Pro&a!";
        this.redeemFail = v != null ? v.getString("messages.redeem-fail", "&cCould not redeem voucher: &f{detail}")
                : "&cCould not redeem voucher: &f{detail}";
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean syncOnJoin() {
        return syncOnJoin;
    }

    public int syncIntervalSeconds() {
        return syncIntervalSeconds;
    }

    public List<MembershipRule> rules() {
        return rules;
    }

    public boolean vouchersEnabled() {
        return vouchersEnabled;
    }

    public Material voucherMonthMaterial() {
        return voucherMonthMaterial;
    }

    public Material voucherLifeMaterial() {
        return voucherLifeMaterial;
    }

    public String voucherMonthName() {
        return voucherMonthName;
    }

    public String voucherLifeName() {
        return voucherLifeName;
    }

    public List<String> voucherMonthLore() {
        return voucherMonthLore;
    }

    public List<String> voucherLifeLore() {
        return voucherLifeLore;
    }

    public String redeemSuccessMonth() {
        return redeemSuccessMonth;
    }

    public String redeemSuccessLife() {
        return redeemSuccessLife;
    }

    public String redeemFail() {
        return redeemFail;
    }
}
