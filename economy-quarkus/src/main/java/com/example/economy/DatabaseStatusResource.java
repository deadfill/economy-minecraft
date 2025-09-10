package com.example.economy;

import com.example.economy.core.Database;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * REST endpoint для мониторинга состояния баз данных и репликации
 */
@Path("/admin/database")
@ApplicationScoped
public class DatabaseStatusResource {
    private static final Logger LOG = Logger.getLogger(DatabaseStatusResource.class.getName());
    
    @Inject
    Database database;

    /**
     * Тестовый endpoint без авторизации
     */
    @GET
    @Path("/test")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getTest() {
        Map<String, Object> test = new HashMap<>();
        test.put("status", "OK");
        test.put("message", "Database endpoints working");
        test.put("timestamp", System.currentTimeMillis());
        return test;
    }

    /**
     * Общий статус всех соединений БД
     */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    // @RolesAllowed({"admin", "developer"}) // Временно отключено для тестирования
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connection_status", database.getConnectionStatus());
        status.put("timestamp", System.currentTimeMillis());
        
        // Дополнительная информация о репликации
        status.put("replication_info", getReplicationInfo());
        
        return status;
    }

    /**
     * Детальная информация о репликации
     */
    @GET
    @Path("/replication")
    @Produces(MediaType.APPLICATION_JSON)
    // @RolesAllowed({"admin", "developer"}) // Временно отключено для тестирования
    public Map<String, Object> getReplicationStatus() {
        return getReplicationInfo();
    }

    /**
     * Тестирование соединений с репликами
     */
    @GET
    @Path("/test-replicas")
    @Produces(MediaType.APPLICATION_JSON)
    // @RolesAllowed({"admin", "developer"}) // Временно отключено для тестирования
    public Map<String, Object> testReplicas() {
        Map<String, Object> results = new HashMap<>();
        
        // Тест replica 1
        results.put("replica1", testReplicaConnection(1));
        
        // Тест replica 2
        results.put("replica2", testReplicaConnection(2));
        
        // Тест чтения из случайной реплики
        results.put("random_read_test", testRandomRead());
        
        return results;
    }

    private Map<String, Object> getReplicationInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try (Connection conn = database.getWriteConnection()) {
            // Проверяем статус репликации на primary
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT slot_name, slot_type, active, restart_lsn, confirmed_flush_lsn " +
                    "FROM pg_replication_slots")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Object> slots = new HashMap<>();
                    while (rs.next()) {
                        Map<String, Object> slot = new HashMap<>();
                        slot.put("type", rs.getString("slot_type"));
                        slot.put("active", rs.getBoolean("active"));
                        slot.put("restart_lsn", rs.getString("restart_lsn"));
                        slot.put("confirmed_flush_lsn", rs.getString("confirmed_flush_lsn"));
                        slots.put(rs.getString("slot_name"), slot);
                    }
                    info.put("replication_slots", slots);
                }
            }
            
            // Информация о состоянии репликации
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT client_addr, state, sync_state, " +
                    "pg_wal_lsn_diff(pg_current_wal_lsn(), flush_lsn) as lag_bytes " +
                    "FROM pg_stat_replication")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Object> replicas = new HashMap<>();
                    int counter = 1;
                    while (rs.next()) {
                        Map<String, Object> replica = new HashMap<>();
                        replica.put("client_addr", rs.getString("client_addr"));
                        replica.put("state", rs.getString("state"));
                        replica.put("sync_state", rs.getString("sync_state"));
                        replica.put("lag_bytes", rs.getLong("lag_bytes"));
                        replicas.put("replica_" + counter++, replica);
                    }
                    info.put("connected_replicas", replicas);
                }
            }
            
        } catch (Exception e) {
            LOG.warning("Failed to get replication info: " + e.getMessage());
            info.put("error", e.getMessage());
        }
        
        return info;
    }

    private Map<String, Object> testReplicaConnection(int replicaNumber) {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection conn = database.getReplicaConnection(replicaNumber)) {
            // Простой тест соединения
            try (PreparedStatement ps = conn.prepareStatement("SELECT current_timestamp, current_database()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("status", "OK");
                        result.put("current_time", rs.getTimestamp(1).toString());
                        result.put("database", rs.getString(2));
                        result.put("available", true);
                    }
                }
            }
            
            // Проверка режима только для чтения
            try (PreparedStatement ps = conn.prepareStatement("SELECT pg_is_in_recovery()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("is_replica", rs.getBoolean(1));
                    }
                }
            }
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
            result.put("available", false);
        }
        
        return result;
    }

    private Map<String, Object> testRandomRead() {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection conn = database.getReadConnection()) {
            // Тест чтения данных
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) as player_count FROM players")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("status", "OK");
                        result.put("players_count", rs.getLong(1));
                    }
                }
            }
            
            // Проверяем что это replica
            try (PreparedStatement ps = conn.prepareStatement("SELECT pg_is_in_recovery()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("read_from_replica", rs.getBoolean(1));
                    }
                }
            }
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
