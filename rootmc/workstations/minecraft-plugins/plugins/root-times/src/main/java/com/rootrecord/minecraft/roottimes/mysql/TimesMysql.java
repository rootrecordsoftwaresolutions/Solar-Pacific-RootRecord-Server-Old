package com.rootrecord.minecraft.roottimes.mysql;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import com.rootrecord.minecraft.roottimes.config.TimesConfig;

import java.sql.Connection;
import java.sql.SQLException;

public final class TimesMysql {

    private final TimesConfig config;

    public TimesMysql(TimesConfig config) {
        this.config = config;
    }

    public boolean ready() {
        return config.mysqlReady();
    }

    public Connection open() throws SQLException {
        RootMcDatabaseConfig.DatabaseSettings db = config.database();
        return MysqlConnections.open(db);
    }

    public void initSchema() throws SQLException {
        if (!ready()) {
            return;
        }
        try (Connection c = open()) {
            PlaytimeStore.initSchema(c, config);
            ActivityHarvestStore.initSchema(c, config);
            AfkSessionStore.initSchema(c, config);
            TimesStatusStore.initSchema(c, config);
        }
    }
}
