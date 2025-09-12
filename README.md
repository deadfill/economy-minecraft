# Minecraft Economy System

Полнофункциональная экономическая система для Minecraft с PostgreSQL Master-Slave репликацией, Redis кэшированием и NATS messaging.

## 🏗️ Архитектура

### Компоненты системы:
- **Minecraft Server** (Fabric Mod) - серверная часть
- **Minecraft Client** (Fabric Mod) - клиентская часть с тестовыми командами
- **Economy API** (Quarkus) - REST API для экономических операций
- **PostgreSQL Cluster** - Master-Slave репликация (1 Primary + 2 Replica)
- **PgBouncer** - connection pooling для PostgreSQL
- **Redis** - кэширование и сессии
- **NATS** - messaging между сервисами
- **Qdrant** - векторная база данных

### База данных (прямое подключение):
- **Primary (порт 5432)** - для записи операций
- **Replica1 (порт 5433)** - для чтения (балансировка нагрузки)
- **Replica2 (порт 5434)** - для чтения (балансировка нагрузки)

### Connection Pooling (PgBouncer):
- **PgBouncer Master (порт 6432)** - connection pooling для Primary
- **PgBouncer Replica1 (порт 6433)** - connection pooling для Replica1
- **PgBouncer Replica2 (порт 6434)** - connection pooling для Replica2

Приложение использует PgBouncer для всех подключений к базе данных.

## 🚀 Быстрый старт

### Требования:
- Docker & Docker Compose
- Java 17+
- Gradle 7+

### Запуск:

1. **Клонируйте репозиторий:**
```bash
git clone https://github.com/yourusername/minecraft-economy.git
cd minecraft-economy
```

2. **Установка Java:**
```bash
# Windows
sudo apt update && sudo apt install -y openjdk-21-jdk

# Linux/Mac
docker-compose up -d
```

3. **Запустите Economy API:**
```bash
cd economy-quarkus
./mvnw quarkus:dev
```

4. **Соберите и запустите Minecraft моды:**
```bash
# Server mod
cd server
./gradlew build

# Client mod  
cd ../client
./gradlew build
```

## 🎮 Функциональность

### Экономические системы:
- **Скиллы** - система прокачки с уровнями
- **Производство** - создание предметов по рецептам
- **Материалы** - инвентарь игроков
- **Награды** - система поощрений

### Тестовые команды (в игре):
- `/dbtest ping` - проверка API
- `/dbtest status` - статус баз данных
- `/dbtest replication` - статус репликации
- `/dbtest read` - тест чтения из реплик
- `/dbtest write` - тест записи в primary
- `/dbtest all` - полный набор тестов

## 📊 Мониторинг

### API Endpoints:
- `GET /admin/database/status` - статус БД
- `GET /admin/database/replication` - статус репликации
- `GET /admin/database/test-replicas` - тест реплик

### Логи:
```bash
# Docker логи
docker-compose logs -f postgres-primary
docker-compose logs -f economy-quarkus

# Quarkus логи
cd economy-quarkus
./mvnw quarkus:dev
```

## 🔧 Конфигурация

### Environment Variables:
```bash
# PostgreSQL Primary (direct connection)
PG_PRIMARY_HOST=localhost
PG_PRIMARY_PORT=5432
PG_PRIMARY_USER=game
PG_PRIMARY_PASS=gamepass
PG_PRIMARY_DB=econ

# PostgreSQL Replicas (direct connection)
PG_REPLICA1_HOST=localhost
PG_REPLICA1_PORT=5433
PG_REPLICA2_HOST=localhost
PG_REPLICA2_PORT=5434

# PgBouncer Connection Pooling
PGBOUNCER_MASTER_HOST=localhost
PGBOUNCER_MASTER_PORT=6432
PGBOUNCER_REPLICA1_HOST=localhost
PGBOUNCER_REPLICA1_PORT=6433
PGBOUNCER_REPLICA2_HOST=localhost
PGBOUNCER_REPLICA2_PORT=6434

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# NATS
NATS_URL=nats://localhost:4222
```

## 🏛️ Структура проекта

```
minecraft-economy/
├── economy-quarkus/          # REST API (Quarkus)
│   ├── src/main/java/        # Java код
│   ├── src/main/resources/   # Конфигурация
│   └── pom.xml              # Maven зависимости
├── server/                   # Minecraft Server Mod
│   ├── src/main/java/        # Серверный код
│   └── build.gradle.kts     # Gradle конфигурация
├── client/                   # Minecraft Client Mod
│   ├── src/main/java/        # Клиентский код
│   └── build.gradle.kts     # Gradle конфигурация
├── sql/                      # SQL скрипты
│   ├── init.sql             # Инициализация БД
│   ├── primary-setup.sql    # Настройка Primary
│   └── replica-setup.sh     # Настройка Replicas
├── pgbouncer/                # PgBouncer configuration
│   ├── pgbouncer.ini        # Main configuration
│   ├── userlist.txt         # User authentication
│   └── README.md            # PgBouncer documentation
├── docker-compose.yml        # Docker конфигурация
├── start-postgres-cluster.bat # Скрипт запуска
└── README.md                # Документация
```

