package com.example.economy.core;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;
import java.util.function.Function;
import java.util.logging.Logger;

@ApplicationScoped
public class Database {
    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private final Random random = new Random();

    @Inject
    @DataSource("write")
    AgroalDataSource primaryDataSource; // Primary (default) - для записи

    @Inject
    @DataSource("read")
    AgroalDataSource replicaDataSource; // Replica - для чтения

    /** Вернуть Connection для записи (primary) */
    public Connection getWriteConnection() throws SQLException {
        return primaryDataSource.getConnection();
    }

    /** Вернуть Connection для чтения (replica) */
    public Connection getReadConnection() throws SQLException {
        try {
            // Используем replica datasource для чтения
            return replicaDataSource.getConnection();
        } catch (SQLException e) {
            // Fallback на primary если replica недоступна
            LOG.warning("Replica connection failed, falling back to primary: " + e.getMessage());
            return primaryDataSource.getConnection();
        }
    }

    /** Старый метод для обратной совместимости - использует primary */
    @Deprecated
    public Connection get() throws SQLException {
        return getWriteConnection();
    }

    /** Helper для операций записи */
    public <T> T withWriteConnection(Function<Connection, T> work) {
        try (Connection conn = getWriteConnection()) {
            return work.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("DB write error", e);
        }
    }

    /** Helper для операций чтения */
    public <T> T withReadConnection(Function<Connection, T> work) {
        try (Connection conn = getReadConnection()) {
            return work.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("DB read error", e);
        }
    }

    /** Старый метод для обратной совместимости - использует primary */
    @Deprecated
    public <T> T withConnection(Function<Connection, T> work) {
        return withWriteConnection(work);
    }

    /** Получить конкретную replica по номеру (1 или 2) */
    public Connection getReplicaConnection(int replicaNumber) throws SQLException {
        // Всегда возвращаем replica datasource, так как у нас только один источник для чтения
        return replicaDataSource.getConnection();
    }

    /** Проверка доступности replica */
    public boolean isReplicaAvailable(int replicaNumber) {
        try (Connection conn = getReplicaConnection(replicaNumber)) {
            return conn.isValid(1); // 1 секунда timeout
        } catch (Exception e) {
            LOG.warning("Replica " + replicaNumber + " is not available: " + e.getMessage());
            return false;
        }
    }

    /** Статус всех соединений */
    public String getConnectionStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Primary: ");
        try (Connection conn = primaryDataSource.getConnection()) {
            status.append(conn.isValid(1) ? "OK" : "FAILED");
        } catch (Exception e) {
            status.append("ERROR - ").append(e.getMessage());
        }
        
        status.append(", Replica: ");
        status.append(isReplicaAvailable(1) ? "OK" : "FAILED");
        
        return status.toString();
    }
}
