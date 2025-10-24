package ru.bont777.bridge;

import io.github.blackbaroness.baronessauth.bungee.api.event.AuthChangePasswordEvent;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordListener implements Listener {

    private final BaronessLaravelBridge plugin;
    private final ConfigManager configManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PasswordListener(BaronessLaravelBridge plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler
    public void onAuthChangePassword(AuthChangePasswordEvent event) {
        String playerName = event.getTargetPlayerName();
        plugin.logDebug("Запуск задачи сброса сессий для: " + playerName);

        executor.submit(() -> handlePasswordChange(playerName));
    }

    private void handlePasswordChange(String playerName) {
        DataSource ds = plugin.getDataSource();

        String tableUsers = configManager.getString("table_users");
        String tableSessions = configManager.getString("table_sessions");
        String colUserId = configManager.getString("column_user_id");
        String colUsername = configManager.getString("column_username");
        String colSessionUserId = configManager.getString("column_session_user_id");
        String message = configManager.getString("message_to_player");

        try (Connection connection = ds.getConnection()) {
            String findUserSql = "SELECT " + colUserId + " FROM " + tableUsers + " WHERE " + colUsername + " = ?";
            try (PreparedStatement pst = connection.prepareStatement(findUserSql)) {
                pst.setString(1, playerName);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) {
                        int userId = rs.getInt(colUserId);
                        int removed = deleteUserSessions(connection, tableSessions, colSessionUserId, userId);
                        plugin.logDebug("Сброшено сессий для игрока " + playerName + ": " + removed);
                    } else {
                        plugin.getLogger().warning("Игрок " + playerName + " не найден в таблице " + tableUsers);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при сбросе сессий для " + playerName + ": " + e.getMessage());
            ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
            if (player != null && player.isConnected()) {
                String coloredMessage = message.replace("&", "§");
                player.sendMessage(coloredMessage);
            }
        }
    }

    private int deleteUserSessions(Connection conn, String table, String column, int userId) throws SQLException {
        String deleteSql = "DELETE FROM " + table + " WHERE " + column + " = ?";
        try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
            del.setInt(1, userId);
            return del.executeUpdate();
        }
    }

    public void shutdown() {
        executor.shutdown();
        plugin.getLogger().info("Остановлен пул потоков PasswordListener");
    }
}
