@echo off
echo Setting up ServerWrapper for GravitLauncher integration...
echo.

REM Проверяем наличие Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Java not found! Please install Java 17 or higher.
    pause
    exit /b 1
)

REM Создаем папку для ServerWrapper если её нет
if not exist "serverwrapper" mkdir serverwrapper
cd serverwrapper

REM Скачиваем ServerWrapper если его нет
if not exist "ServerWrapper.jar" (
    echo Downloading ServerWrapper.jar...
    curl -L -o ServerWrapper.jar "https://gravitlauncher.com/download/ServerWrapper.jar"
    if %errorlevel% neq 0 (
        echo Error: Failed to download ServerWrapper.jar
        pause
        exit /b 1
    )
    echo ServerWrapper.jar downloaded successfully!
    echo.
)

echo ServerWrapper setup instructions:
echo.
echo 1. Make sure your Minecraft server is configured with:
echo    - online-mode=true in server.properties
echo    - AuthLib replacement (if needed for your server type)
echo.
echo 2. Run the following command to setup ServerWrapper:
echo    java -jar ServerWrapper.jar setup
echo.
echo 3. When prompted, provide:
echo    - Server JAR name: fabric-server-launch.jar (or your server jar)
echo    - Server name: MinecraftEconomy
echo    - LaunchServer address: http://localhost:9274
echo    - Server token: (generate with: token server MinecraftEconomy)
echo.
echo 4. After setup, start your server with:
echo    java -jar ServerWrapper.jar
echo.

REM Создаем конфигурационный файл для ServerWrapper
echo Creating ServerWrapper configuration template...
(
echo {
echo   "projectName": "Minecraft Economy",
echo   "address": "localhost",
echo   "port": 9274,
echo   "serverName": "MinecraftEconomy",
echo   "profile": "MinecraftEconomy",
echo   "autoReconnectTime": 10000,
echo   "logFile": "wrapper.log"
echo }
) > ServerWrapperConfig.json

echo.
echo ✓ ServerWrapper setup completed!
echo ✓ Configuration template created: ServerWrapperConfig.json
echo.
echo Next steps:
echo 1. Generate server token in LaunchServer console: token server MinecraftEconomy
echo 2. Run: java -jar ServerWrapper.jar setup
echo 3. Follow the prompts and use the generated token
echo.

pause
