package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.UUID;

@ApplicationScoped
public class Repositories {
    private final Database db;
    private final RedisBus redis;
    
    public Repositories(Database db, RedisBus redis) { 
        this.db = db; 
        this.redis = redis;
    }

    // ===== helpers =====
    /** Стабильный идемпотентный ключ для повторных запросов "тот же владелец+рецепт+окно времени". */
    private static String idemKeyFor(UUID owner, String recipeId, long startMs, long endMs) {
        // для компактности сделаем SHA-256 от строки "<owner>|<recipe>|<start>|<end>"
        String raw = owner + "|" + recipeId + "|" + startMs + "|" + endMs;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString(); // 64 hex символа
        } catch (Exception e) {
            // в маловероятном случае проблемы с MessageDigest — fallback на исходную строку
            return raw;
        }
    }

    // ----- AUTH -----
    public void upsertPlayer(UUID uuid, String username) throws Exception {
        try (Connection c = db.getWriteConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into players(uuid, username) values(?, ?) " +
                            "on conflict (uuid) do update set username = excluded.username, last_seen = now()")) {
                ps.setObject(1, uuid);
                ps.setString(2, username);
                ps.executeUpdate();
            }
            // ensure wallet row
            try (PreparedStatement w = c.prepareStatement(
                    "insert into wallets(owner_uuid) values(?) on conflict (owner_uuid) do nothing")) {
                w.setObject(1, uuid);
                w.executeUpdate();
            }
            c.commit();
        }
    }

    public boolean createAuthUser(UUID uuid, String rawPassword) throws Exception {
        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
        try (Connection c = db.getWriteConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into auth_users(uuid, pass_hash) values(?, ?) " +
                            "on conflict (uuid) do update set pass_hash = excluded.pass_hash, updated_at = now()")) {
                ps.setObject(1, uuid);
                ps.setString(2, hash);
                ps.executeUpdate();
            }
            c.commit();
            return true;
        }
    }

    public boolean verifyAuth(UUID uuid, String rawPassword) throws Exception {
        String hash = null;
        try (Connection c = db.getReadConnection();
             PreparedStatement ps = c.prepareStatement("select pass_hash from auth_users where uuid=?")) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) hash = rs.getString(1);
            }
        }
        return hash != null && BCrypt.checkpw(rawPassword, hash);
    }

    // ----- PRODUCTION -----
    public record JobRow(UUID id, UUID owner, String recipeId, long startMs, long endMs, String status) {}

    /**
     * Создаёт job, используя частичный уникальный индекс по idempotency_key.
     * Если вызов повторный (тот же idemKey) — вернёт уже существующую строку.
     */
    public JobRow createJob(UUID id, UUID owner, String recipeId, long startMs, long endMs, String idemKey) throws Exception {
        if (idemKey == null || idemKey.isBlank()) {
            idemKey = idemKeyFor(owner, recipeId, startMs, endMs);
        }

        // ВАЖНО: для частичного индекса нужен предикат в ON CONFLICT
        final String SQL_INSERT = """
            insert into production_jobs(id, owner_uuid, recipe_id, start_ms, end_ms, status, idempotency_key)
            values (?,?,?,?,?,'IN_PROGRESS',?)
            on conflict (idempotency_key) where (idempotency_key is not null) do nothing
            returning id, owner_uuid, recipe_id, start_ms, end_ms, status
            """;

        try (Connection c = db.getWriteConnection()) {
            c.setAutoCommit(false);

            // Пытаемся вставить
            try (PreparedStatement ps = c.prepareStatement(SQL_INSERT)) {
                ps.setObject(1, id);
                ps.setObject(2, owner);
                ps.setString(3, recipeId);
                ps.setLong(4, startMs);
                ps.setLong(5, endMs);
                ps.setString(6, idemKey);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // Вставка прошла, сразу возвращаем созданную строку
                        JobRow row = new JobRow(
                                (UUID) rs.getObject(1),
                                (UUID) rs.getObject(2),
                                rs.getString(3),
                                rs.getLong(4),
                                rs.getLong(5),
                                rs.getString(6)
                        );
                        c.commit();
                        return row;
                    }
                }
            }

            // Вставка не произошла (повтор) — достанем существующую по idemKey
            try (PreparedStatement sel = c.prepareStatement("""
                    select id, owner_uuid, recipe_id, start_ms, end_ms, status
                    from production_jobs
                    where idempotency_key = ?
                    """)) {
                sel.setString(1, idemKey);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        JobRow row = new JobRow(
                                (UUID) rs.getObject(1),
                                (UUID) rs.getObject(2),
                                rs.getString(3),
                                rs.getLong(4),
                                rs.getLong(5),
                                rs.getString(6)
                        );
                        c.commit();
                        return row;
                    } else {
                        // Теоретически не должно случиться: конфликт был, но строки нет.
                        c.rollback();
                        throw new IllegalStateException("Idempotency conflict but no existing row found");
                    }
                }
            }
        }
    }

    public boolean markDoneAndReward(UUID jobId) throws Exception {
        try (Connection c = db.getWriteConnection()) {
            c.setAutoCommit(false);

            // 1) mark done iff IN_PROGRESS
            int n;
            try (PreparedStatement up = c.prepareStatement(
                    "update production_jobs set status='DONE', updated_at=now() where id=? and status='IN_PROGRESS'")) {
                up.setObject(1, jobId);
                n = up.executeUpdate();
                if (n == 0) { c.rollback(); return false; }
            }

            // 2) add reward to owner
            UUID owner = null;
            try (PreparedStatement sel = c.prepareStatement("select owner_uuid from production_jobs where id=?")) {
                sel.setObject(1, jobId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) owner = (UUID) rs.getObject(1);
                }
            }
            if (owner == null) { c.rollback(); return false; }

            try (PreparedStatement rw = c.prepareStatement(
                    "insert into player_rewards(owner_uuid, count) values(?,1) " +
                            "on conflict (owner_uuid) do update set count = player_rewards.count + 1, updated_at=now()")) {
                rw.setObject(1, owner);
                rw.executeUpdate();
            }

            c.commit();
            return true;
        }
    }

    public List<JobRow> listJobs(UUID owner) throws Exception {
        List<JobRow> out = new ArrayList<>();
        try (Connection c = db.getReadConnection();
             PreparedStatement ps = c.prepareStatement(
                     "select id, owner_uuid, recipe_id, start_ms, end_ms, status " +
                             "from production_jobs where owner_uuid=? order by end_ms asc")) {
            ps.setObject(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new JobRow(
                            (UUID) rs.getObject(1),
                            (UUID) rs.getObject(2),
                            rs.getString(3),
                            rs.getLong(4),
                            rs.getLong(5),
                            rs.getString(6)
                    ));
                }
            }
        }
        return out;
    }

    public int claimRewards(UUID owner) throws Exception {
        try (Connection c = db.getWriteConnection()) {
            c.setAutoCommit(false);

            int current = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "select count from player_rewards where owner_uuid=? for update")) {
                ps.setObject(1, owner);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) current = rs.getInt(1);
                }
            }

            try (PreparedStatement del = c.prepareStatement(
                    "delete from player_rewards where owner_uuid=?")) {
                del.setObject(1, owner);
                del.executeUpdate();
            }

            c.commit();
            return current;
        }
    }

    // Найти владельца джобы по её id
    public UUID findOwnerByJobId(UUID jobId) throws Exception {
        try (Connection c = db.getReadConnection();
             PreparedStatement ps = c.prepareStatement(
                     "select owner_uuid from production_jobs where id = ?")) {
            ps.setObject(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object val = rs.getObject(1);
                    if (val instanceof UUID) return (UUID) val;
                    return UUID.fromString(String.valueOf(val));
                }
            }
        }
        throw new IllegalArgumentException("Job not found: " + jobId);
    }

    /* ===================== НАВЫКИ ===================== */

    /** Текущий уровень навыка (с кэшированием) */
    public int getSkillLevel(UUID owner, String skillId) throws Exception {
        // Сначала проверяем кэш
        Integer cached = redis.getCachedSkillLevel(owner, skillId);
        if (cached != null) {
            return cached;
        }
        
        // Если нет в кэше - идем в БД
        int level;
        try (var c = db.getReadConnection();
             var ps = c.prepareStatement("select level from skill_levels where owner_uuid=? and skill_id=?")) {
            ps.setObject(1, owner);
            ps.setString(2, skillId);
            try (var rs = ps.executeQuery()) {
                level = rs.next() ? rs.getInt(1) : 0;
            }
        }
        
        // Кэшируем результат
        redis.cacheSkillLevel(owner, skillId, level);
        return level;
    }

    public record SkillTrainingRow(String skillId, int targetLevel, long startMs, long endMs) {}
    public record SkillDone(String skillId, int level) {}

    /** Старт обучения (ровно одна активная запись на игрока) */
    public SkillTrainingRow startSkillTraining(UUID owner, String skillId, long nowMs, long durationMs) throws Exception {
        try (var c = db.getWriteConnection()) {
            c.setAutoCommit(false);

            // запрет второй активной
            try (var chk = c.prepareStatement("select 1 from skill_training where owner_uuid=? and status='IN_PROGRESS'")) {
                chk.setObject(1, owner);
                try (var rs = chk.executeQuery()) {
                    if (rs.next()) { c.rollback(); throw new IllegalStateException("Already training"); }
                }
            }

            int current = getSkillLevel(owner, skillId);
            int target  = Math.min(current + 1, 5);
            long endMs  = nowMs + durationMs;

            try (var ins = c.prepareStatement(
                    "insert into skill_training(owner_uuid, skill_id, target_level, start_ms, end_ms, status) " +
                            "values (?,?,?,?,?,'IN_PROGRESS')")) {
                ins.setObject(1, owner);
                ins.setString(2, skillId);
                ins.setInt(3, target);
                ins.setLong(4, nowMs);
                ins.setLong(5, endMs);
                ins.executeUpdate();
            }
            c.commit();
            return new SkillTrainingRow(skillId, target, nowMs, endMs);
        }
    }

    /** Завершение обучения: апдейт уровня и очистка training */
    public SkillDone completeSkillTraining(UUID owner) throws Exception {
        try (var c = db.getWriteConnection()) {
            c.setAutoCommit(false);

            String skill = null; int lvl = 0;

            try (var sel = c.prepareStatement(
                    "select skill_id, target_level from skill_training where owner_uuid=? and status='IN_PROGRESS' for update")) {
                sel.setObject(1, owner);
                try (var rs = sel.executeQuery()) {
                    if (rs.next()) {
                        skill = rs.getString(1);
                        lvl   = rs.getInt(2);
                    }
                }
            }
            if (skill == null) { c.rollback(); throw new IllegalStateException("No active training"); }

            try (var up = c.prepareStatement(
                    "insert into skill_levels(owner_uuid, skill_id, level) values(?,?,?) " +
                            "on conflict (owner_uuid, skill_id) do update set level=excluded.level, updated_at=now()")) {
                up.setObject(1, owner);
                up.setString(2, skill);
                up.setInt(3, lvl);
                up.executeUpdate();
            }
            
            // Инвалидируем кэш после изменения уровня
            redis.invalidateSkillLevel(owner, skill);

            try (var del = c.prepareStatement("delete from skill_training where owner_uuid=?")) {
                del.setObject(1, owner);
                del.executeUpdate();
            }

            c.commit();
            return new SkillDone(skill, lvl);
        }
    }

    /** Активная прокачка (для статуса) */
    public SkillTrainingRow getActiveTraining(UUID owner) throws Exception {
        try (var c = db.getReadConnection();
             var ps = c.prepareStatement(
                     "select skill_id, target_level, start_ms, end_ms from skill_training where owner_uuid=? and status='IN_PROGRESS'")) {
            ps.setObject(1, owner);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SkillTrainingRow(
                            rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4)
                    );
                }
            }
        }
        return null;
    }
    // === INVENTORY (материалы) ===
    public long getMaterial(UUID owner, String itemId) throws Exception {
        try (Connection c = db.getReadConnection();
             PreparedStatement ps = c.prepareStatement(
                     "select qty from player_materials where owner_uuid=? and item_id=?")) {
            ps.setObject(1, owner);
            ps.setString(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0L;
    }

    /** Атомарно списывает требуемые количества. Бросает исключение, если не хватает. */
    public void consumeMaterials(UUID owner, Map<String, Long> req) throws Exception {
        try (Connection c = db.getWriteConnection()) {
            c.setAutoCommit(false);
            // блокируем строки инвентаря
            for (var e : req.entrySet()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "select qty from player_materials where owner_uuid=? and item_id=? for update")) {
                    ps.setObject(1, owner);
                    ps.setString(2, e.getKey());
                    try (ResultSet rs = ps.executeQuery()) {
                        long have = 0;
                        if (rs.next()) have = rs.getLong(1);
                        if (have < e.getValue()) {
                            c.rollback();
                            throw new IllegalStateException("Not enough " + e.getKey() + " need " + e.getValue() + " have " + have);
                        }
                    }
                }
            }
            // списываем
            for (var e : req.entrySet()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "update player_materials set qty = qty - ? where owner_uuid=? and item_id=?")) {
                    ps.setLong(1, e.getValue());
                    ps.setObject(2, owner);
                    ps.setString(3, e.getKey());
                    ps.executeUpdate();
                }
            }
            c.commit();
        }
    }

    /** Удобно пополнить материалы админ-командой или тестом */
    public void addMaterial(UUID owner, String itemId, long delta) throws Exception {
        try (Connection c = db.getWriteConnection();
             PreparedStatement ps = c.prepareStatement(
                     "insert into player_materials(owner_uuid,item_id,qty) values(?,?,?) " +
                             "on conflict (owner_uuid,item_id) do update set qty = player_materials.qty + excluded.qty")) {
            ps.setObject(1, owner);
            ps.setString(2, itemId);
            ps.setLong(3, delta);
            ps.executeUpdate();
        }
    }

}
