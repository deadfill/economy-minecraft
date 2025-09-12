#!/bin/bash

echo "🔑 Генерация токена сервера для GraviteLauncher..."
echo ""
echo "Выполните следующие команды в контейнере:"
echo ""
echo "1. Если вы уже в контейнере (root@...:/app/data#), выполните:"
echo ""
echo "   java -Xmx512M -Dlaunchserver.dir.libraries=../libraries -Dlaunchserver.dir.launcher-libraries=../launcher-libraries -Dlaunchserver.dir.modules=../modules -Dlaunchserver.dir.launcher-modules=../launcher-modules -Dlaunchserver.dir.proguard-libraries=../proguard @/app/javaargs.txt @/app/java24args.txt --module-path /app/LaunchServer.jar:/app/libraries --add-modules ALL-MODULE-PATH -m launchserver/pro.gravit.launchserver.LaunchServerStarter console"
echo ""
echo "2. После запуска консоли LaunchServer (появится приглашение >), введите:"
echo ""
echo "   token server EconomyServer"
echo ""
echo "3. Скопируйте полученный токен и обновите .env файл:"
echo ""
echo "   MINECRAFT_SERVER_TOKEN=ваш_полученный_токен"
echo ""
echo "4. Затем запустите Minecraft сервер:"
echo ""
echo "   docker-compose up -d minecraft-server"
echo ""

# Попробуем автоматически сгенерировать токен
echo "🤖 Попытка автоматической генерации токена..."

# Создаем временный скрипт для выполнения в контейнере
cat > /tmp/generate_token.sh << 'EOF'
#!/bin/bash
cd /app/data
echo "token server EconomyServer" | timeout 30 java -Xmx512M -Dlaunchserver.dir.libraries=../libraries -Dlaunchserver.dir.launcher-libraries=../launcher-libraries -Dlaunchserver.dir.modules=../modules -Dlaunchserver.dir.launcher-modules=../launcher-modules -Dlaunchserver.dir.proguard-libraries=../proguard @/app/javaargs.txt @/app/java24args.txt --module-path /app/LaunchServer.jar:/app/libraries --add-modules ALL-MODULE-PATH -m launchserver/pro.gravit.launchserver.LaunchServerStarter console 2>/dev/null | grep -E "^[A-Za-z0-9+/]{20,}={0,2}$" | tail -1
EOF

# Копируем скрипт в контейнер и выполняем
docker cp /tmp/generate_token.sh economy-minecraft-gravit-launcher-1:/tmp/
TOKEN=$(docker exec economy-minecraft-gravit-launcher-1 bash /tmp/generate_token.sh 2>/dev/null | tail -1)

if [ ! -z "$TOKEN" ] && [ ${#TOKEN} -gt 10 ]; then
    echo ""
    echo "✅ Токен сгенерирован автоматически:"
    echo "TOKEN: $TOKEN"
    echo ""
    echo "Обновляем .env файл..."
    
    # Обновляем .env файл
    if grep -q "MINECRAFT_SERVER_TOKEN=" .env; then
        sed -i.bak "s/MINECRAFT_SERVER_TOKEN=.*/MINECRAFT_SERVER_TOKEN=$TOKEN/" .env
    else
        echo "MINECRAFT_SERVER_TOKEN=$TOKEN" >> .env
    fi
    
    echo "✅ .env файл обновлен!"
    echo ""
    echo "🚀 Теперь можно запустить Minecraft сервер:"
    echo "docker-compose up -d minecraft-server"
else
    echo ""
    echo "⚠️  Автоматическая генерация не удалась."
    echo "Выполните команды выше вручную."
fi

# Очистка
rm -f /tmp/generate_token.sh
docker exec economy-minecraft-gravit-launcher-1 rm -f /tmp/generate_token.sh 2>/dev/null || true