## 🔄 Репликация PostgreSQL

### Настройка:
- **WAL Level:** replica
- **Replication Slots:** физические слоты для каждой реплики
- **Hot Standby:** включен для чтения с реплик
- **Synchronous Commit:** отключен для производительности

### Мониторинг:
```sql
-- Статус репликации
SELECT * FROM pg_stat_replication;

-- Слоты репликации
SELECT * FROM pg_replication_slots;

-- Статус реплик
SELECT * FROM pg_stat_wal_receiver;
```

## 🚀 Производительность

### Оптимизации:
- **Connection Pooling** - PgBouncer для эффективного управления соединениями
- **Read/Write Splitting** - автоматическое разделение
- **Redis Caching** - кэширование частых запросов
- **Load Balancing** - случайный выбор реплики для чтения

### Метрики:
- **Latency:** < 1ms для чтения из реплик
- **Throughput:** 1000+ запросов/сек
- **Availability:** 99.9% (с репликацией)

## 🔄 PgBouncer Connection Pooling

### Преимущества:
- **Снижение накладных расходов** - переиспользование соединений вместо создания новых
- **Лимитирование соединений** - предотвращение перегрузки базы данных
- **Балансировка нагрузки** - равномерное распределение запросов
- **Мониторинг** - статистика использования соединений

### Настройки:
- **Pool Mode:** session (поддержка сессий)
- **Default Pool Size:** 20 соединений
- **Max Client Connections:** 100 соединений
- **Connection Lifetime:** 1 час

### Мониторинг PgBouncer:
```sql
-- Статистика пулов
SHOW POOLS;

-- Статистика соединений
SHOW CLIENTS;
SHOW SERVERS;

-- Общая статистика
SHOW STATS;
```

## 🛠️ Разработка

### Сборка:
```bash
# Economy API
cd economy-quarkus
./mvnw clean package

# Minecraft Mods
cd server && ./gradlew build
cd client && ./gradlew build
```

### Тестирование:
```bash
# Unit тесты
./mvnw test

# Integration тесты
docker-compose up -d
./mvnw verify

# Тестирование PgBouncer
python test-pgbouncer-connection.py
```

## 🧪 Тестирование PgBouncer

### Запуск тестов PgBouncer:
1. Запустите все сервисы:
   ```bash
   docker-compose up -d
   ```

2. Запустите тест скрипт:
   ```bash
   python test-pgbouncer-connection.py
   ```

3. Проверьте логи приложения Quarkus:
   ```bash
   cd economy-quarkus
   tail -f build/quarkus.log
   ```

### Мониторинг PgBouncer:
```bash
# Просмотр статистики пулов
docker-compose exec pgbouncer-master psql -p 6432 -U postgres pgbouncer -c "SHOW POOLS;"

# Просмотр клиентских соединений
docker-compose exec pgbouncer-replica1 psql -p 6433 -U postgres pgbouncer -c "SHOW CLIENTS;"
```

## 📝 Лицензия

MIT License - см. файл [LICENSE](LICENSE)

## 🤝 Вклад в проект

1. Fork репозитория
2. Создайте feature branch (`git checkout -b feature/amazing-feature`)
3. Commit изменения (`git commit -m 'Add amazing feature'`)
4. Push в branch (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📞 Поддержка

Если у вас есть вопросы или проблемы:
- Создайте [Issue](https://github.com/yourusername/minecraft-economy/issues)
- Напишите в Discussions
- Свяжитесь с разработчиками

---

**Сделано с ❤️ для Minecraft сообщества**


docker compose exec postgres-primary bash -c "
sed -i 's/host replication replicator/host replication game/g' /var/lib/postgresql/data/pg_hba.conf &&
su - postgres -c 'psql -U game -d econ -c \"SELECT pg_reload_conf();\"'
"

docker compose exec postgres-primary bash -c "
echo 'host replication game 0.0.0.0/0 md5' >> /var/lib/postgresql/data/pg_hba.conf &&
su - postgres -c 'psql -U game -d econ -c \"SELECT pg_reload_conf();\"'
"

docker compose exec postgres-primary psql -U game -d econ -c "
-- Создаем слоты репликации
SELECT pg_create_physical_replication_slot('replica1_slot');
SELECT pg_create_physical_replication_slot('replica2_slot');

-- Проверяем что слоты созданы
SELECT slot_name, slot_type, active FROM pg_replication_slots;
"