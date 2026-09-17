package com.rootrecord.minecraft.rootmemberships;

import com.rootrecord.minecraft.rootstat.RootStatBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Right-click Pro voucher → cloud redeem. */
public final class ProVoucherListener implements Listener {

    private final RootMembershipsPlugin plugin;

    public ProVoucherListener(RootMembershipsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.config().vouchersEnabled()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        ProVoucherItem vouchers = plugin.vouchers();
        if (!vouchers.isProVoucher(stack)) {
            return;
        }
        event.setCancelled(true);
        String voucherId = vouchers.voucherIdOf(stack);
        String tier = vouchers.tierOf(stack);
        if (voucherId == null || voucherId.isBlank()) {
            player.sendMessage(color(plugin.config().redeemFail().replace("{detail}", "missing id")));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Plugin rootMc = Bukkit.getPluginManager().getPlugin("RootMC");
                if (!(rootMc instanceof RootStatBridge bridge)) {
                    throw new IllegalStateException("RootMC offline");
                }
                String result = bridge.cloud().redeemProVoucher(player.getUniqueId().toString(), voucherId);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result != null && result.contains("\"ok\":true")) {
                        consumeOne(player, stack);
                        String msg = "lifetime".equalsIgnoreCase(tier)
                                ? plugin.config().redeemSuccessLife()
                                : plugin.config().redeemSuccessMonth();
                        player.sendMessage(color(msg));
                        plugin.syncPlayer(player);
                        int appreciationTokens = "lifetime".equalsIgnoreCase(tier) ? 10_000 : 500;
                        if (appreciationTokens > 0) {
                            tryGiveAppreciationTokens(player, appreciationTokens,
                                    "pro-voucher-" + (lifetimeTier(tier) ? "lifetime" : "one_month"));
                        }
                    } else {
                        player.sendMessage(color(plugin.config().redeemFail()
                                .replace("{detail}", extractDetail(result))));
                    }
                });
            } catch (Exception ex) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(color(plugin.config().redeemFail()
                                .replace("{detail}", ex.getMessage() == null ? "error" : ex.getMessage())));
                    }
                });
            }
        });
    }

    private static void consumeOne(Player player, ItemStack stack) {
        int amt = stack.getAmount();
        if (amt <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(amt - 1);
        }
    }

    private static boolean lifetimeTier(String tier) {
        return tier != null && "lifetime".equalsIgnoreCase(tier);
    }

    private static void tryGiveAppreciationTokens(Player player, int amount, String reason) {
        if (player == null || amount <= 0) return;

        // Softly integrate with Root-Appreciation if it's installed.
        Plugin rootApp = Bukkit.getPluginManager().getPlugin("Root-Appreciation");
        if (rootApp == null) {
            rootApp = Bukkit.getPluginManager().getPlugin("root-appreciation");
        }
        if (rootApp == null) return;

        try {
            Object service = rootApp.getClass().getMethod("service").invoke(rootApp);
            service.getClass()
                    .getMethod("giveTokens", Player.class, int.class, String.class, boolean.class)
                    .invoke(service, player, amount, reason, false);
        } catch (Exception ignored) {
            // Token-granting is best-effort: voucher redemption must not fail if Root-Appreciation is missing/broken.
        }
    }

    private static String extractDetail(String json) {
        if (json == null) {
            return "unknown";
        }
        int i = json.indexOf("\"detail\"");
        if (i < 0) {
            return json.length() > 80 ? json.substring(0, 80) : json;
        }
        int q1 = json.indexOf('"', i + 8);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return "failed";
        }
        return json.substring(q1 + 1, q2);
    }

    private static String color(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }
}
