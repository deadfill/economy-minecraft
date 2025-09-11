FROM registry.access.redhat.com/ubi8/openjdk-21:1.20

WORKDIR /app

# Копируем исполняемый jar-файл
COPY --chown=185 build/quarkus-app/lib/ ./lib/
COPY --chown=185 build/quarkus-app/*.jar ./
COPY --chown=185 build/quarkus-app/app/ ./app/
COPY --chown=185 build/quarkus-app/quarkus/ ./quarkus/

# Открываем порт
EXPOSE 8081

# Запускаем приложение
CMD ["java", "-jar", "./quarkus-run.jar"]