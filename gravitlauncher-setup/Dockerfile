# GravitLauncher LaunchServer Docker Image
FROM eclipse-temurin:17-jre-alpine

# Устанавливаем необходимые пакеты
RUN apk add --no-cache \
    curl \
    wget \
    unzip \
    bash \
    && rm -rf /var/cache/apk/*

# Создаем пользователя для запуска лаунчера
RUN addgroup -g 1000 launcher && \
    adduser -D -s /bin/bash -u 1000 -G launcher launcher

# Создаем рабочую директорию
WORKDIR /app

# Копируем файлы лаунчера
COPY --chown=launcher:launcher LaunchServer.jar /app/
COPY --chown=launcher:launcher LaunchServer.json /app/
COPY --chown=launcher:launcher profiles/ /app/profiles/

# Создаем необходимые директории
RUN mkdir -p /app/updates \
             /app/libraries \
             /app/launcher-libraries \
             /app/launcher-modules \
             /app/config \
             /app/logs && \
    chown -R launcher:launcher /app

# Переключаемся на пользователя launcher
USER launcher

# Открываем порты
EXPOSE 9274 9275

# Переменные окружения
ENV JAVA_OPTS="-Xms512M -Xmx2G -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M"

# Команда запуска
CMD java ${JAVA_OPTS} \
    -Dfile.encoding=UTF-8 \
    -Djava.net.preferIPv4Stack=true \
    -Dlog4j2.formatMsgNoLookups=true \
    -javaagent:LaunchServer.jar \
    -jar LaunchServer.jar
