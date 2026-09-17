package com.rootrecord.minecraft.rootmemberships;

import com.rootrecord.minecraft.common.RootMcProVoucherService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RootMembershipsPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private MembershipsConfig config;
    private BukkitTask syncTask;
    private ProVoucherItem vouchers;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_MEMBERSHIPS_CONFIG, "root-memberships.yml");
        yaml.load();
        config = new MembershipsConfig(yaml.config());
        vouchers = new ProVoucherItem(this);

        Bukkit.getServicesManager().register(
                RootMcProVoucherService.class,
                new RootMcProVoucherService() {
                    @Override
                    public ItemStack createVoucher(String tier, String voucherId) {
                        return vouchers.create(config, tier, voucherId);
                    }

                    @Override
                    public boolean isProVoucher(ItemStack stack) {
                        return vouchers.isProVoucher(stack);
                    }

                    @Override
                    public String voucherIdOf(ItemStack stack) {
                        return vouchers.voucherIdOf(stack);
                    }

                    @Override
                    public String tierOf(ItemStack stack) {
                        return vouchers.tierOf(stack);
                    }
                },
                this,
                ServicePriority.Normal);

        MembershipsCommand cmd = new MembershipsCommand(this);
        bind("memberships", cmd, cmd);
        ProCommand pro = new ProCommand();
        bind("pro", pro, pro);
        bind("donor", pro, pro);
        bind("lifetime", pro, pro);
        getServer().getPluginManager().registerEvents(new MembershipJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ProVoucherListener(this), this);
        scheduleIntervalSync();

        getLogger().info("Root-Memberships enabled — cloud Pro/life sync + Pro vouchers.");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    public void reloadAll() {
        yaml.load();
        config = new MembershipsConfig(yaml.config());
        scheduleIntervalSync();
        getLogger().info("Root-Memberships reloaded.");
    }

    private void scheduleIntervalSync() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        if (!config.enabled()) {
            return;
        }
        int seconds = config.syncIntervalSeconds();
        if (seconds <= 0) {
            return;
        }
        long period = Math.max(20L, seconds * 20L);
        syncTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                syncPlayer(player);
            }
        }, period, period);
    }

    public void syncPlayer(Player player) {
        if (player == null || !config.enabled()) {
            return;
        }
        CloudMembershipFlags.Flags flags = CloudMembershipFlags.fetch(player);
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (MembershipsConfig.MembershipRule rule : config.rules()) {
                if (!rule.enabled()) {
                    continue;
                }
                boolean active = CloudMembershipFlags.qualifies(rule, flags);
                MembershipGroupSync.apply(player, rule.group(), active);
            }
        });
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec, org.bukkit.command.TabCompleter tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(exec);
        if (tab != null) {
            cmd.setTabCompleter(tab);
        }
    }

    public MembershipsConfig config() {
        return config;
    }

    public ProVoucherItem vouchers() {
        return vouchers;
    }
}
