package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ShowCommands {

    private ShowCommands() {}

    public static final class ShowHand implements CommandExecutor {
        private final RootEssentialsPlugin plugin;

        public ShowHand(RootEssentialsPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!Permissions.has(player, "show")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            if (args.length > 0 && !args[0].equalsIgnoreCase("hand")) {
                player.sendMessage(plugin.colorize("&eUsage: /show hand"));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                player.sendMessage(plugin.colorize("&eHold an item in your main hand."));
                return true;
            }
            Component item = itemLabel(hand).hoverEvent(hand.asHoverEvent());
            Component broadcast = Component.text(player.getName(), NamedTextColor.WHITE)
                    .append(Component.text(" is holding ", NamedTextColor.GRAY))
                    .append(item)
                    .append(Component.text(" x" + hand.getAmount(), NamedTextColor.GRAY));
            Bukkit.getServer().sendMessage(broadcast);
            return true;
        }

        private static Component itemLabel(ItemStack stack) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return Component.text(meta.getDisplayName(), NamedTextColor.AQUA);
            }
            return Component.translatable(stack.translationKey(), NamedTextColor.AQUA);
        }
    }
}
