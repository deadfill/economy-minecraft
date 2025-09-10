package com.example.economy.core;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class SkillPoller {
    private static final Logger LOG = Logger.getLogger(SkillPoller.class.getName());

    @Inject Repositories repo;
    @Inject RedisBus redis;
    @Inject NatsBus nats;

    @Scheduled(every = "0.25s", concurrentExecution = ConcurrentExecution.SKIP)
    void tick() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            Map.Entry<UUID, Long> e = redis.tryPopSkillDueAtomic(now);
            if (e == null) break;

            UUID owner = e.getKey();
            try {
                var done = repo.completeSkillTraining(owner);
                
                // Публикуем событие завершения тренировки
                String trainingCompletePayload = String.format("""
                    {
                        "type": "COMPLETED",
                        "skillId": "%s",
                        "targetLevel": %d,
                        "startMs": 0,
                        "endMs": %d,
                        "progress": 1.0,
                        "timestamp": %d
                    }
                    """, done.skillId(), done.level(), now, now);
                
                nats.publish("player." + owner + ".skill.training", trainingCompletePayload);
                
                // Публикуем событие изменения уровня скилла
                String levelUpdatePayload = String.format("""
                    {
                        "skillId": "%s",
                        "oldLevel": %d,
                        "newLevel": %d,
                        "timestamp": %d
                    }
                    """, done.skillId(), done.level() - 1, done.level(), now);
                
                nats.publish("player." + owner + ".skill.level", levelUpdatePayload);
                
                // Старое событие для совместимости
                String json = "{\"owner\":\""+owner+"\",\"skill\":\""+done.skillId()+"\",\"level\":"+done.level()+"}";
                nats.publish("econ.skill.done", json);
                
                LOG.info("Published skill completion events for player " + owner + ", skill " + done.skillId() + " -> level " + done.level());
            } catch (Exception ex) {
                LOG.severe("Error completing skill: owner=" + owner + " err=" + ex.getMessage());
            }
        }
    }
}
