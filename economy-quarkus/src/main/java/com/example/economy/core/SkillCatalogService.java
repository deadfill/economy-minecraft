package com.example.economy.core;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@ApplicationScoped
public class SkillCatalogService {
    @Inject SkillRepo repo;
    @Inject Repositories playerRepo; // нужен getSkillLevel(owner, skillId)

    private volatile Map<String, SkillRepo.Skill> skills = Map.of();
    private volatile List<SkillRepo.SkillBonus> bonuses = List.of();
    private volatile String etag = "empty";

    @PostConstruct
    void init() { refresh(); }

    public synchronized void refresh() {
        try {
            var s = repo.loadAllSkills();
            var b = repo.loadAllBonuses();
            Map<String, SkillRepo.Skill> m = new LinkedHashMap<>();
            for (var x : s) m.put(x.id, x);
            skills = Collections.unmodifiableMap(m);
            bonuses = List.copyOf(b);
            etag = computeEtag(s, b);
        } catch (Exception e) {
            // лог и оставляем старые кэши
        }
    }

    private static String computeEtag(List<SkillRepo.Skill> s, List<SkillRepo.SkillBonus> b) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            s.stream().sorted(Comparator.comparing(x->x.id)).forEach(x -> {
                md.update(x.id.getBytes(StandardCharsets.UTF_8));
                md.update((byte)x.maxLevel);
                for (long d : x.durationsMs) md.update(Long.toString(d).getBytes(StandardCharsets.UTF_8));
            });
            b.stream().sorted(Comparator.comparing(x -> x.skillId + "|" + x.kind + "|" + x.target))
                    .forEach(x -> {
                        md.update(x.skillId.getBytes(StandardCharsets.UTF_8));
                        md.update(x.kind.getBytes(StandardCharsets.UTF_8));
                        if (x.target != null) md.update(x.target.getBytes(StandardCharsets.UTF_8));
                        md.update(x.op.getBytes(StandardCharsets.UTF_8));
                        md.update(Integer.toString(x.perLevelBps).getBytes(StandardCharsets.UTF_8));
                        md.update(Integer.toString(x.capBps).getBytes(StandardCharsets.UTF_8));
                    });
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte bb : d) sb.append(String.format("%02x", bb));
            return sb.toString();
        } catch (Exception e) { return Long.toString(System.currentTimeMillis()); }
    }

    public String etag() { return etag; }
    public Collection<SkillRepo.Skill> allSkills() { return skills.values(); }
    public SkillRepo.Skill skillOrNull(String id) { return skills.get(id); }
    public boolean skillExists(String id) { return skills.containsKey(id); }

    /** Итоговый множитель стоимости входов для игрока по recipeId и тегу рецепта. */
    public double inputCostMultiplier(UUID owner, String recipeId, String recipeTag) {
        // стартуем с 1.0, затем применяем «наибольшую скидку» от всех релевантных бонусов
        double best = 1.0;
        for (var b : bonuses) {
            if (!"INPUT_COST_MULTIPLIER".equals(b.op)) continue;
            boolean hit = switch (b.kind) {
                case "all"    -> true;
                case "recipe" -> recipeId != null && recipeId.equalsIgnoreCase(b.target);
                case "tag"    -> recipeTag != null && recipeTag.equalsIgnoreCase(b.target);
                default       -> false;
            };
            if (!hit) continue;

            try {
                int lvl = playerRepo.getSkillLevel(owner, b.skillId);
                if (lvl <= 0) continue;
                int effectiveBps = Math.min(b.capBps, b.perLevelBps * lvl);
                double mult = 1.0 - (effectiveBps / 10_000.0);
                if (mult < best) best = mult; // берём лучшую (наибольшую скидку)
            } catch (Exception ignored) {}
        }
        return Math.max(0.0, best);
    }
}
