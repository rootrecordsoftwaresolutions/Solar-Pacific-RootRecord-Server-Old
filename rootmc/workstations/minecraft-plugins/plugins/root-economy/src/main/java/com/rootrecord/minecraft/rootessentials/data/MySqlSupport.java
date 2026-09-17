package com.rootrecord.minecraft.rootessentials.data;

import com.rootrecord.minecraft.rootessentials.config.RootEconomyConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class MySqlSupport {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public MySqlSupport(RootEconomyConfig cfg) {
        String params = cfg.mysqlJdbcParams() == null ? "" : cfg.mysqlJdbcParams().trim();
        if (params.startsWith("?")) {
            params = params.substring(1);
        }
        this.jdbcUrl = "jdbc:mysql://" + cfg.mysqlHost() + ":" + cfg.mysqlPort() + "/" + cfg.mysqlDatabase()
                + (params.isBlank() ? "" : "?" + params);
        this.user = cfg.mysqlUsername();
        this.password = cfg.mysqlPassword() == null ? "" : cfg.mysqlPassword();
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }
}
