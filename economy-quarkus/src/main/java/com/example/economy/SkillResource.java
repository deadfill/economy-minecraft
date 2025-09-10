package com.example.economy;

import com.example.economy.core.NatsBus;
import com.example.economy.core.RedisBus;
import com.example.economy.core.Repositories;
import com.example.economy.core.SkillCatalogService;
import com.example.economy.core.SkillRepo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.*;

@Path("/api/v1/skills")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SkillResource {

    private static final Logger LOG = Logger.getLogger(SkillResource.class);

    @Inject Repositories repo;
    @Inject RedisBus redis;
    @Inject NatsBus nats;

    // Новый: серверный каталог скиллов (загружен из БД и закэширован в памяти)
    @Inject SkillCatalogService catalog;

    // ===== DTO =====
    public record TrainReq(UUID ownerUuid, String skillId) {}

    // ===== Список скиллов (с ETag) =====
    @GET @Path("/list")
    public Response list(@HeaderParam("If-None-Match") String ifNoneMatch) {
        String etag = catalog.etag();
        if (etag != null && etag.equals(ifNoneMatch)) {
            return Response.status(Response.Status.NOT_MODIFIED).build(); // 304
        }
        var items = new ArrayList<Map<String,Object>>();
        for (SkillRepo.Skill s : catalog.allSkills()) {
            items.add(Map.of(
                    "id", s.id,
                    "title", s.title,
                    "desc", s.description,
                    "maxLevel", s.maxLevel,
                    "durationsMs", s.durationsMs
            ));
        }
        return Response.ok(Map.of("skills", items))
                .header("ETag", etag)
                .build();
    }

    // Быстрая проверка версии каталога
    @GET @Path("/hash")
    public Map<String, Object> hash() {
        return Map.of("etag", catalog.etag());
    }

    // ===== Запуск тренировки =====
    @POST @Path("/train")
    public Map<String,Object> train(TrainReq req) throws Exception {
        LOG.infof("Skill train request: %s", req);
        
        if (req == null || req.ownerUuid() == null || req.skillId() == null || req.skillId().isBlank()) {
            LOG.warnf("Invalid train request: %s", req);
            throw new WebApplicationException("ownerUuid and skillId are required", 400);
        }

        LOG.infof("Training skill '%s' for user: %s", req.skillId(), req.ownerUuid());

        // Валидация skillId по серверному каталогу (источник истины — БД)
        SkillRepo.Skill def = catalog.skillOrNull(req.skillId());
        if (def == null) {
            LOG.warnf("Unknown skillId: %s", req.skillId());
            throw new WebApplicationException("Unknown skillId: " + req.skillId(), 400);
        }

        // Текущий уровень -> целевой
        int current = repo.getSkillLevel(req.ownerUuid(), def.id);
        int target  = Math.min(current + 1, def.maxLevel);
        LOG.infof("Skill '%s' current level: %d, target: %d", def.id, current, target);
        
        if (target <= 0 || target > def.maxLevel) {
            LOG.warnf("Invalid target level %d for skill %s (max: %d)", target, def.id, def.maxLevel);
            throw new WebApplicationException("Invalid target level", 400);
        }

        // Длительность берём из каталога (а не от клиента)
        long[] table = def.durationsMs;
        long dur = table[target - 1];
        LOG.infof("Training duration: %d ms", dur);

        long now = System.currentTimeMillis(); // при желании можно заменить временем БД
        
        try {
            var t = repo.startSkillTraining(req.ownerUuid(), def.id, now, dur);
            LOG.infof("Skill training started: %s", t);

            // Планируем завершение в Redis
            redis.scheduleSkill(req.ownerUuid(), t.endMs());
            LOG.infof("Skill training scheduled in Redis for completion at: %d", t.endMs());

            // Публикуем событие начала тренировки в NATS
            try {
                String trainingStartPayload = String.format("""
                    {
                        "type": "STARTED",
                        "skillId": "%s",
                        "targetLevel": %d,
                        "startMs": %d,
                        "endMs": %d,
                        "progress": 0.0,
                        "timestamp": %d
                    }
                    """, def.id, t.targetLevel(), t.startMs(), t.endMs(), now);
                
                nats.publish("player." + req.ownerUuid() + ".skill.training", trainingStartPayload);
                LOG.infof("Published training start event for player %s, skill %s", req.ownerUuid(), def.id);
            } catch (Exception ex) {
                LOG.warn("Failed to publish training start event: " + ex.getMessage());
            }

            return Map.of(
                    "skill", def.id,
                    "targetLevel", t.targetLevel(),
                    "startMs", t.startMs(),
                    "endMs", t.endMs()
            );
        } catch (Exception e) {
            LOG.errorf(e, "Failed to start skill training for user %s, skill %s", req.ownerUuid(), req.skillId());
            throw e;
        }
    }

    // ===== Текущий уровень конкретного скилла =====
    @GET @Path("/level")
    public Map<String,Object> lvl(@QueryParam("ownerUuid") UUID owner, @QueryParam("skillId") String skillId) throws Exception {
        if (owner == null || skillId == null || skillId.isBlank()) {
            throw new WebApplicationException("ownerUuid and skillId are required", 400);
        }
        if (!catalog.skillExists(skillId)) {
            throw new WebApplicationException("Unknown skillId: " + skillId, 400);
        }
        return Map.of("level", repo.getSkillLevel(owner, skillId));
    }

    // ===== Статус активной тренировки =====
    @GET @Path("/status")
    public Map<String,Object> status(@QueryParam("ownerUuid") UUID owner) throws Exception {
        if (owner == null) throw new WebApplicationException("ownerUuid is required", 400);

        var s = repo.getActiveTraining(owner);
        if (s == null) {
            var map = new HashMap<String,Object>();
            map.put("current", null);
            return map;
        }

        long now = System.currentTimeMillis();
        long left = Math.max(0, s.endMs() - now);
        return Map.of(
                "current", Map.of(
                        "skillId", s.skillId(),
                        "targetLevel", s.targetLevel(),
                        "startMs", s.startMs(),
                        "endMs", s.endMs(),
                        "leftMs", left
                )
        );
    }
}
