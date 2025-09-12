#!/bin/bash
set -e

echo "🚀 Запуск полной системы Minecraft Economy + GravitLauncher"
echo "=================================================="

# Функция для ожидания готовности сервиса
wait_for_service() {
    local service=$1
    local port=$2
    local max_attempts=30
    local attempt=1
    
    echo "⏳ Ожидание готовности $service на порту $port..."
    
    while [ $attempt -le $max_attempts ]; do
        if docker compose exec $service pg_isready -p $port 2>/dev/null || \
           curl -s http://localhost:$port/health 2>/dev/null || \
           nc -z localhost $port 2>/dev/null; then
            echo "✅ $service готов!"
            return 0
        fi
        echo "   Попытка $attempt/$max_attempts..."
        sleep 5
        ((attempt++))
    done
    
    echo "❌ $service не готов после $max_attempts попыток"
    return 1
}

# Шаг 1: Остановка и очистка (если нужно)
echo "🛑 Остановка существующих контейнеров..."
docker compose down

# Шаг 2: Удаление старых volumes для чистой установки
echo "🧹 Очистка старых данных (volumes)..."
docker volume rm -f economy-minecraft_pgdata_primary economy-minecraft_pgdata_replica1 economy-minecraft_pgdata_replica2 2>/dev/null || true

# Шаг 3: Запуск PostgreSQL Primary
echo "🐘 Запуск PostgreSQL Primary..."
docker compose up -d postgres-primary

# Ожидание готовности PostgreSQL Primary
wait_for_service postgres-primary 5432

# Шаг 4: Запуск Redis и NATS
echo "🔄 Запуск Redis и NATS..."
docker compose up -d redis nats

# Шаг 5: Запуск PgBouncer Master
echo "🎯 Запуск PgBouncer Master..."
docker compose up -d pgbouncer-master

# Ожидание готовности PgBouncer
sleep 10

# Шаг 6: Запуск PostgreSQL Replicas
echo "🐘 Запуск PostgreSQL Replicas..."
docker compose up -d postgres-replica1 postgres-replica2

# Ожидание готовности реплик
sleep 15

# Шаг 7: Запуск PgBouncer для реплик
echo "🎯 Запуск PgBouncer для реплик..."
docker compose up -d pgbouncer-replica1 pgbouncer-replica2

# Шаг 8: Запуск Economy Quarkus API
echo "⚡ Запуск Economy Quarkus API..."
docker compose up -d economy-quarkus

# Ожидание готовности API
wait_for_service economy-quarkus 8081

# Шаг 9: Создание таблиц для GravitLauncher
echo "🗄️ Создание таблиц для GravitLauncher..."
sleep 5

docker compose exec postgres-primary psql -U game -d econ -c "
-- Добавляем колонки для GravitLauncher
ALTER TABLE players ADD COLUMN IF NOT EXISTS access_token text, ADD COLUMN IF NOT EXISTS server_id text;

-- Создаем индексы
CREATE INDEX IF NOT EXISTS ix_players_access_token ON players (access_token) WHERE access_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_players_server_id ON players (server_id) WHERE server_id IS NOT NULL;

-- Таблица разрешений
CREATE TABLE IF NOT EXISTS user_permissions (
    uuid uuid NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    name varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_permissions_uuid_name ON user_permissions (uuid, name);

-- Даем права администратора пользователю dev
INSERT INTO user_permissions (uuid, name)
SELECT uuid, 'launchserver.*'
FROM players 
WHERE username = 'dev'
ON CONFLICT (uuid, name) DO NOTHING;

INSERT INTO user_permissions (uuid, name)
SELECT uuid, 'launchserver.profile.*'
FROM players 
WHERE username = 'dev'
ON CONFLICT (uuid, name) DO NOTHING;
"

# Шаг 10: Запуск GravitLauncher
echo "🚀 Запуск GravitLauncher..."
docker compose up -d gravitlauncher gravitlauncher-nginx

# Финальная проверка статуса
echo "📊 Проверка статуса сервисов..."
sleep 10

echo "=================================================="
echo "🎉 СИСТЕМА ЗАПУЩЕНА!"
echo "=================================================="
echo ""
echo "📋 Доступные сервисы:"
echo "   🐘 PostgreSQL Primary:    localhost:5432"
echo "   🐘 PostgreSQL Replica1:   localhost:5433"
echo "   🐘 PostgreSQL Replica2:   localhost:5434"
echo "   🎯 PgBouncer Master:       localhost:6432"
echo "   🎯 PgBouncer Replica1:     localhost:7432"
echo "   🎯 PgBouncer Replica2:     localhost:7433"
echo "   🔄 Redis:                  localhost:6379"
echo "   📡 NATS:                   localhost:4222"
echo "   ⚡ Economy API:            http://localhost:8081"
echo "   🚀 GravitLauncher:         http://localhost:9274"
echo "   🌐 GravitLauncher (Nginx): http://localhost:17549"
echo ""
echo "🔍 Проверка статуса:"
echo "   docker compose ps"
echo "   docker compose logs [service-name]"
echo ""
echo "✅ Система готова к использованию!"
