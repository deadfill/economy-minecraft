@echo off
echo ========================================
echo Установка Minecraft Economy System
echo ========================================

echo.
echo 1. Проверка системы...
ver | find "Windows" >nul
if errorlevel 1 (
    echo Ошибка: Этот скрипт предназначен для Windows
    pause
    exit /b 1
)

echo.
echo 2. Установка Docker Desktop...
echo Скачиваем Docker Desktop для Windows...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://desktop.docker.com/win/main/amd64/Docker%%20Desktop%%20Installer.exe' -OutFile 'DockerDesktopInstaller.exe'}"

echo.
echo Запускаем установку Docker Desktop...
echo ВНИМАНИЕ: Следуйте инструкциям установщика!
DockerDesktopInstaller.exe

echo.
echo 3. Ожидание установки Docker...
echo Пожалуйста, завершите установку Docker Desktop и перезапустите компьютер
echo После перезапуска запустите этот скрипт снова
pause
exit /b 0

:after_restart
echo.
echo 4. Проверка Docker...
docker --version
if errorlevel 1 (
    echo Ошибка: Docker не установлен или не запущен
    echo Убедитесь, что Docker Desktop запущен
    pause
    exit /b 1
)

echo.
echo 5. Создание директории проекта...
set PROJECT_DIR=C:\minecraft-economy
if not exist "%PROJECT_DIR%" mkdir "%PROJECT_DIR%"

echo.
echo 6. Копирование файлов проекта...
if exist ".git" (
    echo Копирование файлов проекта...
    xcopy /E /I /Y . "%PROJECT_DIR%\"
) else (
    echo ВНИМАНИЕ: Не обнаружен Git репозиторий
    echo Скопируйте файлы проекта в %PROJECT_DIR% вручную
)

cd /d "%PROJECT_DIR%"

echo.
echo 7. Создание скриптов управления...

REM Скрипт запуска
echo @echo off > start-system.bat
echo echo ======================================== >> start-system.bat
echo echo Запуск Minecraft Economy System >> start-system.bat
echo echo ======================================== >> start-system.bat
echo. >> start-system.bat
echo REM Останавливаем существующие контейнеры >> start-system.bat
echo docker compose down >> start-system.bat
echo. >> start-system.bat
echo REM Запускаем PostgreSQL кластер >> start-system.bat
echo echo Запуск PostgreSQL кластера... >> start-system.bat
echo docker compose up -d postgres-primary postgres-replica1 postgres-replica2 >> start-system.bat
echo. >> start-system.bat
echo REM Ждем готовности PostgreSQL >> start-system.bat
echo echo Ожидание готовности PostgreSQL... >> start-system.bat
echo timeout /t 15 /nobreak ^>nul >> start-system.bat
echo. >> start-system.bat
echo REM Запускаем PgBouncer >> start-system.bat
echo echo Запуск PgBouncer... >> start-system.bat
echo docker compose up -d pgbouncer-master pgbouncer-replica1 pgbouncer-replica2 >> start-system.bat
echo. >> start-system.bat
echo REM Ждем готовности PgBouncer >> start-system.bat
echo echo Ожидание готовности PgBouncer... >> start-system.bat
echo timeout /t 5 /nobreak ^>nul >> start-system.bat
echo. >> start-system.bat
echo REM Настраиваем репликацию >> start-system.bat
echo echo Настройка репликации... >> start-system.bat
echo docker compose exec postgres-primary bash -c "echo 'host replication game 0.0.0.0/0 md5' ^>^> /var/lib/postgresql/data/pg_hba.conf" >> start-system.bat
echo docker compose exec postgres-primary bash -c "su - postgres -c 'psql -U game -d econ -c \"SELECT pg_reload_conf();\"'" >> start-system.bat
echo docker compose exec postgres-primary psql -U game -d econ -c "SELECT pg_create_physical_replication_slot('replica1_slot') ON CONFLICT DO NOTHING;" >> start-system.bat
echo docker compose exec postgres-primary psql -U game -d econ -c "SELECT pg_create_physical_replication_slot('replica2_slot') ON CONFLICT DO NOTHING;" >> start-system.bat
echo. >> start-system.bat
echo REM Запускаем остальные сервисы >> start-system.bat
echo echo Запуск остальных сервисов... >> start-system.bat
echo docker compose up -d redis nats economy-quarkus >> start-system.bat
echo. >> start-system.bat
echo echo ======================================== >> start-system.bat
echo echo Система запущена! >> start-system.bat
echo echo ======================================== >> start-system.bat
echo echo Сервисы: >> start-system.bat
echo echo - PostgreSQL Primary: localhost:5432 >> start-system.bat
echo echo - PgBouncer Master: localhost:6432 >> start-system.bat
echo echo - Economy API: localhost:8081 >> start-system.bat
echo echo - Redis: localhost:6379 >> start-system.bat
echo echo - NATS: localhost:4222 >> start-system.bat
echo echo. >> start-system.bat
echo echo Проверка статуса: docker compose ps >> start-system.bat
echo echo Просмотр логов: docker compose logs -f [service_name] >> start-system.bat
echo pause >> start-system.bat

