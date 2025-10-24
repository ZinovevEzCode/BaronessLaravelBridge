package ru.bont777.bridge;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.logging.Logger;

public class JwtManager {

    private Key jwtKey;
    private String secretBase64;
    private final Logger logger;
    private final ConfigManager configManager;

    // Время жизни токена (например, 1 час)
    private final long validityMs = 3600_000;

    public JwtManager(String secretBase64, Logger logger, ConfigManager configManager) {
        this.logger = logger;
        initializeKey(secretBase64);
        this.configManager = configManager;    // инициализируем
    }

    private void initializeKey(String secretBase64) {
        try {
            if (secretBase64 == null || secretBase64.isEmpty()) {
                logger.warning("JWT секрет не задан, генерируем новый.");
                SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
                this.secretBase64 = Base64.getEncoder().encodeToString(key.getEncoded());
                this.jwtKey = key;
                configManager.setValue("jwtSecret", this.jwtKey);
                logger.info("Сгенерирован JWT секрет (Base64): " + this.secretBase64);
            } else {
                byte[] keyBytes = Decoders.BASE64.decode(secretBase64);
                if (keyBytes.length < 32) {
                    logger.warning("JWT секрет слишком короткий, генерируем новый.");
                    SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
                    this.secretBase64 = Base64.getEncoder().encodeToString(key.getEncoded());
                    this.jwtKey = key;
                    configManager.setValue("jwtSecret", this.jwtKey);
                    logger.info("Сгенерирован JWT секрет (Base64): " + this.secretBase64);
                } else {
                    this.jwtKey = new SecretKeySpec(keyBytes, "HmacSHA256");
                    this.secretBase64 = secretBase64;
                }
            }
        } catch (Exception e) {
            logger.severe("Ошибка инициализации JWT: " + e);
            throw new RuntimeException(e);
        }
    }

    public String getSecretBase64() {
        return secretBase64;
    }

    public Key getKey() {
        return jwtKey;
    }
    public void updateSecretInConfig(ConfigManager configManager) {
        String base64Key = Base64.getEncoder().encodeToString(jwtKey.getEncoded());
        configManager.setValue("jwtSecret", base64Key);
    }
    /** Генерация JWT токена с subject=username */
    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + validityMs))
                .signWith(jwtKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Валидация токена, возвращает username */
    public String validateTokenAndGetUsername(String token) throws JwtException {
        Jws<Claims> claims = Jwts.parserBuilder()
                .setSigningKey(jwtKey)
                .build()
                .parseClaimsJws(token);
        return claims.getBody().getSubject();
    }

    /** Проверка валидности токена */
    public boolean isValid(String token) {
        try {
            validateTokenAndGetUsername(token);
            return true;
        } catch (JwtException e) {
            logger.warning("JWT validation failed: " + e.getMessage());
            return false;
        }
    }
}
