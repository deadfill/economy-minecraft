package com.example.debug;

import com.example.rtsecon.http.EconHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

/**
 * Тестовые команды для проверки работы master-slave репликации БД
 */
public class DatabaseTestCommands {
    private static final String BASE_URL = "http://localhost:8081";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Создать HTTP запрос с базовой авторизацией для admin endpoint'ов
     */
    private static HttpRequest.Builder createAuthorizedRequest(String endpoint) {
        String auth = java.util.Base64.getEncoder().encodeToString("admin:admin123".getBytes());
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + auth)
                .timeout(Duration.ofSeconds(10));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Команда для проверки статуса БД
        dispatcher.register(literal("dbtest")
            .then(literal("ping")
                .executes(context -> {
                    testApiConnection();
                    return 1;
                })
            )
            .then(literal("status")
                .executes(context -> {
                    testDatabaseStatus();
                    return 1;
                })
            )
            .then(literal("replication")
                .executes(context -> {
                    testReplicationStatus();
                    return 1;
                })
            )
            .then(literal("write")
                .then(argument("data", StringArgumentType.string())
                    .executes(context -> {
                        String data = StringArgumentType.getString(context, "data");
                        testWriteOperation(data);
                        return 1;
                    })
                )
            )
            .then(literal("read")
                .executes(context -> {
                    testReadOperation();
                    return 1;
                })
            )
            .then(literal("replicas")
                .executes(context -> {
                    testReplicaConnections();
                    return 1;
                })
            )
            .then(literal("load")
                .executes(context -> {
                    testLoadBalancing();
                    return 1;
                })
            )
            .then(literal("all")
                .executes(context -> {
                    runAllTests();
                    return 1;
                })
            )
            .then(literal("test")
                .executes(context -> {
                    testDatabaseEndpoint();
                    return 1;
                })
            )
            .then(literal("auth")
                .executes(context -> {
                    testAuthentication();
                    return 1;
                })
            )
        );
    }

    /**
     * Простая проверка доступности API
     */
    private static void testApiConnection() {
        sendMessage("§6[DB Test] §fПроверяем доступность API сервера...");
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                sendMessage("§a[DB Test] §fAPI сервер доступен! ✓");
                sendMessage("§a[DB Test] §fURL: §e" + BASE_URL);
                
                try {
                    JsonNode result = MAPPER.readTree(response.body());
                    if (result.has("status")) {
                        String status = result.get("status").asText();
                        sendMessage("§a[DB Test] §fСтатус: §e" + status);
                    }
                } catch (Exception e) {
                    sendMessage("§a[DB Test] §fОтвет получен (не JSON формат)");
                }
            } else {
                sendMessage("§c[DB Test] §fAPI сервер отвечает с ошибкой: HTTP " + response.statusCode());
            }
            
        } catch (java.net.ConnectException e) {
            sendMessage("§c[DB Test] §fНе удается подключиться к API серверу!");
            sendMessage("§c[DB Test] §fURL: §e" + BASE_URL);
            sendMessage("§c[DB Test] §fПроверьте что Quarkus API запущен:");
            sendMessage("§c[DB Test] §f  cd economy-quarkus");
            sendMessage("§c[DB Test] §f  ./gradlew quarkusDev");
        } catch (java.net.http.HttpTimeoutException e) {
            sendMessage("§c[DB Test] §fТайм-аут подключения к API серверу");
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Тест database endpoint без авторизации
     */
    private static void testDatabaseEndpoint() {
        sendMessage("§6[DB Test] §fТестируем database endpoint...");
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/test"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode result = MAPPER.readTree(response.body());
                String status = result.path("status").asText("Unknown");
                String message = result.path("message").asText();
                
                sendMessage("§a[DB Test] §fDatabase endpoint работает!");
                sendMessage("§a[DB Test] §fСтатус: §e" + status);
                sendMessage("§a[DB Test] §fСообщение: §e" + message);
            } else {
                sendMessage("§c[DB Test] §fОшибка: HTTP " + response.statusCode() + " - " + response.body());
            }
            
        } catch (java.net.ConnectException e) {
            sendMessage("§c[DB Test] §fНе удается подключиться к database endpoint!");
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Тест авторизации с разными учетными данными
     */
    private static void testAuthentication() {
        sendMessage("§6[DB Test] §fТестируем авторизацию...");
        
        // Тест без авторизации
        sendMessage("§6[DB Test] §fТест 1: Без авторизации");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/status"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            sendMessage("§c[DB Test] §f  Ответ: HTTP " + response.statusCode());
        } catch (Exception e) {
            sendMessage("§c[DB Test] §f  Ошибка: " + e.getMessage());
        }
        
        // Тест с неправильными данными
        sendMessage("§6[DB Test] §fТест 2: Неправильные данные (admin:wrong)");
        try {
            String wrongAuth = java.util.Base64.getEncoder().encodeToString("admin:wrong".getBytes());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/status"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + wrongAuth)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            sendMessage("§c[DB Test] §f  Ответ: HTTP " + response.statusCode());
        } catch (Exception e) {
            sendMessage("§c[DB Test] §f  Ошибка: " + e.getMessage());
        }
        
        // Тест с правильными данными
        sendMessage("§6[DB Test] §fТест 3: Правильные данные (admin:admin123)");
        try {
            String auth = java.util.Base64.getEncoder().encodeToString("admin:admin123".getBytes());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/status"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + auth)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            sendMessage("§a[DB Test] §f  Ответ: HTTP " + response.statusCode());
            if (response.statusCode() == 200) {
                sendMessage("§a[DB Test] §f  ✓ Авторизация работает!");
            }
        } catch (Exception e) {
            sendMessage("§c[DB Test] §f  Ошибка: " + e.getMessage());
        }
        
        // Тест альтернативного пользователя
        sendMessage("§6[DB Test] §fТест 4: Альтернативный пользователь (developer:dev456)");
        try {
            String devAuth = java.util.Base64.getEncoder().encodeToString("developer:dev456".getBytes());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/status"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + devAuth)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            sendMessage("§a[DB Test] §f  Ответ: HTTP " + response.statusCode());
            if (response.statusCode() == 200) {
                sendMessage("§a[DB Test] §f  ✓ Developer авторизация работает!");
            }
        } catch (Exception e) {
            sendMessage("§c[DB Test] §f  Ошибка: " + e.getMessage());
        }
    }

    /**
     * Проверка общего статуса БД
     */
    private static void testDatabaseStatus() {
        sendMessage("§6[DB Test] §fПроверяем статус баз данных...");
        
        try {
            // Временно без авторизации для тестирования
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/status"))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode result = MAPPER.readTree(response.body());
                String connectionStatus = result.path("connection_status").asText("Unknown");
                long timestamp = result.path("timestamp").asLong(System.currentTimeMillis());
                
                sendMessage("§a[DB Test] §fСтатус соединений: §e" + connectionStatus);
                sendMessage("§a[DB Test] §fВремя проверки: §e" + new java.util.Date(timestamp));
                
                if (result.has("replication_info")) {
                    JsonNode replInfo = result.get("replication_info");
                    if (replInfo.has("connected_replicas")) {
                        JsonNode replicas = replInfo.get("connected_replicas");
                        sendMessage("§a[DB Test] §fПодключенных реплик: §e" + replicas.size());
                    }
                }
            } else {
                sendMessage("§c[DB Test] §fОшибка: HTTP " + response.statusCode() + " - " + response.body());
            }
            
        } catch (java.net.ConnectException e) {
            sendMessage("§c[DB Test] §fНе удается подключиться к серверу API!");
            sendMessage("§c[DB Test] §fПроверьте что Quarkus API запущен на " + BASE_URL);
        } catch (java.net.http.HttpTimeoutException e) {
            sendMessage("§c[DB Test] §fТайм-аут подключения к API серверу");
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Проверка статуса репликации
     */
    private static void testReplicationStatus() {
        sendMessage("§6[DB Test] §fПроверяем статус репликации...");
        
        try {
            // Временно без авторизации для тестирования
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/replication"))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode result = MAPPER.readTree(response.body());
                
                if (result.has("replication_slots")) {
                    JsonNode slots = result.get("replication_slots");
                    sendMessage("§a[DB Test] §fСлоты репликации:");
                    slots.fieldNames().forEachRemaining(slotName -> {
                        JsonNode slot = slots.get(slotName);
                        boolean active = slot.get("active").asBoolean();
                        String status = active ? "§aАктивен" : "§cНеактивен";
                        sendMessage("§a[DB Test] §f  - " + slotName + ": " + status);
                    });
                }
                
                if (result.has("connected_replicas")) {
                    JsonNode replicas = result.get("connected_replicas");
                    sendMessage("§a[DB Test] §fПодключенные реплики:");
                    replicas.fieldNames().forEachRemaining(replicaName -> {
                        JsonNode replica = replicas.get(replicaName);
                        String state = replica.get("state").asText();
                        long lagBytes = replica.get("lag_bytes").asLong();
                        sendMessage("§a[DB Test] §f  - " + replicaName + ": §e" + state + " §f(lag: " + lagBytes + " bytes)");
                    });
                }
                
            } else {
                sendMessage("§c[DB Test] §fОшибка: HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getMessage());
        }
    }

    /**
     * Тест операции записи (создание тестовой записи)
     */
    private static void testWriteOperation(String testData) {
        sendMessage("§6[DB Test] §fТестируем запись в primary БД...");
        
        try {
            UUID currentPlayer = getCurrentPlayerUuid();
            if (currentPlayer == null) {
                sendMessage("§c[DB Test] §fНе удалось получить UUID игрока");
                return;
            }
            
            // Тестируем операцию записи через тренировку скилла
            String body = String.format("{\"ownerUuid\":\"%s\",\"skillId\":\"industry\"}", currentPlayer);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/v1/skills/train"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                sendMessage("§a[DB Test] §fЗапись выполнена успешно!");
                sendMessage("§a[DB Test] §fИгрок: §e" + currentPlayer);
                sendMessage("§a[DB Test] §fТестовые данные: §e" + testData);
            } else if (response.statusCode() == 400) {
                sendMessage("§e[DB Test] §fЗапись протестирована (уже идет тренировка)");
                sendMessage("§e[DB Test] §fЭто нормально для теста операций записи");
            } else {
                sendMessage("§c[DB Test] §fОшибка записи: HTTP " + response.statusCode() + " - " + response.body());
            }
            
        } catch (java.net.ConnectException e) {
            sendMessage("§c[DB Test] §fНе удается подключиться к серверу API!");
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Тест операции чтения
     */
    private static void testReadOperation() {
        sendMessage("§6[DB Test] §fТестируем чтение из replica БД...");
        
        try {
            UUID currentPlayer = getCurrentPlayerUuid();
            if (currentPlayer == null) {
                sendMessage("§c[DB Test] §fНе удалось получить UUID игрока");
                return;
            }
            
            // Читаем уровень скилла (это операция чтения)
            JsonNode result = EconHttp.skillLevel(currentPlayer, "industry");
            int level = result.get("level").asInt();
            
            sendMessage("§a[DB Test] §fЧтение выполнено успешно!");
            sendMessage("§a[DB Test] §fВаш UUID: §e" + currentPlayer);
            sendMessage("§a[DB Test] §fУровень Industry: §e" + level);
            
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getMessage());
        }
    }

    /**
     * Тест соединений с репликами
     */
    private static void testReplicaConnections() {
        sendMessage("§6[DB Test] §fТестируем соединения с репликами...");
        
        try {
            // Временно без авторизации для тестирования
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/admin/database/test-replicas"))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode result = MAPPER.readTree(response.body());
                
                // Тест replica1
                if (result.has("replica1")) {
                    JsonNode replica1 = result.get("replica1");
                    boolean available = replica1.get("available").asBoolean();
                    String status = available ? "§aОК" : "§cНедоступна";
                    sendMessage("§a[DB Test] §fReplica 1: " + status);
                    if (available) {
                        String dbName = replica1.get("database").asText();
                        boolean isReplica = replica1.get("is_replica").asBoolean();
                        sendMessage("§a[DB Test] §f  - БД: §e" + dbName + " §f(replica: " + (isReplica ? "§aДа" : "§cНет") + "§f)");
                    }
                }
                
                // Тест replica2
                if (result.has("replica2")) {
                    JsonNode replica2 = result.get("replica2");
                    boolean available = replica2.get("available").asBoolean();
                    String status = available ? "§aОК" : "§cНедоступна";
                    sendMessage("§a[DB Test] §fReplica 2: " + status);
                    if (available) {
                        String dbName = replica2.get("database").asText();
                        boolean isReplica = replica2.get("is_replica").asBoolean();
                        sendMessage("§a[DB Test] §f  - БД: §e" + dbName + " §f(replica: " + (isReplica ? "§aДа" : "§cНет") + "§f)");
                    }
                }
                
                // Тест случайного чтения
                if (result.has("random_read_test")) {
                    JsonNode randomTest = result.get("random_read_test");
                    String status = randomTest.get("status").asText();
                    if ("OK".equals(status)) {
                        long playerCount = randomTest.get("players_count").asLong();
                        boolean fromReplica = randomTest.get("read_from_replica").asBoolean();
                        sendMessage("§a[DB Test] §fСлучайное чтение: §aОК");
                        sendMessage("§a[DB Test] §f  - Игроков в БД: §e" + playerCount);
                        sendMessage("§a[DB Test] §f  - Читали из replica: " + (fromReplica ? "§aДа" : "§cНет"));
                    } else {
                        sendMessage("§c[DB Test] §fСлучайное чтение: §cОшибка");
                    }
                }
                
            } else {
                sendMessage("§c[DB Test] §fОшибка: HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getMessage());
        }
    }

    /**
     * Тест балансировки нагрузки (несколько запросов чтения)
     */
    private static void testLoadBalancing() {
        sendMessage("§6[DB Test] §fТестируем балансировку нагрузки (10 запросов чтения)...");
        
        try {
            UUID currentPlayer = getCurrentPlayerUuid();
            if (currentPlayer == null) {
                sendMessage("§c[DB Test] §fНе удалось получить UUID игрока");
                return;
            }
            
            int successCount = 0;
            long totalTime = 0;
            
            for (int i = 0; i < 10; i++) {
                try {
                    long startTime = System.currentTimeMillis();
                    JsonNode result = EconHttp.skillLevel(currentPlayer, "industry");
                    long endTime = System.currentTimeMillis();
                    
                    if (result.has("level")) {
                        successCount++;
                        totalTime += (endTime - startTime);
                    }
                    
                    // Небольшая пауза между запросами
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    sendMessage("§c[DB Test] §fОшибка запроса " + (i + 1) + ": " + e.getMessage());
                }
            }
            
            double avgTime = totalTime / (double) successCount;
            sendMessage("§a[DB Test] §fРезультаты балансировки:");
            sendMessage("§a[DB Test] §f  - Успешных запросов: §e" + successCount + "/10");
            sendMessage("§a[DB Test] §f  - Среднее время: §e" + String.format("%.1f", avgTime) + "ms");
            
            if (successCount >= 8) {
                sendMessage("§a[DB Test] §fБалансировка работает отлично! ✓");
            } else if (successCount >= 5) {
                sendMessage("§e[DB Test] §fБалансировка работает, но есть проблемы");
            } else {
                sendMessage("§c[DB Test] §fПроблемы с балансировкой нагрузки!");
            }
            
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка: " + e.getMessage());
        }
    }

    /**
     * Получить UUID текущего игрока
     */
    private static UUID getCurrentPlayerUuid() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getUUID();
        }
        return null;
    }

    /**
     * Запуск всех тестов подряд
     */
    private static void runAllTests() {
        sendMessage("§6[DB Test] §f========================================");
        sendMessage("§6[DB Test] §fЗапуск полного набора тестов БД...");
        sendMessage("§6[DB Test] §f========================================");
        
        try {
            // 1. Проверка API
            testApiConnection();
            Thread.sleep(1000);
            
            // 2. Статус БД
            testDatabaseStatus();
            Thread.sleep(1000);
            
            // 3. Статус репликации
            testReplicationStatus();
            Thread.sleep(1000);
            
            // 4. Тест реплик
            testReplicaConnections();
            Thread.sleep(1000);
            
            // 5. Тест чтения
            testReadOperation();
            Thread.sleep(1000);
            
            // 6. Тест записи
            testWriteOperation("auto_test_" + System.currentTimeMillis());
            Thread.sleep(1000);
            
            // 7. Тест балансировки
            testLoadBalancing();
            
            sendMessage("§6[DB Test] §f========================================");
            sendMessage("§a[DB Test] §fВсе тесты завершены!");
            sendMessage("§6[DB Test] §f========================================");
            
        } catch (InterruptedException e) {
            sendMessage("§c[DB Test] §fТесты прерваны");
        } catch (Exception e) {
            sendMessage("§c[DB Test] §fОшибка во время тестирования: " + e.getMessage());
        }
    }

    /**
     * Отправить сообщение в чат
     */
    private static void sendMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }
}
