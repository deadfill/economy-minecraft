package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.util.*;

@ApplicationScoped
public class SkillRepo {
    @Inject Database db;

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

    public List<Skill> loadAllSkills() throws Exception {
        List<Skill> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "select id,title,description,max_level,durations_ms,enabled,version from skills where enabled=true")) {
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        }
        return out;
    }

    public List<SkillBonus> loadAllBonuses() throws Exception {
        List<SkillBonus> out = new ArrayList<>();
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                     "select skill_id, kind, target, op, per_level_bps, cap_bps, enabled from skill_bonuses where enabled=true")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SkillBonus(
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getBoolean(7)
                    ));
                }
            }
        }
        return out;
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
}