REM Скрипт остановки
echo @echo off > stop-system.bat
echo echo Остановка Minecraft Economy System... >> stop-system.bat
echo docker compose down >> stop-system.bat
echo echo Система остановлена >> stop-system.bat
echo pause >> stop-system.bat

REM Скрипт перезапуска
echo @echo off > restart-system.bat
echo echo Перезапуск Minecraft Economy System... >> restart-system.bat
echo call stop-system.bat >> restart-system.bat
echo timeout /t 2 /nobreak ^>nul >> restart-system.bat
echo call start-system.bat >> restart-system.bat

REM Скрипт полной очистки
echo @echo off > clean-restart.bat
echo echo ======================================== >> clean-restart.bat
echo echo ПОЛНАЯ ОЧИСТКА И ПЕРЕЗАПУСК СИСТЕМЫ >> clean-restart.bat
echo echo ======================================== >> clean-restart.bat
echo echo ВНИМАНИЕ: Это удалит ВСЕ данные! >> clean-restart.bat
echo echo. >> clean-restart.bat
echo set /p confirm="Вы уверены? (y/N): " >> clean-restart.bat
echo if /i not "%%confirm%%"=="y" ( >> clean-restart.bat
echo     echo Отменено. >> clean-restart.bat
echo     pause >> clean-restart.bat
echo     exit /b 1 >> clean-restart.bat
echo ^) >> clean-restart.bat
echo. >> clean-restart.bat
echo echo Полная остановка и очистка... >> clean-restart.bat
echo docker compose down -v >> clean-restart.bat
echo. >> clean-restart.bat
echo echo Сборка всех сервисов... >> clean-restart.bat
echo docker compose build >> clean-restart.bat
echo. >> clean-restart.bat
echo echo Запуск системы... >> clean-restart.bat
echo call start-system.bat >> clean-restart.bat

echo.
echo 8. Создание ярлыков на рабочем столе...
set DESKTOP=%USERPROFILE%\Desktop
echo [InternetShortcut] > "%DESKTOP%\Minecraft Economy - Start.url"
echo URL=file:///%PROJECT_DIR%\start-system.bat >> "%DESKTOP%\Minecraft Economy - Start.url"
echo [InternetShortcut] > "%DESKTOP%\Minecraft Economy - Stop.url"
echo URL=file:///%PROJECT_DIR%\stop-system.bat >> "%DESKTOP%\Minecraft Economy - Stop.url"

echo.
echo ========================================
echo Установка завершена!
echo ========================================
echo.
echo Директория проекта: %PROJECT_DIR%
echo.
echo Управление системой:
echo   Запуск:     start-system.bat
echo   Остановка:  stop-system.bat
echo   Перезапуск: restart-system.bat
echo   Очистка:    clean-restart.bat
echo.
echo Ярлыки созданы на рабочем столе
echo.
echo Для начала работы выполните:
echo   cd %PROJECT_DIR%
echo   start-system.bat
echo.
pause
