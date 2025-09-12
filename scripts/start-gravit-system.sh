#!/bin/bash

echo "🚀 Запуск системы Economy Minecraft с GraviteLauncher"
echo "=================================================="

# Остановка существующих контейнеров
echo "📦 Остановка существующих контейнеров..."
docker-compose down

echo ""
echo "🗄️  Запуск базы данных и инфраструктуры..."
docker-compose up -d postgres-primary postgres-replica1 postgres-replica2

echo "⏳ Ожидание готовности PostgreSQL..."
sleep 15

echo ""
echo "🔄 Запуск PgBouncer..."
docker-compose up -d pgbouncer-master pgbouncer-replica1 pgbouncer-replica2

echo ""
echo "📨 Запуск Redis и NATS..."
docker-compose up -d redis nats

echo ""
echo "🎯 Запуск Economy Quarkus API..."
docker-compose up -d economy-quarkus

echo ""
echo "🚀 Запуск GraviteLauncher..."
docker-compose up -d gravit-launcher

echo "⏳ Ожидание готовности GraviteLauncher..."
sleep 20

echo ""
echo "🎮 Генерация токена сервера..."
./scripts/generate-server-token.sh EconomyServer

echo ""
echo "🎮 Запуск Minecraft сервера..."
docker-compose up -d minecraft-server

echo ""
echo "✅ Система запущена!"
echo ""
echo "🌐 Доступные сервисы:"
echo "- LaunchServer:      http://localhost:9274"
echo "- WebSocket:         ws://localhost:9275"
echo "- Minecraft Server:  localhost:25565"
echo "- Economy API:       http://localhost:8081"
echo "- PostgreSQL:        localhost:5432"
echo "- PgBouncer Master:  localhost:6432"
echo "- Redis:             localhost:6379"
echo "- NATS:              localhost:4222"
echo ""
echo "📋 Проверка статуса:"
echo "docker-compose ps"
echo ""
echo "📄 Просмотр логов:"
echo "docker-compose logs -f gravit-launcher"
echo "docker-compose logs -f minecraft-server"
echo ""
echo "👤 Администратор по умолчанию:"
echo "Логин: admin"
echo "Пароль: admin123"
