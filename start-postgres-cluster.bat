@echo off
echo Starting PostgreSQL Master-Slave cluster...

echo.
echo Stopping existing containers...
docker-compose down

echo.
echo Removing old volumes to ensure clean setup...
docker volume rm minecraft_pgdata_replica1 minecraft_pgdata_replica2 2>nul

echo.
echo Removing replica containers to rebuild them...
docker-compose rm -f postgres-replica1 postgres-replica2 2>nul

echo.
echo Starting PostgreSQL Primary server...
docker-compose up -d postgres-primary

echo.
echo Waiting for Primary to be ready...
timeout /t 10 /nobreak >nul

echo.
echo Starting Replica servers...
docker-compose up -d postgres-replica1 postgres-replica2

echo.
echo Starting other services...
docker-compose up -d redis nats qdrant

echo.
echo PostgreSQL cluster setup complete!
echo.
echo Services:
echo - Primary (Write):  localhost:5432
echo - Replica 1 (Read): localhost:5433  
echo - Replica 2 (Read): localhost:5434
echo - Redis:            localhost:6379
echo - NATS:             localhost:4222
echo - Qdrant:           localhost:6333
echo.
echo Check cluster status:
echo docker-compose ps
echo.
echo View logs:
echo docker-compose logs postgres-primary
echo docker-compose logs postgres-replica1
echo docker-compose logs postgres-replica2
