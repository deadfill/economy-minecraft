#!/bin/bash
# Скрипт для настройки pg_hba.conf для репликации

echo "Setting up pg_hba.conf for replication..."

# Добавляем правила для репликации в pg_hba.conf
cat >> "$PGDATA/pg_hba.conf" << EOF

# Replication connections
host    replication     replicator      172.16.0.0/12           md5
host    replication     replicator      10.0.0.0/8              md5  
host    replication     replicator      192.168.0.0/16          md5
host    replication     replicator      0.0.0.0/0               md5

# Allow connections from replica servers
host    all             game            172.16.0.0/12           md5
host    all             game            10.0.0.0/8              md5
host    all             game            192.168.0.0/16          md5
EOF

echo "pg_hba.conf updated for replication"
