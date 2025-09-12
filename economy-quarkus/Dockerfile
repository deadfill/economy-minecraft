# Этап сборки
FROM registry.access.redhat.com/ubi8/openjdk-21:1.20 AS builder

# Устанавливаем необходимые пакеты
USER root
RUN microdnf install -y wget unzip && microdnf clean all

# Устанавливаем Gradle
RUN wget https://services.gradle.org/distributions/gradle-8.5-bin.zip -P /tmp && \
    unzip -d /opt /tmp/gradle-8.5-bin.zip && \
    ln -s /opt/gradle-8.5/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle-8.5-bin.zip

WORKDIR /app

# Копируем исходный код
COPY --chown=185 . .

# Собираем проект
RUN gradle build -x test

# Этап выполнения
FROM registry.access.redhat.com/ubi8/openjdk-21:1.20

WORKDIR /app

# Копируем собранное приложение
COPY --from=builder --chown=185 /app/build/quarkus-app/lib/ ./lib/
COPY --from=builder --chown=185 /app/build/quarkus-app/*.jar ./
COPY --from=builder --chown=185 /app/build/quarkus-app/app/ ./app/
COPY --from=builder --chown=185 /app/build/quarkus-app/quarkus/ ./quarkus/

# Открываем порт
EXPOSE 8081

# Запускаем приложение
CMD ["java", "-jar", "./quarkus-run.jar"]