package com.example.economy.core;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.ScoredValue;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class RedisBus {
    // Производство
    public static final String ZSET_DUE = "prod:due";
    // Навыки
    public static final String ZSET_SKILL_DUE = "skill:due";
    // Кэш уровней навыков (TTL 5 минут)
    public static final String SKILL_LEVEL_PREFIX = "skill:level:";

    private final SortedSetCommands<String, String> zset;
    private final ValueCommands<String, String> values;
    private final KeyCommands<String> keys;

    @Inject
    public RedisBus(RedisDataSource ds) {
        this.zset = ds.sortedSet(String.class, String.class);
        this.values = ds.value(String.class, String.class);
        this.keys = ds.key(String.class);
    }

    /* ===================== ПРОИЗВОДСТВО ===================== */

    /** Запланировать завершение производственной задачи */
    public void scheduleJob(String jobId, long endMs) {
        zset.zadd(ZSET_DUE, endMs, jobId);
    }

    /** Атомарно забрать просроченную прод-задачу (или вернуть в ZSET, если ещё рано) */
    public Map.Entry<String, Long> tryPopDueAtomic(long nowMs) {
        var popped = zset.zpopmin(ZSET_DUE, 1);
        if (popped == null || popped.isEmpty()) return null;

        var sv = popped.get(0);
        long endAtMs = (long) sv.score();
        String jobId = sv.value();

        if (endAtMs > nowMs) {
            // ещё не наступил дедлайн — вернём обратно
            zset.zadd(ZSET_DUE, endAtMs, jobId);
            return null;
        }
        return Map.entry(jobId, endAtMs);
    }

    /** Для отладки: показать все задачи производства */
    public List<ScoredValue<String>> listAllJobs() {
        return zset.zrangeWithScores(ZSET_DUE, 0, -1);
    }

    /* ======================= НАВЫКИ ======================== */

    /** Запланировать завершение обучения навыка у игрока */
    public void scheduleSkill(UUID owner, long endMs) {
        zset.zadd(ZSET_SKILL_DUE, endMs, owner.toString());
    }

    /**
     * Атомарно забрать просроченную запись обучения навыка.
     * Возвращает (ownerUuid, endAtMs) или null, если пока рано/пусто.
     */
    public Map.Entry<UUID, Long> tryPopSkillDueAtomic(long nowMs) {
        var popped = zset.zpopmin(ZSET_SKILL_DUE, 1);
        if (popped == null || popped.isEmpty()) return null;

        var sv = popped.get(0);
        long endAtMs = (long) sv.score();
        String ownerStr = sv.value();

        if (endAtMs > nowMs) {
            // вернём обратно — дедлайн ещё не настал
            zset.zadd(ZSET_SKILL_DUE, endAtMs, ownerStr);
            return null;
        }
        return Map.entry(UUID.fromString(ownerStr), endAtMs);
    }

    /** Для отладки: показать все таймеры навыков */
    public List<ScoredValue<String>> listAllSkillTimers() {
        return zset.zrangeWithScores(ZSET_SKILL_DUE, 0, -1);
    }

    /* ===================== КЭШИРОВАНИЕ НАВЫКОВ ===================== */

    /** Получить уровень навыка из кэша (null если нет в кэше) */
    public Integer getCachedSkillLevel(UUID owner, String skillId) {
        try {
            String key = SKILL_LEVEL_PREFIX + owner + ":" + skillId;
            String value = values.get(key);
            return value != null ? Integer.parseInt(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Закэшировать уровень навыка на 5 минут */
    public void cacheSkillLevel(UUID owner, String skillId, int level) {
        try {
            String key = SKILL_LEVEL_PREFIX + owner + ":" + skillId;
            values.set(key, String.valueOf(level));
            keys.expire(key, Duration.ofMinutes(5)); // устанавливаем TTL отдельно
        } catch (Exception ignored) {
            // Если Redis недоступен - не падаем
        }
    }

    /** Инвалидировать кэш уровня навыка (при изменении) */
    public void invalidateSkillLevel(UUID owner, String skillId) {
        try {
            String key = SKILL_LEVEL_PREFIX + owner + ":" + skillId;
            keys.del(key); // удаляем ключ
        } catch (Exception ignored) {}
    }
}
