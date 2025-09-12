#!/bin/bash
# Скрипт установки Minecraft Economy System на сервер
# Поддерживает Ubuntu/Debian и CentOS/RHEL

set -e

echo "========================================"
echo "Установка Minecraft Economy System"
echo "========================================"

# Определяем дистрибутив
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$NAME
    VER=$VERSION_ID
else
    echo "Не удалось определить операционную систему"
    exit 1
fi

echo "Обнаружена ОС: $OS $VER"

# Функция для Ubuntu/Debian
install_ubuntu() {
    echo "Установка для Ubuntu/Debian..."
    
    # Обновляем систему
    sudo apt-get update
    sudo apt-get upgrade -y
    
    # Устанавливаем Docker
    if ! command -v docker &> /dev/null; then
        echo "Установка Docker..."
        curl -fsSL https://get.docker.com -o get-docker.sh
        sudo sh get-docker.sh
        sudo usermod -aG docker $USER
        rm get-docker.sh
    fi
    
    # Устанавливаем Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        echo "Установка Docker Compose..."
        sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        sudo chmod +x /usr/local/bin/docker-compose
    fi
    
    # Устанавливаем необходимые пакеты
    sudo apt-get install -y git curl wget unzip
}

# Функция для CentOS/RHEL
install_centos() {
    echo "Установка для CentOS/RHEL..."
    
    # Обновляем систему
    sudo yum update -y
    
    # Устанавливаем Docker
    if ! command -v docker &> /dev/null; then
        echo "Установка Docker..."
        sudo yum install -y yum-utils
        sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
        sudo yum install -y docker-ce docker-ce-cli containerd.io
        sudo systemctl start docker
        sudo systemctl enable docker
        sudo usermod -aG docker $USER
    fi
    
    # Устанавливаем Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        echo "Установка Docker Compose..."
        sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        sudo chmod +x /usr/local/bin/docker-compose
    fi
    
    # Устанавливаем необходимые пакеты
    sudo yum install -y git curl wget unzip
}

# Выбираем метод установки
case $OS in
    *"Ubuntu"*|*"Debian"*)
        install_ubuntu
        ;;
    *"CentOS"*|*"Red Hat"*|*"Rocky"*|*"AlmaLinux"*)
        install_centos
        ;;
    *)
        echo "Неподдерживаемая операционная система: $OS"
        exit 1
        ;;
esac

echo "========================================"
echo "Настройка системы"
echo "========================================"

# Создаем директорию проекта
PROJECT_DIR="/opt/minecraft-economy"
echo "Создание директории проекта: $PROJECT_DIR"
sudo mkdir -p $PROJECT_DIR
sudo chown $USER:$USER $PROJECT_DIR

# Клонируем репозиторий (если есть)
if [ -d ".git" ]; then
    echo "Копирование файлов проекта..."
    cp -r . $PROJECT_DIR/
else
    echo "ВНИМАНИЕ: Не обнаружен Git репозиторий"
    echo "Скопируйте файлы проекта в $PROJECT_DIR вручную"
fi

cd $PROJECT_DIR

# Создаем скрипты управления
echo "Создание скриптов управления..."

# Скрипт запуска
cat > start-system.sh << 'EOF'
#!/bin/bash
echo "========================================"
echo "Запуск Minecraft Economy System"
echo "========================================"

# Останавливаем существующие контейнеры
docker compose down

# Запускаем PostgreSQL кластер
echo "Запуск PostgreSQL кластера..."
docker compose up -d postgres-primary postgres-replica1 postgres-replica2

# Ждем готовности PostgreSQL
echo "Ожидание готовности PostgreSQL..."
sleep 15

# Запускаем PgBouncer
echo "Запуск PgBouncer..."
docker compose up -d pgbouncer-master pgbouncer-replica1 pgbouncer-replica2

# Ждем готовности PgBouncer
echo "Ожидание готовности PgBouncer..."
sleep 5

