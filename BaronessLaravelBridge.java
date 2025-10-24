package ru.bont777.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.blackbaroness.baronessauth.bungee.api.BaronessAuthBungeeAPI;
import net.md_5.bungee.api.plugin.Plugin;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import java.util.Map;

import javax.sql.DataSource;

public class BaronessLaravelBridge extends Plugin {

    private BaronessAuthBungeeAPI api;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private JwtManager jwtManager;
    private HttpServerManager httpServerManager;
    private boolean debug = false;
    private Gson gson = new Gson();

    @Override
    public void onEnable() {
        api = BaronessAuthBungeeAPI.getInstance();

        // Загрузка конфига
        configManager = new ConfigManager(getDataFolder(), getLogger());
        configManager.load();

        // Переменная debug из конфигурации
        debug = configManager.getBoolean("debug", false);

        // Инициализация JWT менеджера
        jwtManager = new JwtManager(configManager.getString("jwtSecret"), getLogger(), configManager);

        // Инициализация менеджера базы данных
        databaseManager = new DatabaseManager(this, configManager.getConfigMap());
        try {
            databaseManager.init();
        } catch (Exception e) {
            getLogger().severe("Ошибка инициализации базы данных: " + e);
            throw new RuntimeException("Cannot initialize database");
        }

        // Запуск HTTP сервера с API
        httpServerManager = new HttpServerManager(this, api, jwtManager);
        httpServerManager.start(8080);

        // Регистрация слушателей
        getProxy().getPluginManager().registerListener(this, new PasswordListener(this, configManager));

        getLogger().info("🚀 BaronessLaravelBridge enabled, REST API started on port 8080.");
    }

    public void logDebug(String message) {
        if (debug) {
            getLogger().info("[🚀 BaronessLaravelBridge] " + message);
        }
    }

    // Получение DataSource для доступа к базе
    public DataSource getDataSource() {
        return databaseManager.getDataSource();
    }

    // Получение ConfigManager для доступа к конфигу
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public void onDisable() {
        if (httpServerManager != null) {
            httpServerManager.stop();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("🛑 BaronessLaravelBridge disabled, REST API stopped.");
    }
}
