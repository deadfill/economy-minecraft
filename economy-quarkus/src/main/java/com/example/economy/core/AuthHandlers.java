package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AuthHandlers {
    public record RegisterRequest(UUID uuid, String username, String password) {}
    public record LoginRequest(UUID uuid, String password) {}

    private final Repositories repo;

    public AuthHandlers(Repositories repo) {
        this.repo = repo;
    }

    public Map<String,Object> register(RegisterRequest rq) throws Exception {
        if (rq.password() == null || rq.password().length() < 6) {
            throw new IllegalArgumentException("Password too short");
        }
        repo.upsertPlayer(rq.uuid(), rq.username());
        repo.createAuthUser(rq.uuid(), rq.password());
        return Map.of("status","ok");
    }

    public Map<String,Object> login(LoginRequest rq) throws Exception {
        // Этот метод теперь вызывается напрямую из AuthResource
        // с передачей IP и User-Agent
        boolean ok = repo.verifyAuth(rq.uuid(), rq.password());
        return Map.of("ok", ok);
    }
    
    /**
     * Проверяет учетные данные пользователя
     * @param uuid UUID пользователя
     * @param password Пароль пользователя
     * @return true если учетные данные верны, иначе false
     * @throws Exception при ошибках работы с базой данных
     */
    public boolean verifyAuth(UUID uuid, String password) throws Exception {
        return repo.verifyAuth(uuid, password);
    }
    
    /**
     * Создает новую сессию для пользователя
     * @param userUuid UUID пользователя
     * @param ipAddress IP адрес пользователя
     * @param userAgent User-Agent клиента
     * @return Токен сессии
     * @throws Exception при ошибках работы с базой данных
     */
    public String createSession(UUID userUuid, String ipAddress, String userAgent) throws Exception {
        // Генерируем уникальный токен сессии
        String token = UUID.randomUUID().toString();
        
        // Устанавливаем срок действия сессии (24 часа)
        long expirationTime = System.currentTimeMillis() + (24 * 60 * 1000); // 24 часа в миллисекундах
        java.sql.Timestamp expiresAt = new java.sql.Timestamp(expirationTime);
        
        try (var c = repo.getDatabase().get()) {
            c.setAutoCommit(false);
            
            try (var ins = c.prepareStatement(
                    "INSERT INTO user_sessions(session_token, user_uuid, ip_address, user_agent, expires_at) " +
                            "VALUES(?,?,?,?,?)")) {
                ins.setString(1, token);
                ins.setObject(2, userUuid);
                ins.setString(3, ipAddress != null ? ipAddress : "");
                ins.setString(4, userAgent != null ? userAgent : "");
                ins.setTimestamp(5, expiresAt);
                ins.executeUpdate();
            }
            
            c.commit();
        }
        
        return token;
    }
    
    /**
     * Проверяет валидность сессии по токену
     * @param token Токен сессии для проверки
     * @return UUID пользователя, если сессия валидна, иначе null
     * @throws Exception при ошибках работы с базой данных
     */
    public UUID validateSession(String token) throws Exception {
        return repo.validateSession(token);
    }
    
    /**
     * Получает имя пользователя по UUID
     * @param userUuid UUID пользователя
     * @return Имя пользователя, если найден, иначе null
     * @throws Exception при ошибках работы с базой данных
     */
    public String getUsernameByUuid(UUID userUuid) throws Exception {
        return repo.getUsernameByUuid(userUuid);
    }
}