# Настраиваем репликацию
echo "Настройка репликации..."
docker compose exec postgres-primary bash -c "echo 'host replication game 0.0.0.0/0 md5' >> /var/lib/postgresql/data/pg_hba.conf"
docker compose exec postgres-primary bash -c "su - postgres -c 'psql -U game -d econ -c \"SELECT pg_reload_conf();\"'"
docker compose exec postgres-primary psql -U game -d econ -c "SELECT pg_create_physical_replication_slot('replica1_slot') ON CONFLICT DO NOTHING;"
docker compose exec postgres-primary psql -U game -d econ -c "SELECT pg_create_physical_replication_slot('replica2_slot') ON CONFLICT DO NOTHING;"

# Запускаем остальные сервисы
echo "Запуск остальных сервисов..."
docker compose up -d redis nats economy-quarkus

echo "========================================"
echo "Система запущена!"
echo "========================================"
echo "Сервисы:"
echo "- PostgreSQL Primary: localhost:5432"
echo "- PgBouncer Master: localhost:6432"
echo "- Economy API: localhost:8081"
echo "- Redis: localhost:6379"
echo "- NATS: localhost:4222"
echo ""
echo "Проверка статуса: docker compose ps"
echo "Просмотр логов: docker compose logs -f [service_name]"
EOF

# Скрипт остановки
cat > stop-system.sh << 'EOF'
#!/bin/bash
echo "Остановка Minecraft Economy System..."
docker compose down
echo "Система остановлена"
EOF

# Скрипт перезапуска
cat > restart-system.sh << 'EOF'
#!/bin/bash
echo "Перезапуск Minecraft Economy System..."
./stop-system.sh
sleep 2
./start-system.sh
EOF

# Скрипт полной очистки
cat > clean-restart.sh << 'EOF'
#!/bin/bash
echo "========================================"
echo "ПОЛНАЯ ОЧИСТКА И ПЕРЕЗАПУСК СИСТЕМЫ"
echo "========================================"
echo "ВНИМАНИЕ: Это удалит ВСЕ данные!"
echo ""

read -p "Вы уверены? (y/N): " confirm
if [[ $confirm != [yY] ]]; then
    echo "Отменено."
    exit 1
fi

echo "Полная остановка и очистка..."
docker compose down -v

echo "Сборка всех сервисов..."
docker compose build

echo "Запуск системы..."
./start-system.sh
EOF

# Делаем скрипты исполняемыми
chmod +x *.sh

# Создаем systemd сервис
echo "Создание systemd сервиса..."
sudo tee /etc/systemd/system/minecraft-economy.service > /dev/null << EOF
[Unit]
Description=Minecraft Economy System
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$PROJECT_DIR
ExecStart=$PROJECT_DIR/start-system.sh
ExecStop=$PROJECT_DIR/stop-system.sh
User=$USER
Group=$USER

[Install]
WantedBy=multi-user.target
EOF

# Перезагружаем systemd
sudo systemctl daemon-reload

echo "========================================"
echo "Установка завершена!"
echo "========================================"
echo ""
echo "Директория проекта: $PROJECT_DIR"
echo ""
echo "Управление системой:"
echo "  Запуск:     ./start-system.sh"
echo "  Остановка:  ./stop-system.sh"
echo "  Перезапуск: ./restart-system.sh"
echo "  Очистка:    ./clean-restart.sh"
echo ""
echo "Управление через systemd:"
echo "  Запуск:     sudo systemctl start minecraft-economy"
echo "  Остановка:  sudo systemctl stop minecraft-economy"
echo "  Статус:     sudo systemctl status minecraft-economy"
echo "  Автозапуск: sudo systemctl enable minecraft-economy"
echo ""
echo "Для начала работы выполните:"
echo "  cd $PROJECT_DIR"
echo "  ./start-system.sh"
echo ""
echo "Перезагрузите систему или выполните 'newgrp docker' для применения изменений групп"
