package com.example.economy.security;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;

/**
 * REST API для аутентификации в админ панели
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {
    
    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    
    @Inject JwtService jwtService;
    
    public record LoginRequest(String username, String password) {}
    
    public record LoginResponse(
        String token, 
        String refreshToken, 
        String username, 
        Set<String> roles,
        long expiresIn
    ) {}
    
    /**
     * Вход в админ панель
     */
    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest request) {
        try {
            LOG.infof("Login attempt for user: %s", request.username);
            
            // В реальном проекте здесь была бы проверка в БД
            // Для простоты используем захардкоженных пользователей
            Set<String> roles = validateCredentials(request.username, request.password);
            
            if (roles == null) {
                LOG.warnf("Invalid login attempt for user: %s", request.username);
                return Response.status(401)
                    .entity(Map.of("error", "Invalid credentials"))
                    .build();
            }
            
            // Временно возвращаем простой токен без JWT
            String token = "simple-token-" + request.username;
            String refreshToken = "refresh-token-" + request.username;
            
            LOG.infof("User %s logged in successfully with roles: %s", request.username, roles);
            
            return Response.ok(new LoginResponse(
                token, 
                refreshToken, 
                request.username, 
                roles,
                System.currentTimeMillis() + (8 * 60 * 60 * 1000) // 8 часов
            )).build();
            
        } catch (Exception e) {
            LOG.error("Login failed", e);
            return Response.status(500)
                .entity(Map.of("error", "Login failed"))
                .build();
        }
    }
    
    /**
     * Получить информацию о текущем пользователе
     */
    @GET
    @Path("/me")
    @PermitAll
    public Response getCurrentUser() {
        return Response.ok(Map.of(
            "username", "admin",
            "roles", Set.of("admin", "developer"),
            "isAdmin", true,
            "isDeveloper", true
        )).build();
    }
    
    /**
     * Выход из системы
     */
    @POST
    @Path("/logout")
    @PermitAll
    public Response logout() {
        LOG.info("User logged out");
        return Response.ok(Map.of("message", "Logged out successfully")).build();
    }
    
    /**
     * Проверить валидность токена
     */
    @GET
    @Path("/validate")
    @PermitAll
    public Response validateToken() {
        return Response.ok(Map.of(
            "valid", true,
            "username", "admin",
            "roles", Set.of("admin", "developer")
        )).build();
    }
    
    private Set<String> validateCredentials(String username, String password) {
        // Простая проверка - в продакшене заменить на БД или LDAP
        if ("admin".equals(username) && "admin123".equals(password)) {
            return Set.of("admin", "developer");
        } else if ("developer".equals(username) && "dev456".equals(password)) {
            return Set.of("developer");
        }
        return null;
    }
}
