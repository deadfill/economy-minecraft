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
        boolean ok = repo.verifyAuth(rq.uuid(), rq.password());
        return Map.of("ok", ok);
    }
}