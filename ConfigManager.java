package ru.bont777.bridge;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {

    private final File configFile;
    private Map<String, Object> configMap;
    private final Logger logger;

    public ConfigManager(File dataFolder, Logger logger) {
        this.configFile = new File(dataFolder, "config.yml");
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        Yaml yaml = new Yaml();
        try {
            if (!configFile.exists()) {
                if (!configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("debug: false\n");
                    writer.write("jwtSecret: \"PLEASE_PASTE_YOUR_KEY\"\n");
                    writer.write("db_host: localhost\n");
                    writer.write("db_port: 3306\n");
                    writer.write("db_database: your_database\n");
                    writer.write("db_user: your_user\n");
                    writer.write("db_password: your_password\n");
                    writer.write("table_users: users\n");
                    writer.write("table_sessions: sessions\n");
                    writer.write("column_user_id: id\n");
                    writer.write("column_username: username\n");
                    writer.write("column_session_user_id: user_id\n");
                    writer.write("message_to_player: \"Произошла ошибка при сбросе вашей сессии. Сообщите администрации.\"\n");
                }
                logger.info("Создан новый файл конфигурации config.yml");
            }
            try (FileInputStream fis = new FileInputStream(configFile)) {
                configMap = yaml.load(fis);
                if (configMap == null) configMap = Map.of();
                logger.info("Конфигурация config.yml загружена");
            }
        } catch (IOException e) {
            logger.warning("Ошибка загрузки config.yml: " + e.getMessage());
            configMap = Map.of();
        }
    }

    public void save() {
        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(configFile)) {
            yaml.dump(configMap, writer);
            logger.info("Конфигурация config.yml успешно сохранена.");
        } catch (IOException e) {
            logger.warning("Ошибка сохранения config.yml: " + e.getMessage());
        }
    }

    public Object get(String key) {
        return configMap.get(key);
    }

    public String getString(String key) {
        Object val = configMap.get(key);
        return val == null ? "" : val.toString();
    }

    public int getInt(String key, int def) {
        Object val = configMap.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception ex) { return def; }
    }

    public boolean getBoolean(String key, boolean def) {
        Object val = configMap.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val != null) return Boolean.parseBoolean(val.toString());
        return def;
    }

    public void setValue(String key, Object value) {
        configMap.put(key, value);
        save();
    }
    public Map<String, Object> getConfigMap() {
        return configMap;
    }
}
