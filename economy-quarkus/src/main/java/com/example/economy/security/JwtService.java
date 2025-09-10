package com.example.economy.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Set;

/**
 * Сервис для создания и управления JWT токенами
 */
@ApplicationScoped
public class JwtService {
    
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;
    
    /**
     * Создать JWT токен для пользователя
     */
    public String generateToken(String username, Set<String> roles) {
        return Jwt.issuer(issuer)
            .upn(username)
            .groups(roles)
            .claim("username", username)
            .claim("preferred_username", username)
            .expiresIn(Duration.ofHours(8)) // Токен действует 8 часов
            .sign();
    }
    
    /**
     * Создать refresh токен
     */
    public String generateRefreshToken(String username) {
        return Jwt.issuer(issuer)
            .upn(username)
            .claim("type", "refresh")
            .claim("username", username)
            .expiresIn(Duration.ofDays(7)) // Refresh токен на неделю
            .sign();
    }
    
    /**
     * Создать токен для API доступа
     */
    public String generateApiToken(String username, Set<String> roles, Duration duration) {
        return Jwt.issuer(issuer)
            .upn(username)
            .groups(roles)
            .claim("username", username)
            .claim("type", "api")
            .expiresIn(duration)
            .sign();
    }
}
