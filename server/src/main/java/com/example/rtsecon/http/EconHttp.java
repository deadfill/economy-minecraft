package com.example.rtsecon.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconHttp {
    private static final String BASE = System.getProperty("econ.base", "http://localhost:8081");
    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient C = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ===== AUTH =====
    // Авторизация теперь управляется SimpleAuth модом
    // HTTP методы authRegister и authLogin удалены

    // ===== PRODUCTION =====
    public static JsonNode startProduction(UUID owner, String recipeId, int seconds) throws Exception {
        String body = String.format("{\"ownerUuid\":\"%s\",\"recipeId\":\"%s\",\"durationSeconds\":%d}",
                owner, recipeId, seconds);
        HttpRequest rq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/v1/production/start"))
                .header("Content-Type","application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var resp = C.send(rq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        
        JsonNode response = M.readTree(resp.body());
        
        // Проверяем новый формат ответа с полем success
        if (response.has("success") && !response.get("success").asBoolean()) {
            // Ошибка валидации - извлекаем понятное сообщение
            JsonNode error = response.path("error");
            String message = error.path("message").asText("Неизвестная ошибка");
            throw new RuntimeException(message);
        }
        
        return response;
    }

    public static class Job {
        public String jobId;
        public long endMs;
        public String status;
        public Job() {}
    }

    public static List<Job> listJobs(UUID owner) throws Exception {
        HttpRequest rq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/v1/production/list?ownerUuid=" + owner))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        var resp = C.send(rq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        JsonNode arr = M.readTree(resp.body());
        List<Job> out = new ArrayList<>();
        for (var it : arr) {
            Job j = new Job();
            j.jobId = it.get("id").asText();
            j.endMs = it.get("endMs").asLong();
            j.status = it.get("status").asText();
            out.add(j);
        }
        return out;
    }

    public static int claim(UUID owner) throws Exception {
        String body = String.format("{\"ownerUuid\":\"%s\"}", owner);
        HttpRequest rq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/v1/production/claim"))
                .header("Content-Type","application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var resp = C.send(rq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        JsonNode j = M.readTree(resp.body());
        return j.get("claimed").asInt();
    }

    // ===== SKILLS (actions) =====
    public static JsonNode trainSkill(UUID owner, String skillId) throws Exception {
        String body = String.format("{\"ownerUuid\":\"%s\",\"skillId\":\"%s\"}", owner, skillId);
        HttpRequest rq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/v1/skills/train"))
                .header("Content-Type","application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var resp = C.send(rq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        return M.readTree(resp.body());
    }

    public static JsonNode skillStatus(UUID owner) throws Exception {
        HttpRequest rq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/v1/skills/status?ownerUuid=" + owner))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        var resp = C.send(rq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        return M.readTree(resp.body());
    }

    public static JsonNode skillLevel(UUID owner, String skillId) throws Exception {
        String cacheKey = owner + ":" + skillId;
        SkillLevelCache cached = LEVEL_CACHE.get(cacheKey);
        
        // Возвращаем из кэша если не истек
        if (cached != null && !cached.isExpired()) {
            return M.createObjectNode().put("level", cached.level);
        }
        
        // Запрос к серверу
        HttpRequest rq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/v1/skills/level?ownerUuid=" + owner + "&skillId=" + skillId))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        var resp = C.send(rq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        
        JsonNode result = M.readTree(resp.body());
        
        // Кэшируем результат
        int level = result.get("level").asInt();
        LEVEL_CACHE.put(cacheKey, new SkillLevelCache(level));
        
        return result;
    }

    // ===== SKILLS CATALOG (cached by ETag) =====
    public static class SkillDef {
        public String id;
        public String title;
        public String desc;
        public int maxLevel;
        public long[] durationsMs;
    }

    private static class SkillsCache {
        String etag = null;
        List<SkillDef> skills = new ArrayList<>();
    }
    private static final SkillsCache SKILLS_CACHE = new SkillsCache();

    // Кэш уровней навыков (5 минут)
    private static class SkillLevelCache {
        final long timestamp;
        final int level;
        SkillLevelCache(int level) { 
            this.level = level; 
            this.timestamp = System.currentTimeMillis(); 
        }
        boolean isExpired() { 
            return System.currentTimeMillis() - timestamp > 300_000; // 5 минут
        }
    }
    private static final Map<String, SkillLevelCache> LEVEL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Return cached skills list, refresh from server if ETag changed. */
    public static synchronized List<SkillDef> fetchSkills() throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/v1/skills/list"))
                .timeout(Duration.ofSeconds(8))
                .GET();
        if (SKILLS_CACHE.etag != null) {
            b.header("If-None-Match", SKILLS_CACHE.etag);
        }
        var resp = C.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 304) {
            return SKILLS_CACHE.skills;
        }
        if (resp.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode root = M.readTree(resp.body());
        JsonNode arr = root.path("skills");
        List<SkillDef> out = new ArrayList<>();
        for (var it : arr) {
            SkillDef s = new SkillDef();
            s.id = it.get("id").asText();
            s.title = it.path("title").asText("");
            s.desc = it.path("desc").asText("");
            s.maxLevel = it.path("maxLevel").asInt(5);
            var dnode = it.path("durationsMs");
            long[] durs = new long[dnode.size()];
            for (int i=0;i<dnode.size();i++) durs[i] = dnode.get(i).asLong();
            s.durationsMs = durs;
            out.add(s);
        }
        SKILLS_CACHE.skills = out;
        String etag = resp.headers().firstValue("ETag").orElse(null);
        SKILLS_CACHE.etag = etag;
        return out;
    }

    /** Clear client-side skills cache. */
    public static synchronized void invalidateSkillsCache() {
        SKILLS_CACHE.etag = null;
        SKILLS_CACHE.skills = new ArrayList<>();
    }

    /** Clear skill level cache for specific player and skill */
    public static void invalidateSkillLevelCache(UUID owner, String skillId) {
        LEVEL_CACHE.remove(owner + ":" + skillId);
    }

    /** Clear all skill level cache */
    public static void clearAllSkillLevelCache() {
        LEVEL_CACHE.clear();
    }
}

