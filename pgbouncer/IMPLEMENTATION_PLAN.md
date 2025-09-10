# PgBouncer Implementation Plan

## Overview
This document outlines the steps to implement PgBouncer connection pooling for the Minecraft economy service. PgBouncer will help optimize database connections and improve performance.

## Current Architecture
The project currently uses:
- PostgreSQL with master-slave replication (1 primary, 2 replicas)
- Quarkus application connecting directly to PostgreSQL instances
- Read-write separation in the Database class

## PgBouncer Configuration

### 1. Configuration Files

#### pgbouncer.ini
Main PgBouncer configuration file that will define:
- Connection pooling settings
- Database mappings for master and replicas
- Authentication settings
- Pool modes (session, transaction, or statement)

#### userlist.txt
User authentication file containing:
- Database usernames and passwords
- Used for PgBouncer authentication

### 2. Docker Service Integration

Add PgBouncer as services in docker-compose.yml:
- pgbouncer-master: For write operations to primary database
- pgbouncer-replica1: For read operations to replica 1
- pgbouncer-replica2: For read operations to replica 2

### 3. Application Configuration Updates

Update economy-quarkus/src/main/resources/application.properties to:
- Point database connections to PgBouncer instead of direct PostgreSQL
- Update ports from 5432/5433/5434 to PgBouncer ports (e.g., 6432/6433/6434)

### 4. Connection Pooling Strategy

Implement connection pooling with:
- Session pooling mode for maintaining session consistency
- Appropriate pool sizes based on expected concurrent connections
- Connection lifetime settings to prevent stale connections

## Implementation Steps

1. Create PgBouncer configuration files
2. Add PgBouncer services to docker-compose.yml
3. Update application properties
4. Test connection pooling
5. Optimize performance settings

## Expected Benefits

- Reduced database connection overhead
- Better resource utilization
- Improved application performance under load

## Detailed Configuration

### PgBouncer Configuration (pgbouncer.ini)

```ini
[databases]
econ_master = host=postgres-primary port=5432 dbname=econ
econ_replica1 = host=postgres-replica1 port=5432 dbname=econ
econ_replica2 = host=postgres-replica2 port=5432 dbname=econ

[pgbouncer]
pool_mode = session
listen_port = 6432
listen_addr = 0.0.0.0
auth_type = md5
auth_file = /etc/pgbouncer/userlist.txt
logfile = /var/log/pgbouncer/pgbouncer.log
pidfile = /var/run/pgbouncer/pgbouncer.pid
admin_users = postgres
stats_users = stats, postgres
```

### User List (userlist.txt)

```
"game" "gamepass"
"postgres" "postgres"
```

### Docker Compose Services

Add three PgBouncer services:
1. pgbouncer-master (port 6432) -> postgres-primary:5432
2. pgbouncer-replica1 (port 6433) -> postgres-replica1:5432
3. pgbouncer-replica2 (port 6434) -> postgres-replica2:5432

### Application Properties Updates

Update database connection URLs:
- Primary (write): jdbc:postgresql://pgbouncer-master:6432/econ
- Replica 1 (read): jdbc:postgresql://pgbouncer-replica1:6433/econ
- Replica 2 (read): jdbc:postgresql://pgbouncer-replica2:6434/econ

## Testing Plan

1. Verify PgBouncer services start correctly
2. Test database connections through PgBouncer
3. Validate read-write separation still works
4. Monitor connection pooling metrics
5. Performance testing under load