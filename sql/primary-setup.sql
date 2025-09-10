-- Настройка primary сервера для репликации

-- Создание пользователя для репликации
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'replicator') THEN
        CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'replicatorpass';
    END IF;
END
$$;

-- Создание слота репликации для каждой реплики (если не существуют)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'replica1_slot') THEN
        PERFORM pg_create_physical_replication_slot('replica1_slot');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'replica2_slot') THEN
        PERFORM pg_create_physical_replication_slot('replica2_slot');
    END IF;
END
$$;

-- Информация о статусе репликации
CREATE OR REPLACE VIEW replication_status AS
SELECT 
    slot_name,
    slot_type,
    active,
    restart_lsn,
    confirmed_flush_lsn
FROM pg_replication_slots;

-- Функция для мониторинга lag репликации
CREATE OR REPLACE FUNCTION replication_lag() 
RETURNS TABLE(client_addr inet, state text, sync_state text, lag_bytes bigint) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        r.client_addr,
        r.state,
        r.sync_state,
        pg_wal_lsn_diff(pg_current_wal_lsn(), r.flush_lsn) as lag_bytes
    FROM pg_stat_replication r;
END;
$$ LANGUAGE plpgsql;
