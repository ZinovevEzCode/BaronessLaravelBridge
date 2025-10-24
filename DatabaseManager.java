package ru.bont777.bridge;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;

public class DatabaseManager {

    private final BaronessLaravelBridge plugin;
    private HikariDataSource dataSource;
    private Map<String, Object> config;

    public DatabaseManager(BaronessLaravelBridge plugin, Map<String, Object> config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        HikariConfig hikariConfig = new HikariConfig();

        String host = (String) config.get("db_host");
        Number port = (Number) config.get("db_port");
        String database = (String) config.get("db_database");
        String user = (String) config.get("db_user");
        String password = (String) config.get("db_password");

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port.intValue() + "/" + database;

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setMaxLifetime(30 * 60 * 1000); // 30 минут
        hikariConfig.setIdleTimeout(10 * 60 * 1000); // 10 минут
        hikariConfig.setConnectionTimeout(15 * 1000); // 15 секунд
        hikariConfig.setValidationTimeout(3 * 1000);  // 3 секунды
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setLeakDetectionThreshold(60 * 1000); // 1 минута

        try {
            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("✅ DataSource успешно инициализирован.");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Ошибка инициализации DataSource: " + e.getMessage());
            throw e;
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource не инициализирован.");
        }
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("DataSource закрыт.");
        }
    }
}
