package com.rootrecord.minecraft.rootessentials.chat;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.service.GoldProspectService;
import com.rootrecord.minecraft.rootessentials.service.GoldSiteRates;
import com.rootrecord.minecraft.rootessentials.service.SolarMiningMultiplierService;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Live solar bonus + site rates for /gold and /tax. Mint is peg + reserve tax. */
public final class HostPowerInfo {

    private HostPowerInfo() {}

    public static void sendLive(RootEconomyPlugin plugin, CommandSender sender) {
        var solar = plugin.solarMining();
        SolarMiningMultiplierService.Snapshot snap = solar != null ? solar.current() : null;
        if (snap == null) {
            ChatUi.entry(sender, "Host", "solar feed offline · 1.00x · +1% env", "alert");
            return;
        }
        String bank =
                snap.batteryPercent() != null && Double.isFinite(snap.batteryPercent())
                        ? Math.round(snap.batteryPercent()) + "%"
                        : "?";
        String watts =
                snap.solarWatts() != null && Double.isFinite(snap.solarWatts())
                        ? Math.round(snap.solarWatts()) + "W"
                        : "?W";
        ChatUi.entry(
                sender,
                "Gold / skills",
                String.format(Locale.US, "%.3fx", snap.multiplier()),
                snap.online() && snap.multiplier() > 1.0000001d ? "ok" : "alert");
        ChatUi.row(sender, "Bank / solar", bank + " · " + watts);
        if (!snap.online()) {
            ChatUi.entry(sender, "Env tax", "+1% offline", "alert");
        } else {
            ChatUi.entry(sender, "Env tax", "none · connected", "ok");
            double bonusPct = Math.max(0, (snap.multiplier() - 1.0d) * 100.0d);
            ChatUi.row(sender, "Bonus", String.format(Locale.US,
                    "+%.0f%% · 1%% connected + 1%% / 10%% bank + 1%% / 100W", bonusPct));
        }
    }

    public static void sendSiteRates(RootEconomyPlugin plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatUi.section(sender, "Site taxes");
            ChatUi.row(sender, "Env Y", "-20 to 20 → +1% mine tax");
            ChatUi.row(sender, "Mesa", "badlands → 50% mine tax");
            ChatUi.row(sender, "Nether", "0% mine tax");
            ChatUi.row(sender, "Deepslate", "1.5× found valuation");
            ChatUi.tip(sender, "/radar · 4-block compass tape · 1G/min");
            return;
        }
        GoldProspectService prospect = plugin.goldProspect();
        GoldProspectService.Hit hit = prospect != null ? prospect.detect(player) : null;
        GoldSiteRates.Site site = hit != null
                ? hit.site()
                : (prospect != null
                        ? prospect.ratesHere(player)
                        : GoldSiteRates.at(player.getLocation(), null, 1.0d));
        ChatUi.section(sender, "Here");
        ChatUi.entry(
                sender,
                "Mine yield",
                String.format(Locale.US, "%.3fx", site.dropMultiplier()),
                site.hasLocationTax() ? "alert" : "ok");
        if (hit != null) {
            ChatUi.entry(
                    sender,
                    "Ore",
                    hit.blocks() + "m " + hit.bearing() + " · " + GoldSiteRates.oreLabel(hit.ore().getType()),
                    "ok");
        } else {
            ChatUi.row(sender, "Ore", "none within 4 blocks · /radar");
        }
        if (site.deepslate()) {
            ChatUi.entry(sender, "Deepslate", "1.5× valuation", "ok");
        }
        if (site.hasLocationTax()) {
            ChatUi.entry(sender, "Site tax", GoldSiteRates.taxSummary(site), "alert");
        } else {
            ChatUi.entry(sender, "Site tax", "none", "ok");
        }
        ChatUi.row(sender, "Y / biome", "Y " + site.y() + (site.mesa() ? " · mesa" : "") + (site.nether() ? " · nether" : ""));
    }

    public static void sendGoldRules(CommandSender sender) {
        ChatUi.section(sender, "Solar bonus");
        ChatUi.row(sender, "Offline", "1.00x gold + skills · +1% env tax");
        ChatUi.row(sender, "Connected", "+1% base + 1% per 10% bank + 1% per 100W");
        ChatUi.row(sender, "Example", "50% bank + 500W → 1.11x gold and skills");
        ChatUi.section(sender, "Site taxes");
        ChatUi.row(sender, "Env Y", "-20 to 20 → +1% (environmental)");
        ChatUi.row(sender, "Mesa", "badlands / mesa → 50%");
        ChatUi.row(sender, "Nether", "0%");
        ChatUi.row(sender, "Deepslate", "1.5× found valuation");
        ChatUi.tip(sender, "/radar · 4-block compass tape · 1G/min. Gold ore → Notes + mint backing.");
    }

    public static void sendTaxRules(CommandSender sender) {
        ChatUi.section(sender, "Transaction tax");
        ChatUi.row(sender, "Floor", "0.05% on every transfer · always");
        ChatUi.row(sender, "Reserve", "tier on backing − notes · stacked on floor");
        ChatUi.row(sender, "Offline", "+1% env when solar disconnected");
        ChatUi.row(sender, "Applies", "/pay · shops · /mint · gold ore Notes");
        ChatUi.tip(sender, "No CPU / underpowered solar tax. Site taxes are mine-only · /gold");
    }

    public static void sendMintRules(CommandSender sender) {
        ChatUi.section(sender, "Mint");
        ChatUi.row(sender, "hand / all", "nugget/ingot/block → Notes · smelt raw first");
        ChatUi.row(sender, "gold", "Notes → nugget/ingot/block at peg");
        ChatUi.row(sender, "Tax", "reserve + 0.05% min · +1% if solar offline");
        ChatUi.tip(sender, "Ore mining is /gold. Mint is peg conversion.");
    }

    public static void sendLinks(CommandSender sender) {
        ChatUi.links(
                sender,
                "Economy", RootMcEconomyWeb.economy(),
                "Guide", "https://rootmc.net/wiki/economy/#host-power");
    }
}
