package com.example.skill;

/**
 * Модель активной тренировки скилла
 */
public class SkillTraining {
    public String skillId;
    public int targetLevel;
    public long startMs;
    public long endMs;

    public SkillTraining() {}

    public SkillTraining(String skillId, int targetLevel, long startMs, long endMs) {
        this.skillId = skillId;
        this.targetLevel = targetLevel;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    /**
     * Получить прогресс тренировки (0.0 - 1.0)
     */
    public float getProgress() {
        long now = System.currentTimeMillis();
        long total = endMs - startMs;
        long elapsed = now - startMs;
        
        if (total <= 0) return 1.0f;
        return Math.max(0f, Math.min(1.0f, (float) elapsed / total));
    }

    /**
     * Получить время до завершения в миллисекундах
     */
    public long getTimeLeftMs() {
        long now = System.currentTimeMillis();
        return Math.max(0, endMs - now);
    }

    /**
     * Проверить, завершена ли тренировка
     */
    public boolean isCompleted() {
        return System.currentTimeMillis() >= endMs;
    }

    /**
     * Получить общую длительность тренировки
     */
    public long getTotalDurationMs() {
        return endMs - startMs;
    }

    @Override
    public String toString() {
        return String.format("SkillTraining{skill='%s', target=%d, progress=%.1f%%}", 
                           skillId, targetLevel, getProgress() * 100);
    }
}
