# PgBouncer Connection Pooling Setup

## Overview
This directory contains the configuration files for PgBouncer, a connection pooler for PostgreSQL. PgBouncer helps manage database connections efficiently by pooling and reusing connections, reducing the overhead of creating new connections for each request.

## Configuration Files

### pgbouncer.ini
Main configuration file that defines:
- Database mappings for master and replicas
- Connection pooling settings
- Authentication settings
- Network settings

### userlist.txt
User authentication file containing username/password pairs for PgBouncer authentication.

## Docker Services

Three PgBouncer services are defined in docker-compose.yml:

1. **pgbouncer-master** - Connection pooler for the PostgreSQL master database (write operations)
   - Port: 6432
   - Connects to: postgres-primary:5432

2. **pgbouncer-replica1** - Connection pooler for the first PostgreSQL replica (read operations)
   - Port: 6433
   - Connects to: postgres-replica1:5432

3. **pgbouncer-replica2** - Connection pooler for the second PostgreSQL replica (read operations)
   - Port: 6434
   - Connects to: postgres-replica2:5432

## Connection Settings

The Quarkus application is configured to connect through PgBouncer:
- Primary (write): jdbc:postgresql://localhost:6432/econ
- Replica 1 (read): jdbc:postgresql://localhost:6433/econ
- Replica 2 (read): jdbc:postgresql://localhost:6434/econ

## Benefits

- Reduced database connection overhead
- Better resource utilization
- Improved application performance under load
- Connection limits enforcement
- Automatic connection cleanup

## Monitoring

PgBouncer provides statistics and monitoring capabilities:
- Connection counts
- Query statistics
- Pool status information

Admin users can connect to PgBouncer directly for monitoring and management.