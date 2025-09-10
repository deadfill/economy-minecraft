# Testing PgBouncer Integration

This document provides instructions on how to test the PgBouncer connection pooling integration with the Minecraft economy service.

## Prerequisites

1. Docker and Docker Compose installed
2. Python 3 with psycopg2 library (for connection testing)
3. All services should be built and configured

## Starting the Services

1. **Start the PostgreSQL cluster with PgBouncer:**
   ```bash
   docker-compose up -d postgres-primary postgres-replica1 postgres-replica2 pgbouncer-master pgbouncer-replica1 pgbouncer-replica2 redis nats
   ```

2. **Wait for services to initialize** (about 30-60 seconds)

3. **Start the economy service:**
   ```bash
   cd economy-quarkus
   ./gradlew quarkusDev
   ```

## Testing PgBouncer Connections

### Automated Test Script

Run the provided Python test script to verify all PgBouncer connections:

```bash
python test-pgbouncer-connection.py
```

This script will test:
- PgBouncer Master (port 6432) - Write operations
- PgBouncer Replica 1 (port 6433) - Read operations
- PgBouncer Replica 2 (port 6434) - Read operations

### Manual Testing with psql

You can also manually test connections using psql:

1. **Test PgBouncer Master connection:**
   ```bash
   psql -h localhost -p 6432 -U game -d econ
   ```

2. **Test PgBouncer Replica 1 connection:**
   ```bash
   psql -h localhost -p 6433 -U game -d econ
   ```

3. **Test PgBouncer Replica 2 connection:**
   ```bash
   psql -h localhost -p 6434 -U game -d econ
   ```

## Verifying Integration with Economy Service

### Check Application Logs

Monitor the Quarkus application logs for database connection messages:
```bash
cd economy-quarkus
tail -f build/quarkus.log
```

Look for messages like:
- Database connection established
- Connection pool initialization
- SQL queries being executed

### Test API Endpoints

Use curl or a REST client to test the API endpoints that interact with the database:

1. **Health check:**
   ```bash
   curl http://localhost:8081/health
   ```

2. **Database status:**
   ```bash
   curl http://localhost:8081/admin/database/status
   ```

3. **Test skill operations:**
   ```bash
   curl http://localhost:8081/api/v1/skills/list
   ```

## Monitoring PgBouncer

### View PgBouncer Statistics

Connect to any PgBouncer instance and run administrative commands:

```bash
psql -h localhost -p 6432 -U postgres pgbouncer
```

Then run these commands:
```sql
-- Show connection pools
SHOW POOLS;

-- Show client connections
SHOW CLIENTS;

-- Show server connections
SHOW SERVERS;

-- Show statistics
SHOW STATS;
```

### Docker Container Logs

Check PgBouncer container logs:
```bash
docker-compose logs pgbouncer-master
docker-compose logs pgbouncer-replica1
docker-compose logs pgbouncer-replica2
```

## Troubleshooting

### Common Issues

1. **Connection Refused:**
   - Ensure all containers are running: `docker-compose ps`
   - Check container logs for errors: `docker-compose logs <service-name>`
   - Verify port mappings in docker-compose.yml

2. **Authentication Failed:**
   - Check userlist.txt credentials match application.properties
   - Verify PostgreSQL user accounts exist and have correct passwords

3. **Database Not Reachable:**
   - Ensure PostgreSQL services are fully initialized before starting PgBouncer
   - Check network connectivity between containers

### Debugging Steps

1. **Verify container status:**
   ```bash
   docker-compose ps
   ```

2. **Check if services are listening on correct ports:**
   ```bash
   docker-compose port pgbouncer-master 5432
   docker-compose port pgbouncer-replica1 5432
   docker-compose port pgbouncer-replica2 5432
   ```

3. **Inspect container networking:**
   ```bash
   docker-compose exec pgbouncer-master cat /etc/hosts
   ```

## Expected Results

When everything is working correctly:

1. All PgBouncer connections should succeed
2. Economy service should connect through PgBouncer without errors
3. Database operations should work normally
4. Connection pooling statistics should show active connections
5. Application performance should be improved compared to direct connections

## Performance Verification

To verify the benefits of PgBouncer:

1. **Before PgBouncer** (comment out PgBouncer in application.properties):
   - Note connection establishment times
   - Monitor database connection count

2. **After PgBouncer** (use PgBouncer connections):
   - Connection times should be faster
   - Database connection count should be lower and more stable
   - Application should handle more concurrent requests

Compare the difference in performance metrics to validate the effectiveness of the connection pooling implementation.