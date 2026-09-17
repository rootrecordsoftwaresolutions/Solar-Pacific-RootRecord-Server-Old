package com.rootrecord.minecraft.rootops;

import com.rootrecord.minecraft.common.FancyUiConfig;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import com.rootrecord.minecraft.rootannouncer.RootAnnouncerPlugin;
import com.rootrecord.minecraft.rootmapper.RootMapperPlugin;
import com.rootrecord.minecraft.rootrestart.RootRestartPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

/** Root-Ops: Restart + Admin + Announcer + Mapper. BlueMap-R2-Fix stays a STARTUP sidecar. */
public final class RootOpsPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRestartPlugin restart;
    private RootAdminPlugin admin;
    private RootAnnouncerPlugin announcer;
    private RootMapperPlugin mapper;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        FancyUiConfig.load(this);
        if (getServer().getPluginManager().getPlugin("Root-Core") == null) {
            var repair = RootMcCoreConnection.ensureAndRepair(this);
            getLogger().warning(
                    "Root-Core not present — used RootMcCoreConnection fallback (databaseOk="
                            + repair.databaseOk()
                            + ", cloudOk="
                            + repair.cloudOk()
                            + ").");
        }

        // Restart before Admin (Admin may soft-depend restart helpers).
        restart = new RootRestartPlugin(this);
        restart.enable();

        admin = new RootAdminPlugin(this);
        admin.enable();

        announcer = new RootAnnouncerPlugin(this);
        announcer.enable();

        mapper = new RootMapperPlugin(this);
        mapper.enable();

        getLogger().info("Root-Ops enabled (Restart + Admin + Announcer + Mapper).");
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (mapper != null) {
            mapper.disable();
            mapper = null;
        }
        if (announcer != null) {
            announcer.disable();
            announcer = null;
        }
        if (admin != null) {
            admin.disable();
            admin = null;
        }
        if (restart != null) {
            restart.disable();
            restart = null;
        }
    }

    public RootRestartPlugin restart() {
        return restart;
    }

    public RootAdminPlugin admin() {
        return admin;
    }

    public RootAnnouncerPlugin announcer() {
        return announcer;
    }

    public RootMapperPlugin mapper() {
        return mapper;
    }
}
