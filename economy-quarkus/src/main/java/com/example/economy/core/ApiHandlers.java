package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.UUID;

@ApplicationScoped
public class ApiHandlers {

    // входные модели
    public record StartRequest(UUID ownerUuid, String recipeId, long durationSeconds, String idempotencyKey) {}
    public record ClaimRequest(UUID ownerUuid) {}

    private final Repositories repo;
    private final RedisBus redis;
    private final NatsBus nats;

    // новое: рецепты и каталог скиллов (для бонусов)
    private final RecipeRegistry recipes;
    private final SkillCatalogService skills;

    @Inject
    public ApiHandlers(Repositories repo, RedisBus redis, NatsBus nats,
                       RecipeRegistry recipes, SkillCatalogService skills) {
        this.repo = repo;
        this.redis = redis;
        this.nats = nats;
        this.recipes = recipes;
        this.skills = skills;
    }

    /** Старт производства с учётом рецепта и бонусов от скиллов. */
    public Map<String,Object> startProduction(StartRequest rq) throws Exception {
        Objects.requireNonNull(rq.ownerUuid(), "ownerUuid is required");
        Objects.requireNonNull(rq.recipeId(), "recipeId is required");

        // 1) валидируем рецепт
        var recipe = recipes.get(rq.recipeId());
        if (recipe == null) {
            throw new IllegalArgumentException("Unknown recipeId: " + rq.recipeId());
        }

        // 2) считаем множитель стоимости входов по скиллам игрока
        double costMult = skills.inputCostMultiplier(rq.ownerUuid(), recipe.id, recipe.tag);

        // 3) итоговые требования по материалам (ceil, чтобы не терять доли)
        Map<String, Long> needed = new LinkedHashMap<>();
        for (var in : recipe.inputs) {
            long eff = (long) Math.ceil(in.qty * costMult);
            if (eff > 0) needed.put(in.itemId, eff);
        }

        // 4) атомарно списываем материалы (кинет IllegalStateException если не хватает)
        try {
            repo.consumeMaterials(rq.ownerUuid(), needed);
        } catch (IllegalStateException notEnough) {
            // Превращаем в понятное сообщение для пользователя
            String msg = notEnough.getMessage();
            // Парсим сообщение типа "Not enough ore.iron need 10 have 0"
            if (msg.contains("Not enough")) {
                throw new IllegalArgumentException("Недостаточно материалов для производства: " + msg.replace("Not enough", "Нужно").replace("need", "требуется").replace("have", "имеется"));
            } else {
                throw new IllegalArgumentException("Недостаточно материалов: " + msg);
            }
        }

        // 5) считаем длительность
        long now = System.currentTimeMillis();
        long baseDurMs = recipe.baseDurationMs;
        long requestedMs = Math.max(0, rq.durationSeconds()) * 1000L;
        // если клиент ничего не прислал по времени — берём базовую длительность рецепта
        long end = now + (requestedMs > 0 ? requestedMs : baseDurMs);

        // 6) создаём задачу
        UUID jobId = UUID.randomUUID();
        repo.createJob(jobId, rq.ownerUuid(), recipe.id, now, end, rq.idempotencyKey());

        // 7) планируем в Redis
        try {
            redis.scheduleJob(jobId.toString(), end);
        } catch (Exception e) {
            // материалы уже списаны и job создан — логируем, но не падаем
            System.err.println("Redis scheduling failed: " + e.getMessage());
            e.printStackTrace();
        }

        // 8) ответ
        return Map.of(
                "success", true,
                "jobId", jobId.toString(),
                "endMs", end,
                "multiplier", costMult,
                "inputs", needed   // что реально списали
        );
    }

    public List<Repositories.JobRow> listJobs(UUID owner) throws Exception {
        return repo.listJobs(owner);
    }

    /** Возврат наград; публикуем событие с полем 'claimed' (как ждёт мод). */
    public int claimRewards(UUID owner) throws Exception {
        int n = repo.claimRewards(owner);
        if (n > 0) {
            // важно: поле называется 'claimed', чтобы мод показал "Получено наград: X"
            nats.publish("econ.production.claimed",
                    "{\"owner\":\"" + owner + "\",\"claimed\":" + n + "}");
        }
        return n;
    }
}
