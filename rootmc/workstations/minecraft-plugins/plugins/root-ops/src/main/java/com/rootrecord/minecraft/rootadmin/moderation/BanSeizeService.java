package com.rootrecord.minecraft.rootadmin.moderation;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import com.rootrecord.minecraft.rootclaims.ClaimService;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopSellService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Ban confiscation: liquidate inventory, seize wallet/bonds/claim banks to Server Reserve,
 * remove from town/claim, and clear remaining items.
 */
public final class BanSeizeService {

    private BanSeizeService() {}

    public record Report(
            double soldG,
            double mintPegG,
            double walletG,
            double bondCouponsG,
            int bondNotes,
            double claimBanksG,
            int claimsRemoved,
            double townNationG,
            boolean removedFromTown) {

        public double totalReserveG() {
            // soldG already lands in wallet before wallet seize
            return GoldMoney.round(mintPegG + walletG + bondCouponsG + claimBanksG + townNationG);
        }

        public String summary() {
            return String.format(
                    LocaleSafe.EN,
                    "sold=%.3f mint=%.3f wallet=%.3f bonds=%d(c=%.3f) claims=%d(bank=%.3f) town=%.3f removedTown=%s total=%.3f G",
                    soldG,
                    mintPegG,
                    walletG,
                    bondNotes,
                    bondCouponsG,
                    claimsRemoved,
                    claimBanksG,
                    townNationG,
                    removedFromTown,
                    totalReserveG());
        }
    }

    private static final class LocaleSafe {
        static final java.util.Locale EN = java.util.Locale.US;
    }

    public static Report seizeAll(JavaPlugin host, OfflinePlayer target) {
        if (host == null || target == null || target.getUniqueId() == null) {
            return new Report(0, 0, 0, 0, 0, 0, 0, 0, false);
        }
        UUID uuid = target.getUniqueId();
        String name = target.getName() == null ? uuid.toString().substring(0, 8) : target.getName();
        Logger log = host.getLogger();

        double sold = 0;
        double mintPeg = 0;
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            sold = liquidateInventory(online, log);
            mintPeg = seizeMintPegGold(online, uuid, name, log);
            clearRemainingItems(online);
        }

        double wallet = seizeWallet(uuid, name, log);
        BondSeize bonds = seizeBonds(uuid, name, log);
        ClaimSeize claims = seizeClaims(uuid, name, log);
        TownSeize town = seizeTowny(uuid, name, log);

