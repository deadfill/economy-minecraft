package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.util.*;

@ApplicationScoped
public class SkillRepo {
    @Inject Database db;
    @Inject DatabaseRouter databaseRouter;

    public static final class Skill {
        public final String id, title, description;
        public final int maxLevel, version;
        public final boolean enabled;
        public final long[] durationsMs;
        public Skill(String id, String title, String description, int maxLevel, long[] durationsMs, boolean enabled, int version){
            this.id=id; this.title=title; this.description=description;
            this.maxLevel=maxLevel; this.durationsMs=durationsMs;
            this.enabled=enabled; this.version=version;
        }
    }

    public static final class SkillBonus {
        public final String skillId, kind, target, op;
        public final int perLevelBps, capBps;
        public final boolean enabled;
        public SkillBonus(String skillId, String kind, String target, String op, int perLevelBps, int capBps, boolean enabled) {
            this.skillId=skillId; this.kind=kind; this.target=target; this.op=op;
            this.perLevelBps=perLevelBps; this.capBps=capBps; this.enabled=enabled;
        }
    }

    // SELECT операция - читаем с реплик согласно правилам
    public List<Skill> loadAllSkills() throws Exception {
        return databaseRouter.executeRead(conn -> {
            List<Skill> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "select id,title,description,max_level,durations_ms,enabled,version from skills where enabled=true");
                 ResultSet rs = ps.executeQuery()) {
                
                while (rs.next()) {
                    String id = rs.getString(1);
                    String title = rs.getString(2);
                    String desc = rs.getString(3);
                    int maxL = rs.getInt(4);
                    String json = rs.getString(5);
                    long[] dur = parseDurations(json, maxL);
                    boolean enabled = rs.getBoolean(6);
                    int ver = rs.getInt(7);
                    out.add(new Skill(id, title, desc, maxL, dur, enabled, ver));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load skills", e);
            }
            return out;
        });
    }

    // SELECT операция - читаем с реплик согласно правилам
    public List<SkillBonus> loadAllBonuses() throws Exception {
        return databaseRouter.executeRead(conn -> {
            List<SkillBonus> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "select skill_id, kind, target, op, per_level_bps, cap_bps, enabled from skill_bonuses where enabled=true");
                 ResultSet rs = ps.executeQuery()) {
                
                while (rs.next()) {
                    out.add(new SkillBonus(
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getBoolean(7)
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load skill bonuses", e);
            }
            return out;
        });
    }

    private static long[] parseDurations(String json, int maxL) {
        String s = json.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) throw new IllegalArgumentException("bad durations json");
        s = s.substring(1, s.length()-1).trim();
        String[] parts = s.isEmpty()? new String[0] : s.split(",");
        if (parts.length != maxL) throw new IllegalArgumentException("durations length != maxLevel");
        long[] out = new long[parts.length];
        for (int i=0;i<parts.length;i++) out[i] = Long.parseLong(parts[i].trim());
        return out;
    }
    
    // INSERT операция - пишем в primary согласно правилам
    public void saveSkill(Skill skill) throws Exception {
        databaseRouter.executeWrite(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "insert into skills(id,title,description,max_level,durations_ms,enabled,version) values(?,?,?,?,?,?,?) " +
                    "on conflict (id) do update set title=excluded.title, description=excluded.description, " +
                    "max_level=excluded.max_level, durations_ms=excluded.durations_ms, enabled=excluded.enabled, " +
                    "version=excluded.version, updated_at=now()")) {
                
                // Преобразуем durationsMs в JSON
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < skill.durationsMs.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(skill.durationsMs[i]);
                }
                sb.append("]");
                String durationsJson = sb.toString();
                
                ps.setString(1, skill.id);
                ps.setString(2, skill.title);
                ps.setString(3, skill.description);
                ps.setInt(4, skill.maxLevel);
                ps.setString(5, durationsJson);
                ps.setBoolean(6, skill.enabled);
                ps.setInt(7, skill.version);
                
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save skill", e);
            }
            return null;
        });
    }
}
