package com.example.economy;

import com.example.economy.core.RedisBus;
import com.example.economy.core.Repositories;
import io.quarkus.redis.datasource.sortedset.ScoredValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.*;

/**
 * Вспомогательные эндпоинты для отладки очереди продакшн-джобов.
 */
@Path("/api/v1/debug")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class DebugResource {

    @Inject RedisBus redis;
    @Inject Repositories repo; // нужен для drain() чтобы завершать джобы сразу в БД

    /**
     * Получить все задачи из Redis ZSET с признаком просрочки.
     */
    @GET
    @Path("/redis-jobs")
    public Map<String, Object> getRedisJobs() {
        try {
            long now = System.currentTimeMillis();
            List<ScoredValue<String>> all = redis.listAllJobs();

            List<Map<String, Object>> jobs = new ArrayList<>(all.size());
            for (var sv : all) {
                long endMs = (long) sv.score();
                jobs.add(Map.of(
                        "jobId", sv.value(),
                        "endTime", endMs,
                        "endTimeFormatted", new java.util.Date(endMs).toString(),
                        "isExpired", endMs <= now
                ));
            }

            return Map.of(
                    "currentTime", now,
                    "currentTimeFormatted", new java.util.Date(now).toString(),
                    "totalJobs", all.size(),
                    "jobs", jobs
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Проверка статуса CDI-инжектов.
     */
    @GET
    @Path("/cdi-status")
    public Map<String, Object> getCdiStatus() {
        return Map.of(
                "redisBus", redis != null ? "OK" : "NULL",
                "repositories", repo != null ? "OK" : "NULL",
                "message", "CDI status check completed"
        );
    }

    /**
     * Одноразовая попытка подобрать 1 просроченную задачу из Redis.
     * Удобно для пошага отладки.
     */
    @POST
    @Path("/check-jobs")
    public Map<String, Object> checkJobs() {
        try {
            long now = System.currentTimeMillis();
            var expired = redis.tryPopDueAtomic(now);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("checkedAt", now);
            payload.put("checkedAtFormatted", new java.util.Date(now).toString());

            if (expired != null) {
                payload.put("expiredJob", Map.of(
                        "jobId", expired.getKey(),
                        "endTime", expired.getValue(),
                        "endTimeFormatted", new java.util.Date(expired.getValue()).toString()
                ));
            } else {
                payload.put("expiredJob", null);
            }
            return payload;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Слить все просроченные задачи: подобрать из Redis и завершить в БД.
     * Параметр limit ограничивает кол-во обработанных задач за один вызов (по умолчанию 1000).
     */
    @POST
    @Path("/drain")
    public Map<String, Object> drain(@QueryParam("limit") @DefaultValue("1000") int limit) {
        int processed = 0;
        long now = System.currentTimeMillis();
        List<String> done = new ArrayList<>();

        try {
            while (processed < limit) {
                var e = redis.tryPopDueAtomic(now);
                if (e == null) break;

                String jobId = e.getKey();
                try {
                    boolean ok = repo.markDoneAndReward(java.util.UUID.fromString(jobId));
                    if (ok) {
                        done.add(jobId);
                        processed++;
                    } else {
                        // по желанию: отправить в dead-letter
                    }
                } catch (Exception ex) {
                    // по желанию: dead-letter
                }
            }

            return Map.of(
                    "processed", processed,
                    "processedIds", done,
                    "limit", limit,
                    "at", now,
                    "atFormatted", new java.util.Date(now).toString()
            );
        } catch (Exception ex) {
            return Map.of("error", ex.getMessage());
        }
    }

    /**
     * Тестовый эндпоинт: запланировать фейковую задачу через N секунд.
     * Удобно быстро проверить цикл: создать → через N секунд drain / автопуллер должен забрать.
     */
    @POST
    @Path("/schedule-test")
    public Map<String, Object> scheduleTest(@QueryParam("seconds") @DefaultValue("5") int seconds) {
        try {
            String jobId = java.util.UUID.randomUUID().toString();
            long endMs = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
            redis.scheduleJob(jobId, endMs);
            return Map.of(
                    "jobId", jobId,
                    "endTime", endMs,
                    "endTimeFormatted", new java.util.Date(endMs).toString()
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Добавить материалы игроку (для тестирования).
     */
    @POST
    @Path("/add-material")
    public Map<String, Object> addMaterial(
            @QueryParam("ownerUuid") String ownerUuid,
            @QueryParam("itemId") String itemId,
            @QueryParam("qty") @DefaultValue("10") long qty) {
        try {
            if (ownerUuid == null || itemId == null) {
                return Map.of("error", "ownerUuid and itemId are required");
            }
            
            UUID owner = UUID.fromString(ownerUuid);
            repo.addMaterial(owner, itemId, qty);
            
            return Map.of(
                    "success", true,
                    "owner", ownerUuid,
                    "itemId", itemId,
                    "added", qty,
                    "message", "Материал добавлен успешно"
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Получить инвентарь материалов игрока.
     */
    @GET
    @Path("/materials")
    public Map<String, Object> getMaterials(@QueryParam("ownerUuid") String ownerUuid) {
        try {
            if (ownerUuid == null) {
                return Map.of("error", "ownerUuid is required");
            }
            
            UUID owner = UUID.fromString(ownerUuid);
            // Получаем основные материалы для тестирования
            Map<String, Long> materials = Map.of(
                    "ore.iron", repo.getMaterial(owner, "ore.iron"),
                    "ore.copper", repo.getMaterial(owner, "ore.copper"),
                    "ore.gold", repo.getMaterial(owner, "ore.gold")
            );
            
            return Map.of(
                    "success", true,
                    "owner", ownerUuid,
                    "materials", materials
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Очистить кэш уровня навыка (для отладки).
     */
    @POST
    @Path("/clear-skill-cache")
    public Map<String, Object> clearSkillCache(
            @QueryParam("ownerUuid") String ownerUuid,
            @QueryParam("skillId") String skillId) {
        try {
            if (ownerUuid == null || skillId == null) {
                return Map.of("error", "ownerUuid and skillId are required");
            }
            
            UUID owner = UUID.fromString(ownerUuid);
            redis.invalidateSkillLevel(owner, skillId);
            
            return Map.of(
                    "success", true,
                    "message", "Кэш навыка очищен",
                    "owner", ownerUuid,
                    "skillId", skillId
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