        Report report = new Report(
                sold,
                mintPeg,
                wallet,
                bonds.couponsG,
                bonds.notes,
                claims.banksG,
                claims.removed,
                town.banksG,
                town.removed);
        log.info("Ban seize for " + name + ": " + report.summary());
        return report;
    }

    private static double liquidateInventory(Player player, Logger log) {
        Plugin shopsPlugin = Bukkit.getPluginManager().getPlugin("Root-ChestShops");
        if (!(shopsPlugin instanceof RootMcShopsPlugin shops) || !shops.isEnabled()) {
            return 0;
        }
        try {
            return ShopSellService.liquidateForBan(player, shops, shops.economy());
        } catch (Exception ex) {
            log.warning("Ban inventory sell failed for " + player.getName() + ": " + ex.getMessage());
            return 0;
        }
    }

    private static double seizeMintPegGold(Player player, UUID uuid, String name, Logger log) {
        double peg = 0;
        ItemStack[] storage = player.getInventory().getStorageContents();
        peg += stripMintGold(storage);
        player.getInventory().setStorageContents(storage);

        ItemStack[] armor = player.getInventory().getArmorContents();
        peg += stripMintGold(armor);
        player.getInventory().setArmorContents(armor);

        ItemStack off = player.getInventory().getItemInOffHand();
        peg += mintPegValue(off);
        if (off != null && isMintGold(off.getType())) {
            player.getInventory().setItemInOffHand(null);
        }

        ItemStack[] ender = player.getEnderChest().getContents();
        peg += stripMintGold(ender);
        player.getEnderChest().setContents(ender);

        peg = GoldMoney.round(peg);
        if (peg < GoldMoney.MIN_AMOUNT) {
            return 0;
        }
        Plugin ess = Bukkit.getPluginManager().getPlugin("Root-Essentials");
        RootMcTreasuryService treasury = ess != null ? RootMcTreasuryResolver.resolve(ess) : null;
        if (treasury == null) {
            Plugin eco = Bukkit.getPluginManager().getPlugin("Root-Economy");
            treasury = eco != null ? RootMcTreasuryResolver.resolve(eco) : null;
        }
        if (treasury == null) {
            log.warning("Ban mint-peg seize: no treasury for " + name);
            return 0;
        }
        try {
            treasury.creditTreasury(peg, TreasuryLedgerType.OTHER, uuid, name, "ban-seize:mint-peg");
            return peg;
        } catch (Exception ex) {
            log.warning("Ban mint-peg credit failed: " + ex.getMessage());
            return 0;
        }
    }

    private static double stripMintGold(ItemStack[] contents) {
        if (contents == null) {
            return 0;
        }
        double peg = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            peg += mintPegValue(stack);
            if (stack != null && isMintGold(stack.getType())) {
                contents[i] = null;
            }
        }
        return peg;
    }

    private static boolean isMintGold(Material type) {
        return type == Material.GOLD_BLOCK || type == Material.GOLD_INGOT || type == Material.GOLD_NUGGET;
    }

    private static double mintPegValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        int amount = stack.getAmount();
        return switch (stack.getType()) {
            case GOLD_BLOCK -> 9.0 * amount;
            case GOLD_INGOT -> 1.0 * amount;
            case GOLD_NUGGET -> amount / 9.0;
            default -> 0;
        };
    }

    private static void clearRemainingItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.getEnderChest().clear();
        player.setItemOnCursor(null);
    }

    private static double seizeWallet(UUID uuid, String name, Logger log) {
        Plugin ecoPlugin = Bukkit.getPluginManager().getPlugin("Root-Economy");
        if (!(ecoPlugin instanceof RootEconomyPlugin economy) || !economy.isEnabled()) {
            return 0;
        }
        try {
            return economy.seizeWalletToReserve(uuid, name);
        } catch (Exception ex) {
            log.warning("Ban wallet seize failed for " + name + ": " + ex.getMessage());
            return 0;
        }
    }

    private record BondSeize(int notes, double couponsG) {}

    private static BondSeize seizeBonds(UUID uuid, String name, Logger log) {
        Plugin ecoPlugin = Bukkit.getPluginManager().getPlugin("Root-Economy");
        if (!(ecoPlugin instanceof RootEconomyPlugin economy) || !economy.isEnabled()) {
            return new BondSeize(0, 0);
        }
        try {
            var bonds = economy.bondsFeature();
            if (bonds == null || bonds.bonds() == null) {
                return new BondSeize(0, 0);
            }
            var result = bonds.bonds().seizeAllToReserve(uuid, name);
            return new BondSeize(result.notesVoided(), result.couponsForfeitedG());
        } catch (Exception ex) {
            log.warning("Ban bond seize failed for " + name + ": " + ex.getMessage());
            return new BondSeize(0, 0);
        }
    }

    private record ClaimSeize(int removed, double banksG) {}

    private static ClaimSeize seizeClaims(UUID uuid, String name, Logger log) {
        Plugin claimsPlugin = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (!(claimsPlugin instanceof RootClaimsPlugin claims) || !claims.isEnabled()) {
            return new ClaimSeize(0, 0);
        }
        try {
            ClaimService.SeizeResult result = claims.claims().seizeOwnedClaimsForBan(uuid, name);
            return new ClaimSeize(result.claimsRemoved(), result.banksToReserveG());
        } catch (Exception ex) {
            log.warning("Ban claim seize failed for " + name + ": " + ex.getMessage());
            return new ClaimSeize(0, 0);
        }
    }

    private record TownSeize(boolean removed, double banksG) {}

    private static TownSeize seizeTowny(UUID uuid, String name, Logger log) {
        if (Bukkit.getPluginManager().getPlugin("Towny") == null) {
            return new TownSeize(false, 0);
        }
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object resident = invoke(api, "getResident", new Class<?>[]{UUID.class}, uuid);
            if (resident == null) {
                return new TownSeize(false, 0);
            }
            Object town = invokeNoArg(resident, "getTownOrNull", "getTown");
            if (town == null) {
                return new TownSeize(false, 0);
            }
            double seized = 0;
            Object mayor = invokeNoArg(town, "getMayor");
            boolean isMayor = mayor != null && mayor.equals(resident);
            if (isMayor) {
                seized += withdrawTownyAccountToReserve(town, uuid, name, "ban-seize:town-bank", log);
                Object nation = invokeNoArg(town, "getNationOrNull", "getNation");
                if (nation != null) {
                    Object king = invokeNoArg(nation, "getKing");
                    if (king != null && king.equals(resident)) {
                        seized += withdrawTownyAccountToReserve(nation, uuid, name, "ban-seize:nation-bank", log);
                    }
                }
            }
            boolean removed = false;
            try {
                Method remove = resident.getClass().getMethod("removeTown");
                remove.invoke(resident);
                removed = true;
            } catch (NoSuchMethodException ignored) {
                Object result = invoke(resident, "removeTown", new Class<?>[]{boolean.class}, true);
                removed = result != null || true;
            }
            return new TownSeize(removed, GoldMoney.round(seized));
        } catch (Throwable ex) {
            log.warning("Ban Towny seize failed for " + name + ": " + ex.getMessage());
            return new TownSeize(false, 0);
        }
    }

    private static double withdrawTownyAccountToReserve(
            Object gov, UUID uuid, String name, String details, Logger log) {
        try {
            Object account = invokeNoArg(gov, "getAccount");
            if (account == null) {
                return 0;
            }
            Object balObj = invokeNoArg(account, "getHoldingBalance");
            double bal = balObj instanceof Number n ? n.doubleValue() : 0;
            bal = GoldMoney.round(bal);
            if (bal < GoldMoney.MIN_AMOUNT) {
                return 0;
            }
            Method withdraw = findMethod(account.getClass(), "withdraw", double.class, String.class);
            if (withdraw == null) {
                return 0;
            }
            withdraw.invoke(account, bal, details);
            RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(
                    (JavaPlugin) Bukkit.getPluginManager().getPlugin("Root-Essentials"));
            if (treasury == null) {
                treasury = RootMcTreasuryResolver.resolve(
                        (JavaPlugin) Bukkit.getPluginManager().getPlugin("Root-Economy"));
            }
            if (treasury != null) {
                treasury.creditTreasury(bal, TreasuryLedgerType.OTHER, uuid, name, details);
            }
            return bal;
        } catch (Throwable ex) {
            log.warning("Towny bank withdraw failed: " + ex.getMessage());
            return 0;
        }
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Throwable ignored) {
                // try next
            }
        }
        return null;
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            Method m = type.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
