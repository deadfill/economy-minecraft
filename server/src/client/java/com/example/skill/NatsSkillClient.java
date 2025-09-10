package com.example.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.*;
import net.minecraft.client.Minecraft;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * NATS клиент для real-time обновлений скиллов
 * Использует существующую NATS инфраструктуру для мгновенных уведомлений
 */
public class NatsSkillClient {
    private static final Logger LOGGER = Logger.getLogger(NatsSkillClient.class.getName());
    private static final String NATS_URL = "nats://localhost:4222";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Connection natsConnection;
    private Dispatcher dispatcher;
    private final UUID playerUuid;
    
    // Обработчики событий
    private Consumer<SkillLevelUpdate> onSkillLevelUpdate;
    private Consumer<TrainingStatusUpdate> onTrainingStatusUpdate;
    private Runnable onConnected;
    private Runnable onDisconnected;
    
    // События
    public static class SkillLevelUpdate {
        public String skillId;
        public int oldLevel;
        public int newLevel;
        public long timestamp;
    }
    
    public static class TrainingStatusUpdate {
        public String skillId;
        public String status; // "STARTED", "PROGRESS", "COMPLETED", "CANCELLED"
        public int targetLevel;
        public long startMs;
        public long endMs;
        public float progress; // 0.0 - 1.0
        public long timestamp;
    }
    
    public NatsSkillClient(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    /**
     * Подключиться к NATS серверу
     */
    public CompletableFuture<Void> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Options options = new Options.Builder()
                    .server(NATS_URL)
                    .connectionTimeout(java.time.Duration.ofSeconds(5))
                    .maxReconnects(-1) // Бесконечные попытки переподключения
                    .reconnectWait(java.time.Duration.ofSeconds(2))
                    .pingInterval(java.time.Duration.ofSeconds(10))
                    .build();
                
                natsConnection = Nats.connect(options);
                LOGGER.info("Connected to NATS server: " + NATS_URL);
                
                // Подписываемся на события для этого игрока
                subscribeToPlayerEvents();
                
                if (onConnected != null) {
                    Minecraft.getInstance().execute(onConnected);
                }
                
                return null;
            } catch (Exception e) {
                LOGGER.severe("Failed to connect to NATS: " + e.getMessage());
                e.printStackTrace();
                
                // Попытка переподключения через 5 секунд
                CompletableFuture.delayedExecutor(5, java.util.concurrent.TimeUnit.SECONDS)
                    .execute(() -> {
                        LOGGER.info("Attempting to reconnect to NATS...");
                        connect();
                    });
                
                throw new RuntimeException(e);
            }
        });
    }
    
    private void subscribeToPlayerEvents() throws Exception {
        // Создаем dispatcher для обработки сообщений
        dispatcher = natsConnection.createDispatcher((Message msg) -> {
            // Общий обработчик - не используется, так как у нас есть специфические обработчики
        });
        
        // Подписка на обновления уровня скиллов
        String levelSubject = "player." + playerUuid + ".skill.level";
        dispatcher.subscribe(levelSubject, (Message message) -> {
            Minecraft.getInstance().execute(() -> {
                try {
                    handleSkillLevelMessage(new String(message.getData()));
                } catch (Exception e) {
                    LOGGER.warning("Error processing skill level update: " + e.getMessage());
                }
            });
        });
        
        // Подписка на обновления статуса тренировки
        String trainingSubject = "player." + playerUuid + ".skill.training";
        dispatcher.subscribe(trainingSubject, (Message message) -> {
            Minecraft.getInstance().execute(() -> {
                try {
                    handleTrainingStatusMessage(new String(message.getData()));
                } catch (Exception e) {
                    LOGGER.warning("Error processing training update: " + e.getMessage());
                }
            });
        });
        
        LOGGER.info("Subscribed to NATS events for player: " + playerUuid);
        LOGGER.info("  - Level updates: " + levelSubject);
        LOGGER.info("  - Training updates: " + trainingSubject);
    }
    
    private void handleSkillLevelMessage(String data) throws Exception {
        if (onSkillLevelUpdate != null) {
            JsonNode json = objectMapper.readTree(data);
            
            SkillLevelUpdate update = new SkillLevelUpdate();
            update.skillId = json.get("skillId").asText();
            update.oldLevel = json.get("oldLevel").asInt();
            update.newLevel = json.get("newLevel").asInt();
            update.timestamp = json.get("timestamp").asLong();
            
            onSkillLevelUpdate.accept(update);
            LOGGER.info(String.format("NATS: Skill level updated: %s %d -> %d", 
                update.skillId, update.oldLevel, update.newLevel));
        }
    }
    
    private void handleTrainingStatusMessage(String data) throws Exception {
        if (onTrainingStatusUpdate != null) {
            JsonNode json = objectMapper.readTree(data);
            
            TrainingStatusUpdate update = new TrainingStatusUpdate();
            update.skillId = json.get("skillId").asText();
            update.status = json.get("type").asText(); // "STARTED" или "COMPLETED"
            update.targetLevel = json.get("targetLevel").asInt();
            update.startMs = json.get("startMs").asLong();
            update.endMs = json.get("endMs").asLong();
            update.progress = (float) json.get("progress").asDouble();
            update.timestamp = json.get("timestamp").asLong();
            
            onTrainingStatusUpdate.accept(update);
            LOGGER.info(String.format("NATS: Training status updated: %s -> %s (%.1f%%)", 
                update.skillId, update.status, update.progress * 100));
        }
    }
    
    /**
     * Отключиться от NATS
     */
    public void disconnect() {
        if (natsConnection != null) {
            try {
                natsConnection.close();
                LOGGER.info("NATS connection closed");
                
                if (onDisconnected != null) {
                    Minecraft.getInstance().execute(onDisconnected);
                }
            } catch (InterruptedException e) {
                LOGGER.warning("Error closing NATS connection: " + e.getMessage());
            }
            natsConnection = null;
        }
    }
    
    /**
     * Проверить статус подключения
     */
    public boolean isConnected() {
        return natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED;
    }
    
    // === Установка обработчиков событий ===
    
    public void setOnSkillLevelUpdate(Consumer<SkillLevelUpdate> handler) {
        this.onSkillLevelUpdate = handler;
    }
    
    public void setOnTrainingStatusUpdate(Consumer<TrainingStatusUpdate> handler) {
        this.onTrainingStatusUpdate = handler;
    }
    
    public void setOnConnected(Runnable handler) {
        this.onConnected = handler;
    }
    
    public void setOnDisconnected(Runnable handler) {
        this.onDisconnected = handler;
    }
}
