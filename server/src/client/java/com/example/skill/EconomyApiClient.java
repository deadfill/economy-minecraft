package com.example.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * HTTP клиент для общения с economy-quarkus API
 */
public class EconomyApiClient {
    private static final Logger LOGGER = Logger.getLogger(EconomyApiClient.class.getName());
    private static final String BASE_URL = "http://localhost:8081/api/v1";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String cachedEtag = null;
    private List<ServerSkill> cachedSkills = null;

    public EconomyApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Получить список всех скиллов с сервера
     */
    public CompletableFuture<List<ServerSkill>> getSkills() {
        return getSkills(false);
    }

    /**
     * Получить список всех скиллов с сервера
     * @param forceRefresh если true, игнорирует кэш и принудительно загружает данные
     */
    public CompletableFuture<List<ServerSkill>> getSkills(boolean forceRefresh) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/skills/list"))
                        .timeout(Duration.ofSeconds(10))
                        .GET();

                // Добавляем ETag для кеширования (только если не принудительное обновление)
                if (!forceRefresh && cachedEtag != null) {
                    requestBuilder.header("If-None-Match", cachedEtag);
                }

                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 304) {
                    // Данные не изменились, возвращаем кешированные данные
                    LOGGER.fine("Skills data not modified (304) - using cached data");
                    return cachedSkills != null ? new ArrayList<>(cachedSkills) : new ArrayList<>();
                }

                if (response.statusCode() != 200) {
                    LOGGER.warning("Failed to get skills: " + response.statusCode() + " " + response.body());
                    return new ArrayList<>();
                }

                // Обновляем ETag
                cachedEtag = response.headers().firstValue("ETag").orElse(null);

                // Парсим ответ
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode skillsArray = root.get("skills");
                
                if (skillsArray == null || !skillsArray.isArray()) {
                    LOGGER.warning("Invalid response format: skills array not found");
                    return new ArrayList<>();
                }

                List<ServerSkill> skills = new ArrayList<>();
                for (JsonNode skillNode : skillsArray) {
                    try {
                        ServerSkill skill = parseSkill(skillNode);
                        skills.add(skill);
                    } catch (Exception e) {
                        LOGGER.warning("Failed to parse skill: " + e.getMessage());
                    }
                }

                LOGGER.info("Loaded " + skills.size() + " skills from server (fresh data)");
                // Кэшируем скиллы для будущих 304 ответов
                cachedSkills = new ArrayList<>(skills);
                return skills;

            } catch (Exception e) {
                LOGGER.severe("Error getting skills: " + e.getMessage());
                e.printStackTrace();
                return new ArrayList<>();
            }
        });
    }

    /**
     * Получить уровень конкретного скилла для игрока
     */
    public CompletableFuture<Integer> getSkillLevel(UUID playerUuid, String skillId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "/skills/level?ownerUuid=" + playerUuid + "&skillId=" + skillId;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    LOGGER.warning("Failed to get skill level: " + response.statusCode());
                    return 0;
                }

                JsonNode root = objectMapper.readTree(response.body());
                return root.get("level").asInt(0);

            } catch (Exception e) {
                LOGGER.warning("Error getting skill level: " + e.getMessage());
                e.printStackTrace();
                return 0;
            }
        });
    }

    /**
     * Получить статус активной тренировки
     */
    public CompletableFuture<SkillTraining> getTrainingStatus(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "/skills/status?ownerUuid=" + playerUuid;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    LOGGER.warning("Failed to get training status: " + response.statusCode());
                    return null;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode current = root.get("current");
                
                if (current == null || current.isNull()) {
                    return null; // Нет активной тренировки
                }

                return new SkillTraining(
                    current.get("skillId").asText(),
                    current.get("targetLevel").asInt(),
                    current.get("startMs").asLong(),
                    current.get("endMs").asLong()
                );

            } catch (Exception e) {
                LOGGER.warning("Error getting training status: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * Начать тренировку скилла
     */
    public CompletableFuture<Boolean> startTraining(UUID playerUuid, String skillId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestBody = objectMapper.writeValueAsString(Map.of(
                    "ownerUuid", playerUuid.toString(),
                    "skillId", skillId
                ));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/skills/train"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    LOGGER.info("Started training " + skillId + " for player " + playerUuid);
                    return true;
                } else {
                    LOGGER.warning("Failed to start training: " + response.statusCode() + " " + response.body());
                    return false;
                }

            } catch (Exception e) {
                LOGGER.severe("Error starting training: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Получить UUID текущего игрока
     */
    public UUID getCurrentPlayerUuid() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getUUID();
        }
        return null;
    }

    /**
     * Парсинг скилла из JSON
     */
    private ServerSkill parseSkill(JsonNode skillNode) throws Exception {
        String id = skillNode.get("id").asText();
        String title = skillNode.get("title").asText();
        String description = skillNode.get("desc").asText();
        int maxLevel = skillNode.get("maxLevel").asInt();

        // Парсим массив длительностей
        JsonNode durationsNode = skillNode.get("durationsMs");
        long[] durationsMs = new long[durationsNode.size()];
        for (int i = 0; i < durationsNode.size(); i++) {
            durationsMs[i] = durationsNode.get(i).asLong();
        }

        return new ServerSkill(id, title, description, maxLevel, durationsMs);
    }

    /**
     * Закрыть HTTP клиент
     */
    public void close() {
        // HttpClient автоматически закрывается
    }
}
