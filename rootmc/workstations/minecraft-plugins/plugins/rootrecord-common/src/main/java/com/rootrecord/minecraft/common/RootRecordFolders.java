package com.rootrecord.minecraft.common;

import org.bukkit.plugin.Plugin;

import java.io.File;

/** Shared on-disk layout for all RootMC Paper plugins. */
public final class RootRecordFolders {

    public static final String FOLDER_NAME = "RootMC";
    /** Pre-rename folder; migrated once on {@link #ensureDir} if present. */
    public static final String LEGACY_FOLDER_NAME = "RootRecord";

    public static final String CLOUD_CONFIG = "cloud.yml";
    public static final String DATABASE_CONFIG = "database.yml";
    public static final String ROOT_CORE_CONFIG = "root-core.yml";
    public static final String LICENSE_CONFIG = "license.yml";
    public static final String CORE_META = ".core-meta.yml";
    public static final String ROOTMC_CONFIG = "rootmc.yml";
    public static final String ROOTMC_SHOPS_CONFIG = "rootmc-shops.yml";
    public static final String ROOT_MARKET_CONFIG = "root-market.yml";
    public static final String ROOTMC_SHOPS_LISTINGS = "shops.yml";
    public static final String ROOTHELP_CONFIG = "roothelp.yml";
    public static final String ROOT_ESSENTIALS_CONFIG = "root-essentials.yml";
    public static final String ROOT_ECONOMY_CONFIG = "root-economy.yml";
    public static final String ROOT_REWARDS_CONFIG = "root-rewards.yml";
    public static final String ROOT_ANNOUNCER_CONFIG = "root-announcer.yml";
    public static final String ROOT_EXPLORE_CONFIG = "root-explore.yml";
    public static final String ROOT_ADMIN_CONFIG = "root-admin.yml";
    public static final String ROOT_LOANS_CONFIG = "root-loans.yml";
    public static final String ROOT_BONDS_CONFIG = "root-bonds.yml";
    public static final String ROOT_CONTRACTS_CONFIG = "root-contracts.yml";
    public static final String ROOT_BLUEPRINTS_CONFIG = "root-blueprints.yml";
    public static final String ROOT_RESTART_CONFIG = "root-restart.yml";
    public static final String ROOT_SPAWN_CONFIG = "root-spawn.yml";
    public static final String ROOT_RANKS_CONFIG = "root-ranks.yml";
    public static final String ROOT_ASK_CONFIG = "root-ask.yml";
    public static final String ROOT_POTIONS_CONFIG = "root-potions.yml";
    public static final String ROOT_BANNER_CONFIG = "root-banner.yml";
    /** Fancy command/broadcast headlines toggle — {@code plugins/RootMC/rootmc-ui.yml}. */
    public static final String ROOTMC_UI_CONFIG = "rootmc-ui.yml";
    public static final String ROOT_SPAWN_AREA_FILE = "spawnarea.txt";
    public static final String ROOT_SPAWN_REFINED_FILE = "spawnarea-refined.txt";
    public static final String ROOT_SPAWN_CHAMBER_FILE = "chamber.txt";
    public static final String ROOT_SPAWN_WELL_FILE = "well.txt";
    public static final String ROOT_SPAWN_LAVA_FILE = "lava.txt";
    public static final String ROOT_MAPPER_CONFIG = "root-mapper.yml";
    public static final String ROOT_MAPPER_AREAS_DIR = "mapper-areas";
    public static final String ROOT_TERRITORIES_CONFIG = "root-territories.yml";
    public static final String ROOT_TERRITORIES_NATION_FOUNDING = "nation-founding.yml";
    public static final String ROOT_TERRITORIES_NATION_FOUNDING_GRANDFATHER =
            "nation-founding-grandfather.yml";
    public static final String ROOT_UPKEEP_CONFIG = "root-upkeep.yml";
    public static final String ROOT_QUESTIONNAIRE_CONFIG = "root-questionnaire.yml";
    public static final String ROOT_ACTIVITY_CONFIG = "root-activity.yml";
    public static final String ROOT_TIMES_CONFIG = "root-times.yml";
    public static final String ROOT_PERMS_CONFIG = "root-perms.yml";
    public static final String ROOTMC_OFFICIAL_CONFIG = "rootmc-official.yml";
    public static final String ROOT_ITEMINFO_CONFIG = "root-iteminfo.yml";
    public static final String ROOT_ITEMINFO_CENSUS = "item-census.yml";
    /** Root-Webstat HTTP + stats — {@code plugins/RootMC/root-webstat.yml}. */
    public static final String ROOT_WEBSTAT_CONFIG = "root-webstat.yml";
    /** Root-Try rewarded tryouts — {@code plugins/RootMC/root-try.yml}. */
    public static final String ROOT_TRY_CONFIG = "root-try.yml";
    /** Root-Referrals player referral network — {@code plugins/RootMC/root-referrals.yml}. */
    public static final String ROOT_REFERRALS_CONFIG = "root-referrals.yml";
    public static final String ROOT_APPRECIATION_CONFIG = "root-appreciation.yml";
    /** Root-Memberships Pro/life group sync — {@code plugins/RootMC/root-memberships.yml}. */
    public static final String ROOT_MEMBERSHIPS_CONFIG = "root-memberships.yml";
    /** Root-Ping connection samples — {@code plugins/RootMC/root-ping.yml}. */
    public static final String ROOT_PING_CONFIG = "root-ping.yml";
    /** Root-Ava-Core companion status — {@code plugins/RootMC/root-ava-core.yml}. */
    public static final String ROOT_AVA_CORE_CONFIG = "root-ava-core.yml";
    /** Ava claim schematic snapshots — {@code plugins/RootMC/ava-schematics/}. */
    public static final String AVA_SCHEMATICS_DIR = "ava-schematics";
    /** Root-Discord chat / bot flags — {@code plugins/RootMC/root-discord.yml}. */
    public static final String ROOT_DISCORD_CONFIG = "root-discord.yml";
    /** Root-Gamble games — {@code plugins/RootMC/root-gamble.yml}. */
    public static final String ROOT_GAMBLE_CONFIG = "root-gamble.yml";
    /** Root-Heads mob collectible heads — {@code plugins/RootMC/root-heads.yml}. */
    public static final String ROOT_HEADS_CONFIG = "root-heads.yml";
    /** BlueMap R2 sidecar secrets — {@code plugins/RootMC/r2.env}. */
    public static final String ROOT_BLUEMAP_R2_ENV = "r2.env";
    public static final String DOWNLOADED_PLUGINS_STATE = "downloaded-plugins.yml";

    private RootRecordFolders() {}

    /** {@code plugins/RootMC/} — configs and internal state, not per-plugin subfolders. */
    public static File dir(Plugin plugin) {
        return new File(pluginsDir(plugin), FOLDER_NAME);
    }

    public static File pluginsDir(Plugin plugin) {
        return plugin.getServer().getPluginsFolder();
    }

    /** {@code plugins/RootMC/<fileName>} e.g. {@code rootmc.yml}. */
    public static File configFile(Plugin plugin, String fileName) {
        return new File(dir(plugin), fileName);
    }

    public static void ensureDir(Plugin plugin) {
        File dir = dir(plugin);
        if (!dir.isDirectory()) {
            File legacy = new File(pluginsDir(plugin), LEGACY_FOLDER_NAME);
            if (legacy.isDirectory() && legacy.renameTo(dir)) {
                plugin.getLogger().info(
                        "Migrated plugin config folder " + LEGACY_FOLDER_NAME + " -> " + FOLDER_NAME);
            }
        }
        dir.mkdirs();
    }
}
