package com.rootrecord.minecraft.rootbanner.order;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootbanner.RootBannerPlugin;
import com.rootrecord.minecraft.rootbanner.catalog.BannerGlyphCatalog;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BannerShopService {

    private final RootBannerPlugin plugin;
    private final Map<UUID, BannerQuote> pending = new ConcurrentHashMap<>();

    public BannerShopService(RootBannerPlugin plugin) {
        this.plugin = plugin;
    }

    public BannerQuote pending(UUID playerId) {
        BannerQuote quote = pending.get(playerId);
        if (quote == null) {
            return null;
        }
        if (quote.expired(System.currentTimeMillis())) {
            pending.remove(playerId, quote);
            return null;
        }
        return quote;
    }

    public void clear(UUID playerId) {
        pending.remove(playerId);
    }

    public BannerQuote putQuote(UUID playerId, List<BannerLine> lines) {
        double total = 0;
        for (BannerLine line : lines) {
            total += line.price();
        }
        long expires = System.currentTimeMillis() + plugin.confirmSeconds() * 1000L;
        BannerQuote quote = new BannerQuote(List.copyOf(lines), total, expires);
        pending.put(playerId, quote);
        return quote;
    }

    public static int emptyStorageSlots(PlayerInventory inventory) {
        int free = 0;
        ItemStack[] storage = inventory.getStorageContents();
        for (ItemStack stack : storage) {
            if (stack == null || stack.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    public FulfillResult fulfill(Player player, boolean free) {
        BannerQuote quote = pending.get(player.getUniqueId());
        if (quote == null) {
            return FulfillResult.NONE;
        }
        if (quote.expired(System.currentTimeMillis())) {
            pending.remove(player.getUniqueId(), quote);
            return FulfillResult.EXPIRED;
        }

        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (!free && economy == null) {
            return FulfillResult.NO_ECONOMY;
        }

        double total = quote.total();
        if (!free) {
            if (!economy.has(player.getUniqueId(), total)) {
                return FulfillResult.CANNOT_AFFORD;
            }
            if (!economy.withdraw(player.getUniqueId(), total)) {
                return FulfillResult.CHARGE_FAILED;
            }
            RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
            if (treasury != null && total > 0) {
                treasury.settleClosedLoopPayment(
                        player.getUniqueId(),
                        player.getName(),
                        total,
                        "service-fee:banner:" + quote.count());
            }
        }

        DyeColor base = plugin.defaultBase();
        DyeColor ink = plugin.defaultInk();
        List<ItemStack> items = new ArrayList<>(quote.count());
        for (BannerLine line : quote.lines()) {
            items.add(line.createItem(base, ink));
        }

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(items.toArray(ItemStack[]::new));
        int dropped = 0;
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
            dropped += drop.getAmount();
        }
        pending.remove(player.getUniqueId());
        int kept = quote.count() - leftover.size();
        if (dropped > 0) {
            return new FulfillResult.Ok(total, quote.count(), kept, leftover.size());
        }
        return new FulfillResult.Ok(total, quote.count(), quote.count(), 0);
    }

    public sealed interface FulfillResult {
        FulfillResult NONE = new None();
        FulfillResult EXPIRED = new Expired();
        FulfillResult NO_ECONOMY = new NoEconomy();
        FulfillResult CANNOT_AFFORD = new CannotAfford();
        FulfillResult CHARGE_FAILED = new ChargeFailed();

        record None() implements FulfillResult {}

        record Expired() implements FulfillResult {}

        record NoEconomy() implements FulfillResult {}

        record CannotAfford() implements FulfillResult {}

        record ChargeFailed() implements FulfillResult {}

        record Ok(double total, int count, int kept, int droppedStacks) implements FulfillResult {}
    }

    public List<String> sortedColorLabels() {
        List<String> names = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            names.add(BannerGlyphCatalog.prettyColor(color).toLowerCase(java.util.Locale.ROOT));
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }
}
