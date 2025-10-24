package ru.bont777.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.blackbaroness.baronessauth.bungee.api.BaronessAuthBungeeAPI;
import io.github.blackbaroness.baronessauth.bungee.api.model.Transaction;
import io.github.blackbaroness.baronessauth.bungee.api.model.entity.PlayerProfile;
import io.github.blackbaroness.baronessauth.bungee.api.model.ProfilePassword;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;

import javax.sql.DataSource;
import java.util.Map;
import java.util.logging.Logger;

public class HttpServerManager {

    private final BaronessLaravelBridge plugin;
    private final BaronessAuthBungeeAPI api;
    private final JwtManager jwtManager;
    private final Gson gson = new Gson();
    private final Logger logger;
    private Javalin app;

    public HttpServerManager(BaronessLaravelBridge plugin, BaronessAuthBungeeAPI api, JwtManager jwtManager) {
        this.plugin = plugin;
        this.api = api;
        this.jwtManager = jwtManager;
        this.logger = plugin.getLogger();
    }

    public void start(int port) {
        app = Javalin.create(config -> {
            config.jsonMapper(new GsonMapper());
        }).start(port);

        app.before("/api/*", this::jwtAuthMiddleware);
        app.post("/api/baronessauth", this::handleAuth);

        logger.info("REST API запущен на порту " + port);
    }

    private void jwtAuthMiddleware(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.status(401).result("Unauthorized");
            throw new HttpResponseException(401, "Unauthorized");
        }
        String token = authHeader.substring(7);
        try {
            String username = jwtManager.validateTokenAndGetUsername(token);
            plugin.logDebug("Успешная проверка JWT для " + username);
            ctx.attribute("username", username);
        } catch (Exception e) {
            ctx.status(401).result("Unauthorized");
            plugin.logDebug("Ошибка проверки JWT: " + e.getMessage());
            throw new HttpResponseException(401, "Unauthorized");
        }
    }

    private void handleAuth(Context ctx) {
        try {
            ctx.contentType("application/json");
            JsonObject json = gson.fromJson(ctx.body(), JsonObject.class);
            String login = json.has("login") ? json.get("login").getAsString() : null;
            String passwordStr = json.has("password") ? json.get("password").getAsString() : null;
            final JsonObject[] responseHolder = new JsonObject[1];
            plugin.logDebug("Получен запрос аутентификации. login=" + login);

            if (login == null || passwordStr == null) {
                plugin.logDebug("Отсутствует логин или пароль");
                ctx.status(400).json(Map.of("error", "Логин и пароль обязательны"));
                return;
            }

            api.inDatabase(tx -> {
                JsonObject response = new JsonObject();
                String jwtToken = jwtManager.generateToken(login);
                PlayerProfile profile = tx.findProfileByName(login);
                if (profile == null) {
                    plugin.logDebug("Профиль не найден, регистрация нового пользователя: " + login);
                    response.addProperty("status", "OK");
                    response.addProperty("action", "register");

                    tx.createProfile(login, profilelogin -> {
                        ProfilePassword password = api.createPassword(passwordStr).join();
                        profilelogin.setPassword(password);
                        profilelogin.setPremium(false);
                        plugin.logDebug("Пароль установлен для нового пользователя: " + login);
                    });

                    response.addProperty("name", login);
                    response.addProperty("jwt", jwtToken);
                    responseHolder[0] = response;
                } else {
                    plugin.logDebug("Профиль найден, проверка пароля для пользователя: " + login);
                    response.addProperty("jwt", jwtToken);

                    api.verifyPassword(passwordStr, profile.getPassword())
                       .thenAccept(isValid -> {
                           if (isValid) {
                               response.addProperty("action", "login");
                               response.addProperty("status", "OK");
                               plugin.logDebug("Пароль корректен для пользователя: " + login);
                           } else {
                               response.addProperty("error", "Неверный пароль");
                               response.addProperty("status", "error");
                               plugin.logDebug("Неверный пароль для пользователя: " + login);
                           }
                           response.addProperty("name", login);
                           responseHolder[0] = response;
                       }).join();
                }
            }).join();

            ctx.status(200).json(responseHolder[0]);
            plugin.logDebug("Ответ клиенту: 200 OK");

        } catch (Exception e) {
            plugin.logDebug("Ошибка обработки запроса /api/baronessauth: " + e);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Внутренняя ошибка сервера");
            ctx.status(500).json(errorResponse);
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("REST API остановлен.");
        }
    }
}
