package com.example.economy.core;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Роутер для разделения read/write операций между primary и replica базами данных
 */
@ApplicationScoped
public class DatabaseRouter {
    private static final Logger LOG = Logger.getLogger(DatabaseRouter.class);

    @Inject
    @DataSource("write")
    AgroalDataSource writeDataSource;

    @Inject
    @DataSource("read")
    AgroalDataSource readDataSource;

    // Fallback на write datasource если read недоступен
    @Inject
    AgroalDataSource defaultDataSource;

    /**
     * Получить соединение для записи (всегда primary)
     */
    public Connection getWriteConnection() throws SQLException {
        return writeDataSource.getConnection();
    }

    /**
     * Получить соединение для чтения (replica с fallback на primary)
     */
    public Connection getReadConnection() throws SQLException {
        try {
            return readDataSource.getConnection();
        } catch (SQLException e) {
            LOG.warnf("Read replica unavailable, falling back to primary: %s", e.getMessage());
            return writeDataSource.getConnection();
        }
    }

    /**
     * Выполнить операцию записи
     */
    public <T> T executeWrite(Function<Connection, T> operation) {
        try (Connection conn = getWriteConnection()) {
            return operation.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Write operation failed", e);
        }
    }

    /**
     * Выполнить операцию чтения
     */
    public <T> T executeRead(Function<Connection, T> operation) {
        try (Connection conn = getReadConnection()) {
            return operation.apply(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Read operation failed", e);
        }
    }

    /**
     * Выполнить транзакцию записи
     */
    public <T> T executeWriteTransaction(Function<Connection, T> operation) {
        try (Connection conn = getWriteConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = operation.apply(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Write transaction failed", e);
        }
    }

    /**
     * Проверка доступности datasources для health check
     */
    public boolean isWriteHealthy() {
        try (Connection conn = writeDataSource.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            LOG.errorf("Write datasource health check failed: %s", e.getMessage());
            return false;
        }
    }

    public boolean isReadHealthy() {
        try (Connection conn = readDataSource.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            LOG.debugf("Read datasource health check failed: %s", e.getMessage());
            return false;
        }
    }
}