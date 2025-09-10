#!/bin/bash
set -e

# Скрипт для настройки PostgreSQL replica

# Ждем когда primary сервер будет готов
echo "Waiting for primary server to be ready..."
until pg_isready -h ${POSTGRES_PRIMARY_HOST} -p 5432 -U ${POSTGRES_USER}; do
  sleep 2
done

echo "Primary server is ready. Setting up replica..."

# Очищаем директорию данных
rm -rf /var/lib/postgresql/data/*

# Определяем номер реплики по переменным окружения или имени контейнера
if [ "$POSTGRES_SERVICE_NAME" = "postgres-replica2" ] || echo "$HOSTNAME" | grep -q "replica2"; then
    SLOT_NAME="replica2_slot"  
    REPLICA_NAME="replica2"
elif [ "$POSTGRES_SERVICE_NAME" = "postgres-replica1" ] || echo "$HOSTNAME" | grep -q "replica1"; then
    SLOT_NAME="replica1_slot"
    REPLICA_NAME="replica1"
else
    # Fallback: определяем по случайному числу
    if [ $(($(date +%s) % 2)) -eq 0 ]; then
        SLOT_NAME="replica1_slot"
        REPLICA_NAME="replica1"
    else
        SLOT_NAME="replica2_slot"
        REPLICA_NAME="replica2"
    fi
fi

echo "Setting up $REPLICA_NAME with slot $SLOT_NAME"

# Создаем базовый backup с primary сервера
echo "Creating base backup from primary..."

# Устанавливаем пароль для pg_basebackup
export PGPASSWORD=${POSTGRES_REPLICATION_PASSWORD}

pg_basebackup \
    -h ${POSTGRES_PRIMARY_HOST} \
    -D /var/lib/postgresql/data \
    -U ${POSTGRES_REPLICATION_USER} \
    -v \
    -R \
    -X stream \
    -S ${SLOT_NAME}

# Настраиваем postgresql.conf для replica
echo "Configuring replica settings..."
cat >> /var/lib/postgresql/data/postgresql.conf << EOF

# Replica configuration
hot_standby = on
hot_standby_feedback = on
max_standby_streaming_delay = 30s
max_standby_archive_delay = 30s
wal_receiver_status_interval = 2s
wal_receiver_timeout = 30s

# Performance tuning for read-only workload
effective_cache_size = 256MB
shared_buffers = 64MB
maintenance_work_mem = 32MB
checkpoint_completion_target = 0.9
random_page_cost = 1.1

EOF

# Настраиваем recovery.conf для streaming replication
cat > /var/lib/postgresql/data/postgresql.auto.conf << EOF
# Automatic replica configuration
primary_conninfo = 'host=${POSTGRES_PRIMARY_HOST} port=5432 user=${POSTGRES_REPLICATION_USER} password=${POSTGRES_REPLICATION_PASSWORD} application_name=${SLOT_NAME}'
primary_slot_name = '${SLOT_NAME}'
EOF

# Создаем standby.signal для указания что это replica
touch /var/lib/postgresql/data/standby.signal

echo "Replica setup complete. Starting PostgreSQL..."

# Запускаем PostgreSQL
exec postgres
