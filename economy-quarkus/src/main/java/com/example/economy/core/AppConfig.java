package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.annotations.RegisterForReflection;

@ApplicationScoped
@RegisterForReflection
public class AppConfig {
    // Primary database (для записи)
    private final String pgPrimaryUrl;
    private final String pgPrimaryUser;
    private final String pgPrimaryPass;
    
    // Replica databases (для чтения)
    private final String pgReplica1Url;
    private final String pgReplica1User;
    private final String pgReplica1Pass;
    
    private final String pgReplica2Url;
    private final String pgReplica2User;
    private final String pgReplica2Pass;
    
    // Старые поля для обратной совместимости
    private final String pgUrl;
    private final String pgUser;
    private final String pgPass;
    
    private final String redisHost;
    private final int redisPort;
    private final String natsUrl;
    private final int httpPort;

    public AppConfig() {
        // Primary database configuration
        String pgPrimaryHost = getenv("PG_PRIMARY_HOST", "localhost");
        int pgPrimaryPort = Integer.parseInt(getenv("PG_PRIMARY_PORT", "5432"));
        String pgPrimaryDb = getenv("PG_PRIMARY_DB", "econ");
        String pgPrimaryUser = getenv("PG_PRIMARY_USER", "game");
        String pgPrimaryPass = getenv("PG_PRIMARY_PASS", "gamepass");
        String pgPrimaryUrl = "jdbc:postgresql://" + pgPrimaryHost + ":" + pgPrimaryPort + "/" + pgPrimaryDb + "?sslmode=disable";

        // Replica 1 configuration
        String pgReplica1Host = getenv("PG_REPLICA1_HOST", "localhost");
        int pgReplica1Port = Integer.parseInt(getenv("PG_REPLICA1_PORT", "5433"));
        String pgReplica1Db = getenv("PG_REPLICA1_DB", "econ");
        String pgReplica1User = getenv("PG_REPLICA1_USER", "game");
        String pgReplica1Pass = getenv("PG_REPLICA1_PASS", "gamepass");
        String pgReplica1Url = "jdbc:postgresql://" + pgReplica1Host + ":" + pgReplica1Port + "/" + pgReplica1Db + "?sslmode=disable";

        // Replica 2 configuration
        String pgReplica2Host = getenv("PG_REPLICA2_HOST", "localhost");
        int pgReplica2Port = Integer.parseInt(getenv("PG_REPLICA2_PORT", "5434"));
        String pgReplica2Db = getenv("PG_REPLICA2_DB", "econ");
        String pgReplica2User = getenv("PG_REPLICA2_USER", "game");
        String pgReplica2Pass = getenv("PG_REPLICA2_PASS", "gamepass");
        String pgReplica2Url = "jdbc:postgresql://" + pgReplica2Host + ":" + pgReplica2Port + "/" + pgReplica2Db + "?sslmode=disable";

        // Fallback для старого кода
        String pgHost = getenv("PG_HOST", pgPrimaryHost);
        int pgPort = Integer.parseInt(getenv("PG_PORT", String.valueOf(pgPrimaryPort)));
        String pgDb = getenv("PG_DB", pgPrimaryDb);
        String pgUser = getenv("PG_USER", pgPrimaryUser);
        String pgPass = getenv("PG_PASS", pgPrimaryPass);
        String pgUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb + "?sslmode=disable";

        String redisHost = getenv("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(getenv("REDIS_PORT", "6379"));

        String natsUrl = getenv("NATS_URL", "nats://localhost:4222");
        int httpPort = Integer.parseInt(getenv("HTTP_PORT", "8081"));

        // Primary database
        this.pgPrimaryUrl = pgPrimaryUrl;
        this.pgPrimaryUser = pgPrimaryUser;
        this.pgPrimaryPass = pgPrimaryPass;
        
        // Replicas
        this.pgReplica1Url = pgReplica1Url;
        this.pgReplica1User = pgReplica1User;
        this.pgReplica1Pass = pgReplica1Pass;
        
        this.pgReplica2Url = pgReplica2Url;
        this.pgReplica2User = pgReplica2User;
        this.pgReplica2Pass = pgReplica2Pass;
        
        // Обратная совместимость
        this.pgUrl = pgUrl;
        this.pgUser = pgUser;
        this.pgPass = pgPass;
        
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.natsUrl = natsUrl;
        this.httpPort = httpPort;
    }

    // Getters для primary database
    public String pgPrimaryUrl() { return pgPrimaryUrl; }
    public String pgPrimaryUser() { return pgPrimaryUser; }
    public String pgPrimaryPass() { return pgPrimaryPass; }
    
    // Getters для replica databases
    public String pgReplica1Url() { return pgReplica1Url; }
    public String pgReplica1User() { return pgReplica1User; }
    public String pgReplica1Pass() { return pgReplica1Pass; }
    
    public String pgReplica2Url() { return pgReplica2Url; }
    public String pgReplica2User() { return pgReplica2User; }
    public String pgReplica2Pass() { return pgReplica2Pass; }
    
    // Getters для обратной совместимости
    public String pgUrl() { return pgUrl; }
    public String pgUser() { return pgUser; }
    public String pgPass() { return pgPass; }
    public String redisHost() { return redisHost; }
    public int redisPort() { return redisPort; }
    public String natsUrl() { return natsUrl; }
    public int httpPort() { return httpPort; }

    private static String getenv(String k, String def) {
        String v = System.getenv(k);
        return v != null ? v : def;
    }
}